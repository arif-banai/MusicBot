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

import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudDataLoader;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudDataReader;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudPlaylistLoader;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudDataLoader;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudDataReader;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.http.SettableHttpRequestFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * SoundCloud source that can play the current unencrypted AAC HLS streams.
 */
public final class SoundCloudPlaybackSourceManager extends SoundCloudAudioSourceManager
{
    private static final Logger log = LoggerFactory.getLogger(SoundCloudPlaybackSourceManager.class);
    private static final ThreadLocal<HttpInterface> CURRENT_HTTP = new ThreadLocal<>();

    public static SoundCloudPlaybackSourceManager create()
    {
        SoundCloudDataReader reader = new DefaultSoundCloudDataReader();
        SoundCloudDataLoader loader = new DefaultSoundCloudDataLoader();
        SoundCloudFormatHandler formats = new SoundCloudPlaybackFormatHandler();
        return new SoundCloudPlaybackSourceManager(reader, loader, formats);
    }

    private SoundCloudPlaybackSourceManager(
            SoundCloudDataReader reader,
            SoundCloudDataLoader loader,
            SoundCloudFormatHandler formats)
    {
        super(true, reader, loader, formats,
                new DefaultSoundCloudPlaylistLoader(loader, reader, formats),
                false);
        keepSignedCdnUrlsIntact();
    }

    /**
     * LavaPlayer appends {@code client_id} to every request whose host is not {@code sndcdn.com}.
     * The current AAC CDN signs the URL, and an extra parameter makes CloudFront return 403.
     */
    private void keepSignedCdnUrlsIntact()
    {
        try
        {
            Field managerField = SoundCloudAudioSourceManager.class.getDeclaredField("httpInterfaceManager");
            managerField.setAccessible(true);
            HttpInterfaceManager manager = (HttpInterfaceManager) managerField.get(this);

            Field filterField = manager.getClass().getDeclaredField("filter");
            filterField.setAccessible(true);
            SettableHttpRequestFilter settable = (SettableHttpRequestFilter) filterField.get(manager);
            settable.set(new SignedCdnFilter(settable.get()));
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Could not adjust SoundCloud CDN requests.", e);
        }
    }

    private static final class SignedCdnFilter implements HttpContextFilter
    {
        private final HttpContextFilter delegate;

        private SignedCdnFilter(HttpContextFilter delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void onContextOpen(HttpClientContext context)
        {
            delegate.onContextOpen(context);
        }

        @Override
        public void onContextClose(HttpClientContext context)
        {
            delegate.onContextClose(context);
        }

        @Override
        public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition)
        {
            delegate.onRequest(context, request, isRepetition);
            if (!(request instanceof HttpRequestBase httpRequest))
            {
                return;
            }
            URI uri = httpRequest.getURI();
            String host = uri.getHost();
            if (host == null || !host.endsWith("soundcloud.cloud"))
            {
                return;
            }
            log.debug("SoundCloud CDN request {}", uri.getPath());
            try
            {
                List<NameValuePair> kept = new ArrayList<>();
                for (NameValuePair parameter : new URIBuilder(uri).getQueryParams())
                {
                    if (!"client_id".equals(parameter.getName()))
                    {
                        kept.add(parameter);
                    }
                }
                httpRequest.setURI(new URIBuilder(uri).setParameters(kept).build());
            }
            catch (URISyntaxException e)
            {
                throw new IllegalStateException("Could not clean a signed SoundCloud URL.", e);
            }
        }

        @Override
        public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response)
        {
            return delegate.onRequestResponse(context, request, response);
        }

        @Override
        public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error)
        {
            return delegate.onRequestException(context, request, error);
        }
    }

    static HttpInterface currentHttpInterface()
    {
        return CURRENT_HTTP.get();
    }

    @Override
    public HttpInterface getHttpInterface()
    {
        HttpInterface httpInterface = super.getHttpInterface();
        CURRENT_HTTP.set(httpInterface);
        return httpInterface;
    }
}
