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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioStream;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliResponseParser;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliVideo;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliResponseParser Tests")
class BilibiliResponseParserTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode fixture(String name) throws Exception
    {
        try(InputStream in = BilibiliResponseParserTest.class.getResourceAsStream("/bilibili/" + name))
        {
            assertNotNull(in, "missing fixture: " + name);
            return MAPPER.readTree(in);
        }
    }

    @Test
    @DisplayName("parses metadata from a single-page video")
    void parsesSinglePageVideo() throws Exception
    {
        BilibiliVideo video = BilibiliResponseParser.parseView(fixture("view-single-page.json"), 1);

        assertFalse(video.bvid().isBlank());
        assertTrue(video.cid() > 0);
        assertFalse(video.title().isBlank());
        assertFalse(video.author().isBlank());
        assertTrue(video.durationMs() > 0, "duration should be in milliseconds");
        assertTrue(video.thumbnailUrl().startsWith("http"));
    }

    @Test
    @DisplayName("selects the requested page of a multi-page video")
    void selectsRequestedPage() throws Exception
    {
        JsonNode root = fixture("view-multi-page.json");
        BilibiliVideo first = BilibiliResponseParser.parseView(root, 1);
        BilibiliVideo third = BilibiliResponseParser.parseView(root, 3);

        assertNotEquals(first.cid(), third.cid(), "each page has its own cid");
        assertTrue(third.title().contains("P3"), "page title should identify the part: " + third.title());
    }

    @Test
    @DisplayName("falls back to page 1 when the page is out of range")
    void fallsBackToFirstPage() throws Exception
    {
        JsonNode root = fixture("view-multi-page.json");
        assertEquals(BilibiliResponseParser.parseView(root, 1).cid(),
                     BilibiliResponseParser.parseView(root, 999).cid());
    }

    @Test
    @DisplayName("picks the highest-bandwidth DASH audio stream")
    void picksHighestBandwidthAudio() throws Exception
    {
        JsonNode root = fixture("playurl-dash.json");
        BilibiliAudioStream stream = BilibiliResponseParser.parseAudioStream(root);

        int highest = 0;
        for(JsonNode audio : root.path("data").path("dash").path("audio"))
            highest = Math.max(highest, audio.path("bandwidth").asInt());

        assertEquals(highest, stream.bandwidth(), "must select the best available bitrate");
        assertTrue(stream.url().startsWith("http"));
        assertNotNull(stream.backupUrls());
    }

    @Test
    @DisplayName("parses search results and skips entries without a bvid")
    void parsesSearchResults() throws Exception
    {
        List<BilibiliVideo> results = BilibiliResponseParser.parseSearch(fixture("search-video.json"));

        assertFalse(results.isEmpty());
        assertTrue(results.stream().noneMatch(v -> v.bvid() == null || v.bvid().isBlank()),
                "ad/placeholder entries must be filtered out");
        assertTrue(results.stream().noneMatch(v -> v.title().contains("<em")),
                "search titles must have their HTML highlight tags stripped");
        assertTrue(results.stream().anyMatch(v -> v.durationMs() > 0),
                "m:ss durations must be converted to milliseconds");
        assertTrue(results.stream().allMatch(v -> v.thumbnailUrl().startsWith("http")),
                "protocol-relative thumbnails must be normalized");
    }

    @Test
    @DisplayName("throws a FriendlyException carrying Bilibili's message on an error code")
    void throwsOnErrorCode() throws Exception
    {
        JsonNode root = fixture("view-error-notfound.json");
        FriendlyException thrown = assertThrows(FriendlyException.class,
                () -> BilibiliResponseParser.checkCode(root));
        assertTrue(thrown.getMessage().contains("啥都木有"), thrown.getMessage());
    }

    @Test
    @DisplayName("accepts a successful response")
    void acceptsSuccess() throws Exception
    {
        assertDoesNotThrow(() -> BilibiliResponseParser.checkCode(fixture("view-single-page.json")));
    }

    @Test
    @DisplayName("reports a clear error when a response carries no playable audio")
    void reportsMissingAudio() throws Exception
    {
        JsonNode empty = MAPPER.readTree("{\"code\":0,\"data\":{}}");
        FriendlyException thrown = assertThrows(FriendlyException.class,
                () -> BilibiliResponseParser.parseAudioStream(empty));
        assertTrue(thrown.getMessage().toLowerCase().contains("audio"), thrown.getMessage());
    }

    @Test
    @DisplayName("falls back to the progressive durl stream when no DASH audio exists")
    void fallsBackToDurl() throws Exception
    {
        JsonNode durlOnly = MAPPER.readTree(
                "{\"code\":0,\"data\":{\"durl\":[{\"url\":\"https://cdn.example/x.mp4\","
                + "\"backup_url\":[\"https://backup.example/x.mp4\"]}]}}");

        BilibiliAudioStream stream = BilibiliResponseParser.parseAudioStream(durlOnly);

        assertEquals("https://cdn.example/x.mp4", stream.url());
        assertEquals(List.of("https://backup.example/x.mp4"), stream.backupUrls());
    }
}
