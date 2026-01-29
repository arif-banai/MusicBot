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
package com.jagrosh.jmusicbot.testutil.listener;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.testutil.TestConstants;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;

import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test fixture providing common mocks and setup for Listener tests.
 * Uses builder pattern for fluent test configuration.
 */
public class ListenerTestFixture
{
    // Core bot mocks
    private final Bot bot;
    private final BotConfig config;
    private final PlayerManager playerManager;
    private final SettingsManager settingsManager;
    private final Settings settings;
    private final NowPlayingHandler nowPlayingHandler;
    private final AloneInVoiceHandler aloneInVoiceHandler;
    private final MusicService musicService;
    private final CommandClient commandClient;
    private final ScheduledExecutorService threadpool;
    private final YoutubeOauth2TokenHandler youtubeOauth2TokenHandler;
    private final UserInteraction userInteraction;

    // JDA mocks
    private final JDA jda;
    private final Guild guild;
    private final Member member;
    private final User user;
    private final SelfMember selfMember;
    private final SelfUser selfUser;
    private final TextChannel textChannel;
    private final AudioManager audioManager;
    private final AudioHandler audioHandler;
    private final AudioPlayer audioPlayer;

    // Voice mocks
    private final GuildVoiceState selfVoiceState;
    private final GuildVoiceState memberVoiceState;
    private final VoiceChannel voiceChannel;

    // Event mocks
    private final ReadyEvent readyEvent;
    private final ShutdownEvent shutdownEvent;
    private final SessionDisconnectEvent sessionDisconnectEvent;
    private final MessageDeleteEvent messageDeleteEvent;
    private final ButtonInteractionEvent buttonInteractionEvent;
    private final GuildVoiceUpdateEvent guildVoiceUpdateEvent;
    private final GuildJoinEvent guildJoinEvent;

    // Reply action mock
    private final ReplyCallbackAction replyAction;

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
    /** @deprecated Use {@link TestConstants#MESSAGE_ID} instead */
    @Deprecated
    public static final long MESSAGE_ID = TestConstants.MESSAGE_ID;
    /** @deprecated Use {@link TestConstants#CHANNEL_ID} instead */
    @Deprecated
    public static final long CHANNEL_ID = TestConstants.CHANNEL_ID;

    @SuppressWarnings("unchecked")
    private ListenerTestFixture()
    {
        // Create all mocks
        bot = mock(Bot.class);
        config = mock(BotConfig.class);
        playerManager = mock(PlayerManager.class);
        settingsManager = mock(SettingsManager.class);
        settings = mock(Settings.class);
        nowPlayingHandler = mock(NowPlayingHandler.class);
        aloneInVoiceHandler = mock(AloneInVoiceHandler.class);
        musicService = mock(MusicService.class);
        commandClient = mock(CommandClient.class);
        threadpool = mock(ScheduledExecutorService.class);
        youtubeOauth2TokenHandler = mock(YoutubeOauth2TokenHandler.class);
        userInteraction = mock(UserInteraction.class);

        // JDA mocks
        jda = mock(JDA.class);
        guild = mock(Guild.class);
        member = mock(Member.class);
        user = mock(User.class);
        selfMember = mock(SelfMember.class);
        selfUser = mock(SelfUser.class);
        textChannel = mock(TextChannel.class);
        audioManager = mock(AudioManager.class);
        audioHandler = mock(AudioHandler.class);
        audioPlayer = mock(AudioPlayer.class);
        selfVoiceState = mock(GuildVoiceState.class);
        memberVoiceState = mock(GuildVoiceState.class);
        voiceChannel = mock(VoiceChannel.class, withSettings().extraInterfaces(AudioChannelUnion.class));

        // Event mocks
        readyEvent = mock(ReadyEvent.class);
        shutdownEvent = mock(ShutdownEvent.class);
        sessionDisconnectEvent = mock(SessionDisconnectEvent.class);
        messageDeleteEvent = mock(MessageDeleteEvent.class);
        buttonInteractionEvent = mock(ButtonInteractionEvent.class);
        guildVoiceUpdateEvent = mock(GuildVoiceUpdateEvent.class);
        guildJoinEvent = mock(GuildJoinEvent.class);

        // Reply action mock
        replyAction = mock(ReplyCallbackAction.class);

        setupDefaultRelationships();
    }

    /**
     * Creates a new fixture with default configuration.
     */
    public static ListenerTestFixture create()
    {
        return new ListenerTestFixture();
    }

    @SuppressWarnings("unchecked")
    private void setupDefaultRelationships()
    {
        // Bot relationships
        when(bot.getConfig()).thenReturn(config);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
        when(bot.getAloneInVoiceHandler()).thenReturn(aloneInVoiceHandler);
        when(bot.getMusicService()).thenReturn(musicService);
        when(bot.getCommandClient()).thenReturn(commandClient);
        when(bot.getThreadpool()).thenReturn(threadpool);
        when(bot.getJDA()).thenReturn(jda);
        when(bot.getYouTubeOauth2Handler()).thenReturn(youtubeOauth2TokenHandler);
        when(bot.getUserInteraction()).thenReturn(userInteraction);

        // Settings relationships
        when(settingsManager.getSettings(anyLong())).thenReturn(settings);
        when(settingsManager.getSettings(any(Guild.class))).thenReturn(settings);

        // Guild relationships
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guild.getId()).thenReturn(String.valueOf(GUILD_ID));
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(guild.getAudioManager()).thenReturn(audioManager);

        // Audio relationships
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(playerManager.setUpHandler(any(Guild.class))).thenReturn(audioHandler);
        when(audioHandler.getPlayer()).thenReturn(audioPlayer);

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
        when(jda.getSelfUser()).thenReturn(selfUser);
        
        // Guild cache
        SnowflakeCacheView<Guild> guildCache = mock(SnowflakeCacheView.class);
        when(guildCache.isEmpty()).thenReturn(false);
        when(jda.getGuildCache()).thenReturn(guildCache);
        when(jda.getGuilds()).thenReturn(Collections.singletonList(guild));

        // Config defaults
        when(config.getOwnerId()).thenReturn(OWNER_ID);
        when(config.useUpdateAlerts()).thenReturn(false);
        when(config.useYouTubeOauth()).thenReturn(false);
        when(config.getDBots()).thenReturn(false);

        // Settings defaults
        when(settings.getDefaultPlaylist()).thenReturn(null);
        when(settings.getVoiceChannel(any(Guild.class))).thenReturn(null);

        // ReadyEvent defaults
        when(readyEvent.getJDA()).thenReturn(jda);

        // MessageDeleteEvent defaults
        when(messageDeleteEvent.isFromGuild()).thenReturn(true);
        when(messageDeleteEvent.getGuild()).thenReturn(guild);
        when(messageDeleteEvent.getMessageIdLong()).thenReturn(MESSAGE_ID);

        // ButtonInteractionEvent defaults
        when(buttonInteractionEvent.getGuild()).thenReturn(guild);
        when(buttonInteractionEvent.getMember()).thenReturn(member);
        when(buttonInteractionEvent.getJDA()).thenReturn(jda);
        when(buttonInteractionEvent.getComponentId()).thenReturn("unknown");
        when(buttonInteractionEvent.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        // GuildVoiceUpdateEvent defaults
        when(guildVoiceUpdateEvent.getGuild()).thenReturn(guild);
        when(guildVoiceUpdateEvent.getMember()).thenReturn(member);

        // GuildJoinEvent defaults
        when(guildJoinEvent.getJDA()).thenReturn(jda);
        when(guildJoinEvent.getGuild()).thenReturn(guild);

        // ShutdownEvent defaults
        when(shutdownEvent.getJDA()).thenReturn(jda);

        // SessionDisconnectEvent defaults
        when(sessionDisconnectEvent.getJDA()).thenReturn(jda);
        when(sessionDisconnectEvent.getCloseCode()).thenReturn(null);

        // TextChannel defaults
        when(textChannel.getIdLong()).thenReturn(CHANNEL_ID);
    }

    // ==================== Builder Methods ====================

    /**
     * Configures an empty guild cache (no guilds).
     */
    @SuppressWarnings("unchecked")
    public ListenerTestFixture withEmptyGuildCache()
    {
        SnowflakeCacheView<Guild> guildCache = mock(SnowflakeCacheView.class);
        when(guildCache.isEmpty()).thenReturn(true);
        when(jda.getGuildCache()).thenReturn(guildCache);
        when(jda.getGuilds()).thenReturn(Collections.emptyList());
        return this;
    }

    /**
     * Configures update alerts to be enabled.
     */
    public ListenerTestFixture withUpdateAlerts()
    {
        when(config.useUpdateAlerts()).thenReturn(true);
        return this;
    }

    /**
     * Configures YouTube OAuth to be enabled.
     */
    public ListenerTestFixture withYouTubeOauth()
    {
        when(config.useYouTubeOauth()).thenReturn(true);
        YoutubeOauth2TokenHandler.Data oauthData = mock(YoutubeOauth2TokenHandler.Data.class);
        when(oauthData.getAuthorisationUrl()).thenReturn("https://example.com/auth");
        when(oauthData.getCode()).thenReturn("TEST-CODE");
        when(youtubeOauth2TokenHandler.getData()).thenReturn(oauthData);
        
        // Mock private channel for owner
        CacheRestAction<PrivateChannel> privateChannelAction = mock(CacheRestAction.class);
        PrivateChannel privateChannel = mock(PrivateChannel.class);
        when(jda.openPrivateChannelById(OWNER_ID)).thenReturn(privateChannelAction);
        when(privateChannelAction.complete()).thenReturn(privateChannel);
        
        return this;
    }

    /**
     * Configures a default playlist for the guild.
     */
    public ListenerTestFixture withDefaultPlaylist(String playlist)
    {
        when(settings.getDefaultPlaylist()).thenReturn(playlist);
        when(settings.getVoiceChannel(guild)).thenReturn(voiceChannel);
        when(audioHandler.playFromDefault()).thenReturn(true);
        return this;
    }

    /**
     * Configures a button interaction event with a specific button ID.
     */
    public ListenerTestFixture withButtonId(String buttonId)
    {
        when(buttonInteractionEvent.getComponentId()).thenReturn(buttonId);
        return this;
    }

    /**
     * Configures the member to be in a voice channel for button interactions.
     */
    public ListenerTestFixture withMemberInVoiceChannel()
    {
        when(memberVoiceState.inAudioChannel()).thenReturn(true);
        when(memberVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        when(selfVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        return this;
    }

    /**
     * Configures the member to NOT be in a voice channel.
     */
    public ListenerTestFixture withMemberNotInVoiceChannel()
    {
        when(memberVoiceState.inAudioChannel()).thenReturn(false);
        when(memberVoiceState.getChannel()).thenReturn(null);
        return this;
    }

    /**
     * Configures audio handler to be playing.
     */
    public ListenerTestFixture withAudioHandlerPlaying()
    {
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        return this;
    }

    /**
     * Configures no audio handler (null).
     */
    public ListenerTestFixture withNoAudioHandler()
    {
        when(audioManager.getSendingHandler()).thenReturn(null);
        return this;
    }

    /**
     * Configures the SessionDisconnectEvent with a specific close code.
     */
    public ListenerTestFixture withCloseCode(CloseCode closeCode)
    {
        when(sessionDisconnectEvent.getCloseCode()).thenReturn(closeCode);
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

    public NowPlayingHandler getNowPlayingHandler()
    {
        return nowPlayingHandler;
    }

    public AloneInVoiceHandler getAloneInVoiceHandler()
    {
        return aloneInVoiceHandler;
    }

    public MusicService getMusicService()
    {
        return musicService;
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

    public ReadyEvent getReadyEvent()
    {
        return readyEvent;
    }

    public ShutdownEvent getShutdownEvent()
    {
        return shutdownEvent;
    }

    public SessionDisconnectEvent getSessionDisconnectEvent()
    {
        return sessionDisconnectEvent;
    }

    public UserInteraction getUserInteraction()
    {
        return userInteraction;
    }

    public MessageDeleteEvent getMessageDeleteEvent()
    {
        return messageDeleteEvent;
    }

    public ButtonInteractionEvent getButtonInteractionEvent()
    {
        return buttonInteractionEvent;
    }

    public GuildVoiceUpdateEvent getGuildVoiceUpdateEvent()
    {
        return guildVoiceUpdateEvent;
    }

    public GuildJoinEvent getGuildJoinEvent()
    {
        return guildJoinEvent;
    }

    public ReplyCallbackAction getReplyAction()
    {
        return replyAction;
    }
}
