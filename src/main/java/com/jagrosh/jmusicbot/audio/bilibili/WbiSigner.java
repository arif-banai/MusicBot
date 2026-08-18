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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements Bilibili's WBI request signing.
 *
 * <p>Signed requests carry a {@code wts} timestamp and a {@code w_rid} MD5 digest. The
 * digest key is derived by permuting the concatenation of two keys published by the
 * {@code nav} endpoint through a fixed table.
 *
 * <p>MD5 comes from {@link MessageDigest}, which lives in {@code java.base}. That keeps
 * the Docker image's jlink module list unchanged.
 *
 * @author Arif Banai (arif-banai)
 */
public final class WbiSigner
{
    /** Bilibili's fixed permutation table for deriving the mixin key. */
    private static final int[] MIXIN_KEY_TABLE = {
        46, 47, 18,  2, 53,  8, 23, 32, 15, 50, 10, 31, 58,  3, 45, 35,
        27, 43,  5, 49, 33,  9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48,  7, 16, 24, 55, 40, 61, 26, 17,  0,  1, 60, 51, 30,  4,
        22, 25, 54, 21, 56, 59,  6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    /** Characters Bilibili strips from parameter values before signing. */
    private static final String EXCLUDED_CHARACTERS = "!'()*";

    private static final int MIXIN_KEY_LENGTH = 32;

    private WbiSigner() {}

    /**
     * Derives the signing key from the two keys published by the {@code nav} endpoint.
     *
     * @param imgKey the key taken from {@code wbi_img.img_url}
     * @param subKey the key taken from {@code wbi_img.sub_url}
     * @return a 32-character mixin key
     */
    public static String mixinKey(String imgKey, String subKey)
    {
        String raw = imgKey + subKey;
        StringBuilder builder = new StringBuilder(MIXIN_KEY_LENGTH);

        for(int index : MIXIN_KEY_TABLE)
        {
            if(builder.length() == MIXIN_KEY_LENGTH)
                break;
            if(index < raw.length())
                builder.append(raw.charAt(index));
        }
        return builder.toString();
    }

    /**
     * Extracts {@code <key>} from a {@code .../<key>.png} URL.
     */
    public static String keyFromUrl(String url)
    {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /**
     * Signs the given parameters.
     *
     * @param params the request parameters, excluding {@code wts} and {@code w_rid}
     * @param mixinKey the key from {@link #mixinKey(String, String)}
     * @param wtsSeconds the request timestamp in seconds
     * @return a complete query string ending in {@code &w_rid=...}
     */
    public static String sign(Map<String, String> params, String mixinKey, long wtsSeconds)
    {
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.put("wts", Long.toString(wtsSeconds));

        StringBuilder query = new StringBuilder();
        for(Map.Entry<String, String> entry : sorted.entrySet())
        {
            if(query.length() > 0)
                query.append('&');

            query.append(encode(entry.getKey()))
                 .append('=')
                 .append(encode(strip(entry.getValue())));
        }

        return query + "&w_rid=" + md5(query + mixinKey);
    }

    private static String strip(String value)
    {
        StringBuilder builder = new StringBuilder(value.length());
        for(int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if(EXCLUDED_CHARACTERS.indexOf(c) < 0)
                builder.append(c);
        }
        return builder.toString();
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String md5(String input)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for(byte b : digest)
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));

            return hex.toString();
        }
        catch(NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("MD5 is required for Bilibili WBI signing", e);
        }
    }
}
