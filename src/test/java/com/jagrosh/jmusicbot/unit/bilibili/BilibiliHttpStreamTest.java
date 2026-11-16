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
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliHttpStream Tests")
class BilibiliHttpStreamTest
{
    private static final byte[] BODY = "....ftypdash".getBytes(StandardCharsets.US_ASCII);

    private MockWebServer server;
    private BilibiliAudioSourceManager manager;

    @BeforeEach
    void setUp() throws Exception
    {
        server = new MockWebServer();
        // Behaves like upos-sz-mirrorcosov.bilivideo.com: a GET without Range is refused.
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                if(request.getHeader("Range") == null)
                    return new MockResponse().setResponseCode(567);
                return new MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-" + (BODY.length - 1) + "/" + BODY.length)
                        .setBody(new Buffer().write(BODY));
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

    @Test
    @DisplayName("asks for a range on the first request, which some Bilibili CDN nodes require")
    void sendsRangeFromTheStart() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface();
            BilibiliHttpStream stream = new BilibiliHttpStream(http, server.url("/audio.m4s").uri(), null))
        {
            byte[] header = new byte[12];
            assertEquals(12, stream.read(header, 0, 12));
            assertEquals("ftyp", new String(header, 4, 4, StandardCharsets.US_ASCII));
        }

        assertEquals("bytes=0-", server.takeRequest().getHeader("Range"));
    }
}
