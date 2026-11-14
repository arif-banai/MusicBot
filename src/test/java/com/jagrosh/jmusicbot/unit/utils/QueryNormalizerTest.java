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
package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.QueryNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QueryNormalizer}, which prefixes scheme-less YouTube links
 * with {@code https://} so they are loaded directly instead of being treated
 * as search text.
 */
public class QueryNormalizerTest
{
    @Test
    public void schemeLessYoutubeWatchUrl_isPrefixedWithHttps()
    {
        assertEquals("https://youtube.com/watch?v=IY1oU1G2DA0",
                QueryNormalizer.normalize("youtube.com/watch?v=IY1oU1G2DA0"));
    }

    @Test
    public void schemeLessWwwYoutubeUrl_isPrefixedWithHttps()
    {
        assertEquals("https://www.youtube.com/watch?v=x",
                QueryNormalizer.normalize("www.youtube.com/watch?v=x"));
    }

    @Test
    public void schemeLessYoutuBeUrl_isPrefixedWithHttps()
    {
        assertEquals("https://youtu.be/x",
                QueryNormalizer.normalize("youtu.be/x"));
    }

    @Test
    public void schemeLessMusicYoutubeUrl_isPrefixedWithHttps()
    {
        assertEquals("https://music.youtube.com/watch?v=x",
                QueryNormalizer.normalize("music.youtube.com/watch?v=x"));
    }

    @Test
    public void alreadySchemedYoutuBeUrl_isUnchanged()
    {
        assertEquals("https://youtu.be/x",
                QueryNormalizer.normalize("https://youtu.be/x"));
    }

    @Test
    public void plainSearchText_isUnchanged()
    {
        assertEquals("crazy frog", QueryNormalizer.normalize("crazy frog"));
    }

    @Test
    public void ytsearchPrefixedQuery_isUnchanged()
    {
        assertEquals("ytsearch:youtube.com", QueryNormalizer.normalize("ytsearch:youtube.com"));
    }

    @Test
    public void lookalikeDomain_isUnchanged()
    {
        assertEquals("myyoutube.com/x", QueryNormalizer.normalize("myyoutube.com/x"));
    }

    @Test
    public void query_isTrimmed()
    {
        assertEquals("https://youtu.be/x",
                QueryNormalizer.normalize("  youtu.be/x  "));
    }

    @Test
    public void nullQuery_returnsNull()
    {
        assertNull(QueryNormalizer.normalize(null));
    }
}
