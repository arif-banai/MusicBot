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
package com.jagrosh.jmusicbot.testutil.service;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.testutil.TestConstants;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static com.jagrosh.jmusicbot.testutil.TestConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test fixture providing common mocks and setup for service-level tests.
 * Uses builder pattern for fluent test configuration.
 * 
 * This fixture is designed for testing MusicService and related service classes
 * where a real MusicService instance is created and tested against mocked dependencies.
 */
public class ServiceTestFixture
{
    // Core bot mocks
    private final Bot bot;
    private final BotConfig config;
    private final PlayerManager playerManager;
    private final SettingsManager settingsManager;
    private final Settings settings;
    private final PlaylistLoader playlistLoader;
    private final NowPlayingHandler nowPlayingHandler;
    private final EventWaiter eventWaiter;
    private final CommandClient commandClient;
    private final ScheduledExecutorService threadpool;

    // JDA mocks
    private final JDA jda;
    private final Guild guild;
    private final Member member;
    private final User user;
    private final SelfMember selfMember;
    private final TextChannel textChannel;
    private final AudioManager audioManager;
    private final AudioHandler audioHandler;
    private final AudioPlayer audioPlayer;

    // Voice state mocks
    private final GuildVoiceState selfVoiceState;
    private final GuildVoiceState memberVoiceState;
    private final VoiceChannel voiceChannel;

    // Track mocks
    private AudioTrack currentTrack;
    private AudioTrackInfo currentTrackInfo;

    // Queue mock
    private AbstractQueue<QueuedTrack> queue;

    // Re-export constants for backwards compatibility
    // New code should use TestConstants directly
    /** @deprecated Use {@link TestConstants#GUILD_ID} instead */
    @Deprecated
    public static final long GUILD_ID = TestConstants.GUILD_ID;
    /** @deprecated Use {@link TestConstants#USER_ID} instead */
    @Deprecated
    public static final long USER_ID = TestConstants.USER_ID;
    /** @deprecated Use {@link TestConstants#OWNER_ID} instead */
    @Deprecated
    public static final long OWNER_ID = TestConstants.OWNER_ID;
    /** @deprecated Use {@link TestConstants#DJ_ROLE_ID} instead */
    @Deprecated
    public static final long DJ_ROLE_ID = TestConstants.DJ_ROLE_ID;

    @SuppressWarnings("unchecked")
    private ServiceTestFixture()
    {
        // Create all mocks
        bot = mock(Bot.class);
        config = mock(BotConfig.class);
        playerManager = mock(PlayerManager.class);
        settingsManager = mock(SettingsManager.class);
        settings = mock(Settings.class);
        playlistLoader = mock(PlaylistLoader.class);
        nowPlayingHandler = mock(NowPlayingHandler.class);
        eventWaiter = mock(EventWaiter.class);
        commandClient = mock(CommandClient.class);
        threadpool = mock(ScheduledExecutorService.class);

        // JDA mocks
        jda = mock(JDA.class);
        guild = mock(Guild.class);
        member = mock(Member.class);
        user = mock(User.class);
        selfMember = mock(SelfMember.class);
        textChannel = mock(TextChannel.class);
        audioManager = mock(AudioManager.class);
        audioHandler = mock(AudioHandler.class);
        audioPlayer = mock(AudioPlayer.class);
        selfVoiceState = mock(GuildVoiceState.class);
        memberVoiceState = mock(GuildVoiceState.class);
        voiceChannel = mock(VoiceChannel.class, withSettings().extraInterfaces(AudioChannelUnion.class));

        // Track mocks - initially null (no track playing)
        currentTrack = null;
        currentTrackInfo = null;

        // Queue mock
        queue = mock(AbstractQueue.class);

        setupDefaultRelationships();
    }

    /**
     * Creates a new fixture with default configuration.
     */
    public static ServiceTestFixture create()
    {
        return new ServiceTestFixture();
    }

    private void setupDefaultRelationships()
    {
        // Bot relationships
        when(bot.getConfig()).thenReturn(config);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getPlaylistLoader()).thenReturn(playlistLoader);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
        when(bot.getWaiter()).thenReturn(eventWaiter);
        when(bot.getCommandClient()).thenReturn(commandClient);
        when(bot.getThreadpool()).thenReturn(threadpool);
        when(bot.getJDA()).thenReturn(jda);

        // Settings relationships
        when(settingsManager.getSettings(anyLong())).thenReturn(settings);
        when(settingsManager.getSettings(any(Guild.class))).thenReturn(settings);

        // Guild relationships
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guild.getId()).thenReturn(String.valueOf(GUILD_ID));
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(guild.getAfkChannel()).thenReturn(null);

        // Audio relationships
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(playerManager.setUpHandler(any(Guild.class))).thenReturn(audioHandler);
        when(playerManager.getBot()).thenReturn(bot);
        when(audioHandler.getPlayer()).thenReturn(audioPlayer);
        when(audioHandler.getQueue()).thenReturn(queue);

        // Voice state relationships
        when(selfMember.getVoiceState()).thenReturn(selfVoiceState);
        when(member.getVoiceState()).thenReturn(memberVoiceState);

        // Member/User relationships
        when(member.getUser()).thenReturn(user);
        when(member.getGuild()).thenReturn(guild);
        when(member.getIdLong()).thenReturn(USER_ID);
        when(user.getIdLong()).thenReturn(USER_ID);
        when(user.getId()).thenReturn(String.valueOf(USER_ID));
        when(user.getName()).thenReturn("TestUser");

        // JDA relationships
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        // Config defaults
        when(config.getOwnerId()).thenReturn(OWNER_ID);
        when(config.getAliases(anyString())).thenReturn(new String[0]);
        when(config.getMaxTime()).thenReturn("0"); // No max time by default
        when(config.isTooLong(any(AudioTrack.class))).thenReturn(false);

        // Settings defaults
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);
        when(settings.getVolume()).thenReturn(100);
        when(settings.getDefaultPlaylist()).thenReturn(null);
        when(settings.getRole(any(Guild.class))).thenReturn(null); // No DJ role by default
        when(settings.getSkipRatio()).thenReturn(0.55);
        when(settings.getQueueType()).thenReturn(QueueType.FAIR);

        // Queue defaults
        when(queue.size()).thenReturn(0);
        when(queue.isEmpty()).thenReturn(true);
        when(queue.getList()).thenReturn(Collections.emptyList());

        // Default: no track playing
        when(audioPlayer.getPlayingTrack()).thenReturn(null);
        when(audioPlayer.isPaused()).thenReturn(false);
        when(audioPlayer.getVolume()).thenReturn(100);

        // Default: bot not in voice channel
        when(selfVoiceState.getChannel()).thenReturn(null);

        // Default: user not in voice channel
        when(memberVoiceState.getChannel()).thenReturn(null);

        // TextChannel defaults
        when(textChannel.getIdLong()).thenReturn(CHANNEL_ID);
    }

    // ==================== DJ Permission Builder Methods ====================

    /**
     * Configures the member to have DJ permission (as bot owner).
     */
    public ServiceTestFixture withDJPermission()
    {
        // Make user the bot owner
        when(config.getOwnerId()).thenReturn(USER_ID);
        return this;
    }

    /**
     * Configures the member to have DJ permission via DJ role.
     */
    public ServiceTestFixture withDJRole()
    {
        Role djRole = mock(Role.class);
        when(djRole.getIdLong()).thenReturn(DJ_ROLE_ID);
        when(settings.getRole(any(Guild.class))).thenReturn(djRole);
        when(member.getRoles()).thenReturn(Collections.singletonList(djRole));
        return this;
    }

    /**
     * Configures the member to have DJ permission via MANAGE_SERVER permission.
     */
    public ServiceTestFixture withManageServerPermission()
    {
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        return this;
    }

    /**
     * Configures the member to NOT have DJ permission.
     */
    public ServiceTestFixture withoutDJPermission()
    {
        Role djRole = mock(Role.class);
        when(djRole.getIdLong()).thenReturn(DJ_ROLE_ID);
        when(config.getOwnerId()).thenReturn(OWNER_ID); // Different from USER_ID
        when(settings.getRole(any(Guild.class))).thenReturn(djRole);
        when(member.getRoles()).thenReturn(Collections.emptyList());
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);
        return this;
    }

    // ==================== Playback State Builder Methods ====================

    /**
     * Configures a track to be currently playing.
     */
    public ServiceTestFixture withPlayingTrack()
    {
        return withPlayingTrack("Test Track", "Test Author", 180000L);
    }

    /**
     * Configures a specific track to be currently playing.
     */
    public ServiceTestFixture withPlayingTrack(String title, String author, long durationMs)
    {
        currentTrack = mock(AudioTrack.class);
        currentTrackInfo = new AudioTrackInfo(title, author, durationMs, "test-id", false, "https://example.com/track");
        when(currentTrack.getInfo()).thenReturn(currentTrackInfo);
        when(currentTrack.getDuration()).thenReturn(durationMs);
        when(currentTrack.getPosition()).thenReturn(0L);
        when(audioPlayer.getPlayingTrack()).thenReturn(currentTrack);
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        return this;
    }

    /**
     * Configures the player to be paused with a track.
     */
    public ServiceTestFixture withPausedTrack()
    {
        withPlayingTrack();
        when(audioPlayer.isPaused()).thenReturn(true);
        return this;
    }

    /**
     * Configures no track to be playing.
     */
    public ServiceTestFixture withNoTrack()
    {
        when(audioPlayer.getPlayingTrack()).thenReturn(null);
        when(audioHandler.isMusicPlaying(jda)).thenReturn(false);
        return this;
    }

    // ==================== Queue State Builder Methods ====================

    /**
     * Configures the queue with a specific number of tracks.
     */
    public ServiceTestFixture withQueueSize(int size)
    {
        List<QueuedTrack> queueList = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Track " + (i + 1), "Author", 180000L, "id-" + i, false, "https://example.com/" + i);
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(180000L);
            when(qt.getTrack()).thenReturn(track);
            queueList.add(qt);
        }
        when(queue.size()).thenReturn(size);
        when(queue.isEmpty()).thenReturn(size == 0);
        when(queue.getList()).thenReturn(queueList);
        if (size > 0)
        {
            when(queue.get(anyInt())).thenAnswer(inv -> {
                int index = inv.getArgument(0);
                return index < queueList.size() ? queueList.get(index) : null;
            });
        }
        return this;
    }

    /**
     * Configures an empty queue.
     */
    public ServiceTestFixture withEmptyQueue()
    {
        return withQueueSize(0);
    }

    // ==================== Voice Channel Builder Methods ====================

    /**
     * Configures the user to be in a voice channel.
     */
    public ServiceTestFixture withUserInVoiceChannel()
    {
        when(memberVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        when(memberVoiceState.inAudioChannel()).thenReturn(true);
        return this;
    }

    /**
     * Configures the bot to be in a voice channel.
     */
    public ServiceTestFixture withBotInVoiceChannel()
    {
        when(selfVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        when(selfVoiceState.inAudioChannel()).thenReturn(true);
        return this;
    }

    /**
     * Configures both user and bot in the same voice channel.
     */
    public ServiceTestFixture withBothInVoiceChannel()
    {
        withUserInVoiceChannel();
        withBotInVoiceChannel();
        return this;
    }

    // ==================== Settings Builder Methods ====================

    /**
     * Configures a specific repeat mode.
     */
    public ServiceTestFixture withRepeatMode(RepeatMode mode)
    {
        when(settings.getRepeatMode()).thenReturn(mode);
        return this;
    }

    /**
     * Configures a specific volume level.
     */
    public ServiceTestFixture withVolume(int volume)
    {
        when(settings.getVolume()).thenReturn(volume);
        when(audioPlayer.getVolume()).thenReturn(volume);
        return this;
    }

    /**
     * Configures a specific queue type.
     */
    public ServiceTestFixture withQueueType(QueueType queueType)
    {
        when(settings.getQueueType()).thenReturn(queueType);
        return this;
    }

    /**
     * Configures tracks to be rejected if they exceed a max duration.
     */
    public ServiceTestFixture withMaxTrackDuration(long maxMs)
    {
        when(config.isTooLong(any(AudioTrack.class))).thenAnswer(inv -> {
            AudioTrack track = inv.getArgument(0);
            return track.getDuration() > maxMs;
        });
        when(config.getMaxTime()).thenReturn(String.valueOf(maxMs / 1000));
        return this;
    }

    // ==================== Getters ====================

    public Bot getBot()
    {
        return bot;
    }

    public BotConfig getConfig()
    {
        return config;
    }

    public PlayerManager getPlayerManager()
    {
        return playerManager;
    }

    public SettingsManager getSettingsManager()
    {
        return settingsManager;
    }

    public Settings getSettings()
    {
        return settings;
    }

    public PlaylistLoader getPlaylistLoader()
    {
        return playlistLoader;
    }

    public NowPlayingHandler getNowPlayingHandler()
    {
        return nowPlayingHandler;
    }

    public EventWaiter getEventWaiter()
    {
        return eventWaiter;
    }

    public CommandClient getCommandClient()
    {
        return commandClient;
    }

    public ScheduledExecutorService getThreadpool()
    {
        return threadpool;
    }

    public JDA getJda()
    {
        return jda;
    }

    public Guild getGuild()
    {
        return guild;
    }

    public Member getMember()
    {
        return member;
    }

    public User getUser()
    {
        return user;
    }

    public SelfMember getSelfMember()
    {
        return selfMember;
    }

    public TextChannel getTextChannel()
    {
        return textChannel;
    }

    public AudioManager getAudioManager()
    {
        return audioManager;
    }

    public AudioHandler getAudioHandler()
    {
        return audioHandler;
    }

    public AudioPlayer getAudioPlayer()
    {
        return audioPlayer;
    }

    public GuildVoiceState getSelfVoiceState()
    {
        return selfVoiceState;
    }

    public GuildVoiceState getMemberVoiceState()
    {
        return memberVoiceState;
    }

    public VoiceChannel getVoiceChannel()
    {
        return voiceChannel;
    }

    public AudioTrack getCurrentTrack()
    {
        return currentTrack;
    }

    public AudioTrackInfo getCurrentTrackInfo()
    {
        return currentTrackInfo;
    }

    public AbstractQueue<QueuedTrack> getQueue()
    {
        return queue;
    }
}
