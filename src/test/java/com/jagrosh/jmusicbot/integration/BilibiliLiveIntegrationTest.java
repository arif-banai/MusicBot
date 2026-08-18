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
package com.jagrosh.jmusicbot.integration;

import com.jagrosh.jmusicbot.audio.bilibili.BilibiliApiClient;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioSourceManager;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioStream;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliVideo;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the Bilibili source against the live API.
 *
 * <p>Disabled unless {@code BILIBILI_LIVE_TEST=1}, so CI stays offline and deterministic.
 * Run with: {@code BILIBILI_LIVE_TEST=1 mvn test -Dtest=BilibiliLiveIntegrationTest}
 *
 * @author Arif Banai (arif-banai)
 */
@EnabledIfEnvironmentVariable(named = "BILIBILI_LIVE_TEST", matches = "1")
@DisplayName("Bilibili Live API Integration")
class BilibiliLiveIntegrationTest
{
    @Test
    @DisplayName("searches, resolves metadata, and opens a readable audio stream")
    void resolvesRealVideoEndToEnd() throws Exception
    {
        BilibiliAudioSourceManager manager = new BilibiliAudioSourceManager();

        try(HttpInterface http = manager.getHttpInterface())
        {
            BilibiliApiClient client = manager.getApiClient();

            List<BilibiliVideo> results = client.search(http, "周杰伦", 5);
            assertFalse(results.isEmpty(), "search should return playable results");
            assertTrue(results.stream().allMatch(v -> !v.bvid().isBlank()));

            BilibiliVideo video = client.loadVideo(http, results.get(0).bvid(), 1);
            assertTrue(video.cid() > 0, "view should yield a page id");
            assertTrue(video.durationMs() > 0, "view should yield a duration");

            BilibiliAudioStream stream = client.loadAudioStream(http, video.bvid(), video.cid());
            assertTrue(stream.url().startsWith("http"), stream.url());

            // Proves the Referer/User-Agent headers are actually reaching the CDN: without
            // them Bilibili answers 403 and no bytes arrive.
            try(PersistentHttpStream input = new PersistentHttpStream(http, new URI(stream.url()), null))
            {
                byte[] header = new byte[12];
                assertEquals(12, input.read(header, 0, 12), "should read the container header");

                String boxType = new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
                assertEquals("ftyp", boxType, "Bilibili audio should be an MP4 container");
            }
        }
        finally
        {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("resolves a multi-page video to the requested page only")
    void resolvesRequestedPageOnly() throws Exception
    {
        BilibiliAudioSourceManager manager = new BilibiliAudioSourceManager();

        try(HttpInterface http = manager.getHttpInterface())
        {
            BilibiliApiClient client = manager.getApiClient();
            List<BilibiliVideo> results = client.search(http, "纪录片", 10);
            assertFalse(results.isEmpty());

            // Any video resolves page 1; a video with several pages gives page 2 a distinct cid.
            BilibiliVideo page1 = client.loadVideo(http, results.get(0).bvid(), 1);
            BilibiliVideo page2 = client.loadVideo(http, results.get(0).bvid(), 2);

            assertTrue(page1.cid() > 0);
            assertTrue(page2.cid() > 0);
        }
        finally
        {
            manager.shutdown();
        }
    }
}
