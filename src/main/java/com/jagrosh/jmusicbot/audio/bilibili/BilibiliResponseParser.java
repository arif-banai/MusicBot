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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

/**
 * Converts Bilibili API responses into typed values.
 *
 * <p>Every method here is a pure function of its {@link JsonNode} input, so the whole
 * class is tested offline against captured real responses.
 *
 * @author Arif Banai (arif-banai)
 */
public final class BilibiliResponseParser
{
    private BilibiliResponseParser() {}

    /**
     * Throws when the response carries a non-zero {@code code}.
     *
     * <p>Bilibili's own message is preserved, so users see "稿件不可见" rather than a
     * numeric code they cannot act on.
     */
    public static void checkCode(JsonNode root)
    {
        int code = root.path("code").asInt(-1);
        if(code == 0)
            return;

        String message = root.path("message").asText("unknown error");
        throw new FriendlyException("Bilibili rejected the request: " + message + " (code " + code + ")",
                FriendlyException.Severity.COMMON, null);
    }

    /**
     * Reads video metadata, selecting the given 1-based page.
     *
     * <p>Out-of-range pages fall back to page 1 rather than failing, since a bad
     * {@code ?p=} should not block playback of a video that exists.
     */
    public static BilibiliVideo parseView(JsonNode root, int page)
    {
        checkCode(root);
        JsonNode data = root.path("data");
        JsonNode pages = data.path("pages");

        boolean hasPages = pages.isArray() && !pages.isEmpty();
        int index = (hasPages && page >= 1 && page <= pages.size()) ? page - 1 : 0;
        JsonNode selected = hasPages ? pages.get(index) : null;

        long cid = selected != null ? selected.path("cid").asLong() : data.path("cid").asLong();
        long seconds = selected != null ? selected.path("duration").asLong() : data.path("duration").asLong();

        return new BilibiliVideo(
                data.path("bvid").asText(),
                cid,
                buildTitle(data.path("title").asText(), selected, pages.size(), index),
                data.path("owner").path("name").asText(),
                seconds * 1000L,
                data.path("pic").asText());
    }

    /**
     * Qualifies the title with the part name when a video has several pages, so a queue
     * of parts from the same video is readable.
     */
    private static String buildTitle(String title, JsonNode selected, int pageCount, int index)
    {
        if(selected == null || pageCount <= 1)
            return title;

        String part = selected.path("part").asText("");
        if(part.isBlank() || part.equals(title))
            return title + " - P" + (index + 1);

        return title + " - P" + (index + 1) + " " + part;
    }

    /**
     * Selects the best audio stream: the highest-bandwidth DASH entry when present,
     * otherwise the progressive {@code durl} that older videos still return.
     */
    public static BilibiliAudioStream parseAudioStream(JsonNode root)
    {
        checkCode(root);
        JsonNode data = root.path("data");

        JsonNode audioList = data.path("dash").path("audio");
        if(audioList.isArray() && !audioList.isEmpty())
        {
            JsonNode best = null;
            for(JsonNode candidate : audioList)
                if(best == null || candidate.path("bandwidth").asInt() > best.path("bandwidth").asInt())
                    best = candidate;

            return new BilibiliAudioStream(best.path("baseUrl").asText(),
                    readUrlList(best.path("backupUrl")), best.path("bandwidth").asInt());
        }

        JsonNode durl = data.path("durl");
        if(durl.isArray() && !durl.isEmpty())
            return new BilibiliAudioStream(durl.get(0).path("url").asText(),
                    readUrlList(durl.get(0).path("backup_url")), 0);

        throw new FriendlyException("Bilibili returned no playable audio for this video",
                FriendlyException.Severity.SUSPICIOUS, null);
    }

    /**
     * Reads search results, skipping the ad and placeholder entries that carry no
     * {@code bvid} and therefore cannot be played.
     */
    public static List<BilibiliVideo> parseSearch(JsonNode root)
    {
        checkCode(root);
        List<BilibiliVideo> videos = new ArrayList<>();

        for(JsonNode item : root.path("data").path("result"))
        {
            String bvid = item.path("bvid").asText("");
            if(bvid.isBlank())
                continue;

            videos.add(new BilibiliVideo(
                    bvid,
                    0L,
                    stripHighlight(item.path("title").asText()),
                    item.path("author").asText(),
                    parseDuration(item.path("duration").asText("")),
                    normalizeThumbnail(item.path("pic").asText(""))));
        }
        return videos;
    }

    private static List<String> readUrlList(JsonNode node)
    {
        List<String> urls = new ArrayList<>();
        for(JsonNode url : node)
            urls.add(url.asText());
        return urls;
    }

    /**
     * Search titles wrap matched terms in {@code <em>} tags; the queue should show plain
     * text.
     */
    private static String stripHighlight(String title)
    {
        return title.replaceAll("<[^>]+>", "");
    }

    /**
     * Search results express duration as {@code m:ss} or {@code h:mm:ss}, unlike the view
     * endpoint which returns seconds.
     */
    private static long parseDuration(String duration)
    {
        if(duration.isBlank())
            return 0L;

        long total = 0L;
        for(String part : duration.split(":"))
        {
            try
            {
                total = total * 60 + Long.parseLong(part.trim());
            }
            catch(NumberFormatException e)
            {
                return 0L;
            }
        }
        return total * 1000L;
    }

    /**
     * Search thumbnails come back protocol-relative, e.g. {@code //i0.hdslb.com/...}.
     */
    private static String normalizeThumbnail(String pic)
    {
        return pic.startsWith("//") ? "https:" + pic : pic;
    }
}
