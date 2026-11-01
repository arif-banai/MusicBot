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
package com.jagrosh.jmusicbot.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SoundCloud AAC playlist parsing")
class SoundCloudAacSegmentDecoderTest
{
    @Test
    @DisplayName("resolves the initialization segment from EXT-X-MAP")
    void readsInitializationSegment() throws IOException
    {
        String playlist = "https://playback.example/aac_160k/playlist.m3u8?expires=1";
        String init = SoundCloudAacSegmentDecoder.initializationUrl(playlist, new String[] {
                "#EXTM3U",
                "#EXT-X-MAP:URI=\"init.mp4?expires=1\"",
                "#EXTINF:10.0,",
                "data000.m4s"
        });

        assertEquals("https://playback.example/aac_160k/init.mp4?expires=1", init);
    }

    @Test
    @DisplayName("rejects an encrypted media playlist")
    void rejectsEncryptedPlaylist()
    {
        IOException error = assertThrows(IOException.class, () -> SoundCloudAacSegmentDecoder.initializationUrl(
                "https://playback.example/playlist.m3u8",
                new String[] {
                        "#EXTM3U",
                        "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"https://keys.example/key\"",
                        "#EXT-X-MAP:URI=\"init.mp4\""
                }));

        assertEquals("SoundCloud AAC stream is encrypted.", error.getMessage());
    }
}
