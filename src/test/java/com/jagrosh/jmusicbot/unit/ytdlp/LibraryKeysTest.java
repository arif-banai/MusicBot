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
package com.jagrosh.jmusicbot.unit.ytdlp;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryKeys;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LibraryKeys Tests")
class LibraryKeysTest {

    private static final String SC_PERMALINK = "https://soundcloud.com/crazyfrog/axel-f";
    private static final String SC_IDENTIFIER =
            "O:https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/0a1b2c3d/stream/hls";
    private static final String BANDCAMP_URL = "https://artist.bandcamp.com/track/some-song";

    private static AudioTrack track(String sourceName, String identifier, String uri, boolean stream) {
        AudioSourceManager source = sourceName == null ? null : mock(AudioSourceManager.class);
        if (source != null) {
            when(source.getSourceName()).thenReturn(sourceName);
        }
        AudioTrack track = mock(AudioTrack.class);
        when(track.getSourceManager()).thenReturn(source);
        when(track.getInfo()).thenReturn(new AudioTrackInfo("Title", "Author", stream ? Long.MAX_VALUE : 1000L,
                identifier, stream, uri, null, null));
        return track;
    }

    @Test
    @DisplayName("YouTube keys are the bare video ID")
    void youtube_isBareId() {
        AudioTrack t = track("youtube", "k85mRPqvMbE", "https://www.youtube.com/watch?v=k85mRPqvMbE", false);

        assertEquals(Optional.of("k85mRPqvMbE"), LibraryKeys.of(t));
    }

    @Test
    @DisplayName("Bandcamp, Vimeo and NicoNico keys are <source>:<identifier>")
    void otherSources_areSourceColonIdentifier() {
        assertEquals(Optional.of("bandcamp:" + BANDCAMP_URL),
                LibraryKeys.of(track("bandcamp", BANDCAMP_URL, BANDCAMP_URL, false)));
        assertEquals(Optional.of("vimeo:76979871"),
                LibraryKeys.of(track("vimeo", "76979871", "https://vimeo.com/76979871", false)));
        assertEquals(Optional.of("niconico:sm9"),
                LibraryKeys.of(track("niconico", "sm9", "http://www.nicovideo.jp/watch/sm9", false)));
    }

    @Test
    @DisplayName("SoundCloud keys use the permalink URL, not lavaplayer's per-format identifier")
    void soundcloud_usesPermalink() {
        AudioTrack t = track("soundcloud", SC_IDENTIFIER, SC_PERMALINK, false);

        assertEquals(Optional.of("soundcloud:" + SC_PERMALINK), LibraryKeys.of(t));
    }

    @Test
    @DisplayName("streams, local files, HTTP and unsupported sources have no key")
    void unsupported_isEmpty() {
        assertEquals(Optional.empty(), LibraryKeys.of(track("twitch", "https://twitch.tv/x", "https://twitch.tv/x", true)));
        assertEquals(Optional.empty(), LibraryKeys.of(track("youtube", "liveLiveLiv", "https://youtu.be/liveLiveLiv", true)));
        assertEquals(Optional.empty(), LibraryKeys.of(track("local", "C:/music/a.mp3", "C:/music/a.mp3", false)));
        assertEquals(Optional.empty(), LibraryKeys.of(track("http", "https://x.test/a.mp3", "https://x.test/a.mp3", false)));
        assertEquals(Optional.empty(), LibraryKeys.of(track("getyarn.io", "abc", "https://getyarn.io/abc", false)));
        assertEquals(Optional.empty(), LibraryKeys.of(track(null, "abc", "abc", false)));
        assertEquals(Optional.empty(), LibraryKeys.of(track("youtube", "", "", false)));
        assertEquals(Optional.empty(), LibraryKeys.of(null));
    }

    @Test
    @DisplayName("a library track's key is the key it was built with")
    void libraryTrack_usesItsKey() {
        AudioTrackInfo info = new AudioTrackInfo("t", "a", 1000L, "x", false, "u", null, null);
        LibraryAudioTrack t = new LibraryAudioTrack(info, "soundcloud:" + SC_PERMALINK,
                () -> new CompletableFuture<>(), null, null);

        assertEquals(Optional.of("soundcloud:" + SC_PERMALINK), LibraryKeys.of(t));
    }

    @Test
    @DisplayName("a yt-dlp sidecar maps to the same key LibraryKeys.of gives that site's lavaplayer track")
    void sidecarKey_agreesWithTrackKey() {
        assertEquals(LibraryKeys.of(track("youtube", "k85mRPqvMbE", "https://www.youtube.com/watch?v=k85mRPqvMbE", false)),
                LibraryKeys.fromSidecar("Youtube", "k85mRPqvMbE", "https://www.youtube.com/watch?v=k85mRPqvMbE"));
        assertEquals(LibraryKeys.of(track("soundcloud", SC_IDENTIFIER, SC_PERMALINK, false)),
                LibraryKeys.fromSidecar("Soundcloud", "123456789", SC_PERMALINK));
        assertEquals(LibraryKeys.of(track("bandcamp", BANDCAMP_URL, BANDCAMP_URL, false)),
                LibraryKeys.fromSidecar("Bandcamp", "2468", BANDCAMP_URL));
        assertEquals(LibraryKeys.of(track("vimeo", "76979871", "https://vimeo.com/76979871", false)),
                LibraryKeys.fromSidecar("Vimeo", "76979871", "https://vimeo.com/76979871"));
        assertEquals(LibraryKeys.of(track("niconico", "sm9", "http://www.nicovideo.jp/watch/sm9", false)),
                LibraryKeys.fromSidecar("Niconico", "sm9", "https://www.nicovideo.jp/watch/sm9"));
    }

    @Test
    @DisplayName("sidecar extractor names are matched case-insensitively; unknown extractors have no key")
    void sidecarKey_caseInsensitiveAndUnknown() {
        assertEquals(Optional.of("vimeo:1"), LibraryKeys.fromSidecar("vimeo", "1", "https://vimeo.com/1"));
        assertEquals(Optional.empty(), LibraryKeys.fromSidecar("Twitter", "1", "https://x.com/a/status/1"));
        assertEquals(Optional.empty(), LibraryKeys.fromSidecar("Youtube", "notAnId", "https://youtube.com"));
        assertEquals(Optional.empty(), LibraryKeys.fromSidecar(null, "1", "u"));
        assertEquals(Optional.empty(), LibraryKeys.fromSidecar("Soundcloud", "1", null));
    }

    @Test
    @DisplayName("sourceOf() names the source of a key; file: keys have none")
    void sourceOf_namesTheSource() {
        assertEquals(Optional.of("youtube"), LibraryKeys.sourceOf("k85mRPqvMbE"));
        assertEquals(Optional.of("soundcloud"), LibraryKeys.sourceOf("soundcloud:" + SC_PERMALINK));
        assertEquals(Optional.of("vimeo"), LibraryKeys.sourceOf("vimeo:1"));
        assertEquals(Optional.empty(), LibraryKeys.sourceOf("file:Song.mp3"));
        assertEquals(Optional.empty(), LibraryKeys.sourceOf(null));
    }

    @Test
    @DisplayName("downloadUrl() is the watch URL for YouTube and the track URI elsewhere")
    void downloadUrl_perSource() {
        assertEquals(Optional.of("https://www.youtube.com/watch?v=k85mRPqvMbE"),
                LibraryKeys.downloadUrl(track("youtube", "k85mRPqvMbE", "https://youtu.be/k85mRPqvMbE", false)));
        assertEquals(Optional.of(SC_PERMALINK),
                LibraryKeys.downloadUrl(track("soundcloud", SC_IDENTIFIER, SC_PERMALINK, false)));
        assertEquals(Optional.empty(),
                LibraryKeys.downloadUrl(track("local", "C:/a.mp3", "C:/a.mp3", false)));
    }
}
