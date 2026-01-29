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
package com.jagrosh.jmusicbot.testutil.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.service.SearchService;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test fixture providing common mocks and setup for SlashCommand tests.
 * Uses builder pattern for fluent test configuration.
 */
public class SlashCommandTestFixture
{
    // Core bot mocks
    private final Bot bot;
    private final BotConfig config;
    private final PlayerManager playerManager;
    private final SettingsManager settingsManager;
    private final Settings settings;

    // Service mocks
    private final MusicService musicService;
    private final SearchService searchService;
    private final NowPlayingHandler nowPlayingHandler;
    private final EventWaiter eventWaiter;

    // JDA mocks
    private final JDA jda;
    private final Guild guild;
    private final Member member;
    private final User user;
    private final SelfMember selfMember;
    private final TextChannel textChannel;
    private final AudioManager audioManager;
    private final AudioHandler audioHandler;

    // Voice state mocks
    private final GuildVoiceState selfVoiceState;
    private final GuildVoiceState memberVoiceState;

    // Shared voice channel - mock implements both VoiceChannel and AudioChannelUnion
    // This allows the same instance to be used in both settings.getVoiceChannel() and
    // memberVoiceState.getChannel() so that equality checks pass
    private final VoiceChannel voiceChannel;

    // Event mocks
    private final SlashCommandEvent event;
    private final CommandClient client;
    private final ReplyCallbackAction replyAction;

    // Interaction mocks
    private final InteractionHook hook;
    private final WebhookMessageEditAction<Message> editAction;
    private final RestAction<Message> retrieveAction;
    private final Message message;

    // AutoComplete mocks
    private final CommandAutoCompleteInteractionEvent autoCompleteEvent;
    private final AutoCompleteQuery focusedOption;
    private final AutoCompleteCallbackAction autoCompleteCallback;

    // Constants
    public static final long GUILD_ID = 123456789L;
    public static final long USER_ID = 987654321L;

    @SuppressWarnings("unchecked")
    private SlashCommandTestFixture()
    {
        // Create all mocks
        bot = mock(Bot.class);
        config = mock(BotConfig.class);
        playerManager = mock(PlayerManager.class);
        settingsManager = mock(SettingsManager.class);
        settings = mock(Settings.class);

        // Service mocks
        musicService = mock(MusicService.class);
        searchService = mock(SearchService.class);
        nowPlayingHandler = mock(NowPlayingHandler.class);
        eventWaiter = mock(EventWaiter.class);

        // JDA mocks
        jda = mock(JDA.class);
        guild = mock(Guild.class);
        member = mock(Member.class);
        user = mock(User.class);
        selfMember = mock(SelfMember.class);
        textChannel = mock(TextChannel.class);
        audioManager = mock(AudioManager.class);
        audioHandler = mock(AudioHandler.class);
        selfVoiceState = mock(GuildVoiceState.class);
        memberVoiceState = mock(GuildVoiceState.class);
        // Create a VoiceChannel mock that also implements AudioChannelUnion
        // This allows the same instance to be used for both settings.getVoiceChannel()
        // (which returns VoiceChannel) and memberVoiceState.getChannel() (which returns AudioChannelUnion)
        voiceChannel = mock(VoiceChannel.class, withSettings().extraInterfaces(AudioChannelUnion.class));

        // Event mocks
        event = mock(SlashCommandEvent.class);
        client = mock(CommandClient.class);
        replyAction = mock(ReplyCallbackAction.class);

        // Interaction mocks
        hook = mock(InteractionHook.class);
        editAction = mock(WebhookMessageEditAction.class);
        retrieveAction = mock(RestAction.class);
        message = mock(Message.class);

        // AutoComplete mocks
        autoCompleteEvent = mock(CommandAutoCompleteInteractionEvent.class);
        focusedOption = mock(AutoCompleteQuery.class);
        autoCompleteCallback = mock(AutoCompleteCallbackAction.class);

        setupDefaultRelationships();
    }

    /**
     * Creates a new fixture with default configuration.
     */
    public static SlashCommandTestFixture create()
    {
        return new SlashCommandTestFixture();
    }

    private void setupDefaultRelationships()
    {
        // Bot relationships
        when(bot.getConfig()).thenReturn(config);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getMusicService()).thenReturn(musicService);
        when(bot.getSearchService()).thenReturn(searchService);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
        when(bot.getWaiter()).thenReturn(eventWaiter);

        // Settings relationships
        when(settingsManager.getSettings(anyLong())).thenReturn(settings);

        // Event relationships
        when(event.getClient()).thenReturn(client);
        when(event.getJDA()).thenReturn(jda);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getTextChannel()).thenReturn(textChannel);
        when(event.getUser()).thenReturn(user);

        // Client defaults
        when(client.getError()).thenReturn("❌");
        when(client.getWarning()).thenReturn("⚠️");
        when(client.getSuccess()).thenReturn("✅");
        when(client.getSettingsFor(guild)).thenReturn(settings);

        // Guild relationships
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(guild.getAfkChannel()).thenReturn(null);

        // Audio relationships
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(playerManager.setUpHandler(any(Guild.class))).thenReturn(audioHandler);

        // Voice state relationships
        when(selfMember.getVoiceState()).thenReturn(selfVoiceState);
        when(member.getVoiceState()).thenReturn(memberVoiceState);

        // Member/User relationships
        when(member.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(USER_ID);
        when(member.getColor()).thenReturn(null);

        // Reply action chain
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        // Hook and edit action chain
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(hook.retrieveOriginal()).thenReturn(retrieveAction);
        doNothing().when(editAction).queue();

        // AutoComplete defaults
        when(autoCompleteEvent.getFocusedOption()).thenReturn(focusedOption);
        when(autoCompleteEvent.getGuild()).thenReturn(guild);
        when(autoCompleteEvent.replyChoices()).thenReturn(autoCompleteCallback);
        doNothing().when(autoCompleteCallback).queue();

        // Default: no text channel restriction
        when(settings.getTextChannel(guild)).thenReturn(null);

        // Default: no voice channel configured
        when(settings.getVoiceChannel(guild)).thenReturn(null);

        // Default: bot not in voice channel
        when(selfVoiceState.getChannel()).thenReturn(null);

        // Default: user not in voice channel
        when(memberVoiceState.getChannel()).thenReturn(null);
        when(memberVoiceState.isDeafened()).thenReturn(false);

        // Default: config aliases and emojis
        when(config.getAliases(anyString())).thenReturn(new String[0]);
        when(config.getLoading()).thenReturn("⏳");
        when(config.getSearching()).thenReturn("🔍");
    }

    // ==================== Builder Methods ====================

    /**
     * Configures a required text channel for commands.
     */
    public SlashCommandTestFixture withRequiredTextChannel(TextChannel requiredChannel)
    {
        when(settings.getTextChannel(guild)).thenReturn(requiredChannel);
        return this;
    }

    /**
     * Configures the user to be in a voice channel.
     * Uses the shared voiceChannel mock to ensure equality checks pass.
     */
    public SlashCommandTestFixture withUserInVoiceChannel()
    {
        // voiceChannel implements both VoiceChannel and AudioChannelUnion
        when(memberVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        return this;
    }

    /**
     * Configures the user to be in a specific voice channel.
     * The channel should be created with extraInterfaces(AudioChannelUnion.class) for compatibility.
     */
    public SlashCommandTestFixture withUserInVoiceChannel(AudioChannelUnion channel)
    {
        when(memberVoiceState.getChannel()).thenReturn(channel);
        return this;
    }

    /**
     * Configures the bot to be in a voice channel.
     * Uses the shared voiceChannel mock to ensure equality checks pass.
     */
    public SlashCommandTestFixture withBotInVoiceChannel()
    {
        // voiceChannel implements both VoiceChannel and AudioChannelUnion
        when(selfVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        return this;
    }

    /**
     * Configures the bot to be in a specific voice channel.
     */
    public SlashCommandTestFixture withBotInVoiceChannel(AudioChannelUnion channel)
    {
        when(selfVoiceState.getChannel()).thenReturn(channel);
        return this;
    }

    /**
     * Configures a required voice channel in settings.
     * Uses the shared voiceChannel mock to ensure equality checks pass.
     */
    public SlashCommandTestFixture withRequiredVoiceChannel()
    {
        when(settings.getVoiceChannel(guild)).thenReturn(voiceChannel);
        return this;
    }

    /**
     * Configures a required voice channel in settings with a specific channel.
     */
    public SlashCommandTestFixture withRequiredVoiceChannel(VoiceChannel channel)
    {
        when(settings.getVoiceChannel(guild)).thenReturn(channel);
        return this;
    }

    /**
     * Configures the user as deafened.
     */
    public SlashCommandTestFixture withUserDeafened()
    {
        when(memberVoiceState.isDeafened()).thenReturn(true);
        return this;
    }

    /**
     * Configures the AFK channel.
     * Uses the shared voiceChannel mock to ensure equality checks pass.
     */
    public SlashCommandTestFixture withAfkChannel()
    {
        when(guild.getAfkChannel()).thenReturn(voiceChannel);
        return this;
    }

    /**
     * Configures a specific AFK channel.
     */
    public SlashCommandTestFixture withAfkChannel(VoiceChannel afkChannel)
    {
        when(guild.getAfkChannel()).thenReturn(afkChannel);
        return this;
    }

    /**
     * Configures music as playing.
     */
    public SlashCommandTestFixture withMusicPlaying()
    {
        when(audioHandler.isMusicPlaying(jda)).thenReturn(true);
        return this;
    }

    /**
     * Configures music as not playing.
     */
    public SlashCommandTestFixture withMusicNotPlaying()
    {
        when(audioHandler.isMusicPlaying(jda)).thenReturn(false);
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

    public SlashCommandEvent getEvent()
    {
        return event;
    }

    public CommandClient getClient()
    {
        return client;
    }

    public ReplyCallbackAction getReplyAction()
    {
        return replyAction;
    }

    public MusicService getMusicService()
    {
        return musicService;
    }

    public SearchService getSearchService()
    {
        return searchService;
    }

    public NowPlayingHandler getNowPlayingHandler()
    {
        return nowPlayingHandler;
    }

    public EventWaiter getEventWaiter()
    {
        return eventWaiter;
    }

    public InteractionHook getHook()
    {
        return hook;
    }

    public WebhookMessageEditAction<Message> getEditAction()
    {
        return editAction;
    }

    public RestAction<Message> getRetrieveAction()
    {
        return retrieveAction;
    }

    public Message getMessage()
    {
        return message;
    }

    public CommandAutoCompleteInteractionEvent getAutoCompleteEvent()
    {
        return autoCompleteEvent;
    }

    public AutoCompleteQuery getFocusedOption()
    {
        return focusedOption;
    }

    public AutoCompleteCallbackAction getAutoCompleteCallback()
    {
        return autoCompleteCallback;
    }

    // ==================== Additional Builder Methods ====================

    /**
     * Configures the reply action to execute a callback with the hook when queue is called.
     */
    public SlashCommandTestFixture withReplyQueueCallback()
    {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(replyAction).queue(any());
        return this;
    }

    /**
     * Configures the edit action to execute a callback with the message when queue is called.
     */
    public SlashCommandTestFixture withEditQueueCallback()
    {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Message> callback = invocation.getArgument(0);
            callback.accept(message);
            return null;
        }).when(editAction).queue(any());
        return this;
    }

    /**
     * Configures the retrieve action to execute a callback with the message when queue is called.
     */
    public SlashCommandTestFixture withRetrieveQueueCallback()
    {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Message> callback = invocation.getArgument(0);
            callback.accept(message);
            return null;
        }).when(retrieveAction).queue(any());
        return this;
    }
}
