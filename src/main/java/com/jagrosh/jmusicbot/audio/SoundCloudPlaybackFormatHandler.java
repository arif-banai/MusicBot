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

import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudM3uInfo;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudTrackFormat;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

/**
 * Selects a SoundCloud stream LavaPlayer can decode.
 *
 * <p>LavaPlayer's built-in handler accepts progressive MP3, HLS MP3, and HLS Opus.
 * SoundCloud now serves most tracks as unencrypted AAC in fragmented MP4 HLS,
 * with a mime type such as {@code audio/mp4; codecs="mp4a.40.2"}. Those tracks
 * are selected here and played by {@link SoundCloudAacSegmentDecoder}.
 *
 * <p>Tracks offered only as {@code cbc-encrypted-hls} or {@code ctr-encrypted-hls}
 * are reported as unavailable.
 */
public final class SoundCloudPlaybackFormatHandler implements SoundCloudFormatHandler
{
    static final String AAC_PREFIX = "A:";
    static final String ENCRYPTED_STREAM_MESSAGE =
            "SoundCloud only offers an encrypted stream for this track, which cannot be played.";
    static final String UNSUPPORTED_STREAM_MESSAGE =
            "SoundCloud did not offer a playable stream format for this track.";

    private static final String NO_SUPPORTED_FORMAT = "Did not detect any supported formats";

    private final SoundCloudFormatHandler delegate;

    public SoundCloudPlaybackFormatHandler()
    {
        this(new DefaultSoundCloudFormatHandler());
    }

    SoundCloudPlaybackFormatHandler(SoundCloudFormatHandler delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public SoundCloudTrackFormat chooseBestFormat(List<SoundCloudTrackFormat> formats)
    {
        try
        {
            return delegate.chooseBestFormat(formats);
        }
        catch (RuntimeException ex)
        {
            if (!NO_SUPPORTED_FORMAT.equals(ex.getMessage()))
            {
                throw ex;
            }
            SoundCloudTrackFormat aac = firstPlainAac(formats);
            if (aac != null)
            {
                return aac;
            }
            throw unsupportedFormats(formats, ex);
        }
    }

    @Override
    public String buildFormatIdentifier(SoundCloudTrackFormat format)
    {
        if (isPlainAacHls(format))
        {
            return AAC_PREFIX + format.getLookupUrl();
        }
        return delegate.buildFormatIdentifier(format);
    }

    @Override
    public SoundCloudM3uInfo getM3uInfo(String identifier)
    {
        if (identifier != null && identifier.startsWith(AAC_PREFIX))
        {
            String lookupUrl = identifier.substring(AAC_PREFIX.length());
            return new SoundCloudM3uInfo(lookupUrl,
                    nextStream -> new SoundCloudAacSegmentDecoder(nextStream, lookupUrl));
        }
        return delegate.getM3uInfo(identifier);
    }

    @Override
    public String getMp3LookupUrl(String identifier)
    {
        return delegate.getMp3LookupUrl(identifier);
    }

    private static SoundCloudTrackFormat firstPlainAac(List<SoundCloudTrackFormat> formats)
    {
        if (formats == null)
        {
            return null;
        }
        for (SoundCloudTrackFormat format : formats)
        {
            if (isPlainAacHls(format))
            {
                return format;
            }
        }
        return null;
    }

    static boolean isPlainAacHls(SoundCloudTrackFormat format)
    {
        if (format == null || format.getLookupUrl() == null || format.getLookupUrl().isEmpty())
        {
            return false;
        }
        String protocol = format.getProtocol();
        if (protocol == null || !"hls".equalsIgnoreCase(protocol))
        {
            return false;
        }
        String mimeType = format.getMimeType();
        if (mimeType == null)
        {
            return false;
        }
        String baseType = mimeType.toLowerCase();
        int separator = baseType.indexOf(';');
        if (separator >= 0)
        {
            baseType = baseType.substring(0, separator).trim();
        }
        return "audio/mp4".equals(baseType) || "audio/aac".equals(baseType) || "audio/m4a".equals(baseType);
    }

    private static FriendlyException unsupportedFormats(List<SoundCloudTrackFormat> formats, RuntimeException cause)
    {
        boolean encrypted = formats != null && formats.stream()
                .anyMatch(SoundCloudPlaybackFormatHandler::isEncrypted);
        String message = encrypted ? ENCRYPTED_STREAM_MESSAGE : UNSUPPORTED_STREAM_MESSAGE;
        return new FriendlyException(message, COMMON, cause);
    }

    private static boolean isEncrypted(SoundCloudTrackFormat format)
    {
        String protocol = format.getProtocol();
        return protocol != null && protocol.toLowerCase().contains("encrypted");
    }
}
