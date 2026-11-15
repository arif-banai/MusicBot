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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Lavaplayer {@link AudioSourceManager} that intercepts a YouTube link whose video is
 * already in the yt-dlp library, so it plays straight from disk without lavaplayer ever
 * asking YouTube for it.
 * <p>
 * Registered <b>before</b> the YouTube source manager (see {@code AudioSource} and
 * {@code PlayerManager#init()}), so a hit never reaches YouTube at all: lavaplayer asks
 * registered source managers in registration order and uses the first one that returns a
 * non-null {@link AudioItem}. Active whenever the library index has the video, in any mode
 * and even with the yt-dlp feature turned off, matching the "library hits play from disk in
 * any mode" rule the rest of this package follows.
 * <p>
 * {@link #loadItem} is cheap and never throws: it does only a regex match plus one in-memory
 * index lookup (which also does a cheap {@code Files.exists} check on a hit's file), and
 * returns {@code null} fast for anything that isn't a plain YouTube watch/short/{@code youtu.be}
 * URL (search queries, other platforms, playlist URLs), a miss, or an <b>adopted</b> entry
 * (see {@link YtDlpService#isAdopted}): an adopted entry's first link still has to go through
 * YouTube once so {@code YtDlpService#prepareForPlayback} can fill its real metadata in;
 * only later links are intercepted here once it's no longer adopted.
 *
 * @author Arif Banai (arif-banai)
 */
public final class LibrarySourceManager implements AudioSourceManager {

    private static final String VIDEO_ID = "[A-Za-z0-9_-]{11}";
    private static final Pattern LIST_PARAM = Pattern.compile("[?&]list=");
    private static final Pattern WATCH_HOST = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.|m\\.|music\\.)?youtube\\.com/watch\\?.+$");
    private static final Pattern WATCH_VIDEO_PARAM = Pattern.compile("[?&]v=(" + VIDEO_ID + ")(?=[&]|$)");
    private static final Pattern SHORTS_URL = Pattern.compile(
            "(?i)^(?:https?://)?(?:www\\.|m\\.|music\\.)?youtube\\.com/shorts/(" + VIDEO_ID + ")(?:[/?].*)?$");
    private static final Pattern YOUTU_BE_URL = Pattern.compile(
            "(?i)^(?:https?://)?youtu\\.be/(" + VIDEO_ID + ")(?:[/?].*)?$");

    /**
     * Looked up lazily on every call, never cached. {@code PlayerManager.init()} does run
     * after {@code Bot} has finished constructing its {@code YtDlpService}, so resolving it
     * once at registration time would also work in production; the supplier is used anyway
     * so this class doesn't depend on that ordering and tolerates a {@code Bot} (or test
     * double) whose {@code getYtDlp()} returns {@code null}. A {@code null} result is
     * treated as "nothing indexed".
     */
    private final Supplier<@Nullable YtDlpService> serviceSupplier;

    public LibrarySourceManager(Supplier<@Nullable YtDlpService> serviceSupplier) {
        this.serviceSupplier = serviceSupplier;
    }

    @Override
    public String getSourceName() {
        return "ytdlp-library";
    }

    @Override
    public @Nullable AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String videoId = extractVideoId(reference == null ? null : reference.identifier);
        if (videoId == null) {
            return null;
        }
        YtDlpService service = serviceSupplier.get();
        if (service == null) {
            return null;
        }
        Optional<LibraryEntry> hit = service.library().find(videoId);
        if (hit.isEmpty()) {
            // A video a person linked to a library song plays that copy (yt-dlp enabled only).
            return service.linkedLink(videoId, youtubeSource(manager)).orElse(null);
        }
        LibraryEntry entry = hit.get();
        if (YtDlpService.isAdopted(entry)) {
            // Don't short-circuit an adopted entry's first link: let it fall through to
            // YouTube once, so prepareForPlayback's library-hit branch plays it from disk
            // AND fills in the real metadata. Only later links (once it's no longer adopted)
            // are intercepted here.
            return null;
        }
        return service.libraryTrack(entry, youtubeSource(manager));
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return false;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {
        // Never encoded: isTrackEncodable() is always false.
    }

    @Override
    public @Nullable AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return null;
    }

    @Override
    public void shutdown() {
        // No owned resources.
    }

    /**
     * Extracts an 11-character YouTube video ID from a watch, {@code shorts} or
     * {@code youtu.be} URL, with or without a scheme, and with a {@code www.}, {@code m.} or
     * {@code music.} subdomain for the first two forms. Returns {@code null} for anything
     * else, including a watch URL that also carries a {@code list=} parameter, so YouTube
     * loads it as a playlist unchanged.
     */
    static @Nullable String extractVideoId(@Nullable String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String trimmed = identifier.trim();
        if (LIST_PARAM.matcher(trimmed).find()) {
            return null;
        }
        if (WATCH_HOST.matcher(trimmed).matches()) {
            Matcher v = WATCH_VIDEO_PARAM.matcher(trimmed);
            return v.find() ? v.group(1) : null;
        }
        Matcher shorts = SHORTS_URL.matcher(trimmed);
        if (shorts.matches()) {
            return shorts.group(1);
        }
        Matcher youtuBe = YOUTU_BE_URL.matcher(trimmed);
        if (youtuBe.matches()) {
            return youtuBe.group(1);
        }
        return null;
    }

    /** The registered YouTube source manager, or null if none is registered. */
    private static @Nullable AudioSourceManager youtubeSource(AudioPlayerManager manager) {
        for (AudioSourceManager source : manager.getSourceManagers()) {
            if ("youtube".equals(source.getSourceName())) {
                return source;
            }
        }
        return null;
    }
}
