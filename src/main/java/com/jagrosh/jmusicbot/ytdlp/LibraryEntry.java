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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * A single entry in the yt-dlp download library's index: a downloaded (or imported) file and
 * the metadata known for it.
 *
 * @param key        the library key (see {@link LibraryKeys}): a bare YouTube video ID,
 *                   {@code <source>:<identity>}, or {@code file:<filename>}
 * @param author     never null; empty when unknown
 * @param uri        never null: the page URL for keyed entries, or the file's own path for
 *                   {@code file:} entries, so saved playlists and queue links keep working
 * @param source     the lavaplayer source name the song came from, or null when unknown
 * @param nameParts  for an entry whose details came only from a filename of the form
 *                   {@code "X - Y"}: the two halves, in no particular order (people write both
 *                   "Artist - Title" and "Title - Artist"). Empty otherwise. Used for matching only.
 * @param adopted    whether the details came from the file itself (its name or tags) rather than
 *                   from a real track or a yt-dlp sidecar; such entries get their details filled in
 *                   from the first real track that plays them
 * @param artist     from a yt-dlp sidecar's {@code artist}/{@code artists[0]}/{@code creator}, if known
 * @param track      from a yt-dlp sidecar's {@code track}, if known
 * @param album      from a yt-dlp sidecar's {@code album}, if known
 * @param releaseYear from a yt-dlp sidecar's {@code release_year} or the year of {@code release_date}, if known
 * @param isrc       from a yt-dlp sidecar's {@code isrc}, if known
 * @author Arif Banai (arif-banai)
 */
public record LibraryEntry(String key, Path file, String title, String author, long lengthMs,
                           @Nullable String artworkUrl, String uri, @Nullable String source,
                           List<String> nameParts, boolean adopted,
                           @Nullable String artist, @Nullable String track, @Nullable String album,
                           @Nullable Integer releaseYear, @Nullable String isrc) {

    public LibraryEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(file, "file");
        author = author == null ? "" : author;
        nameParts = nameParts == null ? List.of() : List.copyOf(nameParts);
        if (source == null) {
            source = LibraryKeys.sourceOf(key).orElse(null);
        }
        if (uri == null || uri.isBlank()) {
            uri = LibraryKeys.isYoutubeKey(key) ? LibraryKeys.youtubeUrl(key) : file.toString();
        }
        if (title == null || title.isBlank()) {
            title = file.getFileName() == null ? key : file.getFileName().toString();
        }
    }

    /**
     * Pre-metadata constructor, kept so existing call sites compile unchanged: {@code artist},
     * {@code track}, {@code album}, {@code releaseYear} and {@code isrc} are all unknown.
     */
    public LibraryEntry(String key, Path file, String title, String author, long lengthMs,
                        @Nullable String artworkUrl, String uri, @Nullable String source,
                        List<String> nameParts, boolean adopted) {
        this(key, file, title, author, lengthMs, artworkUrl, uri, source, nameParts, adopted,
                null, null, null, null, null);
    }

    /**
     * An entry recorded from a real track, or adopted from a YouTube {@code [id]} filename
     * (no author and no length, as v1 adopted them).
     */
    public LibraryEntry(String key, Path file, String title, String author, long lengthMs,
                        @Nullable String artworkUrl, String uri) {
        this(key, file, title, author, lengthMs, artworkUrl, uri, null, List.of(),
                (author == null || author.isEmpty()) && lengthMs <= 0);
    }

    /**
     * Whether any of author, length or artwork is missing (to be filled in from a real track). An
     * imported ({@code file:}) entry's author doesn't count: it is never filled in.
     */
    public boolean missingDetails() {
        return (author.isEmpty() && !LibraryKeys.isFileKey(key)) || lengthMs <= 0
                || artworkUrl == null || artworkUrl.isBlank();
    }

    /**
     * Fills in whichever of {@code artist}, {@code track}, {@code album}, {@code releaseYear} and
     * {@code isrc} the sidecar provides (non-null), keeping this entry's existing value for any
     * field the sidecar leaves null. The sidecar's duration, if positive, replaces {@link #lengthMs}
     * only when this entry's length isn't already known.
     */
    public LibraryEntry withMetadata(SidecarMetadata m) {
        String newArtist = m.artist() != null ? m.artist() : artist;
        String newTrack = m.track() != null ? m.track() : track;
        String newAlbum = m.album() != null ? m.album() : album;
        Integer newReleaseYear = m.releaseYear() != null ? m.releaseYear() : releaseYear;
        String newIsrc = m.isrc() != null ? m.isrc() : isrc;
        long newLengthMs = lengthMs <= 0 && m.durationMs() > 0 ? m.durationMs() : lengthMs;
        if (Objects.equals(newArtist, artist) && Objects.equals(newTrack, track) && Objects.equals(newAlbum, album)
                && Objects.equals(newReleaseYear, releaseYear) && Objects.equals(newIsrc, isrc)
                && newLengthMs == lengthMs) {
            return this;
        }
        return new LibraryEntry(key, file, title, author, newLengthMs, artworkUrl, uri, source, nameParts, adopted,
                newArtist, newTrack, newAlbum, newReleaseYear, newIsrc);
    }

    /**
     * Rebuilds an {@link AudioTrackInfo} as if this were the original track, so library hits look
     * like the original everywhere the bot shows them. The identifier is the lavaplayer identifier
     * the key was built from where that is recoverable (the video ID for YouTube, the part after
     * {@code <source>:} otherwise), and the file path for {@code file:} entries. Never a stream.
     */
    public AudioTrackInfo toTrackInfo() {
        String identifier;
        if (LibraryKeys.isYoutubeKey(key)) {
            identifier = key;
        } else if (LibraryKeys.isFileKey(key)) {
            identifier = file.toString();
        } else {
            identifier = key.substring(key.indexOf(':') + 1);
        }
        return new AudioTrackInfo(title, author, lengthMs, identifier, false, uri, artworkUrl, null);
    }
}
