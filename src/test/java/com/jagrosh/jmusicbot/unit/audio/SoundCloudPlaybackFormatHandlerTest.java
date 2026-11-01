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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.SoundCloudPlaybackFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudTrackFormat;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudTrackFormat;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SoundCloud playback format handler")
class SoundCloudPlaybackFormatHandlerTest
{
    private final SoundCloudPlaybackFormatHandler handler = new SoundCloudPlaybackFormatHandler();

    @Test
    @DisplayName("keeps progressive MP3 when SoundCloud still offers it")
    void prefersProgressiveMp3()
    {
        SoundCloudTrackFormat chosen = handler.chooseBestFormat(List.of(
                format("cbc-encrypted-hls", "audio/mp4; codecs=\"mp4a.40.2\""),
                format("progressive", "audio/mpeg")
        ));

        assertEquals("progressive", chosen.getProtocol());
        assertTrue(handler.buildFormatIdentifier(chosen).startsWith("M:"));
    }

    @Test
    @DisplayName("reports encrypted HLS when it is the only available protocol")
    void reportsEncryptedOnlyTracks()
    {
        FriendlyException error = assertThrows(FriendlyException.class, () -> handler.chooseBestFormat(List.of(
                format("cbc-encrypted-hls", "audio/mp4; codecs=\"mp4a.40.2\""),
                format("ctr-encrypted-hls", "audio/mp4; codecs=\"mp4a.40.2\"")
        )));

        assertEquals(COMMON, error.severity);
        assertEquals("SoundCloud only offers an encrypted stream for this track, which cannot be played.",
                error.getMessage());
    }

    @Test
    @DisplayName("selects the first unencrypted AAC HLS stream")
    void selectsAacHls()
    {
        SoundCloudTrackFormat chosen = handler.chooseBestFormat(List.of(
                format("hls", "audio/mp4; codecs=\"mp4a.40.2\"", "https://api-v2.soundcloud.com/media/aac160"),
                format("hls", "audio/mp4; codecs=\"mp4a.40.2\"", "https://api-v2.soundcloud.com/media/aac96"),
                format("hls", "audio/mpegurl", "https://api-v2.soundcloud.com/media/abr")
        ));

        assertEquals("https://api-v2.soundcloud.com/media/aac160", chosen.getLookupUrl());
        String identifier = handler.buildFormatIdentifier(chosen);
        assertTrue(identifier.startsWith("A:"));
        assertNotNull(handler.getM3uInfo(identifier));
        assertEquals("https://api-v2.soundcloud.com/media/aac160", handler.getM3uInfo(identifier).lookupUrl);
    }

    @Test
    @DisplayName("reports an unsupported format when nothing playable or encrypted is present")
    void reportsOtherUnsupportedFormats()
    {
        FriendlyException error = assertThrows(FriendlyException.class, () -> handler.chooseBestFormat(List.of(
                format("hls", "audio/mpegurl")
        )));

        assertEquals(COMMON, error.severity);
        assertEquals("SoundCloud did not offer a playable stream format for this track.",
                error.getMessage());
    }

    private static SoundCloudTrackFormat format(String protocol, String mimeType)
    {
        return format(protocol, mimeType, "https://api-v2.soundcloud.com/media/stream");
    }

    private static SoundCloudTrackFormat format(String protocol, String mimeType, String lookupUrl)
    {
        return new DefaultSoundCloudTrackFormat("track", protocol, mimeType, lookupUrl);
    }
}
