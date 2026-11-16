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

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;

/**
 * A {@link PersistentHttpStream} that always sends a {@code Range} header.
 *
 * <p>The base class only asks for a range once it has a non-zero position, so the first
 * request for a stream goes out without one. Some of Bilibili's CDN nodes refuse that
 * request: {@code upos-sz-mirrorcosov.bilivideo.com} answers HTTP 567 to a plain GET of an
 * audio URL and 206 to the same URL with {@code Range: bytes=0-}, which is what a browser
 * sends. Other nodes, such as the Akamai mirror, accept both. Which node a URL points at
 * varies from one {@code playurl} call to the next, so without this a track fails or plays
 * depending on where Bilibili happened to route it.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliHttpStream extends PersistentHttpStream
{
    /**
     * @param httpInterface the HTTP interface carrying Bilibili's required headers
     * @param contentUrl the CDN URL of the stream
     * @param contentLength the stream length if known, otherwise {@code null}
     */
    public BilibiliHttpStream(HttpInterface httpInterface, URI contentUrl, Long contentLength)
    {
        super(httpInterface, contentUrl, contentLength);
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
