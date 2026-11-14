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
package com.jagrosh.jmusicbot.utils;

import java.util.regex.Pattern;

/**
 * Normalizes user-supplied play/playNext queries so that scheme-less YouTube
 * links (e.g. {@code youtube.com/watch?v=x}) are treated as links to load
 * directly, instead of falling through to a {@code ytsearch:} text search.
 *
 * <p>Every other kind of query — real URLs (which already have a scheme),
 * plain search text, {@code ytsearch:}/{@code scsearch:} prefixed queries,
 * and lookalike domains — is returned unchanged.</p>
 */
public final class QueryNormalizer
{
    private static final Pattern SCHEMELESS_YOUTUBE_URL = Pattern.compile(
            "^(www\\.|m\\.|music\\.)?(youtube\\.com|youtu\\.be)/\\S*$",
            Pattern.CASE_INSENSITIVE);

    private QueryNormalizer()
    {
        // Utility class - prevent instantiation
    }

    /**
     * Trims the given query and, if it is a scheme-less YouTube link, prefixes
     * it with {@code https://} so it is loaded as a link rather than searched
     * for. Any other query is returned trimmed but otherwise unchanged.
     *
     * @param query the raw user-supplied query; may be {@code null}
     * @return the normalized query, or {@code null} if {@code query} was {@code null}
     */
    public static String normalize(String query)
    {
        if (query == null)
            return null;

        String trimmed = query.trim();

        if (SCHEMELESS_YOUTUBE_URL.matcher(trimmed).matches())
            return "https://" + trimmed;

        return trimmed;
    }
}
