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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The on-disk index of videos the bot has downloaded with yt-dlp, plus the playlist
 * file that lists them for offline playback.
 *
 * <p>Filenames in the library folder end in {@code [<videoId>].<ext>}. The index file,
 * {@code <libraryFolder>/library.json}, maps video ID to the metadata that was known for
 * that video when it was recorded. Files found in the folder with an {@code [id]} suffix
 * but no index entry (for example, songs downloaded by hand) are adopted with a title
 * taken from the filename; their full metadata is filled in the next time that video is
 * played from YouTube and {@link #record} is called again.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpLibrary.class);
    private static final String INDEX_FILE_NAME = "library.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Matches a trailing {@code [<videoId>].<ext>} suffix. The video ID may contain {@code _} or {@code -}. */
    private static final Pattern BRACKET_SUFFIX = Pattern.compile("^(.*)\\[([A-Za-z0-9_-]+)\\]\\.[^.\\s\\[\\]]+$");

    private final Path libraryFolder;
    private final Path playlistFile;
    private final Map<String, LibraryEntry> entries = new LinkedHashMap<>();

    public YtDlpLibrary(Path libraryFolder, Path playlistFile) {
        this.libraryFolder = libraryFolder;
        this.playlistFile = playlistFile;
    }

    /**
     * Loads {@code library.json}, drops entries whose file no longer exists, and adopts
     * any file in the library folder that has an {@code [id]} suffix but no index entry.
     * Does nothing if the library folder doesn't exist.
     */
    public synchronized void load() throws IOException {
        if (!Files.isDirectory(libraryFolder)) {
            return;
        }

        Map<String, LibraryEntry> loaded = new LinkedHashMap<>();
        Path indexFile = libraryFolder.resolve(INDEX_FILE_NAME);
        if (Files.isRegularFile(indexFile)) {
            Map<String, JsonEntry> raw;
            try {
                raw = MAPPER.readValue(indexFile.toFile(), new TypeReference<Map<String, JsonEntry>>() {});
            } catch (IOException ex) {
                LOGGER.warn("Failed to read {}: {}", indexFile, ex.toString());
                raw = Map.of();
            }
            for (Map.Entry<String, JsonEntry> e : raw.entrySet()) {
                String videoId = e.getKey();
                JsonEntry json = e.getValue();
                Path file = Path.of(json.file());
                if (Files.exists(file)) {
                    loaded.put(videoId, new LibraryEntry(videoId, normalize(file), json.title(), json.author(),
                            json.lengthMs(), json.artworkUrl(), json.uri()));
                }
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libraryFolder)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                Optional<String> videoId = videoIdFromFileName(fileName);
                if (videoId.isEmpty() || loaded.containsKey(videoId.get())) {
                    continue;
                }
                String title = titleFromFileName(fileName);
                Path abs = normalize(path);
                loaded.put(videoId.get(), new LibraryEntry(videoId.get(), abs, title, "", 0L, null,
                        "https://www.youtube.com/watch?v=" + videoId.get()));
            }
        }

        entries.clear();
        entries.putAll(loaded);
    }

    /** Returns the entry for the given video ID, if indexed and its file still exists. */
    public synchronized Optional<LibraryEntry> findById(String videoId) {
        LibraryEntry entry = entries.get(videoId);
        if (entry == null || !Files.exists(entry.file())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * Returns the entry for the given file, if indexed and it still exists. The file is
     * compared by normalized absolute path, so a relative or {@code ./}-qualified path
     * matches the same entry.
     */
    public synchronized Optional<LibraryEntry> findByFile(Path file) {
        Path target = normalize(file);
        for (LibraryEntry entry : entries.values()) {
            if (normalize(entry.file()).equals(target) && Files.exists(entry.file())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Upserts the entry for {@code videoId}, saves {@code library.json} atomically, and
     * appends the file's absolute path to the playlist file if it isn't already there
     * (creating parent folders as needed). Serialized so concurrent downloads don't
     * corrupt the index or duplicate playlist lines.
     */
    public synchronized LibraryEntry record(String videoId, Path file, AudioTrackInfo info) throws IOException {
        Path absFile = normalize(file);
        LibraryEntry entry = new LibraryEntry(videoId, absFile, info.title, info.author, info.length,
                info.artworkUrl, info.uri);
        entries.put(videoId, entry);
        saveIndex();
        appendToPlaylist(absFile);
        return entry;
    }

    /**
     * Parses the video ID out of a library filename's trailing {@code [<videoId>].<ext>}
     * suffix. Empty if the filename has no such suffix.
     */
    public static Optional<String> videoIdFromFileName(String fileName) {
        Matcher matcher = BRACKET_SUFFIX.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(2));
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

    private void saveIndex() throws IOException {
        Files.createDirectories(libraryFolder);
        Map<String, JsonEntry> toWrite = new LinkedHashMap<>();
        for (LibraryEntry entry : entries.values()) {
            toWrite.put(entry.videoId(), new JsonEntry(entry.file().toString(), entry.title(), entry.author(),
                    entry.lengthMs(), entry.artworkUrl(), entry.uri()));
        }

        Path indexFile = libraryFolder.resolve(INDEX_FILE_NAME);
        Path tempFile = Files.createTempFile(libraryFolder, "library", ".json.tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), toWrite);
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

        if (Files.isRegularFile(playlistFile)) {
            for (String line : Files.readAllLines(playlistFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                if (normalize(Path.of(line)).equals(absFile)) {
                    return;
                }
            }
        }

        try (var writer = Files.newBufferedWriter(playlistFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(absFile.toString());
            writer.newLine();
        }
    }

    /** Plain JSON shape for {@code library.json}; {@link LibraryEntry#file()} is a {@link Path}, not a String. */
    private record JsonEntry(String file, String title, String author, long lengthMs, String artworkUrl,
                              String uri) {
    }
}
