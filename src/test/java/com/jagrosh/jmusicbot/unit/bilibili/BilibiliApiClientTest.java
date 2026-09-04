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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliApiClient Tests")
class BilibiliApiClientTest
{
    private static final String MIXIN_KEY = "ea1db124af3c7062474693fa704f4ff8";

    @Test
    @DisplayName("sends a Referer, which Bilibili's CDN requires")
    void definesReferer()
    {
        assertEquals("https://www.bilibili.com", BilibiliApiClient.REFERER);
    }

    @Test
    @DisplayName("sends a browser User-Agent, which Bilibili's CDN requires")
    void definesBrowserUserAgent()
    {
        assertTrue(BilibiliApiClient.USER_AGENT.contains("Mozilla/5.0"), BilibiliApiClient.USER_AGENT);
    }

    @Test
    @DisplayName("builds a signed playurl query with the DASH parameters")
    void buildsSignedPlayurlQuery()
    {
        String query = BilibiliApiClient.buildPlayurlQuery("BV1DTbv6xEHK", 40990605342L,
                MIXIN_KEY, 1755400000L);

        assertEquals("bvid=BV1DTbv6xEHK&cid=40990605342&fnval=16&fnver=0&fourk=1"
                + "&wts=1755400000&w_rid=b2ccf4c580d6c55ccd9dbe3aa170907e", query);
    }

    @Test
    @DisplayName("uses aid for av ids and bvid for BV ids")
    void selectsIdParameter()
    {
        assertEquals("aid=170001", BilibiliApiClient.buildUnsignedViewQuery("av170001"));
        assertEquals("bvid=BV1DTbv6xEHK", BilibiliApiClient.buildUnsignedViewQuery("BV1DTbv6xEHK"));
    }

    @Test
    @DisplayName("signs the view query, which the plain endpoint's risk control now requires")
    void buildsSignedViewQuery()
    {
        assertEquals("bvid=BV1DTbv6xEHK&wts=1755400000&w_rid=5f94615885f2ee37b38f22b141df7f76",
                BilibiliApiClient.buildViewQuery("BV1DTbv6xEHK", MIXIN_KEY, 1755400000L));

        assertEquals("aid=170001&wts=1755400000&w_rid=3549f09727df8b40a060137c8d36e2ba",
                BilibiliApiClient.buildViewQuery("av170001", MIXIN_KEY, 1755400000L));
    }

    @Test
    @DisplayName("builds an unsigned playurl query as the fallback when signing is unavailable")
    void buildsUnsignedFallbackQuery()
    {
        String query = BilibiliApiClient.buildUnsignedPlayurlQuery("BV1DTbv6xEHK", 40990605342L);

        assertTrue(query.contains("bvid=BV1DTbv6xEHK"), query);
        assertTrue(query.contains("cid=40990605342"), query);
        assertTrue(query.contains("fnval=16"), query);
        assertFalse(query.contains("w_rid"), "the fallback must not claim to be signed");
    }
}
