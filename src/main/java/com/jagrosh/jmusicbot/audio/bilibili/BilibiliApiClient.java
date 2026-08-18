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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs the HTTP calls against Bilibili's web API.
 *
 * <p>Bilibili's CDN rejects requests that lack a {@code Referer} or that do not present a
 * browser {@code User-Agent}; both cases return HTTP 403. Those headers are therefore part
 * of this class's contract rather than an optional nicety.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliApiClient
{
    /** Required by Bilibili's CDN; requests without it are answered with HTTP 403. */
    public static final String REFERER = "https://www.bilibili.com";

    /** Required by Bilibili's CDN; requests without a browser UA are answered with HTTP 403. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VIEW_URL = "https://api.bilibili.com/x/web-interface/view?";
    private static final String PLAYURL_SIGNED = "https://api.bilibili.com/x/player/wbi/playurl?";
    private static final String PLAYURL_PLAIN = "https://api.bilibili.com/x/player/playurl?";
    private static final String SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type?";
    private static final String NAV_URL = "https://api.bilibili.com/x/web-interface/nav";

    /** DASH format, which yields an audio-only stream instead of a full video download. */
    private static final String DASH_PARAMS = "&fnval=16&fnver=0&fourk=1";

    private static final long KEY_TTL_MS = 12L * 60 * 60 * 1000;

    private volatile String cachedMixinKey;
    private volatile long cachedMixinKeyAt;

    /**
     * Loads metadata for one page of a video.
     *
     * @param id a {@code BV} id or an {@code av} id
     * @param page the 1-based page number
     */
    public BilibiliVideo loadVideo(HttpInterface http, String id, int page) throws IOException
    {
        return BilibiliResponseParser.parseView(getJson(http, VIEW_URL + buildViewQuery(id)), page);
    }

    /**
     * Resolves a playable audio stream.
     *
     * <p>Prefers the WBI-signed endpoint. If the signing key cannot be obtained, falls
     * back to the unsigned endpoint so playback degrades rather than breaks.
     *
     * @param cid the page id, or 0 to look it up from the {@code bvid} first
     */
    public BilibiliAudioStream loadAudioStream(HttpInterface http, String bvid, long cid) throws IOException
    {
        long pageId = cid;
        if(pageId <= 0)
        {
            // Search results arrive without a cid, so resolve it before asking for a stream.
            pageId = loadVideo(http, bvid, 1).cid();
        }

        String mixinKey = mixinKey(http);
        String url = mixinKey != null
                ? PLAYURL_SIGNED + buildPlayurlQuery(bvid, pageId, mixinKey, System.currentTimeMillis() / 1000L)
                : PLAYURL_PLAIN + buildUnsignedPlayurlQuery(bvid, pageId);

        return BilibiliResponseParser.parseAudioStream(getJson(http, url));
    }

    /**
     * Searches Bilibili for videos matching the query.
     *
     * @param limit the maximum number of results to request
     */
    public List<BilibiliVideo> search(HttpInterface http, String query, int limit) throws IOException
    {
        String mixinKey = mixinKey(http);
        if(mixinKey == null)
            throw new FriendlyException("Bilibili search is unavailable: could not obtain a signing key",
                    FriendlyException.Severity.SUSPICIOUS, null);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("search_type", "video");
        params.put("keyword", query);
        params.put("page", "1");
        params.put("page_size", Integer.toString(limit));

        String signed = WbiSigner.sign(params, mixinKey, System.currentTimeMillis() / 1000L);
        return BilibiliResponseParser.parseSearch(getJson(http, SEARCH_URL + signed));
    }

    /**
     * Follows a {@code b23.tv} redirect to the real video URL.
     *
     * @return the resolved URL, or the input unchanged if no redirect was recorded
     */
    public String resolveShortLink(HttpInterface http, String shortUrl) throws IOException
    {
        HttpGet request = new HttpGet(shortUrl);
        applyHeaders(request);

        try(CloseableHttpResponse response = http.execute(request))
        {
            EntityUtils.consumeQuietly(response.getEntity());
            return http.getFinalLocation() != null ? http.getFinalLocation().toString() : shortUrl;
        }
    }

    /**
     * Builds the {@code view} query, which identifies a video by {@code aid} or {@code bvid}.
     */
    public static String buildViewQuery(String id)
    {
        if(id.regionMatches(true, 0, "av", 0, 2))
            return "aid=" + encode(id.substring(2));

        return "bvid=" + encode(id);
    }

    /**
     * Builds the WBI-signed {@code playurl} query.
     */
    public static String buildPlayurlQuery(String bvid, long cid, String mixinKey, long wtsSeconds)
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("bvid", bvid);
        params.put("cid", Long.toString(cid));
        params.put("fnval", "16");
        params.put("fnver", "0");
        params.put("fourk", "1");

        return WbiSigner.sign(params, mixinKey, wtsSeconds);
    }

    /**
     * Builds the unsigned {@code playurl} query used when a signing key is unavailable.
     */
    public static String buildUnsignedPlayurlQuery(String bvid, long cid)
    {
        return "bvid=" + encode(bvid) + "&cid=" + cid + DASH_PARAMS;
    }

    /**
     * Returns the cached mixin key, refreshing it when stale.
     *
     * @return the key, or null if it could not be obtained
     */
    private String mixinKey(HttpInterface http)
    {
        long now = System.currentTimeMillis();
        String cached = cachedMixinKey;
        if(cached != null && now - cachedMixinKeyAt < KEY_TTL_MS)
            return cached;

        try
        {
            JsonNode wbi = getJson(http, NAV_URL).path("data").path("wbi_img");
            String key = WbiSigner.mixinKey(
                    WbiSigner.keyFromUrl(wbi.path("img_url").asText()),
                    WbiSigner.keyFromUrl(wbi.path("sub_url").asText()));

            cachedMixinKey = key;
            cachedMixinKeyAt = now;
            return key;
        }
        catch(Exception e)
        {
            LOGGER.warn("Could not fetch Bilibili WBI keys, falling back to unsigned requests: {}",
                    e.getMessage());
            return null;
        }
    }

    private JsonNode getJson(HttpInterface http, String url) throws IOException
    {
        HttpGet request = new HttpGet(url);
        applyHeaders(request);

        try(CloseableHttpResponse response = http.execute(request))
        {
            int status = response.getStatusLine().getStatusCode();
            if(status != 200)
                throw new IOException("Bilibili API returned HTTP " + status);

            return MAPPER.readTree(response.getEntity().getContent());
        }
    }

    private static void applyHeaders(HttpGet request)
    {
        request.setHeader("Referer", REFERER);
        request.setHeader("User-Agent", USER_AGENT);
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
