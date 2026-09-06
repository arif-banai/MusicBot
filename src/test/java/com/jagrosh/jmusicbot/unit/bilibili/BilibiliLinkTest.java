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

import com.jagrosh.jmusicbot.audio.bilibili.BilibiliLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliLink Parsing Tests")
class BilibiliLinkTest
{
    @Test
    @DisplayName("parses a standard video URL to page 1")
    void parsesStandardVideoUrl()
    {
        BilibiliLink link = BilibiliLink.parse("https://www.bilibili.com/video/BV1DTbv6xEHK");
        assertNotNull(link);
        assertEquals(BilibiliLink.Kind.VIDEO, link.kind());
        assertEquals("BV1DTbv6xEHK", link.id());
        assertEquals(1, link.page());
    }

    @Test
    @DisplayName("honours the ?p= page parameter")
    void honoursPageParameter()
    {
        BilibiliLink link = BilibiliLink.parse("https://www.bilibili.com/video/BV1GFbk6LEVm?p=3");
        assertEquals(3, link.page());
        assertEquals("BV1GFbk6LEVm", link.id());
    }

    @Test
    @DisplayName("defaults to page 1 when p is absent, zero, or negative")
    void defaultsPageToOne()
    {
        assertEquals(1, BilibiliLink.parse("https://www.bilibili.com/video/BV1GFbk6LEVm?p=0").page());
        assertEquals(1, BilibiliLink.parse("https://www.bilibili.com/video/BV1GFbk6LEVm?p=-2").page());
        assertEquals(1, BilibiliLink.parse("https://www.bilibili.com/video/BV1GFbk6LEVm?p=abc").page());
    }

    @Test
    @DisplayName("parses av ids and mobile/short host variants")
    void parsesAvAndHostVariants()
    {
        assertEquals("av170001", BilibiliLink.parse("https://www.bilibili.com/video/av170001").id());
        assertEquals("BV1DTbv6xEHK", BilibiliLink.parse("https://m.bilibili.com/video/BV1DTbv6xEHK").id());
        assertEquals("BV1DTbv6xEHK", BilibiliLink.parse("bilibili.com/video/BV1DTbv6xEHK").id());
    }

    @Test
    @DisplayName("parses a bare BV id")
    void parsesBareBvId()
    {
        BilibiliLink link = BilibiliLink.parse("BV1DTbv6xEHK");
        assertEquals(BilibiliLink.Kind.VIDEO, link.kind());
        assertEquals("BV1DTbv6xEHK", link.id());
    }

    @Test
    @DisplayName("parses a b23.tv short link as SHORT_LINK")
    void parsesShortLink()
    {
        BilibiliLink link = BilibiliLink.parse("https://b23.tv/AbCdEfG");
        assertEquals(BilibiliLink.Kind.SHORT_LINK, link.kind());
        assertEquals("https://b23.tv/AbCdEfG", link.id());
    }

    @Test
    @DisplayName("parses the bilisearch: prefix and trims the query")
    void parsesSearchPrefix()
    {
        BilibiliLink link = BilibiliLink.parse("bilisearch:  周杰伦 稻香  ");
        assertEquals(BilibiliLink.Kind.SEARCH, link.kind());
        assertEquals("周杰伦 稻香", link.id());
    }

    @Test
    @DisplayName("returns null for non-Bilibili input")
    void returnsNullForForeignInput()
    {
        assertNull(BilibiliLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertNull(BilibiliLink.parse("never gonna give you up"));
        assertNull(BilibiliLink.parse(null));
        assertNull(BilibiliLink.parse(""));
        assertNull(BilibiliLink.parse("bilisearch:   "));
    }
}
