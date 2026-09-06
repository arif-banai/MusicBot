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
package com.jagrosh.jmusicbot.unit.bilibili;

import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioSourceManager;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliAudioSourceManager Tests")
class BilibiliAudioSourceManagerTest
{
    private BilibiliAudioSourceManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new BilibiliAudioSourceManager();
    }

    @AfterEach
    void tearDown()
    {
        manager.shutdown();
    }

    @Test
    @DisplayName("reports the source name used in config and logs")
    void reportsSourceName()
    {
        assertEquals("bilibili", manager.getSourceName());
    }

    @Test
    @DisplayName("declines identifiers belonging to other sources without any network call")
    void declinesForeignIdentifiers()
    {
        assertNull(manager.loadItem(null, new AudioReference("https://youtube.com/watch?v=abc", null)));
        assertNull(manager.loadItem(null, new AudioReference("ytsearch:hello", null)));
        assertNull(manager.loadItem(null, new AudioReference("scsearch:hello", null)));
        assertNull(manager.loadItem(null, new AudioReference("some random text", null)));
    }

    @Test
    @DisplayName("exposes an HTTP interface for the track to stream through")
    void exposesHttpInterface()
    {
        assertNotNull(manager.getHttpInterface());
        assertNotNull(manager.getApiClient());
    }

    @Test
    @DisplayName("round-trips a track's cid through encode and decode")
    void roundTripsTrackEncoding() throws Exception
    {
        AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 1000L, "BV1DTbv6xEHK",
                false, "https://www.bilibili.com/video/BV1DTbv6xEHK", "https://pic", null);
        AudioTrack original = new BilibiliAudioTrack(info, 40990605342L, manager);

        assertTrue(manager.isTrackEncodable(original));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        manager.encodeTrack(original, new DataOutputStream(bytes));

        AudioTrack decoded = manager.decodeTrack(info,
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertInstanceOf(BilibiliAudioTrack.class, decoded);
        assertEquals(40990605342L, ((BilibiliAudioTrack) decoded).getCid(),
                "the cid must survive a restart so queued tracks stay playable");
    }

    @Test
    @DisplayName("a cloned track keeps its cid and source manager")
    void clonesTrack()
    {
        AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 1000L, "BV1DTbv6xEHK",
                false, "https://www.bilibili.com/video/BV1DTbv6xEHK", "https://pic", null);
        BilibiliAudioTrack original = new BilibiliAudioTrack(info, 12345L, manager);

        AudioTrack clone = original.makeClone();

        assertInstanceOf(BilibiliAudioTrack.class, clone);
        assertEquals(12345L, ((BilibiliAudioTrack) clone).getCid());
        assertSame(manager, clone.getSourceManager());
    }
}
