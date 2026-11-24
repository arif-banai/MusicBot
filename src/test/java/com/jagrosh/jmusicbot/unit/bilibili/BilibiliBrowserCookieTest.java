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
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bilibili browser cookie Tests")
class BilibiliBrowserCookieTest
{
    private static final String NAV = "{\"code\":0,\"data\":{\"wbi_img\":{"
            + "\"img_url\":\"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png\","
            + "\"sub_url\":\"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png\"}}}";
    private static final String SPI = "{\"code\":0,\"data\":{\"b_3\":\"B3-ID\",\"b_4\":\"B4-ID\"}}";

    private final List<RecordedRequest> requests = new ArrayList<>();
    private MockWebServer server;
    private BilibiliAudioSourceManager manager;
    private int spiStatus = 200;

    @BeforeEach
    void setUp() throws Exception
    {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                synchronized(requests)
                {
                    requests.add(request);
                }
                if(request.getPath().startsWith("/x/frontend/finger/spi"))
                    return new MockResponse().setResponseCode(spiStatus).setBody(spiStatus == 200 ? SPI : "");
                return new MockResponse().setBody(NAV);
            }
        });
        server.start();
        manager = new BilibiliAudioSourceManager();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        manager.shutdown();
        server.shutdown();
    }

    private BilibiliApiClient client(boolean browserCookie)
    {
        String base = server.url("/").toString();
        return new BilibiliApiClient(new BilibiliRequestThrottle(0, 0, System::currentTimeMillis, ms -> {}),
                base.substring(0, base.length() - 1), browserCookie);
    }

    private List<String> searchCookies(BilibiliApiClient client) throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface())
        {
            // An empty search result is fine: only the requests matter here.
            client.search(http, "lofi", 5);
            client.search(http, "jazz", 5);
        }
        List<String> cookies = new ArrayList<>();
        for(RecordedRequest request : requests)
            if(request.getPath().startsWith("/x/web-interface/wbi/search"))
                cookies.add(request.getHeader("Cookie"));
        return cookies;
    }

    @Test
    @DisplayName("sends the browser id cookies on API calls when enabled, fetching them once")
    void sendsCookieWhenEnabled() throws Exception
    {
        assertEquals(List.of("buvid3=B3-ID; buvid4=B4-ID", "buvid3=B3-ID; buvid4=B4-ID"), searchCookies(client(true)));
        assertEquals(1, requests.stream().filter(r -> r.getPath().startsWith("/x/frontend/finger/spi")).count());
    }

    @Test
    @DisplayName("sends no cookie and never asks for one when disabled")
    void noCookieWhenDisabled() throws Exception
    {
        List<String> cookies = searchCookies(client(false));
        assertEquals(2, cookies.size());
        cookies.forEach(c -> assertNull(c));
        assertTrue(requests.stream().noneMatch(r -> r.getPath().startsWith("/x/frontend/finger/spi")));
    }

    @Test
    @DisplayName("carries on without the cookie when Bilibili won't hand one out")
    void continuesWithoutCookie() throws Exception
    {
        spiStatus = 412;
        List<String> cookies = searchCookies(client(true));
        assertEquals(2, cookies.size());
        cookies.forEach(c -> assertNull(c));
    }
}
