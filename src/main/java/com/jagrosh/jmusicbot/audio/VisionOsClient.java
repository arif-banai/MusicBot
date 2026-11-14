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

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Playback-only YouTube client identifying as the visionOS app (VISIONOS).
 *
 * <p>Without a GVS PO token, googlevideo now refuses the audio URLs of youtube-source's usual
 * playback clients (ANDROID_VR, MWEB, TVHTML5_SIMPLY) beyond roughly the first minute, so every
 * longer track failed with "Not success status code: 403". VISIONOS URLs need no PO token, no
 * {@code n} challenge and no signature cipher; yt-dlp uses this client by default for the same
 * reason. Values mirror yt-dlp 2026.10.26 ({@code yt_dlp/extractor/youtube/_base.py}).
 *
 * <p>It is used only to load playback formats: search, video, playlist and mix loading stay with
 * the other clients ({@link #canHandleRequest} is false). List it first so it is tried first.
 * YouTube could start requiring a PO token for this client too, as it did for ANDROID_VR; the
 * other clients then remain as fallbacks.
 */
public class VisionOsClient extends StreamingNonMusicClient
{
    public static final String CLIENT_NAME = "VISIONOS";
    public static final String CLIENT_VERSION = "1.02";
    public static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15";

    private final ClientOptions options;

    public VisionOsClient()
    {
        this(ClientOptions.DEFAULT);
    }

    public VisionOsClient(@NotNull ClientOptions options)
    {
        this.options = options;
    }

    /** Built fresh on every call: {@link ClientConfig#copy()} is shallow and would leak changes. */
    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface)
    {
        return new ClientConfig()
            .withClientName(CLIENT_NAME)
            .withUserAgent(USER_AGENT)
            .withClientField("clientVersion", CLIENT_VERSION)
            .withClientField("deviceMake", "Apple")
            .withClientField("deviceModel", "RealityDevice17,1")
            .withClientField("osName", "visionOS")
            .withClientField("osVersion", "26.5.23O471");
    }

    /** No {@code params} field is sent, as yt-dlp does for this client. */
    @Override
    @Nullable
    public String getPlayerParams()
    {
        return null;
    }

    @Override
    @NotNull
    public ClientOptions getOptions()
    {
        return options;
    }

    /** VISIONOS stream URLs carry no {@code n} parameter and no signature cipher. */
    @Override
    public boolean requirePlayerScript()
    {
        return false;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier)
    {
        return false;
    }

    @Override
    @NotNull
    public String getIdentifier()
    {
        return CLIENT_NAME;
    }

    @Override
    public AudioItem loadVideo(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                               @NotNull String videoId)
    {
        throw playbackOnly("load videos");
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId, @Nullable String selectedVideoId)
    {
        throw playbackOnly("load playlists");
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                             @NotNull String mixId, @Nullable String selectedVideoId)
    {
        throw playbackOnly("load mixes");
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                                @NotNull String searchQuery)
    {
        throw playbackOnly("search");
    }

    private static FriendlyException playbackOnly(String what)
    {
        return new FriendlyException("This client cannot " + what, Severity.COMMON,
            new UnsupportedOperationException(CLIENT_NAME + " is used for playback only"));
    }
}
