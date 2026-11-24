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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bilibili CDN mirror fallback Tests")
class BilibiliMirrorFallbackTest
{
    private static final byte[] BODY = "....ftypdash".getBytes(StandardCharsets.US_ASCII);

    private final List<String> paths = new ArrayList<>();
    private MockWebServer server;
    private BilibiliAudioSourceManager manager;

    @BeforeEach
    void setUp() throws Exception
    {
        server = new MockWebServer();
        // /akam behaves like upos-hz-mirrorakam.akamaized.net, which refuses this bot with
        // 403 whatever headers it sends; /cosov like upos-sz-mirrorcosov, which serves it.
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                synchronized(paths)
                {
                    paths.add(request.getPath());
                }
                if(request.getPath().startsWith("/akam"))
                    return new MockResponse().setResponseCode(403).setBody("Access Denied");
                if(request.getPath().startsWith("/gone"))
                    return new MockResponse().setResponseCode(404);
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

    private String url(String path)
    {
        return server.url(path).toString();
    }

    @Test
    @DisplayName("falls back to a backup mirror when the first one refuses")
    void fallsBackToBackup() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface();
            BilibiliHttpStream stream = BilibiliHttpStream.openFirstAvailable(http,
                    List.of(url("/akam/a.m4s"), url("/cosov/a.m4s"))))
        {
            byte[] header = new byte[12];
            assertEquals(12, stream.read(header, 0, 12));
            assertEquals("ftyp", new String(header, 4, 4, StandardCharsets.US_ASCII));
            assertTrue(stream.getUri().getPath().startsWith("/cosov"), stream.getUri().toString());
        }
        assertEquals(List.of("/akam/a.m4s", "/cosov/a.m4s"), paths);
    }

    @Test
    @DisplayName("uses the first mirror when it works, without touching the backups")
    void usesFirstWhenItWorks() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface();
            BilibiliHttpStream stream = BilibiliHttpStream.openFirstAvailable(http,
                    List.of(url("/cosov/a.m4s"), url("/akam/a.m4s"))))
        {
            assertEquals(12, stream.read(new byte[12], 0, 12));
        }
        assertEquals(List.of("/cosov/a.m4s"), paths);
    }

    @Test
    @DisplayName("reports the last refusal when no mirror will serve the audio")
    void failsWhenAllRefuse() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface())
        {
            IOException e = assertThrows(IOException.class, () -> BilibiliHttpStream.openFirstAvailable(http,
                    List.of(url("/akam/a.m4s"), url("/gone/a.m4s"))));
            assertTrue(e.getMessage().contains("404"), e.getMessage());
        }
        assertEquals(List.of("/akam/a.m4s", "/gone/a.m4s"), paths);
    }

    @Test
    @DisplayName("stops trying mirrors when playback is stopped mid-connect")
    void stopsWhenInterrupted() throws Exception
    {
        try(HttpInterface http = manager.getHttpInterface())
        {
            Thread.currentThread().interrupt();
            try
            {
                assertThrows(InterruptedIOException.class, () -> BilibiliHttpStream.openFirstAvailable(http,
                        List.of(url("/akam/a.m4s"), url("/cosov/a.m4s"))));
            }
            finally
            {
                Thread.interrupted();
            }
        }
        assertFalse(paths.contains("/cosov/a.m4s"), paths.toString());
    }
}
