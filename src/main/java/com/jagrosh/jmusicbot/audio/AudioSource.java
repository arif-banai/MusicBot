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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.MWebWithThumbnail;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.TvHtml5SimplyWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import dev.lavalink.youtube.clients.skeleton.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.bilibili.BilibiliAudioSourceManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;

/**
 * Enum representing available audio sources that can be listed in the config file.
 * 
 * <p><b>Registration Order Matters:</b> Sources are registered with the audio player manager
 * in order of their {@link #getRegistrationPriority() registrationPriority}. When a URL is
 * played, sources are checked in registration order until one claims the URL. This means:
 * <ul>
 *   <li>Platform-specific sources (YouTube, SoundCloud, etc.) should have LOW priority numbers
 *       so they are registered FIRST and get first chance to claim their URLs</li>
 *   <li>Catch-all sources (HTTP, LOCAL) should have HIGH priority numbers so they are
 *       registered LAST and only claim URLs that no specific source wanted</li>
 * </ul>
 * 
 * <p>If HTTP is registered before SoundCloud, it will claim SoundCloud URLs and fail to play them.
 * 
 * @author Arif Banai (arif-banai)
 */
public enum AudioSource
{
    // Platform-specific sources (priority 10-90) - registered first to claim their URLs
    YOUTUBE(
        "youtube",
        "YouTube videos and playlists",
        10,
        (manager, config) -> {
            YoutubeAudioSourceManager yt = setupYoutubeAudioSourceManager(config);
            manager.registerSourceManager(yt);
        }
    ),
    SOUNDCLOUD(
        "soundcloud",
        "SoundCloud tracks",
        20,
        (manager, config) -> manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
    ),
    BANDCAMP(
        "bandcamp",
        "Bandcamp albums and tracks",
        30,
        (manager, config) -> manager.registerSourceManager(new BandcampAudioSourceManager())
    ),
    VIMEO(
        "vimeo",
        "Vimeo videos",
        40,
        (manager, config) -> manager.registerSourceManager(new VimeoAudioSourceManager())
    ),
    TWITCH(
        "twitch",
        "Twitch streams",
        50,
        (manager, config) -> manager.registerSourceManager(new TwitchStreamAudioSourceManager())
    ),
    BEAM(
        "beam",
        "Beam.pro streams",
        60,
        (manager, config) -> manager.registerSourceManager(new BeamAudioSourceManager())
    ),
    GETYARN(
        "getyarn",
        "Getyarn.io clips",
        70,
        (manager, config) -> manager.registerSourceManager(new GetyarnAudioSourceManager())
    ),
    NICO(
        "nico",
        "NicoNico videos",
        80,
        (manager, config) -> manager.registerSourceManager(new NicoAudioSourceManager())
    ),
    BILIBILI(
        "bilibili",
        "Bilibili (哔哩哔哩) videos and search",
        25,
        (manager, config) -> manager.registerSourceManager(new BilibiliAudioSourceManager())
    ),
    
    // Catch-all sources (priority 100+) - registered last as fallbacks
    HTTP(
        "http",
        "Direct HTTP audio links",
        100,
        (manager, config) -> manager.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY))
    ),
    LOCAL(
        "local",
        "Local file playback",
        110,
        (manager, config) -> AudioSourceManagers.registerLocalSource(manager)
    );

    private final String configName;
    private final String description;
    private final int registrationPriority;
    private final BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction;

    AudioSource(String configName, String description, int registrationPriority, 
                BiConsumer<DefaultAudioPlayerManager, BotConfig> registrationAction)
    {
        this.configName = configName;
        this.description = description;
        this.registrationPriority = registrationPriority;
        this.registrationAction = registrationAction;
    }

    /**
     * Gets the configuration name for this audio source (used in config files).
     * 
     * @return the lowercase configuration name
     */
    public String getConfigName()
    {
        return configName;
    }

    /**
     * Gets a human-readable description of this audio source.
     * 
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }
    
    /**
     * Gets the registration priority for this audio source.
     * Lower numbers are registered first. Platform-specific sources should have
     * low priorities (10-90), while catch-all sources like HTTP should have
     * high priorities (100+).
     * 
     * @return the registration priority
     */
    public int getRegistrationPriority()
    {
        return registrationPriority;
    }
    
    /**
     * Returns all audio sources sorted by registration priority (lowest first).
     * This ensures platform-specific sources are registered before catch-all sources.
     * 
     * @return list of audio sources in registration order
     */
    public static java.util.List<AudioSource> valuesSortedByPriority()
    {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(AudioSource::getRegistrationPriority))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Parses an audio source from its configuration name.
     * 
     * @param configName the configuration name (case-insensitive)
     * @return an Optional containing the AudioSource if found, empty otherwise
     */
    public static Optional<AudioSource> fromConfigName(String configName)
    {
        if(configName == null)
            return Optional.empty();
        
        return Arrays.stream(values())
                .filter(source -> source.configName.equalsIgnoreCase(configName.trim()))
                .findFirst();
    }
    
    /**
     * Registers this audio source with the given player manager.
     * 
     * @param manager the player manager to register with
     * @param config the bot configuration
     */
    public void register(DefaultAudioPlayerManager manager, BotConfig config)
    {
        registrationAction.accept(manager, config);
    }
    
    /**
     * Sets up and configures a YouTube audio source manager.
     * 
     * @param config the bot configuration
     * @return the configured YouTube audio source manager
     */
    private static YoutubeAudioSourceManager setupYoutubeAudioSourceManager(BotConfig config)
    {
        final Logger logger = LoggerFactory.getLogger(AudioSource.class);
        
        boolean useOauth = config.useYouTubeOauth();
        YoutubeSourceOptions options = buildYoutubeOptions(config);
        Client[] clients = buildYoutubeClients(useOauth);

        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(options, clients);
        yt.setPlaylistPageCount(config.getMaxYTPlaylistPages());

        if (useOauth)
        {
            applyOAuth(yt, logger);
        }
        return yt;
    }
    
    /**
     * Builds YouTube source options.
     */
    private static YoutubeSourceOptions buildYoutubeOptions(BotConfig config)
    {
        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true);
        
        if (config.useYouTubeOauth())
        {
            options.setRemoteCipher("https://cipher.kikkia.dev/", null, "jmusicbot");
        }
        
        return options;
    }
    
    /**
     * Builds the appropriate YouTube clients based on OAuth setting.
     * 
     * <p>When OAuth is enabled, we use a combination of clients:
     * <ul>
     *   <li><b>AndroidVrWithThumbnail</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>MWebWithThumbnail</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>Web</b> - Metadata loading (non-embedded, non-OAuth)</li>
     *   <li><b>Tv</b> - OAuth-compatible streaming-only client. Used as fallback for loading
     *       audio stream formats during playback.</li>
     *   <li><b>TvHtml5SimplyWithThumbnail</b> - *Not oAuth compatible* Used as fallback for loading
     *       audio stream formats during playback.</li>
     * </ul>
     * 
     * <p>
     */
    private static Client[] buildYoutubeClients(boolean useOauth)
    {
        if (useOauth)
        {
            // Clients configured for metadata loading only (no playback/streaming)
            // This handles direct URLs without embedded player restrictions
            ClientOptions metadataOnly = new ClientOptions();
            metadataOnly.setPlayback(false);
            
            return new Client[] {
                new AndroidVrWithThumbnail(metadataOnly), // metadata loading (non-embedded, non-OAuth)
                new MWebWithThumbnail(metadataOnly),      // metadata loading (non-embedded, non-OAuth)
                new WebWithThumbnail(metadataOnly),       // metadata loading (non-embedded, non-OAuth)
                new Tv(),
                new TvHtml5SimplyWithThumbnail()
            };
        }
        // Clients are required even without OAuth to properly handle YouTube URLs
        return new Client[] {
            new AndroidVrWithThumbnail(),
            new MWebWithThumbnail(),
            new WebWithThumbnail(),
            new TvHtml5SimplyWithThumbnail() 
        };
    }
    
    /**
     * Reads OAuth token and applies it to the YouTube source manager.
     * If no token exists, triggers the OAuth device flow to obtain one.
     */
    private static void applyOAuth(YoutubeAudioSourceManager yt, Logger logger)
    {
        String token = readOAuthToken(logger);
        if (token != null)
        {
            logger.debug("Read YouTube OAuth2 refresh token from youtubetoken.txt");
        }
        
        try
        {
            // Call useOauth2 with null token to trigger the device flow.
            // When token is null, this initiates the OAuth device code flow which
            // logs the authorization URL and code that YoutubeOauth2TokenHandler captures.
            yt.useOauth2(token, false);
        }
        catch (Exception e)
        {
            logger.warn("Failed to authorise with YouTube. If this issue persists, delete the youtubetoken.txt file to reauthorise.", e);
        }
    }
    
    /**
     * Reads the OAuth token from file.
     * 
     * @return the token, or null if file doesn't exist or read failed
     */
    private static String readOAuthToken(Logger logger)
    {
        try
        {
            return Files.readString(OtherUtil.getPath("youtubetoken.txt"));
        }
        catch (NoSuchFileException e)
        {
            return null;
        }
        catch (IOException e)
        {
            logger.warn("Failed to read YouTube OAuth2 token file: {}", e.getMessage());
            return null;
        }
    }
}
