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
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PersistentHttpStream} that always sends a {@code Range} header.
 *
 * <p>The base class only asks for a range once it has a non-zero position, so the first
 * request for a stream goes out without one. Some of Bilibili's CDN nodes refuse that
 * request: {@code upos-sz-mirrorcosov.bilivideo.com} answers HTTP 567 to a plain GET of an
 * audio URL and 206 to the same URL with {@code Range: bytes=0-}, which is what a browser
 * sends. Which node a URL points at varies from one {@code playurl} call to the next, so
 * without this a track fails or plays depending on where Bilibili happened to route it.
 *
 * <p>Nodes can also refuse a bot outright: from outside China
 * {@code upos-hz-mirrorakam.akamaized.net} answers 403 whatever headers are sent, and
 * Bilibili sometimes lists it first. {@link #openFirstAvailable} therefore tries each
 * mirror Bilibili offers until one serves the audio.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliHttpStream extends PersistentHttpStream
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliHttpStream.class);

    private final URI uri;

    /**
     * @param httpInterface the HTTP interface carrying Bilibili's required headers
     * @param contentUrl the CDN URL of the stream
     * @param contentLength the stream length if known, otherwise {@code null}
     */
    public BilibiliHttpStream(HttpInterface httpInterface, URI contentUrl, Long contentLength)
    {
        super(httpInterface, contentUrl, contentLength);
        this.uri = contentUrl;
    }

    /**
     * Opens the first mirror that serves the audio, trying them in the order Bilibili listed.
     *
     * @param urls the stream URL followed by its backups
     * @return a connected stream positioned at the start
     * @throws InterruptedIOException if playback was stopped while connecting
     * @throws IOException if no mirror would serve the audio
     */
    public static BilibiliHttpStream openFirstAvailable(HttpInterface httpInterface, List<String> urls)
            throws IOException
    {
        String lastFailure = "no stream URL";
        for(String url : urls)
        {
            BilibiliHttpStream stream;
            try
            {
                stream = new BilibiliHttpStream(httpInterface, new URI(url), null);
            }
            catch(URISyntaxException e)
            {
                lastFailure = "bad URL " + url;
                continue;
            }

            try
            {
                int status = stream.checkStatusCode();
                if(status == 200 || status == 206)
                    return stream;
                lastFailure = stream.uri.getHost() + " answered HTTP " + status;
            }
            catch(InterruptedIOException e)
            {
                stream.close();
                throw e;
            }
            catch(IOException e)
            {
                lastFailure = stream.uri.getHost() + " failed: " + e.getMessage();
            }

            stream.close();
            LOGGER.debug("Bilibili mirror refused, trying the next: {}", lastFailure);
            if(Thread.currentThread().isInterrupted())
                throw new InterruptedIOException("Stopped while connecting to Bilibili");
        }
        throw new IOException("No Bilibili mirror would serve the audio (" + lastFailure + ")");
    }

    /**
     * Gets the URL this stream reads from.
     */
    public URI getUri()
    {
        return uri;
    }

    @Override
    protected HttpGet getConnectRequest()
    {
        HttpGet request = super.getConnectRequest();
        if(!request.containsHeader(HttpHeaders.RANGE))
            request.setHeader(HttpHeaders.RANGE, "bytes=" + position + "-");
        return request;
    }
}
