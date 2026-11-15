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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fields {@link YtDlpLibrary} reads from a yt-dlp {@code .info.json} sidecar, beyond the
 * title/author/length/artwork it already read: artist, track, album, release year and ISRC.
 *
 * @param artist     {@code artist}, else {@code artists[0]}, else {@code creator}
 * @param track      {@code track}
 * @param album      {@code album}
 * @param releaseYear {@code release_year}, else the year of {@code release_date}
 * @param isrc       {@code isrc}
 * @param durationMs {@code duration} in milliseconds, or 0 if absent or not a number
 * @author Arif Banai (arif-banai)
 */
public record SidecarMetadata(@Nullable String artist, @Nullable String track, @Nullable String album,
                              @Nullable Integer releaseYear, @Nullable String isrc, long durationMs) {

    private static final Logger LOGGER = LoggerFactory.getLogger(SidecarMetadata.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIDECAR_SUFFIX = ".info.json";
    private static final Pattern DATE_8 = Pattern.compile("\\d{8}");
    private static final Pattern YEAR_4 = Pattern.compile("\\d{4}");

    private static final SidecarMetadata EMPTY = new SidecarMetadata(null, null, null, null, null, 0L);

    /**
     * Reads the sidecar beside {@code mediaFile} (same base name, {@code .info.json}), if there is
     * a readable one. Empty if the file doesn't exist or can't be parsed as JSON.
     */
    public static Optional<SidecarMetadata> read(Path mediaFile) {
        String fileName = mediaFile.getFileName() == null ? "" : mediaFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path sidecar = mediaFile.resolveSibling(base + SIDECAR_SUFFIX);
        if (!Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(MAPPER.readTree(sidecar.toFile())));
        } catch (IOException | RuntimeException ex) {
            LOGGER.debug("Ignoring unreadable sidecar {}: {}", sidecar, ex.toString());
            return Optional.empty();
        }
    }

    /** Parses a sidecar's JSON tree into its metadata fields. Never throws. */
    public static SidecarMetadata parse(@Nullable JsonNode json) {
        try {
            if (json == null || !json.isObject()) {
                return EMPTY;
            }
            String artist = artist(json);
            String track = textOrNull(json, "track");
            String album = textOrNull(json, "album");
            Integer releaseYear = year(json.get("release_year"));
            if (releaseYear == null) {
                releaseYear = year(json.get("release_date"));
            }
            String isrc = textOrNull(json, "isrc");
            JsonNode duration = json.get("duration");
            long durationMs = duration != null && duration.isNumber()
                    ? Math.round(duration.asDouble() * 1000.0) : 0L;
            return new SidecarMetadata(artist, track, album, releaseYear, isrc, durationMs);
        } catch (RuntimeException ex) {
            LOGGER.debug("Ignoring unparsable sidecar metadata: {}", ex.toString());
            return EMPTY;
        }
    }

    private static @Nullable String artist(JsonNode json) {
        String artist = textOrNull(json, "artist");
        if (artist != null) {
            return artist;
        }
        JsonNode artists = json.get("artists");
        if (artists != null && artists.isArray() && !artists.isEmpty()) {
            JsonNode first = artists.get(0);
            if (first.isTextual() && !first.asText().isBlank()) {
                return first.asText();
            }
        }
        return textOrNull(json, "creator");
    }

    /**
     * A year from a number ({@code 2009}), a 4-digit year string ({@code "2009"}), or an 8-digit
     * {@code yyyymmdd} string ({@code "20090101"}). Null for anything else (e.g. {@code "garbage"}).
     */
    private static @Nullable Integer year(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            String text = node.asText().strip();
            if (DATE_8.matcher(text).matches()) {
                return Integer.parseInt(text.substring(0, 4));
            }
            if (YEAR_4.matcher(text).matches()) {
                return Integer.parseInt(text);
            }
        }
        return null;
    }

    private static @Nullable String textOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
