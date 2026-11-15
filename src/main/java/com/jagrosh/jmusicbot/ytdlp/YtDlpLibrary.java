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
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The on-disk index of songs in the yt-dlp library folder, plus the playlist file that lists
 * the bot's own downloads for offline playback.
 *
 * <p>The index file, {@code <libraryFolder>/library.json}, maps each library key (see
 * {@link LibraryKeys}) to the metadata known for that song, and also keeps <em>aliases</em>
 * (requested key → library key: "this other upload is the same song") and <em>rejections</em>
 * (pairs a person said are not the same song). Two schemas are read:
 * <ul>
 *   <li>v1: a flat {@code videoId → entry} map;</li>
 *   <li>v2: {@code {"version": 2, "entries": {...}, "aliases": {...}, "rejections": [[from, to], ...]}}.</li>
 * </ul>
 * Saving always writes v2, atomically (temp file, then move). Loading is tolerant: a bad entry,
 * alias or rejection is skipped on its own, and an index that can't be parsed at all is copied to
 * {@code library.json.corrupt} before anything can overwrite it.
 *
 * <p>{@link #load()} also scans the folder for files the index doesn't know yet (songs downloaded
 * earlier and copied in by hand), taking each one's details from, best first: a yt-dlp
 * {@code .info.json} sidecar; a YouTube {@code [id]} in the filename (as v1 did); tags and length
 * read by probing the file with lavaplayer's local source; the filename itself. Files are never
 * moved, renamed or modified, and only files missing from the index are probed.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpLibrary.class);
    private static final String INDEX_FILE_NAME = "library.json";
    private static final String CORRUPT_COPY_NAME = "library.json.corrupt";
    private static final String SIDECAR_SUFFIX = ".info.json";
    private static final int SCHEMA_VERSION = 2;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Lavaplayer's placeholders for a file with no tags; treated as missing. */
    private static final String UNKNOWN_TITLE = "Unknown title";
    private static final String UNKNOWN_ARTIST = "Unknown artist";

    /**
     * Matches a trailing {@code [<videoId>].<ext>} suffix. The video ID may contain
     * {@code _} or {@code -} but must be exactly 11 characters, matching a real YouTube
     * video ID, so catalogue numbers in hand-placed files (e.g. {@code "Thiago [HARLO004].opus"})
     * aren't misread as video IDs.
     */
    private static final Pattern BRACKET_SUFFIX = Pattern.compile("^(.*)\\[([A-Za-z0-9_-]{11})\\]\\.[^.\\s\\[\\]]+$");

    /** A trailing bracketed catalogue code such as {@code [UTR035]}: letters, digits, at least one digit. */
    private static final Pattern CATALOGUE_CODE = Pattern.compile("\\s*\\[[A-Za-z0-9_.-]*\\d[A-Za-z0-9_.-]*\\]\\s*$");

    /** Extensions that are never audio: cover art, text, subtitles and similar files kept beside songs. */
    private static final Set<String> NON_AUDIO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp",
            "txt", "json", "description", "vtt", "srt", "ass", "lrc", "nfo", "log", "ini", "db", "url", "tmp",
            "corrupt", "pdf", "zip", "rar", "7z", "m3u", "m3u8", "pls", "cue", "md", "htm", "html", "sfv", "md5");

    /** Probes a file for its tags and length. Abstracted so tests don't need real audio files. */
    @FunctionalInterface
    public interface Prober {
        /** The file's details, or empty if it isn't audio lavaplayer can play. */
        Optional<ProbeResult> probe(Path file);
    }

    /**
     * What a probe found. {@code title} and {@code author} may be null or lavaplayer's
     * "Unknown title"/"Unknown artist" placeholders; {@code lengthMs} may be 0 or unknown.
     */
    public record ProbeResult(@Nullable String title, @Nullable String author, long lengthMs) {
    }

    /**
     * Deletes a media file for {@link #remove}. Abstracted so tests can simulate a delete failure;
     * public for the same reason {@link Prober} is (this package's tests live in a sibling
     * {@code unit.ytdlp} package, which needs to be able to target it with a lambda).
     */
    @FunctionalInterface
    public interface FileDeleter {
        void delete(Path file) throws IOException;
    }

    /**
     * Reads the metadata in a media file's yt-dlp {@code .info.json} sidecar. Abstracted so tests
     * can count reads; public for the same reason {@link Prober} is.
     */
    @FunctionalInterface
    public interface SidecarReader {
        /** The sidecar's metadata, or empty if there's no readable sidecar beside {@code mediaFile}. */
        Optional<SidecarMetadata> read(Path mediaFile);
    }

    /** What {@link #remove} did. */
    public record RemoveResult(boolean removed, boolean fileDeleted, @Nullable String problem) {
    }

    /** What {@link #rescan} found, compared to the in-memory index just before it ran. */
    public record ScanSummary(int added, int updated, int missing) {
    }

    /** What {@link #stats} reports about the library. */
    public record LibraryStats(int songs, long totalBytes, Path folder) {
    }

    private static final LocalAudioSourceManager LOCAL = new LocalAudioSourceManager();

    /** Probes with lavaplayer's local source ({@code loadItem} on the path), as {@link LibraryAudioTrack} plays it. */
    public static Prober localProber() {
        return file -> {
            AudioItem item;
            try {
                item = LOCAL.loadItem(null, new AudioReference(file.toString(), null));
            } catch (FriendlyException ex) {
                return Optional.empty();
            }
            if (!(item instanceof AudioTrack track)) {
                return Optional.empty();
            }
            AudioTrackInfo info = track.getInfo();
            return Optional.of(new ProbeResult(info.title, info.author, info.length));
        };
    }

    private final Path libraryFolder;
    private final Path playlistFile;
    private final Prober prober;
    private final FileDeleter fileDeleter;
    private final SidecarReader sidecarReader;
    private final Map<String, LibraryEntry> entries = new LinkedHashMap<>();
    /**
     * The same entries keyed by normalized absolute file path, so {@link #findByFile} (run once per
     * queued track) is a map lookup rather than a scan of the whole library. Kept in step with
     * {@link #entries} by {@link #load} and {@link #record}.
     */
    private final Map<Path, LibraryEntry> entriesByFile = new HashMap<>();
    /** Requested key → library key. */
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Set<Pair> rejections = new LinkedHashSet<>();
    /**
     * Keys whose sidecar has already been read into their entry (saved as an optional
     * {@code "metadataChecked": true} on the entry), so a load or rescan doesn't parse the whole
     * {@code .info.json} again for an entry it gave no artist, track or ISRC.
     */
    private final Set<String> metadataChecked = new HashSet<>();
    /**
     * Bumped by every change to the in-memory index outside a scan. A scan runs without this
     * object's lock and swaps its result in only if this is unchanged, so it never wipes out a
     * change made while it ran.
     */
    private long generation;
    /** Serializes {@link #load()} and {@link #rescan()}; always taken before this object's lock. */
    private final Object scanLock = new Object();
    /**
     * Why saving is blocked for this run, or null: an unreadable index couldn't be copied aside, or a
     * load failed after an index existed. Saving would then destroy what the index holds.
     */
    private volatile @Nullable String saveBlocked;

    public YtDlpLibrary(Path libraryFolder, Path playlistFile) {
        this(libraryFolder, playlistFile, localProber());
    }

    public YtDlpLibrary(Path libraryFolder, Path playlistFile, Prober prober) {
        this(libraryFolder, playlistFile, prober, Files::delete);
    }

    /** For tests: lets a delete failure be simulated without relying on real file-locking tricks. */
    public YtDlpLibrary(Path libraryFolder, Path playlistFile, Prober prober, FileDeleter fileDeleter) {
        this(libraryFolder, playlistFile, prober, fileDeleter, SidecarMetadata::read);
    }

    /** For tests: also lets sidecar reads be counted. */
    public YtDlpLibrary(Path libraryFolder, Path playlistFile, Prober prober, FileDeleter fileDeleter,
                        SidecarReader sidecarReader) {
        this.libraryFolder = libraryFolder;
        this.playlistFile = playlistFile;
        this.prober = prober;
        this.fileDeleter = fileDeleter;
        this.sidecarReader = sidecarReader;
    }

    /**
     * Loads {@code library.json} (v1 or v2), drops entries whose file no longer exists (with the
     * aliases and rejections that point at them), and indexes any file in the library folder the
     * index doesn't know yet. An indexed file that moved with the library folder is found again by
     * name. Saves the index only if the scan imported a file from a sidecar or a probe, read a
     * sidecar into an entry, or dropped an alias or rejection; an entry dropped on its own is
     * dropped in memory and persisted by the next save, as in v1. Does nothing, and writes nothing,
     * if the library folder doesn't exist. The folder walk, probes and sidecar reads run without
     * this object's lock (see {@link #scanAndSwap}).
     */
    public void load() throws IOException {
        synchronized (scanLock) {
            if (!Files.isDirectory(libraryFolder)) {
                return;
            }
            Path indexFile = libraryFolder.resolve(INDEX_FILE_NAME);
            boolean indexExisted = Files.exists(indexFile);
            boolean loaded = false;
            Swapped swapped;
            try {
                swapped = scanAndSwap(indexFile, false);
                loaded = true;
            } finally {
                if (!loaded && indexExisted && saveBlocked == null) {
                    // What the index holds isn't in memory, so the next save would write only new data.
                    saveBlocked = "loading the library failed earlier this run";
                    LOGGER.warn("Loading the library in {} failed; {} won't be saved this run", libraryFolder,
                            INDEX_FILE_NAME);
                }
            }
            // Outside the guard above: the index is in memory now, so a failed write fails only itself.
            if (swapped.save()) {
                synchronized (this) {
                    saveIndex();
                }
            }
        }
    }

    /** At most this many scans per load or rescan; the last runs under the lock, so it always lands. */
    private static final int MAX_SCAN_ATTEMPTS = 3;

    /**
     * Scans (reads the index, walks the folder, probes new files, reads sidecars) without this
     * object's lock, then takes it only to swap the result in; the caller saves if told to. If the
     * in-memory index changed while the scan ran (a download recorded, a link saved), the scan is
     * thrown away and run again from the index as now saved; the last attempt holds the lock
     * throughout. The caller holds {@link #scanLock}.
     *
     * @param rescan whether to also save when the swap changed the in-memory entries (a rescan
     *               does; a load saves only for the reasons {@link #load()} lists)
     */
    private Swapped scanAndSwap(Path indexFile, boolean rescan) throws IOException {
        for (int attempt = 1; ; attempt++) {
            if (attempt == MAX_SCAN_ATTEMPTS) {
                synchronized (this) {
                    return swap(scan(indexFile), rescan);
                }
            }
            long startGeneration;
            synchronized (this) {
                startGeneration = generation;
            }
            ScanResult result = scan(indexFile);
            synchronized (this) {
                if (generation == startGeneration) {
                    return swap(result, rescan);
                }
            }
            LOGGER.debug("The library changed during a scan; scanning again");
        }
    }

    /** What a swap changed, and whether the index should now be saved. */
    private record Swapped(ScanSummary summary, boolean save) {
    }

    /** What one {@link #scan} found, ready to swap into memory. */
    private record ScanResult(Map<String, LibraryEntry> entries, Map<Path, LibraryEntry> byFile,
                              Map<String, String> aliases, Set<Pair> rejections, Set<String> checked,
                              boolean save, ScanCounts counts, int fromIndex, @Nullable String blocked) {
    }

    /**
     * Builds the index from {@code library.json} and the folder, touching nothing in memory. The
     * result's {@code save} says whether the index should be saved: the scan imported a file from
     * a sidecar or a probe, read a sidecar into an entry, or dropped an alias or rejection.
     */
    private ScanResult scan(Path indexFile) throws IOException {
        ParsedIndex parsed = readIndex(indexFile);
        Map<String, LibraryEntry> loaded = new LinkedHashMap<>();
        for (LibraryEntry entry : parsed.entries.values()) {
            if (Files.exists(entry.file())) {
                loaded.put(entry.key(), entry);
                continue;
            }
            // The index stores absolute paths; if the library folder was moved, find the file by
            // name in the folder as it is now, so its recorded details aren't lost.
            Path here = entry.file().getFileName() == null ? null
                    : normalize(libraryFolder.resolve(entry.file().getFileName().toString()));
            if (here != null && Files.isRegularFile(here)) {
                String uri = entry.uri().equals(entry.file().toString()) ? here.toString() : entry.uri();
                loaded.put(entry.key(), new LibraryEntry(entry.key(), here, entry.title(), entry.author(),
                        entry.lengthMs(), entry.artworkUrl(), uri, entry.source(), entry.nameParts(),
                        entry.adopted(), entry.artist(), entry.track(), entry.album(), entry.releaseYear(),
                        entry.isrc()));
            }
        }
        int fromIndex = loaded.size();
        Set<String> checked = new HashSet<>(parsed.checked);
        checked.retainAll(loaded.keySet());
        int backfilled = 0;
        for (String key : new ArrayList<>(loaded.keySet())) {
            LibraryEntry entry = loaded.get(key);
            if (checked.contains(key) || entry.artist() != null || entry.track() != null || entry.isrc() != null) {
                continue;
            }
            Optional<SidecarMetadata> metadata = sidecarReader.read(entry.file());
            if (metadata.isEmpty()) {
                continue; // no sidecar (yet): looking again next time is one cheap file check
            }
            // Read once: a sidecar that names no artist, track or ISRC isn't parsed again on every scan.
            checked.add(key);
            backfilled++;
            loaded.put(key, entry.withMetadata(metadata.get()));
        }
        Map<Path, LibraryEntry> byFile = new HashMap<>();
        for (LibraryEntry entry : loaded.values()) {
            byFile.put(entry.file(), entry);
        }

        ScanCounts counts = scanFolder(loaded, byFile, checked);

        Map<String, String> keptAliases = new LinkedHashMap<>();
        parsed.aliases.forEach((from, to) -> {
            if (loaded.containsKey(to)) {
                keptAliases.put(from, to);
            }
        });
        Set<Pair> keptRejections = new LinkedHashSet<>();
        for (Pair pair : parsed.rejections) {
            if (loaded.containsKey(pair.to())) {
                keptRejections.add(pair);
            }
        }
        int droppedLinks = parsed.aliases.size() - keptAliases.size()
                + parsed.rejections.size() - keptRejections.size();
        boolean save = counts.sidecars + counts.probed > 0 || droppedLinks > 0 || backfilled > 0;
        return new ScanResult(loaded, byFile, keptAliases, keptRejections, checked, save, counts, fromIndex,
                parsed.blocked);
    }

    /**
     * Puts a scan's result in memory, counting how the entries changed, and says whether to save:
     * if the scan says so, or (for a rescan) if anything changed. Caller holds this object's lock.
     */
    private Swapped swap(ScanResult result, boolean rescan) {
        if (result.blocked() != null && saveBlocked == null) {
            saveBlocked = result.blocked();
        }
        int added = 0;
        int updated = 0;
        for (Map.Entry<String, LibraryEntry> e : result.entries().entrySet()) {
            LibraryEntry previous = entries.get(e.getKey());
            if (previous == null) {
                added++;
            } else if (!previous.equals(e.getValue())) {
                updated++;
            }
        }
        int missing = 0;
        for (String key : entries.keySet()) {
            if (!result.entries().containsKey(key)) {
                missing++;
            }
        }

        entries.clear();
        entries.putAll(result.entries());
        entriesByFile.clear();
        entriesByFile.putAll(result.byFile());
        aliases.clear();
        aliases.putAll(result.aliases());
        rejections.clear();
        rejections.addAll(result.rejections());
        metadataChecked.clear();
        metadataChecked.addAll(result.checked());

        ScanCounts counts = result.counts();
        long unknownLength = entries.values().stream().filter(e -> e.lengthMs() <= 0).count();
        LOGGER.info("Library: {} songs ({} already indexed; {} from sidecars, {} from YouTube IDs, {} from tags/filenames;"
                        + " {} with unknown length)",
                entries.size(), result.fromIndex(), counts.sidecars, counts.youtubeIds, counts.probed, unknownLength);
        boolean save = result.save() || (rescan && (added > 0 || updated > 0 || missing > 0));
        return new Swapped(new ScanSummary(added, updated, missing), save);
    }

    /** Returns the entry for the given key, if indexed and its file still exists. Never follows aliases. */
    public synchronized Optional<LibraryEntry> find(String key) {
        LibraryEntry entry = key == null ? null : entries.get(key);
        if (entry == null || !Files.exists(entry.file())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /** The entry for the exact key if there is one, otherwise the entry the key is aliased to. */
    public synchronized Optional<LibraryEntry> resolve(String key) {
        Optional<LibraryEntry> exact = find(key);
        if (exact.isPresent()) {
            return exact;
        }
        String target = key == null ? null : aliases.get(key);
        return target == null ? Optional.empty() : find(target);
    }

    /** The library key {@code fromKey} is aliased to, if any. */
    public synchronized Optional<String> aliasTarget(String fromKey) {
        return Optional.ofNullable(aliases.get(fromKey));
    }

    /** Records that {@code fromKey} (a requested song) is the same song as library entry {@code toKey}, and saves. */
    public synchronized void alias(String fromKey, String toKey) throws IOException {
        if (fromKey == null || toKey == null || fromKey.equals(toKey) || toKey.equals(aliases.get(fromKey))) {
            return;
        }
        aliases.put(fromKey, toKey);
        generation++;
        saveIndex();
    }

    /** Removes {@code fromKey}'s alias, saving if there was one. Returns the library key it pointed at. */
    public synchronized Optional<String> unalias(String fromKey) throws IOException {
        String removed = aliases.remove(fromKey);
        if (removed != null) {
            generation++;
            try {
                saveIndex();
            } catch (IOException | RuntimeException ex) {
                aliases.put(fromKey, removed); // not saved, so still linked
                throw ex;
            }
        }
        return Optional.ofNullable(removed);
    }

    /** Records that {@code fromKey} is not the same song as library entry {@code toKey}, and saves. */
    public synchronized void reject(String fromKey, String toKey) throws IOException {
        if (fromKey == null || toKey == null) {
            return;
        }
        if (rejections.add(new Pair(fromKey, toKey))) {
            generation++;
            saveIndex();
        }
    }

    /** Whether a person said {@code fromKey} is not the same song as {@code toKey}. Order matters. */
    public synchronized boolean isRejected(String fromKey, String toKey) {
        return rejections.contains(new Pair(fromKey, toKey));
    }

    /** Every requested key aliased to library song {@code key}. */
    public synchronized List<String> aliasesTo(String key) {
        List<String> result = new ArrayList<>();
        aliases.forEach((from, to) -> {
            if (to.equals(key)) {
                result.add(from);
            }
        });
        return List.copyOf(result);
    }

    /**
     * Entries matching every word of {@code query} (cleaned up the same way as a song title for
     * matching) in their title, author (uploader), sidecar artist or sidecar track, or whose key
     * is exactly {@code query}; exact-key matches sort first, then the rest by title. Empty for a
     * blank query or a non-positive {@code limit}.
     */
    public synchronized List<LibraryEntry> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        String key = query.strip();
        Set<String> queryWords = normalizedWords(query);
        List<LibraryEntry> matches = new ArrayList<>();
        for (LibraryEntry entry : entries.values()) {
            if (key.equals(entry.key()) || (!queryWords.isEmpty() && entryWords(entry).containsAll(queryWords))) {
                matches.add(entry);
            }
        }
        matches.sort((a, b) -> {
            boolean aExact = key.equals(a.key());
            boolean bExact = key.equals(b.key());
            if (aExact != bExact) {
                return aExact ? -1 : 1;
            }
            return a.title().compareToIgnoreCase(b.title());
        });
        return List.copyOf(matches.subList(0, Math.min(limit, matches.size())));
    }

    /**
     * Every word of {@code text} after cleaning it up the same way as a song title for matching:
     * both halves of an "Artist - Title" split, so splitting on a dash never loses a word.
     */
    private static Set<String> normalizedWords(@Nullable String text) {
        SongMatcher.Song song = SongMatcher.normalize(text, null);
        Set<String> words = new LinkedHashSet<>(song.title());
        words.addAll(song.artist());
        return words;
    }

    private static Set<String> entryWords(LibraryEntry entry) {
        Set<String> words = new LinkedHashSet<>(normalizedWords(entry.title()));
        words.addAll(normalizedWords(entry.author()));
        words.addAll(normalizedWords(entry.artist()));
        words.addAll(normalizedWords(entry.track()));
        return words;
    }

    /**
     * Removes the library song {@code key}: with {@code deleteFile}, deletes the media file first
     * (and, best-effort, its sidecar and thumbnail), then drops the entry, every alias pointing at
     * it, every rejection whose {@code to} is it, and its line in the library playlist file, and
     * saves. If the file can't be deleted, nothing changes and {@code removed} is false. See
     * {@link #remove(String, boolean, Long)} to also log who asked.
     */
    public RemoveResult remove(String key, boolean deleteFile) throws IOException {
        return remove(key, deleteFile, null);
    }

    /** As {@link #remove(String, boolean)}, logging {@code actorUserId} (the DJ who asked) if given. */
    public synchronized RemoveResult remove(String key, boolean deleteFile, @Nullable Long actorUserId)
            throws IOException {
        String actorSuffix = actorUserId != null ? ", by user " + actorUserId : "";
        LibraryEntry entry = key == null ? null : entries.get(key);
        if (entry == null) {
            LOGGER.info("Remove {}: not in the library{}", key, actorSuffix);
            return new RemoveResult(false, false, "not in the library");
        }
        boolean fileDeleted = false;
        if (deleteFile) {
            if (!entry.file().toAbsolutePath().normalize().startsWith(libraryFolder.toAbsolutePath().normalize())) {
                String problem = "That file is outside the library folder; not deleting it.";
                LOGGER.info("Remove {}: {}{}", key, problem, actorSuffix);
                return new RemoveResult(false, false, problem);
            }
            try {
                fileDeleter.delete(entry.file());
                fileDeleted = true;
            } catch (IOException ex) {
                String problem = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                LOGGER.info("Remove {}: the file couldn't be deleted ({}); nothing changed{}", key, problem, actorSuffix);
                return new RemoveResult(false, false, problem);
            }
            deleteSidecarAndThumbnails(entry.file());
        }
        entries.remove(key);
        entriesByFile.remove(entry.file(), entry);
        metadataChecked.remove(key);
        generation++;
        aliases.entrySet().removeIf(e -> e.getValue().equals(key));
        rejections.removeIf(pair -> pair.to().equals(key));
        removeFromPlaylist(entry.file());
        saveIndex();
        LOGGER.info("Removed library song {} (file deleted: {}{})", key, fileDeleted, actorSuffix);
        return new RemoveResult(true, fileDeleted, null);
    }

    /** Best-effort: the sidecar and any thumbnail (.jpg/.png/.webp) with the same base name as {@code file}. */
    private void deleteSidecarAndThumbnails(Path file) {
        Path parent = file.getParent();
        if (parent == null || file.getFileName() == null) {
            return;
        }
        String base = baseName(file.getFileName().toString());
        List<Path> extras = new ArrayList<>();
        extras.add(parent.resolve(base + SIDECAR_SUFFIX));
        for (String ext : new String[] {"jpg", "png", "webp"}) {
            extras.add(parent.resolve(base + "." + ext));
        }
        for (Path extra : extras) {
            try {
                Files.deleteIfExists(extra);
            } catch (IOException ex) {
                LOGGER.debug("Couldn't delete {}: {}", extra, ex.toString());
            }
        }
    }

    /** Rewrites the playlist file without {@code absFile}'s line, if it has one. */
    private void removeFromPlaylist(Path absFile) throws IOException {
        if (!Files.isRegularFile(playlistFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(playlistFile, StandardCharsets.UTF_8);
        List<String> kept = new ArrayList<>();
        boolean changed = false;
        for (String line : lines) {
            if (!line.isBlank()) {
                try {
                    if (normalize(Path.of(line)).equals(absFile)) {
                        changed = true;
                        continue;
                    }
                } catch (InvalidPathException ex) {
                    // Not a path this platform can parse; keep it.
                }
            }
            kept.add(line);
        }
        if (changed) {
            // Atomic, as saveIndex() writes library.json: a temp file in the same directory,
            // then one atomic move, so a crash or concurrent read never sees a half-written file.
            Path parent = playlistFile.getParent();
            Path tempFile = Files.createTempFile(parent, "playlist", ".txt.tmp");
            try {
                Files.write(tempFile, kept, StandardCharsets.UTF_8);
                Files.move(tempFile, playlistFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.deleteIfExists(tempFile);
                throw ex;
            }
        }
    }

    /**
     * Re-runs the import scan from the saved index (as {@link #load()} does), counting how the
     * in-memory map changed: entries added, entries whose details changed, and entries that are now
     * missing (their file is gone). Saves if anything did. Does nothing if the library folder
     * doesn't exist, as {@link #load()} does. Refused (an {@link IOException}) when the index
     * couldn't be read at startup: the result could never be saved. The slow part runs without
     * this object's lock (see {@link #scanAndSwap}), so lookups from the player don't wait on it.
     */
    public ScanSummary rescan() throws IOException {
        if (saveBlocked != null) {
            throw new IOException(RESCAN_BLOCKED);
        }
        synchronized (scanLock) {
            if (!Files.isDirectory(libraryFolder)) {
                return new ScanSummary(0, 0, 0);
            }
            Swapped swapped = scanAndSwap(libraryFolder.resolve(INDEX_FILE_NAME), true);
            if (swapped.save()) {
                synchronized (this) {
                    saveIndex();
                }
            }
            return swapped.summary();
        }
    }

    /** Why {@link #rescan} is refused once saving is blocked for this run. */
    public static final String RESCAN_BLOCKED =
            "The library index couldn't be read at startup; fix or remove library.json and restart.";

    /**
     * The song count, total size on disk of their files, and the library folder. Sizes the files
     * without this object's lock.
     */
    public LibraryStats stats() {
        List<LibraryEntry> snapshot = entries();
        long totalBytes = 0L;
        for (LibraryEntry entry : snapshot) {
            try {
                totalBytes += Files.size(entry.file());
            } catch (IOException ex) {
                // File missing or unreadable; doesn't count toward the total.
            }
        }
        return new LibraryStats(snapshot.size(), totalBytes, libraryFolder);
    }

    /**
     * Fills in the author, length and artwork of the entry for {@code key} from a real track,
     * where the entry has none, and saves. An imported ({@code file:}) entry never takes an author:
     * the track's uploader is often not the artist (a lyrics channel), and an artist-less entry
     * stays matchable by title. The title, key, file, source, name parts and the adopted flag are
     * kept. Returns the entry as it now is, or empty if {@code key} isn't indexed.
     */
    public synchronized Optional<LibraryEntry> fillMissing(String key, AudioTrackInfo info) throws IOException {
        LibraryEntry current = key == null ? null : entries.get(key);
        if (current == null || info == null) {
            return Optional.ofNullable(current);
        }
        boolean fillAuthor = current.author().isEmpty() && info.author != null && !LibraryKeys.isFileKey(key);
        String author = fillAuthor ? info.author : current.author();
        long lengthMs = current.lengthMs() <= 0 && !info.isStream && info.length > 0 ? info.length : current.lengthMs();
        String artwork = current.artworkUrl() == null || current.artworkUrl().isBlank() ? info.artworkUrl
                : current.artworkUrl();
        LibraryEntry filled = new LibraryEntry(current.key(), current.file(), current.title(), author, lengthMs,
                artwork, current.uri(), current.source(), current.nameParts(), current.adopted(),
                current.artist(), current.track(), current.album(), current.releaseYear(), current.isrc());
        if (filled.equals(current)) {
            return Optional.of(current);
        }
        entries.put(key, filled);
        entriesByFile.put(normalize(filled.file()), filled);
        generation++;
        saveIndex();
        return Optional.of(filled);
    }

    /**
     * An unmodifiable snapshot of every entry, for matching. Entries whose file has gone since
     * the last {@link #load()} are included; check with {@link #find} before playing one.
     */
    public synchronized List<LibraryEntry> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * Returns the entry for the given file, if indexed and it still exists. The file is
     * compared by normalized absolute path, so a relative or {@code ./}-qualified path
     * matches the same entry.
     */
    public synchronized Optional<LibraryEntry> findByFile(Path file) {
        LibraryEntry entry = entriesByFile.get(normalize(file));
        if (entry == null || !Files.exists(entry.file())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * Upserts the entry for {@code key} with metadata from a real track, saves
     * {@code library.json} atomically, and appends the file's absolute path to the playlist
     * file if it isn't already there (creating parent folders as needed). Serialized so
     * concurrent downloads don't corrupt the index or duplicate playlist lines.
     */
    public synchronized LibraryEntry record(String key, Path file, AudioTrackInfo info) throws IOException {
        Path absFile = normalize(file);
        LibraryEntry entry = new LibraryEntry(key, absFile, info.title, info.author, info.length,
                info.artworkUrl, info.uri, null, List.of(), false);
        Optional<SidecarMetadata> sidecar = sidecarReader.read(absFile);
        entry = sidecar.map(entry::withMetadata).orElse(entry);
        if (sidecar.isPresent()) {
            metadataChecked.add(key);
        } else {
            metadataChecked.remove(key);
        }
        generation++;
        LibraryEntry previous = entries.put(key, entry);
        if (previous != null && !previous.file().equals(absFile)) {
            // Re-recorded at a new path: the old path no longer belongs to this key.
            entriesByFile.remove(previous.file(), previous);
        }
        entriesByFile.put(absFile, entry);
        saveIndex();
        appendToPlaylist(absFile);
        return entry;
    }

    /**
     * Parses the video ID out of a library filename's trailing {@code [<videoId>].<ext>}
     * suffix. Empty if the filename has no such suffix, or if the bracket holds one of the bot's
     * own {@code <source>-<id>} suffixes that happens to be 11 characters long
     * (e.g. {@code [vimeo-12345]}).
     */
    public static Optional<String> videoIdFromFileName(String fileName) {
        Matcher matcher = BRACKET_SUFFIX.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String id = matcher.group(2);
        for (String source : LibraryKeys.SUPPORTED_SOURCES) {
            if (!LibraryKeys.YOUTUBE.equals(source) && id.startsWith(source + "-")) {
                return Optional.empty();
            }
        }
        return Optional.of(id);
    }

    /** True for yt-dlp's own in-progress temp names: {@code .part}, {@code .ytdl}, {@code .temp}, or a {@code .part-Frag*} fragment. */
    static boolean isTempFileName(String fileName) {
        return fileName.endsWith(".part") || fileName.endsWith(".ytdl") || fileName.endsWith(".temp")
                || fileName.contains(".part-Frag");
    }

    // ---- folder scan ----

    private static final class ScanCounts {
        int sidecars;
        int youtubeIds;
        int probed;
    }

    /**
     * Indexes every file in the folder that {@code byFile} doesn't know yet, in filename order.
     * An entry imported from its sidecar is added to {@code checked}.
     */
    private ScanCounts scanFolder(Map<String, LibraryEntry> loaded, Map<Path, LibraryEntry> byFile,
                                  Set<String> checked) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libraryFolder)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    files.add(path);
                }
            }
        }
        files.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        ScanCounts counts = new ScanCounts();
        for (Path path : files) {
            String fileName = path.getFileName().toString();
            Path abs = normalize(path);
            if (byFile.containsKey(abs) || isIgnoredFile(fileName)) {
                continue;
            }

            LibraryEntry entry = fromSidecar(abs, fileName, loaded).orElse(null);
            if (entry != null) {
                counts.sidecars++;
                checked.add(entry.key());
            } else {
                Optional<String> videoId = videoIdFromFileName(fileName);
                if (videoId.isPresent()) {
                    if (loaded.containsKey(videoId.get())) {
                        continue; // that video is already indexed with another file
                    }
                    entry = new LibraryEntry(videoId.get(), abs, titleFromFileName(fileName), "", 0L, null,
                            LibraryKeys.youtubeUrl(videoId.get()));
                    counts.youtubeIds++;
                } else {
                    entry = fromProbe(abs, fileName).orElse(null);
                    if (entry == null) {
                        continue;
                    }
                    counts.probed++;
                }
            }
            loaded.put(entry.key(), entry);
            byFile.put(abs, entry);
        }
        return counts;
    }

    /**
     * Whether a file in the library folder is never a song: the index, yt-dlp sidecars and temp
     * files, and files with a non-audio extension (cover art, text, subtitles).
     */
    static boolean isIgnoredFile(String fileName) {
        if (fileName.equals(INDEX_FILE_NAME) || fileName.endsWith(SIDECAR_SUFFIX) || isTempFileName(fileName)) {
            return true;
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && NON_AUDIO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** An entry from a yt-dlp {@code .info.json} sidecar with the same base name, if there is a readable one. */
    private Optional<LibraryEntry> fromSidecar(Path file, String fileName, Map<String, LibraryEntry> loaded) {
        Path sidecar = file.resolveSibling(baseName(fileName) + SIDECAR_SUFFIX);
        if (!Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        JsonNode json;
        try {
            json = MAPPER.readTree(sidecar.toFile());
        } catch (IOException | RuntimeException ex) {
            LOGGER.debug("Ignoring unreadable sidecar {}: {}", sidecar, ex.toString());
            return Optional.empty();
        }
        if (json == null || !json.isObject()) {
            return Optional.empty();
        }

        String extractor = firstText(json, "extractor_key", "extractor");
        String id = firstText(json, "id");
        String webpageUrl = firstText(json, "webpage_url");
        String key = LibraryKeys.fromSidecar(extractor, id, webpageUrl)
                .filter(k -> !loaded.containsKey(k))
                .orElse(LibraryKeys.fileKey(fileName));
        String uri = null;
        if (LibraryKeys.isYoutubeKey(key)) {
            uri = LibraryKeys.youtubeUrl(key);
        } else if (!LibraryKeys.isFileKey(key)) {
            uri = webpageUrl;
        }
        String title = firstText(json, "title", "track");
        String author = firstText(json, "uploader", "channel", "artist", "creator");
        JsonNode duration = json.get("duration");
        long lengthMs = duration != null && duration.isNumber() ? Math.round(duration.asDouble() * 1000.0) : 0L;
        LibraryEntry entry = new LibraryEntry(key, file, title != null ? title : baseName(fileName), author, lengthMs,
                firstText(json, "thumbnail"), uri, null, List.of(), false);
        return Optional.of(entry.withMetadata(SidecarMetadata.parse(json)));
    }

    /** An entry from the file's tags and length, or its filename; empty if the file isn't playable audio. */
    private Optional<LibraryEntry> fromProbe(Path file, String fileName) {
        ProbeResult probe;
        try {
            probe = prober.probe(file).orElse(null);
        } catch (Exception | LinkageError | StackOverflowError | AssertionError ex) {
            // A bad file, or a decoder that can't load, mustn't stop the scan. Other errors (out of
            // memory and the like) mean the JVM itself is failing, so they still propagate.
            LOGGER.debug("Skipping library file {} that couldn't be probed: {}", file, ex.toString());
            return Optional.empty();
        }
        if (probe == null) {
            LOGGER.debug("Skipping library file {}: not playable audio", file);
            return Optional.empty();
        }
        String tagTitle = tagValue(probe.title(), UNKNOWN_TITLE);
        String tagArtist = tagValue(probe.author(), UNKNOWN_ARTIST);
        long lengthMs = probe.lengthMs() > 0 && probe.lengthMs() != Long.MAX_VALUE ? probe.lengthMs() : 0L;
        String title = tagTitle != null ? tagTitle : baseName(fileName);
        List<String> nameParts = tagTitle == null && tagArtist == null ? nameParts(title) : List.of();
        return Optional.of(new LibraryEntry(LibraryKeys.fileKey(fileName), file, title,
                tagArtist == null ? "" : tagArtist, lengthMs, null, null, null, nameParts, true));
    }

    /**
     * The two halves of a filename-derived title with exactly one {@code " - "}, with a trailing
     * catalogue code dropped. Empty for no {@code " - "}, several, or an empty half.
     */
    private static List<String> nameParts(String title) {
        int first = title.indexOf(" - ");
        if (first < 0 || title.indexOf(" - ", first + 1) >= 0) {
            return List.of();
        }
        String left = CATALOGUE_CODE.matcher(title.substring(0, first)).replaceFirst("").trim();
        String right = CATALOGUE_CODE.matcher(title.substring(first + 3)).replaceFirst("").trim();
        if (left.isEmpty() || right.isEmpty()) {
            return List.of();
        }
        return List.of(left, right);
    }

    private static @Nullable String tagValue(@Nullable String value, String placeholder) {
        if (value == null || value.isBlank() || value.equals(placeholder)) {
            return null;
        }
        return value.strip();
    }

    private static @Nullable String firstText(JsonNode json, String... fields) {
        for (String field : fields) {
            JsonNode node = json.get(field);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String titleFromFileName(String fileName) {
        Matcher matcher = BRACKET_SUFFIX.matcher(fileName);
        if (!matcher.matches()) {
            return fileName;
        }
        return matcher.group(1).trim();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    // ---- index file ----

    private record Pair(String from, String to) {
    }

    /**
     * @param checked keys saved with {@code "metadataChecked": true}
     * @param blocked why saving must be blocked for this run (the unreadable index couldn't be
     *                copied aside), or null
     */
    private record ParsedIndex(Map<String, LibraryEntry> entries, Map<String, String> aliases, Set<Pair> rejections,
                               Set<String> checked, @Nullable String blocked) {
    }

    /** Reads a v1 or v2 index, skipping anything malformed. A missing file is an empty index. */
    private ParsedIndex readIndex(Path indexFile) {
        ParsedIndex empty = new ParsedIndex(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>(),
                new HashSet<>(), null);
        JsonNode root;
        try {
            // Read under this object's lock, as saveIndex writes under it: on Windows, replacing
            // library.json fails while another thread has it open, and that save would be lost.
            byte[] bytes;
            synchronized (this) {
                if (!Files.isRegularFile(indexFile)) {
                    return empty;
                }
                bytes = Files.readAllBytes(indexFile);
            }
            root = MAPPER.readTree(bytes);
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Failed to read {}: {}", indexFile, ex.toString());
            return unreadable(indexFile);
        }
        if (root == null || !root.isObject()) {
            LOGGER.warn("Ignoring {}: not a JSON object", indexFile);
            return unreadable(indexFile);
        }

        JsonNode entriesNode = root.get("entries");
        boolean v2 = entriesNode != null && entriesNode.isObject();
        if (v2 && root.path("version").asInt(SCHEMA_VERSION) > SCHEMA_VERSION) {
            LOGGER.warn("{} was written by a newer version (schema {}); reading the fields this version knows",
                    indexFile, root.get("version"));
        }
        ParsedIndex parsed = empty;
        Iterator<Map.Entry<String, JsonNode>> it = (v2 ? entriesNode : root).fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> field = it.next();
            Optional<LibraryEntry> entry = parseEntry(field.getKey(), field.getValue());
            if (entry.isPresent()) {
                parsed.entries.put(field.getKey(), entry.get());
                if (field.getValue().path("metadataChecked").asBoolean(false)) {
                    parsed.checked.add(field.getKey());
                }
            } else {
                LOGGER.warn("Skipping malformed library entry '{}' in {}", field.getKey(), indexFile);
            }
        }
        if (!v2) {
            return parsed;
        }

        JsonNode aliasesNode = root.get("aliases");
        if (aliasesNode != null && aliasesNode.isObject()) {
            aliasesNode.fields().forEachRemaining(field -> {
                if (field.getValue().isTextual()) {
                    parsed.aliases.put(field.getKey(), field.getValue().asText());
                }
            });
        }
        JsonNode rejectionsNode = root.get("rejections");
        if (rejectionsNode != null && rejectionsNode.isArray()) {
            for (JsonNode pair : rejectionsNode) {
                if (pair.isArray() && pair.size() == 2 && pair.get(0).isTextual() && pair.get(1).isTextual()) {
                    parsed.rejections.add(new Pair(pair.get(0).asText(), pair.get(1).asText()));
                }
            }
        }
        return parsed;
    }

    private static Optional<LibraryEntry> parseEntry(String key, JsonNode json) {
        if (key.isEmpty() || json == null || !json.isObject()) {
            return Optional.empty();
        }
        JsonNode fileNode = json.get("file");
        if (fileNode == null || !fileNode.isTextual() || fileNode.asText().isBlank()) {
            return Optional.empty();
        }
        Path file;
        try {
            file = normalize(Path.of(fileNode.asText()));
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
        String author = textOrNull(json, "author");
        long lengthMs = json.path("lengthMs").canConvertToLong() ? json.path("lengthMs").asLong() : 0L;
        List<String> nameParts = new ArrayList<>();
        JsonNode partsNode = json.get("nameParts");
        if (partsNode != null && partsNode.isArray()) {
            for (JsonNode part : partsNode) {
                if (part.isTextual()) {
                    nameParts.add(part.asText());
                }
            }
        }
        JsonNode adoptedNode = json.get("adopted");
        boolean adopted = adoptedNode != null && adoptedNode.isBoolean()
                ? adoptedNode.asBoolean()
                : (author == null || author.isEmpty()) && lengthMs <= 0; // v1's rule
        JsonNode releaseYearNode = json.get("releaseYear");
        Integer releaseYear = releaseYearNode != null && releaseYearNode.isInt() ? releaseYearNode.asInt() : null;
        return Optional.of(new LibraryEntry(key, file, textOrNull(json, "title"), author, lengthMs,
                textOrNull(json, "artworkUrl"), textOrNull(json, "uri"), textOrNull(json, "source"),
                nameParts, adopted, textOrNull(json, "artist"), textOrNull(json, "track"),
                textOrNull(json, "album"), releaseYear, textOrNull(json, "isrc")));
    }

    private static @Nullable String textOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    /**
     * An empty index for one that couldn't be read, after copying it aside so the next save can't
     * destroy it; if even the copy fails, the result says saving must be blocked.
     */
    private static ParsedIndex unreadable(Path indexFile) {
        String blocked = null;
        try {
            Files.copy(indexFile, indexFile.resolveSibling(CORRUPT_COPY_NAME), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Kept a copy of the unreadable {} as {}", indexFile.getFileName(), CORRUPT_COPY_NAME);
        } catch (IOException ex) {
            blocked = "the unreadable original couldn't be copied aside";
            LOGGER.error("Couldn't copy the unreadable {} aside ({}); the library index won't be saved this run",
                    indexFile, ex.toString());
        }
        return new ParsedIndex(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), new HashSet<>(),
                blocked);
    }

    private void saveIndex() throws IOException {
        if (saveBlocked != null) {
            throw new IOException("not saving " + INDEX_FILE_NAME + ": " + saveBlocked);
        }
        Files.createDirectories(libraryFolder);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", SCHEMA_VERSION);
        ObjectNode entriesNode = root.putObject("entries");
        for (LibraryEntry entry : entries.values()) {
            ObjectNode node = entriesNode.putObject(entry.key());
            node.put("file", entry.file().toString());
            node.put("title", entry.title());
            node.put("author", entry.author());
            node.put("lengthMs", entry.lengthMs());
            node.put("artworkUrl", entry.artworkUrl());
            node.put("uri", entry.uri());
            if (entry.source() != null) {
                node.put("source", entry.source());
            }
            if (!entry.nameParts().isEmpty()) {
                ArrayNode parts = node.putArray("nameParts");
                entry.nameParts().forEach(parts::add);
            }
            node.put("adopted", entry.adopted());
            if (entry.artist() != null) {
                node.put("artist", entry.artist());
            }
            if (entry.track() != null) {
                node.put("track", entry.track());
            }
            if (entry.album() != null) {
                node.put("album", entry.album());
            }
            if (entry.releaseYear() != null) {
                node.put("releaseYear", entry.releaseYear());
            }
            if (entry.isrc() != null) {
                node.put("isrc", entry.isrc());
            }
            if (metadataChecked.contains(entry.key())) {
                node.put("metadataChecked", true);
            }
        }
        ObjectNode aliasesNode = root.putObject("aliases");
        aliases.forEach(aliasesNode::put);
        ArrayNode rejectionsNode = root.putArray("rejections");
        for (Pair pair : rejections) {
            rejectionsNode.addArray().add(pair.from()).add(pair.to());
        }

        Path indexFile = libraryFolder.resolve(INDEX_FILE_NAME);
        Path tempFile = Files.createTempFile(libraryFolder, "library", ".json.tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), root);
            Files.move(tempFile, indexFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }
    }

    private void appendToPlaylist(Path absFile) throws IOException {
        Path parent = playlistFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean needsLeadingNewline = false;
        if (Files.isRegularFile(playlistFile)) {
            byte[] bytes = Files.readAllBytes(playlistFile);
            if (bytes.length > 0) {
                byte last = bytes[bytes.length - 1];
                needsLeadingNewline = last != '\n' && last != '\r';
            }
            for (String line : new String(bytes, StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    if (normalize(Path.of(line)).equals(absFile)) {
                        return;
                    }
                } catch (InvalidPathException ex) {
                    // Not a path this platform can parse (e.g. a URL or a line with reserved
                    // characters); it can't be the same file as absFile, so keep scanning.
                }
            }
        }

        try (var writer = Files.newBufferedWriter(playlistFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            // A hand-edited playlist file might not end in a line terminator; starting on a
            // fresh line keeps "one absolute path per line" true for both entries.
            if (needsLeadingNewline) {
                writer.newLine();
            }
            writer.write(absFile.toString());
            writer.newLine();
        }
    }
}
