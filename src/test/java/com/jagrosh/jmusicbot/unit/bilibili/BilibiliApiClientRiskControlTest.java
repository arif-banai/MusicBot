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

import com.jagrosh.jmusicbot.audio.bilibili.BilibiliApiClient;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioSourceManager;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliRequestThrottle;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How the client reacts when Bilibili withholds a stream or refuses a request, run against
 * a local server that answers like Bilibili's API.
 */
@DisplayName("BilibiliApiClient risk-control Tests")
class BilibiliApiClientRiskControlTest
{
    private static final String NAV = "{\"code\":0,\"data\":{\"wbi_img\":{"
            + "\"img_url\":\"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png\","
            + "\"sub_url\":\"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png\"}}}";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<Long> sleeps = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();

    private MockWebServer server;
    private BilibiliAudioSourceManager manager;
    private BilibiliRequestThrottle throttle;
    private BilibiliApiClient client;

    private String view = "view-single-page.json";
    private String playurl = "playurl-dash.json";
    private int playurlStatus = 200;

    @BeforeEach
    void setUp() throws Exception
    {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                String path = request.getPath();
                synchronized(paths)
                {
                    paths.add(path.substring(0, path.indexOf('?') < 0 ? path.length() : path.indexOf('?')));
                }
                if(path.startsWith("/x/web-interface/nav"))
                    return json(NAV);
                if(path.startsWith("/x/web-interface/wbi/view"))
                    return json(fixture(view));
                if(path.startsWith("/x/player/wbi/playurl"))
                    return json(fixture(playurl)).setResponseCode(playurlStatus);
                if(path.startsWith("/x/frontend/finger/spi"))
                    return json("{\"code\":0,\"data\":{\"b_3\":\"B3-ID\",\"b_4\":\"B4-ID\"}}");
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        manager = new BilibiliAudioSourceManager();
        throttle = new BilibiliRequestThrottle(1000, 120_000, now::get, ms ->
        {
            sleeps.add(ms);
            now.addAndGet(ms);
        });
        client = client(false);
    }

    private BilibiliApiClient client(boolean browserCookie)
    {
        String base = server.url("/").toString();
        return new BilibiliApiClient(throttle, base.substring(0, base.length() - 1), browserCookie);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        manager.shutdown();
        server.shutdown();
    }

    @Test
    @DisplayName("spaces every API call it makes")
    void throttlesCalls() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface())
        {
            assertNotNull(client.loadAudioStream(http, "BV1DTbv6xEHK", 0));
        }

        // nav (signing key), view, playurl: the first goes straight out, the rest wait their turn
        assertEquals(List.of("/x/web-interface/nav", "/x/web-interface/wbi/view", "/x/player/wbi/playurl"), paths);
        assertEquals(List.of(1000L, 1000L), sleeps);
    }

    @Test
    @DisplayName("refuses a member-only search pick before asking for its stream")
    void refusesMemberOnlyBeforeStream() throws Exception
    {
        view = "view-member-only.json";

        try(HttpInterface http = manager.getHttpInterface())
        {
            FriendlyException thrown = assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 0));
            assertEquals(BilibiliApiClient.memberOnly().getMessage(), thrown.getMessage());
        }
        assertFalse(paths.contains("/x/player/wbi/playurl"), paths.toString());
    }

    @Test
    @DisplayName("blames the video, not risk control, when a member-only stream is withheld")
    void memberOnlyVoucherDoesNotCoolDown() throws Exception
    {
        view = "view-member-only.json";
        playurl = "playurl-voucher.json";

        try(HttpInterface http = manager.getHttpInterface())
        {
            FriendlyException thrown = assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertEquals(BilibiliApiClient.memberOnly().getMessage(), thrown.getMessage());

            // No cooldown: the next video still goes through.
            view = "view-single-page.json";
            playurl = "playurl-dash.json";
            assertNotNull(client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
        }
    }

    @Test
    @DisplayName("backs off when an ordinary video's stream is withheld despite the browser cookie")
    void voucherStartsCooldown() throws Exception
    {
        playurl = "playurl-voucher.json";
        client = client(true);

        try(HttpInterface http = manager.getHttpInterface())
        {
            FriendlyException thrown = assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertEquals(BilibiliRequestThrottle.rateLimited().getMessage(), thrown.getMessage());

            int sent = paths.size();
            assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertEquals(sent, paths.size(), "no request may go out during the cooldown");
        }
    }

    @Test
    @DisplayName("names the setting to turn on when a stream is withheld and the browser cookie is off")
    void voucherWithoutCookieNamesTheSetting() throws Exception
    {
        playurl = "playurl-voucher.json";

        try(HttpInterface http = manager.getHttpInterface())
        {
            FriendlyException thrown = assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertEquals(FriendlyException.Severity.COMMON, thrown.severity);
            assertTrue(thrown.getMessage().contains("playback.bilibili.browserCookie"), thrown.getMessage());

            // Waiting doesn't help without the cookie, so no cooldown: the next request still goes out.
            int sent = paths.size();
            assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertTrue(paths.size() > sent, "a request should go out: there is no cooldown");
        }
    }

    @Test
    @DisplayName("backs off when Bilibili answers HTTP 412")
    void http412StartsCooldown() throws Exception
    {
        playurlStatus = 412;

        try(HttpInterface http = manager.getHttpInterface())
        {
            FriendlyException thrown = assertThrows(FriendlyException.class,
                    () -> client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
            assertEquals(FriendlyException.Severity.COMMON, thrown.severity);

            int sent = paths.size();
            assertThrows(FriendlyException.class, () -> client.loadVideo(http, "BV1DTbv6xEHK", 1));
            assertEquals(sent, paths.size());

            now.addAndGet(120_000);
            playurlStatus = 200;
            assertNotNull(client.loadAudioStream(http, "BV1DTbv6xEHK", 40990605342L));
        }
    }

    private static MockResponse json(String body)
    {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String fixture(String name)
    {
        try(InputStream in = BilibiliApiClientRiskControlTest.class.getResourceAsStream("/bilibili/" + name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch(Exception e)
        {
            throw new IllegalStateException("missing fixture: " + name, e);
        }
    }
}
