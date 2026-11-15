/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.ytdlp;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the optional yt-dlp download library. Owns the library index, the
 * downloader and the prompt, and decides at the one playback choke point
 * ({@link #prepareForPlayback}) whether a track plays from the library.
 * <p>
 * With {@code ytdlp.path} blank (the default) the feature is off: misses and every track
 * that isn't already in the library index are returned unchanged, no download is ever
 * requested, and no worker thread is started. Tracks already in the index (exact-key hits
 * from YouTube or another supported source, or local files from the library folder) are
 * still wrapped so they play from disk and show their recorded metadata.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpService.class);
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_PLAYLISTS_FOLDER = "Playlists";

    private final YtDlpSettings settings;
    private final YtDlpLibrary library;
    private final YtDlpDownloader downloader;
    private final ProcessRunner runner;
    /** Starts as {@link YtDlpSettings#enabled()}; turned off for this run if the version check fails. */
    private volatile boolean enabled;
    private volatile @Nullable YtDlpPrompt prompt;
    private volatile @Nullable MatchSuggestions matchSuggestions;
    private volatile @Nullable ScheduledExecutorService ownedScheduler;
    /** Runs Switch alignment jobs off the scheduler (which is single-threaded); see {@link MatchSuggestions}. */
    private volatile @Nullable ExecutorService alignmentExecutor;
    /** Runs library index updates off the player and audio threads. */
    private final Executor background;
    /**
     * Runs the slow library work the panel asks for (rescan, remove, stats, and its views) off the
     * JDA event thread, and off {@link #background}: in production that is the single prompt
     * thread, whose timers and suggestion scans mustn't wait behind a rescan.
     */
    private final Executor libraryWork;
    private volatile @Nullable ExecutorService ownedLibraryExecutor;

    public YtDlpService(YtDlpSettings s, YtDlpLibrary library, YtDlpDownloader downloader, ProcessRunner runner) {
        this(s, library, downloader, runner, ForkJoinPool.commonPool());
    }

    /**
     * @param background runs library index updates (file I/O), so they never run on the thread
     *                   that called {@link #prepareForPlayback}
     */
    public YtDlpService(YtDlpSettings s, YtDlpLibrary library, YtDlpDownloader downloader, ProcessRunner runner,
                        Executor background) {
        this(s, library, downloader, runner, background, background);
    }

    /**
     * @param libraryWork runs the library panel's slow work (rescan, remove, stats and its views),
     *                    see {@link #runLibraryWork}
     */
    public YtDlpService(YtDlpSettings s, YtDlpLibrary library, YtDlpDownloader downloader, ProcessRunner runner,
                        Executor background, Executor libraryWork) {
        this.libraryWork = libraryWork;
        this.settings = s;
        this.library = library;
        this.downloader = downloader;
        this.runner = runner;
        this.background = background;
        this.enabled = s.enabled();
    }

    /**
     * Builds the service from the bot's config. The library playlist file is
     * {@code <playlistsFolder>/<libraryPlaylist>.txt}. Missing settings (e.g. a mocked config)
     * fall back to the feature being off.
     */
    public static YtDlpService create(Bot bot) {
        BotConfig config = bot.getConfig();
        YtDlpSettings settings = config == null ? null : config.getYtDlpSettings();
        if (settings == null) {
            settings = YtDlpSettings.disabled();
        }
        String playlistsFolder = config == null ? null : config.getPlaylistsFolder();
        if (playlistsFolder == null || playlistsFolder.isBlank()) {
            playlistsFolder = DEFAULT_PLAYLISTS_FOLDER;
        }
        Path playlistsPath;
        try {
            playlistsPath = OtherUtil.getPath(playlistsFolder).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            LOGGER.warn("Invalid playlists folder '{}' for the yt-dlp library playlist; using '{}'",
                    playlistsFolder, DEFAULT_PLAYLISTS_FOLDER);
            playlistsPath = Path.of(DEFAULT_PLAYLISTS_FOLDER).toAbsolutePath().normalize();
        }
        Path playlistFile = playlistsPath.resolve(settings.libraryPlaylist() + ".txt");

        YtDlpLibrary library = new YtDlpLibrary(settings.libraryFolder(), playlistFile);
        ProcessRunner runner = new SystemProcessRunner();
        YtDlpDownloader downloader = new YtDlpDownloader(settings, library, runner,
                d -> Thread.sleep(d.toMillis()));

        // The pool creates its thread on its first task, so nothing runs while the feature is off
        // (apart from filling in an adopted library file's metadata, which works in any mode).
        // Single-threaded (FIFO) on purpose: queued- and play-time suggestion scans (MatchSuggestions)
        // rely on this to run one at a time, in order.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ytdlp-prompt");
            t.setDaemon(true);
            return t;
        });
        // Small and bounded: a Switch press needs at most two concurrent tasks here (the wait, and
        // the alignment job it waits on); it creates threads on demand and lets them die when idle.
        ExecutorService alignmentExecutor = new ThreadPoolExecutor(0, 4, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "ytdlp-align");
                    t.setDaemon(true);
                    return t;
                });
        // Two threads, created on demand: a rescan (or a file delete) never holds up the panel's
        // other views for long, and neither waits behind the prompt thread.
        ThreadPoolExecutor libraryExecutor = new ThreadPoolExecutor(2, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, "ytdlp-library");
                    t.setDaemon(true);
                    return t;
                });
        libraryExecutor.allowCoreThreadTimeOut(true);
        YtDlpService service = new YtDlpService(settings, library, downloader, runner, scheduler, libraryExecutor);
        service.ownedScheduler = scheduler;
        service.ownedLibraryExecutor = libraryExecutor;
        service.alignmentExecutor = alignmentExecutor;
        service.prompt = new YtDlpPrompt(bot, service, new PendingPrompts(), scheduler);
        service.matchSuggestions = new MatchSuggestions(bot, service, service.prompt, scheduler, alignmentExecutor);
        return service;
    }

    /**
     * The startup warning for a JVM that can't use non-ASCII file names, or empty. On Linux and
     * macOS, Java encodes file names with the locale's charset; without a UTF-8 locale (e.g. a
     * container or service with no {@code LANG}) a song file named after an accented or
     * non-Latin title can't be opened. Windows file names don't depend on it.
     *
     * @param osName      the {@code os.name} system property
     * @param jnuEncoding the {@code sun.jnu.encoding} system property
     */
    public static Optional<String> filenameEncodingWarning(@Nullable String osName, @Nullable String jnuEncoding) {
        if (osName != null && osName.startsWith("Windows")) {
            return Optional.empty();
        }
        if (jnuEncoding != null && (jnuEncoding.equalsIgnoreCase("UTF-8") || jnuEncoding.equalsIgnoreCase("UTF8"))) {
            return Optional.empty();
        }
        return Optional.of("Java can't use non-ASCII file names here (file name encoding " + jnuEncoding
                + "), so songs with accented or non-Latin titles won't download or play from the library. "
                + "Start the bot with a UTF-8 locale, e.g. LANG=C.UTF-8.");
    }

    /**
     * Loads the library index whenever the library folder exists. If the feature is on,
     * checks {@code yt-dlp --version}: logs the version, or warns and turns the feature off
     * for this run. Then starts the download worker.
     */
    public void start() {
        try {
            library.load();
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Couldn't load the yt-dlp library in {}: {}", settings.libraryFolder(), ex.toString());
        }
        if (!enabled) {
            return;
        }

        String failure;
        try {
            ProcessResult result = runner.run(YtDlpCommand.version(settings.path()), VERSION_TIMEOUT);
            if (!result.timedOut() && result.exitCode() == 0) {
                String version = result.stdout() == null ? "" : result.stdout().strip();
                LOGGER.info("yt-dlp {} found at {} ({} mode); library folder {}", version, settings.path(),
                        settings.mode().name().toLowerCase(), settings.libraryFolder());
                filenameEncodingWarning(System.getProperty("os.name"), System.getProperty("sun.jnu.encoding"))
                        .ifPresent(LOGGER::warn);
                downloader.start();
                return;
            }
            String summary = YtDlpCommand.errorSummary(result, (int) VERSION_TIMEOUT.toSeconds());
            failure = summary.isBlank() ? "exit code " + result.exitCode() : summary;
        } catch (IOException ex) {
            failure = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failure = "interrupted";
        } catch (RuntimeException ex) {
            failure = ex.toString();
        }
        enabled = false;
        LOGGER.warn("yt-dlp could not be run from '{}' ({}). The yt-dlp library is turned off for this run; "
                + "check ytdlp.path in your config.", settings.path(), failure);
    }

    /** Stops the download worker, the prompt timers and the alignment executor. Safe to call more than once. */
    public void shutdown() {
        downloader.shutdown();
        ScheduledExecutorService scheduler = ownedScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        ExecutorService align = alignmentExecutor;
        if (align != null) {
            align.shutdownNow();
        }
        ExecutorService libraryExecutor = ownedLibraryExecutor;
        if (libraryExecutor != null) {
            libraryExecutor.shutdownNow();
        }
    }

    /** Whether the feature is on for this run: a path is configured and the version check didn't fail. */
    public boolean isEnabled() {
        return enabled;
    }

    public YtDlpSettings.Mode mode() {
        return settings.mode();
    }

    public YtDlpSettings settings() {
        return settings;
    }

    public YtDlpLibrary library() {
        return library;
    }

    /** The prompt, or null for a service built without a bot (e.g. in tests). */
    public @Nullable YtDlpPrompt getPrompt() {
        return prompt;
    }

    /** The similar-song suggestions, or null for a service built without a bot (e.g. in tests). */
    public @Nullable MatchSuggestions getMatchSuggestions() {
        return matchSuggestions;
    }

    /** Records, in the background, that a person said {@code fromKey} is the same song as library song {@code toKey}. */
    public void rememberLink(String fromKey, String toKey) {
        inBackground("link " + fromKey + " to " + toKey, () -> library.alias(fromKey, toKey));
    }

    /** Records, in the background, that a person said {@code fromKey} is not the same song as {@code toKey}. */
    public void rememberRejection(String fromKey, String toKey) {
        inBackground("reject " + toKey + " for " + fromKey, () -> library.reject(fromKey, toKey));
    }

    /** The library's songs matching {@code query}, for the library panel. In-memory only: no IO. */
    public List<LibraryEntry> searchLibrary(String query, int limit) {
        return library.search(query, limit);
    }

    /** The requested keys aliased to library song {@code key}, for the library panel. In-memory only: no IO. */
    public List<String> aliasesTo(String key) {
        return library.aliasesTo(key);
    }

    /** Removes a library song on the library executor (deletes files; rewrites the index and playlist). */
    public CompletableFuture<YtDlpLibrary.RemoveResult> removeFromLibrary(String key, boolean deleteFile,
                                                                           long actorUserId) {
        return supplyOn(libraryWork, () -> library.remove(key, deleteFile, actorUserId));
    }

    /** Re-scans the library folder on the library executor, as the library panel's Rescan button does. */
    public CompletableFuture<YtDlpLibrary.ScanSummary> rescanLibrary() {
        return supplyOn(libraryWork, library::rescan);
    }

    /** The library's song count, total size and folder, on the library executor (sizes every file). */
    public CompletableFuture<YtDlpLibrary.LibraryStats> libraryStats() {
        return supplyOn(libraryWork, library::stats);
    }

    /**
     * Runs {@code task} on the library executor, for the library panel: anything that touches the
     * library (its lock, its files) after the interaction was acknowledged, so the JDA event
     * thread never waits on it. If the executor refuses the task (shut down), runs it here.
     */
    public void runLibraryWork(Runnable task) {
        Runnable logged = () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                LOGGER.warn("Library panel work failed: {}", ex.toString(), ex);
            }
        };
        try {
            libraryWork.execute(logged);
        } catch (RuntimeException ex) { // e.g. the executor is shut down
            LOGGER.debug("Running library work on the caller's thread: {}", ex.toString());
            logged.run();
        }
    }

    /**
     * Removes the link (alias) behind the song now playing, if it has one: a library copy
     * played for a linked song, or a requested song whose key is linked.
     *
     * @return what was unlinked, or empty if the song isn't linked to a library song
     */
    public Optional<Unlinked> unlink(@Nullable AudioTrack playing) throws IOException {
        if (playing == null || playing.getInfo() == null) {
            return Optional.empty();
        }
        String fromKey;
        String requested;
        if (playing instanceof LibraryAudioTrack libraryTrack) {
            fromKey = libraryTrack.getLinkedFrom();
            String title = libraryTrack.getLinkedFromTitle();
            requested = title != null && !title.isBlank() ? title : null; // null: named after the library song
        } else {
            fromKey = LibraryKeys.of(playing).orElse(null);
            requested = playing.getInfo().title;
        }
        if (fromKey == null) {
            return Optional.empty();
        }
        Optional<String> target = library.unalias(fromKey);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        String librarySong = library.find(target.get()).map(LibraryEntry::title).orElse(target.get());
        LOGGER.info("Unlinked {} from library song {}", fromKey, target.get());
        // A copy loaded straight from a link (LibrarySourceManager) never saw the requested title.
        return Optional.of(new Unlinked(requested == null ? librarySong : requested, librarySong));
    }

    /** What {@link #unlink} removed: the requested song's name and the library song's title. */
    public record Unlinked(String requested, String librarySong) {
    }

    private interface IoTask {
        void run() throws IOException;
    }

    private void inBackground(String what, IoTask task) {
        try {
            background.execute(() -> {
                try {
                    task.run();
                } catch (IOException | RuntimeException ex) {
                    LOGGER.warn("Couldn't {} in the library: {}", what, ex.toString());
                }
            });
        } catch (RuntimeException ex) { // e.g. the executor is shut down
            LOGGER.warn("Couldn't {} in the library: {}", what, ex.toString());
        }
    }

    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    /** Runs an IO-doing library call on {@code executor}, completing the returned future with its result. */
    private static <T> CompletableFuture<T> supplyOn(Executor executor, IoSupplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (IOException | RuntimeException ex) {
                    future.completeExceptionally(ex);
                }
            });
        } catch (RuntimeException ex) { // e.g. the executor is shut down
            future.completeExceptionally(ex);
        }
        return future;
    }

    /**
     * The single choke point every track passes through right before the player starts it.
     * See the decision table in the class docs and the design. Never throws for odd tracks;
     * anything it doesn't recognise is returned unchanged.
     */
    public AudioTrack prepareForPlayback(AudioTrack track, long guildId) {
        if (track == null || track instanceof LibraryAudioTrack) {
            return track;
        }
        AudioTrackInfo info = track.getInfo();
        if (info == null) {
            return track;
        }

        Optional<String> key = LibraryKeys.of(track);
        if (isYoutube(track)) {
            if (key.isEmpty()) {
                return track;
            }
            Optional<LibraryEntry> hit = library.find(key.get());
            if (hit.isPresent()) {
                if (isAdopted(hit.get())) {
                    fillInMetadata(key.get(), info);
                }
                return copyState(track, new LibraryAudioTrack(info, key.get(), completed(hit.get()), null,
                        track.getSourceManager()));
            }
            Optional<LibraryAudioTrack> linked = linkedCopy(track, key.get());
            if (linked.isPresent()) {
                return linked.get();
            }
            if (enabled && settings.mode() == YtDlpSettings.Mode.ALWAYS) {
                return wrap(track, guildId, true, null);
            }
            return track;
        }

        // Another supported source (e.g. a SoundCloud song saved earlier): an exact library hit
        // plays from disk, keeping the original track's info and source. Misses are unchanged;
        // prompt and always mode stay YouTube-only.
        if (key.isPresent()) {
            Optional<LibraryEntry> hit = library.find(key.get());
            if (hit.isPresent()) {
                return copyState(track, new LibraryAudioTrack(info, key.get(), completed(hit.get()), null,
                        track.getSourceManager()));
            }
            Optional<LibraryAudioTrack> linked = linkedCopy(track, key.get());
            return linked.isPresent() ? linked.get() : track;
        }

        return wrapLibraryFile(track);
    }

    /**
     * Wraps a local track whose file is already in the library index, so the queue, history
     * and saved playlists show the library's title, author, length and URL as soon as the
     * track is queued, instead of waiting for {@link #prepareForPlayback} at play time.
     * With yt-dlp enabled, a track from a supported source whose key a person linked to a
     * library song is wrapped too (see {@link #linkedCopy(AudioTrack, String, LibraryEntry)}).
     * Returns the same instance for a {@code null} track, a track that's already a
     * {@link LibraryAudioTrack}, any other non-local track (e.g. a YouTube exact hit or miss;
     * those are decided only at play time), or a local file that isn't in the library index.
     * For an adopted file (no YouTube metadata recorded yet) the local track's own length and
     * author are kept. Does no I/O on the calling thread beyond the index lookup; filling in a
     * linked entry's missing details is handed to the background executor.
     */
    public AudioTrack wrapLibraryFile(AudioTrack track) {
        if (track == null || track instanceof LibraryAudioTrack) {
            return track;
        }
        Optional<String> key = LibraryKeys.of(track);
        if (key.isPresent()) {
            // A song a person linked to a library song (yt-dlp enabled only) shows the library copy
            // at once. Exact hits and misses are still decided at play time.
            if (!enabled || library.find(key.get()).isPresent()) {
                return track;
            }
            Optional<LibraryAudioTrack> linked = linkedCopy(track, key.get());
            return linked.isPresent() ? linked.get() : track;
        }
        Path localFile = localFile(track);
        if (localFile == null) {
            return track;
        }
        Optional<LibraryEntry> entry = library.findByFile(localFile);
        if (entry.isEmpty()) {
            return track;
        }
        LibraryEntry hit = entry.get();
        if (!isAdopted(hit)) {
            return copyState(track, libraryTrack(hit, track.getSourceManager()));
        }
        // An adopted file's details came from its name or tags, not a real track, so it may lack a
        // length or author; keep what the local track itself knows, otherwise the queue shows 00:00.
        AudioTrackInfo local = track.getInfo();
        String author = hit.author().isEmpty() ? local.author : hit.author();
        AudioTrackInfo info = new AudioTrackInfo(hit.title(), author, Math.max(hit.lengthMs(), local.length),
                hit.toTrackInfo().identifier, false, hit.uri(), hit.artworkUrl(), null);
        return copyState(track, new LibraryAudioTrack(info, hit.key(), completed(hit), null,
                track.getSourceManager()));
    }

    /**
     * Builds a {@link LibraryAudioTrack} straight from a library entry, with a completed
     * future and the given display source manager (typically the YouTube source, so the
     * queue, history and saved playlists record the YouTube URL). For a source manager
     * (e.g. {@link LibrarySourceManager}) that builds a track directly from the index at
     * {@code loadItem} time, rather than wrapping an existing {@link AudioTrack}.
     */
    public LibraryAudioTrack libraryTrack(LibraryEntry entry, @Nullable AudioSourceManager displaySource) {
        return new LibraryAudioTrack(entry.toTrackInfo(), entry.key(), completed(entry), null, displaySource);
    }

    /**
     * The library copy for a requested track whose key is linked to a library song (an alias),
     * if yt-dlp is enabled. See {@link #linkedCopy(AudioTrack, String, LibraryEntry)}.
     */
    private Optional<LibraryAudioTrack> linkedCopy(AudioTrack requested, String requestedKey) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<LibraryEntry> target = library.resolve(requestedKey);
        if (target.isEmpty() || target.get().key().equals(requestedKey)) {
            return Optional.empty();
        }
        return Optional.of(linkedCopy(requested, requestedKey, target.get()));
    }

    /**
     * Builds the library copy that plays in place of {@code requested}, a song linked to the
     * library entry {@code target}. It shows the library song's title (and its author, length and
     * artwork, or the requested track's where the entry has none), but keeps the requested track's
     * identifier, URI, source, user data and start position, so queue links, history and saved
     * playlists still point at what was asked for. If the entry is missing details, they are filled
     * in from the requested track in the background.
     */
    public LibraryAudioTrack linkedCopy(AudioTrack requested, String requestedKey, LibraryEntry target) {
        AudioTrackInfo req = requested.getInfo();
        String author = !target.author().isEmpty() ? target.author() : req.author;
        long length = target.lengthMs() > 0 ? target.lengthMs() : req.length;
        String artwork = target.artworkUrl() != null && !target.artworkUrl().isBlank() ? target.artworkUrl()
                : req.artworkUrl;
        AudioTrackInfo info = new AudioTrackInfo(target.title(), author, length, req.identifier, false, req.uri,
                artwork, req.isrc);
        if (target.missingDetails()) {
            fillInLinkedEntry(target.key(), req);
        }
        return copyState(requested, new LibraryAudioTrack(info, target.key(), completed(target), null,
                requested.getSourceManager(), requestedKey, req.title));
    }

    /**
     * For {@link LibrarySourceManager}: the library copy for a YouTube link whose video is linked
     * to a library song, built without loading the video. Empty if yt-dlp is off, the video isn't
     * linked, or the linked entry has no length, or no author (then the link goes through YouTube
     * once, so {@link #prepareForPlayback} can fill those in from the real track). An imported
     * ({@code file:}) entry is never given an author, so for one without its own, artwork counts
     * in its place.
     */
    Optional<LibraryAudioTrack> linkedLink(String videoId, @Nullable AudioSourceManager youtubeSource) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<LibraryEntry> resolved = library.resolve(videoId);
        if (resolved.isEmpty() || resolved.get().key().equals(videoId)) {
            return Optional.empty();
        }
        LibraryEntry target = resolved.get();
        boolean hasArtwork = target.artworkUrl() != null && !target.artworkUrl().isBlank();
        boolean ready = target.lengthMs() > 0
                && (!target.author().isEmpty() || (LibraryKeys.isFileKey(target.key()) && hasArtwork));
        if (!ready) {
            return Optional.empty();
        }
        String url = LibraryKeys.youtubeUrl(videoId);
        AudioTrackInfo info = new AudioTrackInfo(target.title(), target.author(), target.lengthMs(), videoId, false,
                url, target.artworkUrl(), null);
        return Optional.of(new LibraryAudioTrack(info, target.key(), completed(target), null, youtubeSource,
                videoId, null));
    }

    /** Fills a linked library entry's missing author, length and artwork from the requested track, in the background. */
    public void fillInLinkedEntry(String libraryKey, AudioTrackInfo requested) {
        try {
            background.execute(() -> {
                try {
                    library.fillMissing(libraryKey, requested);
                } catch (IOException | RuntimeException ex) {
                    LOGGER.warn("Couldn't fill in details for library song {}: {}", libraryKey, ex.toString());
                }
            });
        } catch (RuntimeException ex) { // e.g. the executor is shut down
            LOGGER.debug("Skipped filling in details for library song {}: {}", libraryKey, ex.toString());
        }
    }

    /**
     * {@code always} mode only: starts a background (not next-up) download for the track after
     * the one now playing, so it is usually ready in time.
     */
    public void prefetch(AudioTrack next, long guildId) {
        if (!enabled || settings.mode() != YtDlpSettings.Mode.ALWAYS || !isDownloadable(next)) {
            return;
        }
        requestDownload(next, guildId, false);
    }

    /**
     * Wraps a YouTube track so it plays from the library, downloading it first if needed.
     * The wrapper keeps the track's info, source manager, user data and start position.
     * With no {@code gate}, the download is requested now with priority {@code nextUp}.
     * With a gate, nothing is requested until the gate opens and the track starts.
     * Whenever the wrapper (or a clone of it, e.g. from history) starts, it asks the
     * downloader again as next up; that joins a running job or hits the library.
     * The caller checks {@link #isEnabled()}.
     */
    public LibraryAudioTrack wrap(AudioTrack youtubeTrack, long guildId, boolean nextUp,
                                  @Nullable CompletableFuture<Void> gate) {
        AudioTrackInfo info = youtubeTrack.getInfo();
        String videoId = info.identifier;
        String url = LibraryKeys.youtubeUrl(videoId);
        Supplier<CompletableFuture<LibraryEntry>> fileSource = () -> {
            if (!enabled) {
                return CompletableFuture.failedFuture(new YtDlpException("yt-dlp is not available"));
            }
            // Clones share this supplier but not the gate. A clone of a prompt's hold (e.g. from
            // "previous" while the prompt is open) may download only if someone said yes.
            if (gate != null && (!gate.isDone() || gate.isCompletedExceptionally())) {
                return CompletableFuture.failedFuture(new YtDlpException(
                        "its download prompt wasn't accepted; request the song again to get a new prompt"));
            }
            return downloader.request(videoId, url, info, guildId, true);
        };
        LibraryAudioTrack wrapper = copyState(youtubeTrack,
                new LibraryAudioTrack(info, videoId, fileSource, gate, youtubeTrack.getSourceManager()));
        if (gate == null) {
            downloader.request(videoId, url, info, guildId, nextUp);
        }
        return wrapper;
    }

    /**
     * Requests the library file for a track from any supported source (see {@link LibraryKeys}),
     * returning the downloader's shared future: yt-dlp fetches the watch URL for YouTube and the
     * track's URI otherwise, and the file is recorded under the track's library key. A track with
     * no key (a stream, a local file, an HTTP link, an unsupported source) gets a failed future and
     * no download. Never cancel or complete the returned future; use {@link #cancelGuild(long)}.
     */
    public CompletableFuture<LibraryEntry> requestDownload(AudioTrack track, long guildId, boolean nextUp) {
        Optional<String> key = LibraryKeys.of(track);
        Optional<String> url = LibraryKeys.downloadUrl(track);
        if (key.isEmpty() || url.isEmpty()) {
            return CompletableFuture.failedFuture(new YtDlpException("yt-dlp can't download this track"));
        }
        return downloader.request(key.get(), url.get(), track.getInfo(), guildId, nextUp);
    }

    /**
     * Whether a track could be fetched by a download: a YouTube track that isn't already
     * wrapped, isn't a stream, and isn't in the library yet, nor (with yt-dlp enabled) linked
     * to a library song, which plays that copy instead.
     */
    public boolean isDownloadable(AudioTrack track) {
        if (track == null || track instanceof LibraryAudioTrack || !isYoutube(track)) {
            return false;
        }
        Optional<String> key = LibraryKeys.of(track);
        if (key.isEmpty()) {
            return false;
        }
        return (enabled ? library.resolve(key.get()) : library.find(key.get())).isEmpty();
    }

    /**
     * Whether an entry was adopted from a file in the library folder rather than recorded from
     * a real track (see {@link LibraryEntry#adopted()}), so its details came only from the file's
     * name or tags and may lack an author or length.
     * Package-private so {@link LibrarySourceManager} can reuse the same check: it must not
     * short-circuit an adopted entry's first link, so that link still goes through YouTube once
     * and {@link #prepareForPlayback} gets a chance to fill the real metadata in.
     */
    static boolean isAdopted(LibraryEntry entry) {
        return entry.adopted();
    }

    /**
     * Records the YouTube track's metadata for an adopted library file, in the background
     * (it rewrites {@code library.json}). Re-checks first, so a second play doesn't record twice.
     */
    private void fillInMetadata(String videoId, AudioTrackInfo info) {
        try {
            background.execute(() -> {
                try {
                    Optional<LibraryEntry> current = library.find(videoId);
                    if (current.isPresent() && isAdopted(current.get())) {
                        library.record(videoId, current.get().file(), info);
                        LOGGER.info("Filled in YouTube metadata for library file {}", current.get().file());
                    }
                } catch (IOException | RuntimeException ex) {
                    LOGGER.warn("Couldn't record metadata for library video {}: {}", videoId, ex.toString());
                }
            });
        } catch (RuntimeException ex) { // e.g. the executor is shut down
            LOGGER.debug("Skipped recording metadata for library video {}: {}", videoId, ex.toString());
        }
    }

    /** Withdraws this guild from every queued download (stop, or leaving voice). */
    public void cancelGuild(long guildId) {
        downloader.cancelGuild(guildId);
    }

    public static boolean isYoutube(AudioTrack t) {
        AudioSourceManager source = t.getSourceManager();
        return source != null && "youtube".equals(source.getSourceName());
    }

    private static Supplier<CompletableFuture<LibraryEntry>> completed(LibraryEntry entry) {
        CompletableFuture<LibraryEntry> done = CompletableFuture.completedFuture(entry);
        return () -> done;
    }

    /** Copies the user data (request metadata) and any start position onto the wrapper. */
    private static LibraryAudioTrack copyState(AudioTrack original, LibraryAudioTrack wrapper) {
        wrapper.setUserData(original.getUserData());
        try {
            long position = original.getPosition();
            if (position > 0) {
                wrapper.setPosition(position);
            }
        } catch (RuntimeException ex) {
            LOGGER.debug("Couldn't carry the start position over to the library track: {}", ex.toString());
        }
        return wrapper;
    }

    /** The file of a track from lavaplayer's local source, or null for any other track. */
    private static @Nullable Path localFile(AudioTrack track) {
        AudioSourceManager source = track.getSourceManager();
        if (source == null || !"local".equals(source.getSourceName())) {
            return null;
        }
        String identifier = track.getInfo().identifier;
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        try {
            return Path.of(identifier);
        } catch (InvalidPathException ex) {
            return null;
        }
    }
}
