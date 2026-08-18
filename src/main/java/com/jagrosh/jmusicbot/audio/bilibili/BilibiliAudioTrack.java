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

import java.net.URI;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Bilibili video page, played as audio.
 *
 * <p>The stream URL is resolved here rather than when the track is queued, because
 * Bilibili's CDN URLs expire after roughly 120 minutes. A track sitting behind a long
 * queue would otherwise hold a dead URL by the time it started playing. Resolving late
 * also means a replay fetches a fresh URL.
 *
 * <p>Bilibili serves DASH audio as fragmented MP4 containing AAC, which is the same shape
 * as YouTube's adaptive formats, so {@link MpegAudioTrack} decodes it directly and no
 * transcoding is needed.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliAudioTrack extends DelegatedAudioTrack
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliAudioTrack.class);

    private final long cid;
    private final BilibiliAudioSourceManager sourceManager;

    /**
     * @param trackInfo standard lavaplayer metadata; {@code identifier} holds the bvid
     * @param cid the page id, or 0 to resolve it at playback time
     * @param sourceManager the manager that created this track
     */
    public BilibiliAudioTrack(AudioTrackInfo trackInfo, long cid, BilibiliAudioSourceManager sourceManager)
    {
        super(trackInfo);
        this.cid = cid;
        this.sourceManager = sourceManager;
    }

    /**
     * Gets the Bilibili page id backing this track.
     *
     * @return the cid, or 0 if it is resolved at playback time
     */
    public long getCid()
    {
        return cid;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception
    {
        try(HttpInterface httpInterface = sourceManager.getHttpInterface())
        {
            BilibiliAudioStream stream = sourceManager.getApiClient()
                    .loadAudioStream(httpInterface, trackInfo.identifier, cid);

            LOGGER.debug("Streaming Bilibili {} at {} bps", trackInfo.identifier, stream.bandwidth());

            try(PersistentHttpStream inputStream =
                        new PersistentHttpStream(httpInterface, new URI(stream.url()), null))
            {
                processDelegate(new MpegAudioTrack(trackInfo, inputStream), executor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone()
    {
        return new BilibiliAudioTrack(trackInfo, cid, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager()
    {
        return sourceManager;
    }
}
