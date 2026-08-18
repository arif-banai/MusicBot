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
package com.jagrosh.jmusicbot.audio.bilibili;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses user input into a Bilibili request descriptor.
 *
 * <p>This class performs no I/O, which keeps URL handling fully unit-testable.
 * {@code b23.tv} short links cannot be resolved without a network round trip, so they
 * are reported as {@link Kind#SHORT_LINK} for the source manager to expand.
 *
 * @author Arif Banai (arif-banai)
 */
public record BilibiliLink(Kind kind, String id, int page)
{
    public enum Kind { VIDEO, SEARCH, SHORT_LINK }

    public static final String SEARCH_PREFIX = "bilisearch:";

    private static final Pattern VIDEO_URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/"
            + "((?:BV[0-9A-Za-z]{10})|(?:[Aa][Vv]\\d+))/?(?:\\?.*)?$");
    private static final Pattern BARE_BV = Pattern.compile("^BV[0-9A-Za-z]{10}$");
    private static final Pattern SHORT_URL = Pattern.compile(
            "^(?:https?://)?b23\\.tv/[0-9A-Za-z]+/?(?:\\?.*)?$");
    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]p=(-?\\d+)");

    /**
     * Parses the given input.
     *
     * @param input raw user input, may be null
     * @return the parsed link, or null if the input does not belong to Bilibili
     */
    public static BilibiliLink parse(String input)
    {
        if(input == null)
            return null;

        String trimmed = input.trim();
        if(trimmed.isEmpty())
            return null;

        if(trimmed.regionMatches(true, 0, SEARCH_PREFIX, 0, SEARCH_PREFIX.length()))
        {
            String query = trimmed.substring(SEARCH_PREFIX.length()).trim();
            return query.isEmpty() ? null : new BilibiliLink(Kind.SEARCH, query, 1);
        }

        if(SHORT_URL.matcher(trimmed).matches())
            return new BilibiliLink(Kind.SHORT_LINK, trimmed, 1);

        Matcher video = VIDEO_URL.matcher(trimmed);
        if(video.matches())
            return new BilibiliLink(Kind.VIDEO, normalizeId(video.group(1)), extractPage(trimmed));

        if(BARE_BV.matcher(trimmed).matches())
            return new BilibiliLink(Kind.VIDEO, trimmed, 1);

        return null;
    }

    /**
     * Lowercases the {@code av} prefix so ids compare equal regardless of input casing.
     */
    private static String normalizeId(String id)
    {
        return id.regionMatches(true, 0, "av", 0, 2) ? "av" + id.substring(2) : id;
    }

    /**
     * Reads {@code ?p=N}, falling back to 1 for absent, unparseable, or non-positive
     * values. A bad page number should not stop the video from playing.
     */
    private static int extractPage(String url)
    {
        Matcher matcher = PAGE_PARAM.matcher(url);
        if(!matcher.find())
            return 1;

        try
        {
            int page = Integer.parseInt(matcher.group(1));
            return page > 0 ? page : 1;
        }
        catch(NumberFormatException ignored)
        {
            return 1;
        }
    }
}
