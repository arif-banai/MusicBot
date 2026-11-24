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

import com.jagrosh.jmusicbot.audio.bilibili.WbiSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WbiSigner Tests")
class WbiSignerTest
{
    private static final String IMG_KEY = "7cd084941338484aae1ad9425b84077c";
    private static final String SUB_KEY = "4932caff0ff746eab6f01bf08b70ac45";
    private static final String MIXIN_KEY = "ea1db124af3c7062474693fa704f4ff8";

    @Test
    @DisplayName("derives the mixin key from real key material")
    void derivesMixinKey()
    {
        assertEquals(MIXIN_KEY, WbiSigner.mixinKey(IMG_KEY, SUB_KEY));
    }

    @Test
    @DisplayName("mixin key is always 32 characters")
    void mixinKeyLength()
    {
        assertEquals(32, WbiSigner.mixinKey(IMG_KEY, SUB_KEY).length());
    }

    @Test
    @DisplayName("extracts the key from a wbi image URL")
    void extractsKeyFromUrl()
    {
        assertEquals(IMG_KEY, WbiSigner.keyFromUrl("https://i0.hdslb.com/bfs/wbi/" + IMG_KEY + ".png"));
    }

    @Test
    @DisplayName("signs playurl parameters to the expected w_rid")
    void signsPlayurlParameters()
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fourk", "1");
        params.put("bvid", "BV1DTbv6xEHK");
        params.put("cid", "40990605342");
        params.put("fnval", "16");
        params.put("fnver", "0");

        String query = WbiSigner.sign(params, MIXIN_KEY, 1755400000L);

        assertEquals("bvid=BV1DTbv6xEHK&cid=40990605342&fnval=16&fnver=0&fourk=1"
                + "&wts=1755400000&w_rid=b2ccf4c580d6c55ccd9dbe3aa170907e", query);
    }

    @Test
    @DisplayName("signs a CJK search query, encoding spaces as plus")
    void signsSearchQuery()
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search_type", "video");
        params.put("keyword", "周杰伦 稻香");
        params.put("page", "1");

        String query = WbiSigner.sign(params, MIXIN_KEY, 1755400000L);

        assertTrue(query.endsWith("&w_rid=6d3769134fbbf27529071cfd988de7fb"), query);
        assertTrue(query.contains("keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6+%E7%A8%BB%E9%A6%99"), query);
    }

    @Test
    @DisplayName("strips the characters Bilibili excludes from signed values")
    void stripsExcludedCharacters()
    {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("keyword", "a!b'c(d)e*f");
        Map<String, String> clean = new LinkedHashMap<>();
        clean.put("keyword", "abcdef");

        assertEquals(WbiSigner.sign(clean, MIXIN_KEY, 1755400000L),
                WbiSigner.sign(raw, MIXIN_KEY, 1755400000L));
    }
}
