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

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Library keys: the identity of a song in the yt-dlp library.
 * <ul>
 *   <li>YouTube: the bare 11-character video ID, exactly as v1 stored it.</li>
 *   <li>Other supported sources: {@code <lavaplayer source name>:<identity>}. The identity is
 *       {@link AudioTrackInfo#identifier} for Bandcamp (the track page URL), Vimeo (the numeric ID),
 *       NicoNico (e.g. {@code sm9}) and Twitch. SoundCloud is the exception: lavaplayer's
 *       identifier there is a per-format lookup URL ({@code O:https://api-v2...}) that changes with
 *       the chosen format and can't be rebuilt from a yt-dlp sidecar, so its key uses the track's
 *       permalink ({@link AudioTrackInfo#uri}), which is also yt-dlp's {@code webpage_url}.</li>
 *   <li>Files imported with no known source: {@code file:<filename>}.</li>
 * </ul>
 * The key is deliberately separate from {@link AudioTrackInfo#identifier}; only for YouTube are
 * the two equal.
 *
 * @author Arif Banai (arif-banai)
 */
public final class LibraryKeys {

    public static final String YOUTUBE = "youtube";
    public static final String SOUNDCLOUD = "soundcloud";
    public static final String BANDCAMP = "bandcamp";
    public static final String VIMEO = "vimeo";
    public static final String TWITCH = "twitch";
    public static final String NICONICO = "niconico";

    /** Lavaplayer source names that yt-dlp can download from. */
    public static final Set<String> SUPPORTED_SOURCES = Set.of(YOUTUBE, SOUNDCLOUD, BANDCAMP, VIMEO, TWITCH, NICONICO);

    private static final String FILE_PREFIX = "file:";
    private static final Pattern YOUTUBE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final String YOUTUBE_WATCH = "https://www.youtube.com/watch?v=";

    private LibraryKeys() {
    }

    /**
     * The library key for a track, or empty for streams, local files, HTTP links, unsupported
     * sources, and tracks missing the data their key needs. A {@link LibraryAudioTrack} returns
     * the key it was built with.
     */
    public static Optional<String> of(@Nullable AudioTrack track) {
        if (track == null) {
            return Optional.empty();
        }
        if (track instanceof LibraryAudioTrack library) {
            return Optional.ofNullable(library.getKey()).filter(k -> !k.isEmpty());
        }
        AudioSourceManager source = track.getSourceManager();
        AudioTrackInfo info = track.getInfo();
        if (source == null || info == null || info.isStream) {
            return Optional.empty();
        }
        String sourceName = source.getSourceName();
        if (!SUPPORTED_SOURCES.contains(sourceName)) {
            return Optional.empty();
        }
        String identity = SOUNDCLOUD.equals(sourceName) ? info.uri : info.identifier;
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(YOUTUBE.equals(sourceName) ? identity : sourceName + ":" + identity);
    }

    /**
     * The key a yt-dlp {@code .info.json} sidecar describes, chosen to equal what {@link #of}
     * gives that site's lavaplayer track. {@code extractor} is the sidecar's {@code extractor_key}
     * (or {@code extractor}), matched case-insensitively. Empty for a site lavaplayer can't play,
     * or when the field the key needs is missing.
     */
    public static Optional<String> fromSidecar(@Nullable String extractor, @Nullable String id,
                                               @Nullable String webpageUrl) {
        if (extractor == null) {
            return Optional.empty();
        }
        String identity = switch (extractor.toLowerCase(Locale.ROOT)) {
            case YOUTUBE -> id != null && YOUTUBE_ID.matcher(id).matches() ? id : null;
            case SOUNDCLOUD, BANDCAMP -> webpageUrl;
            case VIMEO, NICONICO -> id;
            default -> null;
        };
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        String source = extractor.toLowerCase(Locale.ROOT);
        return Optional.of(YOUTUBE.equals(source) ? identity : source + ":" + identity);
    }

    /** The key for an imported file with no known source: {@code file:<filename>}. */
    public static String fileKey(String fileName) {
        return FILE_PREFIX + fileName;
    }

    /**
     * Whether the key is a YouTube key: a bare video ID. Every other key has a {@code <prefix>:},
     * and a YouTube ID never contains a colon.
     */
    public static boolean isYoutubeKey(@Nullable String key) {
        return key != null && !key.isEmpty() && key.indexOf(':') < 0;
    }

    /** Whether the key is a {@code file:} key (an imported file with no known source). */
    public static boolean isFileKey(@Nullable String key) {
        return key != null && key.startsWith(FILE_PREFIX);
    }

    /**
     * The lavaplayer source name a key belongs to: {@code youtube} for a bare video ID, the prefix
     * for {@code <source>:...} keys, and empty for {@code file:} keys and anything unrecognised.
     */
    public static Optional<String> sourceOf(@Nullable String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (isYoutubeKey(key)) {
            return Optional.of(YOUTUBE);
        }
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        String prefix = key.substring(0, colon);
        return SUPPORTED_SOURCES.contains(prefix) ? Optional.of(prefix) : Optional.empty();
    }

    /**
     * The URL yt-dlp should fetch for a track: the watch URL built from the ID for YouTube, the
     * track's {@link AudioTrackInfo#uri} for other supported sources. Empty when the track has no key.
     */
    public static Optional<String> downloadUrl(@Nullable AudioTrack track) {
        Optional<String> key = of(track);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (isYoutubeKey(key.get())) {
            return Optional.of(youtubeUrl(key.get()));
        }
        String uri = track.getInfo() == null ? null : track.getInfo().uri;
        return uri == null || uri.isBlank() ? Optional.empty() : Optional.of(uri);
    }

    /** {@code https://www.youtube.com/watch?v=<videoId>}. */
    public static String youtubeUrl(String videoId) {
        return YOUTUBE_WATCH + videoId;
    }
}
