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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.Header;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays audio from Bilibili (哔哩哔哩) videos.
 *
 * <p>Accepts video URLs, bare {@code BV} ids, {@code b23.tv} short links, and
 * {@code bilisearch:} queries. A multi-page (分P) video always resolves to exactly one
 * track: the page named by {@code ?p=}, or page 1 when none is given. Expanding every
 * part into the queue would flood it with content the user did not ask for.
 *
 * <p>Works anonymously; no account or cookie is required.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliAudioSourceManager implements AudioSourceManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliAudioSourceManager.class);

    private static final String SOURCE_NAME = "bilibili";
    private static final int SEARCH_LIMIT = 20;
    private static final String VIDEO_URL_PREFIX = "https://www.bilibili.com/video/";

    private final HttpInterfaceManager httpInterfaceManager;
    private final BilibiliApiClient apiClient;

    public BilibiliAudioSourceManager()
    {
        this.apiClient = new BilibiliApiClient();
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.httpInterfaceManager.configureBuilder(BilibiliAudioSourceManager::configureHeaders);
    }

    /**
     * Bilibili's CDN answers 403 unless both a Referer and a browser User-Agent are
     * present, so they are set as defaults on every request this manager makes.
     */
    private static void configureHeaders(HttpClientBuilder builder)
    {
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Referer", BilibiliApiClient.REFERER));
        headers.add(new BasicHeader("User-Agent", BilibiliApiClient.USER_AGENT));
        builder.setDefaultHeaders(headers);
    }

    @Override
    public String getSourceName()
    {
        return SOURCE_NAME;
    }

    /**
     * Gets an HTTP interface carrying the headers Bilibili requires.
     */
    public HttpInterface getHttpInterface()
    {
        return httpInterfaceManager.getInterface();
    }

    public BilibiliApiClient getApiClient()
    {
        return apiClient;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference)
    {
        BilibiliLink link = BilibiliLink.parse(reference.identifier);
        if(link == null)
            return null;

        try(HttpInterface httpInterface = getHttpInterface())
        {
            return switch(link.kind())
            {
                case SEARCH -> loadSearch(httpInterface, link.id());
                case VIDEO -> loadVideo(httpInterface, link.id(), link.page());
                case SHORT_LINK -> loadShortLink(httpInterface, link.id());
            };
        }
        catch(FriendlyException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            throw ExceptionTools.wrapUnfriendlyExceptions(
                    "Could not load this Bilibili item.", FriendlyException.Severity.FAULT, e);
        }
    }

    private AudioItem loadVideo(HttpInterface httpInterface, String id, int page) throws IOException
    {
        return buildTrack(apiClient.loadVideo(httpInterface, id, page));
    }

    /**
     * Expands a b23.tv link, then loads whatever it points at. Deliberately does not
     * recurse further, so a redirect loop cannot hang the bot.
     */
    private AudioItem loadShortLink(HttpInterface httpInterface, String shortUrl) throws IOException
    {
        String resolved = apiClient.resolveShortLink(httpInterface, shortUrl);
        BilibiliLink link = BilibiliLink.parse(resolved);

        if(link == null || link.kind() != BilibiliLink.Kind.VIDEO)
        {
            LOGGER.debug("b23.tv link {} resolved to unsupported target {}", shortUrl, resolved);
            return AudioReference.NO_TRACK;
        }
        return loadVideo(httpInterface, link.id(), link.page());
    }

    private AudioItem loadSearch(HttpInterface httpInterface, String query) throws IOException
    {
        List<BilibiliVideo> results = apiClient.search(httpInterface, query, SEARCH_LIMIT);
        if(results.isEmpty())
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>(results.size());
        for(BilibiliVideo video : results)
            tracks.add(buildTrack(video));

        return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
    }

    /**
     * Builds a track. Search results carry no cid, so those tracks hold 0 and resolve it
     * from the bvid when they start playing.
     */
    private AudioTrack buildTrack(BilibiliVideo video)
    {
        AudioTrackInfo info = new AudioTrackInfo(
                video.title(),
                video.author(),
                video.durationMs(),
                video.bvid(),
                false,
                VIDEO_URL_PREFIX + video.bvid(),
                video.thumbnailUrl(),
                null);

        return new BilibiliAudioTrack(info, video.cid(), this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track)
    {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException
    {
        output.writeLong(((BilibiliAudioTrack) track).getCid());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException
    {
        return new BilibiliAudioTrack(trackInfo, input.readLong(), this);
    }

    @Override
    public void shutdown()
    {
        try
        {
            httpInterfaceManager.close();
        }
        catch(IOException e)
        {
            LOGGER.debug("Failed to close the Bilibili HTTP interface manager", e);
        }
    }
}
