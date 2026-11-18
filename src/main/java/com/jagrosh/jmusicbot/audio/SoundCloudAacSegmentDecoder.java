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
package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAacTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mpeg.reader.MpegFileTrackProvider;
import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudHelper;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudSegmentDecoder;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioProcessingContext;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.net.URI;
import java.util.function.Supplier;

/**
 * Decodes SoundCloud's unencrypted AAC HLS, which is a fragmented MP4 playlist
 * with an initialization segment referenced by {@code EXT-X-MAP}.
 */
final class SoundCloudAacSegmentDecoder implements SoundCloudSegmentDecoder
{
    private static final Logger log = LoggerFactory.getLogger(SoundCloudAacSegmentDecoder.class);
    /**
     * Empty mdat box. LavaPlayer stops header parsing at the first mdat and
     * skips it, so this keeps the init segment's moov from consuming the first
     * media fragment.
     */
    private static final byte[] HEADER_STOP = new byte[] {0, 0, 0, 8, 'm', 'd', 'a', 't'};

    private final Supplier<SeekableInputStream> nextStreamProvider;
    private final String lookupUrl;

    SoundCloudAacSegmentDecoder(Supplier<SeekableInputStream> nextStreamProvider, String lookupUrl)
    {
        this.nextStreamProvider = nextStreamProvider;
        this.lookupUrl = lookupUrl;
    }

    @Override
    public void prepareStream(boolean beginning)
    {
        // The fragmented MP4 reader is opened in playStream.
    }

    @Override
    public void resetStream()
    {
        // Each playStream call opens its own stream.
    }

    @Override
    public void playStream(AudioProcessingContext context, long startPosition, long desiredPosition)
            throws InterruptedException, IOException
    {
        byte[] prefix = prefixWithInitSegment(loadInitSegment());
        log.debug("SoundCloud AAC init ready, {} bytes", prefix.length);
        SeekableInputStream media = nextStreamProvider.get();
        try (SeekableInputStream stream = new NonSeekableInputStream(new SequenceInputStream(
                new ByteArrayInputStream(prefix), media)))
        {
            MpegFileLoader file = new MpegFileLoader(stream);
            file.parseHeaders();
            log.debug("SoundCloud AAC headers parsed, {} tracks", file.getTrackList().size());
            MpegTrackConsumer consumer = openAacConsumer(file, context);
            try
            {
                consumer.initialise();
                consumer.seekPerformed(desiredPosition, startPosition);
                MpegFileTrackProvider reader = file.loadReader(consumer);
                if (reader == null)
                {
                    throw new IOException("SoundCloud AAC stream is not a fragmented MP4 file.");
                }
                log.debug("SoundCloud AAC playback starting");
                reader.provideFrames();
                log.debug("SoundCloud AAC playback finished");
            }
            finally
            {
                consumer.close();
            }
        }
    }

    @Override
    public void close()
    {
        // Nothing to close between playback calls.
    }

    private byte[] loadInitSegment() throws IOException
    {
        HttpInterface httpInterface = SoundCloudPlaybackSourceManager.currentHttpInterface();
        if (httpInterface == null)
        {
            throw new IOException("SoundCloud playback HTTP client is not available.");
        }

        String playlistUrl = SoundCloudHelper.loadPlaybackUrl(httpInterface, lookupUrl);
        String[] lines = HttpClientTools.fetchResponseLines(httpInterface, new HttpGet(playlistUrl),
                "soundcloud aac playlist");
        String initUrl = initializationUrl(playlistUrl, lines);
        if (initUrl == null)
        {
            throw new IOException("SoundCloud AAC playlist is missing its initialization segment.");
        }

        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(initUrl)))
        {
            HttpClientTools.assertSuccessWithContent(response, "soundcloud aac init");
            return EntityUtils.toByteArray(response.getEntity());
        }
    }

    static String initializationUrl(String playlistUrl, String[] lines) throws IOException
    {
        for (String lineText : lines)
        {
            ExtendedM3uParser.Line line = ExtendedM3uParser.parseLine(lineText);
            if (!line.isDirective())
            {
                continue;
            }
            if ("EXT-X-KEY".equals(line.directiveName))
            {
                String method = line.directiveArguments.get("METHOD");
                if (method != null && !"NONE".equalsIgnoreCase(method))
                {
                    throw new IOException("SoundCloud AAC stream is encrypted.");
                }
            }
            if ("EXT-X-MAP".equals(line.directiveName))
            {
                String uri = line.directiveArguments.get("URI");
                if (uri != null && !uri.isEmpty())
                {
                    return URI.create(playlistUrl).resolve(uri).toString();
                }
            }
        }
        return null;
    }

    private static byte[] prefixWithInitSegment(byte[] initSegment)
    {
        byte[] prefix = new byte[initSegment.length + HEADER_STOP.length];
        System.arraycopy(initSegment, 0, prefix, 0, initSegment.length);
        System.arraycopy(HEADER_STOP, 0, prefix, initSegment.length, HEADER_STOP.length);
        return prefix;
    }

    private static MpegTrackConsumer openAacConsumer(MpegFileLoader file, AudioProcessingContext context)
            throws IOException
    {
        for (MpegTrackInfo track : file.getTrackList())
        {
            if ("soun".equals(track.handler) && "mp4a".equals(track.codecName))
            {
                return new MpegAacTrackConsumer(context, track);
            }
        }
        throw new IOException("SoundCloud AAC stream did not contain an AAC track.");
    }
}
