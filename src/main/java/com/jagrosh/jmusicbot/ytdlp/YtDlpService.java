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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
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
 * requested, and no worker thread is started. Tracks already in the index (YouTube hits,
 * or local files from the library folder) are still wrapped so they show their YouTube
 * metadata.
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
    private volatile @Nullable ScheduledExecutorService ownedScheduler;
    /** Runs library index updates off the player and audio threads. */
    private final Executor background;

    public YtDlpService(YtDlpSettings s, YtDlpLibrary library, YtDlpDownloader downloader, ProcessRunner runner) {
        this(s, library, downloader, runner, ForkJoinPool.commonPool());
    }

    /**
     * @param background runs library index updates (file I/O), so they never run on the thread
     *                   that called {@link #prepareForPlayback}
     */
    public YtDlpService(YtDlpSettings s, YtDlpLibrary library, YtDlpDownloader downloader, ProcessRunner runner,
                        Executor background) {
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
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ytdlp-prompt");
            t.setDaemon(true);
            return t;
        });
        YtDlpService service = new YtDlpService(settings, library, downloader, runner, scheduler);
        service.ownedScheduler = scheduler;
        service.prompt = new YtDlpPrompt(bot, service, new PendingPrompts(), scheduler);
        return service;
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

    /** Stops the download worker and the prompt timers. Safe to call more than once. */
    public void shutdown() {
        downloader.shutdown();
        ScheduledExecutorService scheduler = ownedScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
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

        if (isYoutube(track)) {
            String videoId = info.identifier;
            if (videoId == null || videoId.isEmpty()) {
                return track;
            }
            Optional<LibraryEntry> hit = library.findById(videoId);
            if (hit.isPresent()) {
                if (isAdopted(hit.get())) {
                    fillInMetadata(videoId, info);
                }
                return copyState(track, new LibraryAudioTrack(info, videoId, completed(hit.get()), null,
                        track.getSourceManager()));
            }
            if (enabled && settings.mode() == YtDlpSettings.Mode.ALWAYS && !info.isStream) {
                return wrap(track, guildId, true, null);
            }
            return track;
        }

        return wrapLibraryFile(track);
    }

    /**
     * Wraps a local track whose file is already in the library index, so the queue, history
     * and saved playlists show the library's title, author, length and URL as soon as the
     * track is queued, instead of waiting for {@link #prepareForPlayback} at play time.
     * Returns the same instance for a {@code null} track, a track that's already a
     * {@link LibraryAudioTrack}, a non-local track (e.g. YouTube; those are decided only at
     * play time), or a local file that isn't in the library index. Does no I/O beyond the
     * in-memory index lookup.
     */
    public AudioTrack wrapLibraryFile(AudioTrack track) {
        if (track == null || track instanceof LibraryAudioTrack) {
            return track;
        }
        Path localFile = localFile(track);
        if (localFile == null) {
            return track;
        }
        Optional<LibraryEntry> entry = library.findByFile(localFile);
        if (entry.isEmpty()) {
            return track;
        }
        LibraryEntry e = entry.get();
        return copyState(track, new LibraryAudioTrack(e.toTrackInfo(), e.videoId(), completed(e), null,
                track.getSourceManager()));
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
            return downloader.request(videoId, info, guildId, true);
        };
        LibraryAudioTrack wrapper = copyState(youtubeTrack,
                new LibraryAudioTrack(info, videoId, fileSource, gate, youtubeTrack.getSourceManager()));
        if (gate == null) {
            downloader.request(videoId, info, guildId, nextUp);
        }
        return wrapper;
    }

    /**
     * Requests the library file for a YouTube track, returning the downloader's shared future.
     * Never cancel or complete the returned future; use {@link #cancelGuild(long)}.
     */
    public CompletableFuture<LibraryEntry> requestDownload(AudioTrack youtubeTrack, long guildId, boolean nextUp) {
        AudioTrackInfo info = youtubeTrack.getInfo();
        return downloader.request(info.identifier, info, guildId, nextUp);
    }

    /**
     * Whether a track could be fetched by a download: a YouTube track that isn't already
     * wrapped, isn't a stream, and isn't in the library yet.
     */
    public boolean isDownloadable(AudioTrack track) {
        if (track == null || track instanceof LibraryAudioTrack || !isYoutube(track)) {
            return false;
        }
        AudioTrackInfo info = track.getInfo();
        if (info == null || info.isStream || info.identifier == null || info.identifier.isEmpty()) {
            return false;
        }
        return library.findById(info.identifier).isEmpty();
    }

    /**
     * Whether an entry was adopted from a file in the library folder rather than recorded from
     * a YouTube track, so it only has a title guessed from the filename: no author, no length.
     */
    private static boolean isAdopted(LibraryEntry entry) {
        return (entry.author() == null || entry.author().isEmpty()) && entry.lengthMs() <= 0;
    }

    /**
     * Records the YouTube track's metadata for an adopted library file, in the background
     * (it rewrites {@code library.json}). Re-checks first, so a second play doesn't record twice.
     */
    private void fillInMetadata(String videoId, AudioTrackInfo info) {
        try {
            background.execute(() -> {
                try {
                    Optional<LibraryEntry> current = library.findById(videoId);
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
