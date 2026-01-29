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
package com.jagrosh.jmusicbot.testutil.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.queue.HistoryQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.testutil.TestConstants;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
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
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test fixture providing common mocks and setup for audio component tests.
 * Specifically designed for testing AudioHandler, NowPlayingHandler, and related classes.
 */
public class AudioTestFixture
{
    // Core bot mocks
    private final Bot bot;
    private final BotConfig config;
    private final PlayerManager playerManager;
    private final SettingsManager settingsManager;
    private final Settings settings;
    private final NowPlayingHandler nowPlayingHandler;
    private final ScheduledExecutorService threadpool;

    // JDA mocks
    private final JDA jda;
    private final Guild guild;
    private final Member member;
    private final User user;
    private final SelfMember selfMember;
    private final TextChannel textChannel;
    private final AudioManager audioManager;
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager audioPlayerManager;

    // Voice mocks
    private final GuildVoiceState selfVoiceState;
    private final VoiceChannel voiceChannel;

    // Track mocks
    private final List<AudioTrack> mockTracks = new ArrayList<>();
    private AudioTrack currentTrack;

    // Queue mock
    private AbstractQueue<QueuedTrack> queue;
    private HistoryQueue<QueuedTrack> history;

    // Message mocks
    private final Message message;
    private final MessageCreateAction messageCreateAction;
    private final MessageEditAction messageEditAction;
    private final AuditableRestAction<Void> deleteAction;

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
    private AudioTestFixture()
    {
        // Create all mocks
        bot = mock(Bot.class);
        config = mock(BotConfig.class);
        playerManager = mock(PlayerManager.class);
        settingsManager = mock(SettingsManager.class);
        settings = mock(Settings.class);
        nowPlayingHandler = mock(NowPlayingHandler.class);
        threadpool = mock(ScheduledExecutorService.class);

        // JDA mocks
        jda = mock(JDA.class);
        guild = mock(Guild.class);
        member = mock(Member.class);
        user = mock(User.class);
        selfMember = mock(SelfMember.class);
        textChannel = mock(TextChannel.class, withSettings().extraInterfaces(MessageChannelUnion.class));
        audioManager = mock(AudioManager.class);
        audioPlayer = mock(AudioPlayer.class);
        audioPlayerManager = mock(AudioPlayerManager.class);
        selfVoiceState = mock(GuildVoiceState.class);
        voiceChannel = mock(VoiceChannel.class, withSettings().extraInterfaces(AudioChannelUnion.class));

        // Queue mocks
        queue = mock(AbstractQueue.class);
        history = mock(HistoryQueue.class);

        // Message mocks
        message = mock(Message.class);
        messageCreateAction = mock(MessageCreateAction.class);
        messageEditAction = mock(MessageEditAction.class);
        deleteAction = mock(AuditableRestAction.class);

        setupDefaultRelationships();
    }

    /**
     * Creates a new fixture with default configuration.
     */
    public static AudioTestFixture create()
    {
        return new AudioTestFixture();
    }

    @SuppressWarnings("unchecked")
    private void setupDefaultRelationships()
    {
        // Bot relationships
        when(bot.getConfig()).thenReturn(config);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
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
        when(guild.getTextChannelById(CHANNEL_ID)).thenReturn(textChannel);

        // JDA relationships
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);

        // PlayerManager relationships
        when(playerManager.getBot()).thenReturn(bot);

        // Audio player defaults
        when(audioPlayer.getPlayingTrack()).thenReturn(null);
        when(audioPlayer.isPaused()).thenReturn(false);
        when(audioPlayer.getVolume()).thenReturn(100);

        // Voice state relationships
        when(selfMember.getVoiceState()).thenReturn(selfVoiceState);
        when(selfVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);

        // Member/User relationships
        when(member.getUser()).thenReturn(user);
        when(member.getGuild()).thenReturn(guild);
        when(member.getIdLong()).thenReturn(USER_ID);
        when(user.getIdLong()).thenReturn(USER_ID);
        when(user.getId()).thenReturn(String.valueOf(USER_ID));
        when(user.getName()).thenReturn("TestUser");

        // Config defaults
        when(config.getOwnerId()).thenReturn(OWNER_ID);
        when(config.useNPImages()).thenReturn(false);
        when(config.getSongInStatus()).thenReturn(false);
        when(config.getMaxHistorySize()).thenReturn(10);

        // Settings defaults
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);
        when(settings.getVolume()).thenReturn(100);
        when(settings.getQueueType()).thenReturn(QueueType.FAIR);

        // Queue defaults
        when(queue.size()).thenReturn(0);
        when(queue.isEmpty()).thenReturn(true);
        when(queue.getList()).thenReturn(Collections.emptyList());
        when(queue.getHistory()).thenReturn(history);
        when(history.isEmpty()).thenReturn(true);
        when(history.getList()).thenReturn(Collections.emptyList());

        // TextChannel defaults
        when(textChannel.getIdLong()).thenReturn(CHANNEL_ID);
        when(textChannel.sendMessage(any(MessageCreateData.class))).thenReturn(messageCreateAction);
        when(textChannel.editMessageById(anyLong(), any(MessageEditData.class))).thenReturn(messageEditAction);
        when(textChannel.deleteMessageById(anyLong())).thenReturn(deleteAction);

        // Message action chaining
        doAnswer(inv -> {
            Consumer<Message> callback = inv.getArgument(0);
            callback.accept(message);
            return null;
        }).when(messageCreateAction).queue(any());
        doNothing().when(messageCreateAction).queue();

        doAnswer(inv -> {
            Consumer<Message> callback = inv.getArgument(0);
            callback.accept(message);
            return null;
        }).when(messageEditAction).queue(any(), any());
        doNothing().when(messageEditAction).queue();

        doNothing().when(deleteAction).queue(any(), any());
        doNothing().when(deleteAction).queue();

        // Message defaults
        when(message.getIdLong()).thenReturn(MESSAGE_ID);
        when(message.getChannel()).thenReturn((MessageChannelUnion) textChannel);
        when(message.getGuild()).thenReturn(guild);
    }

    // ==================== Track Creation Methods ====================

    /**
     * Creates a mock audio track with the given properties.
     */
    public AudioTrack createMockTrack(String title, String author, long durationMs)
    {
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = new AudioTrackInfo(title, author, durationMs, 
                "id-" + mockTracks.size(), false, "https://example.com/" + mockTracks.size());
        
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(durationMs);
        when(track.getPosition()).thenReturn(0L);
        when(track.isSeekable()).thenReturn(true);
        when(track.makeClone()).thenReturn(track);
        
        mockTracks.add(track);
        return track;
    }

    /**
     * Creates a mock queued track with the given properties.
     */
    public QueuedTrack createMockQueuedTrack(String title, String author, long durationMs)
    {
        return createMockQueuedTrack(title, author, durationMs, USER_ID);
    }

    /**
     * Creates a mock queued track with the given properties and user ID.
     */
    public QueuedTrack createMockQueuedTrack(String title, String author, long durationMs, long userId)
    {
        AudioTrack track = createMockTrack(title, author, durationMs);
        QueuedTrack qt = mock(QueuedTrack.class);
        
        when(qt.getTrack()).thenReturn(track);
        when(qt.getIdentifier()).thenReturn(userId);
        
        RequestMetadata metadata = mock(RequestMetadata.class);
        when(metadata.getOwner()).thenReturn(userId);
        when(metadata.channelId).thenReturn(CHANNEL_ID);
        when(track.getUserData(RequestMetadata.class)).thenReturn(metadata);
        
        return qt;
    }

    // ==================== Builder Methods ====================

    /**
     * Configures a track to be currently playing.
     */
    public AudioTestFixture withPlayingTrack()
    {
        return withPlayingTrack("Test Track", "Test Author", 180000);
    }

    /**
     * Configures a specific track to be currently playing.
     */
    public AudioTestFixture withPlayingTrack(String title, String author, long durationMs)
    {
        currentTrack = createMockTrack(title, author, durationMs);
        when(audioPlayer.getPlayingTrack()).thenReturn(currentTrack);
        return this;
    }

    /**
     * Configures the player to be paused.
     */
    public AudioTestFixture withPausedTrack()
    {
        withPlayingTrack();
        when(audioPlayer.isPaused()).thenReturn(true);
        return this;
    }

    /**
     * Configures no track to be playing.
     */
    public AudioTestFixture withNoTrack()
    {
        currentTrack = null;
        when(audioPlayer.getPlayingTrack()).thenReturn(null);
        return this;
    }

    /**
     * Configures the queue with a specific number of tracks.
     */
    public AudioTestFixture withQueueSize(int size)
    {
        List<QueuedTrack> queueList = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            QueuedTrack qt = createMockQueuedTrack("Track " + (i + 1), "Author", 180000);
            queueList.add(qt);
        }
        when(queue.size()).thenReturn(size);
        when(queue.isEmpty()).thenReturn(size == 0);
        when(queue.getList()).thenReturn(queueList);
        if (size > 0)
        {
            when(queue.get(anyInt())).thenAnswer(inv -> {
                int index = inv.getArgument(0);
                return index >= 0 && index < queueList.size() ? queueList.get(index) : null;
            });
        }
        return this;
    }

    /**
     * Configures the history with a specific number of tracks.
     */
    public AudioTestFixture withHistorySize(int size)
    {
        List<QueuedTrack> historyList = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            QueuedTrack qt = createMockQueuedTrack("Previous " + (i + 1), "Author", 180000);
            historyList.add(qt);
        }
        when(history.isEmpty()).thenReturn(size == 0);
        when(history.size()).thenReturn(size);
        when(history.getList()).thenReturn(historyList);
        if (size > 0)
        {
            when(history.removeFirst()).thenReturn(historyList.get(0));
        }
        return this;
    }

    /**
     * Configures the repeat mode.
     */
    public AudioTestFixture withRepeatMode(RepeatMode mode)
    {
        when(settings.getRepeatMode()).thenReturn(mode);
        return this;
    }

    /**
     * Configures NP images to be used.
     */
    public AudioTestFixture withNPImages()
    {
        when(config.useNPImages()).thenReturn(true);
        return this;
    }

    /**
     * Configures song in status to be enabled.
     */
    public AudioTestFixture withSongInStatus()
    {
        when(config.getSongInStatus()).thenReturn(true);
        return this;
    }

    /**
     * Configures the bot to be in a voice channel.
     */
    public AudioTestFixture withBotInVoiceChannel()
    {
        when(selfVoiceState.getChannel()).thenReturn((AudioChannelUnion) voiceChannel);
        when(selfVoiceState.inAudioChannel()).thenReturn(true);
        return this;
    }

    /**
     * Configures the bot to NOT be in a voice channel.
     */
    public AudioTestFixture withBotNotInVoiceChannel()
    {
        when(selfVoiceState.getChannel()).thenReturn(null);
        when(selfVoiceState.inAudioChannel()).thenReturn(false);
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

    public AudioPlayer getAudioPlayer()
    {
        return audioPlayer;
    }

    public AudioPlayerManager getAudioPlayerManager()
    {
        return audioPlayerManager;
    }

    public GuildVoiceState getSelfVoiceState()
    {
        return selfVoiceState;
    }

    public VoiceChannel getVoiceChannel()
    {
        return voiceChannel;
    }

    public AudioTrack getCurrentTrack()
    {
        return currentTrack;
    }

    public AbstractQueue<QueuedTrack> getQueue()
    {
        return queue;
    }

    public HistoryQueue<QueuedTrack> getHistory()
    {
        return history;
    }

    public Message getMessage()
    {
        return message;
    }

    public MessageCreateAction getMessageCreateAction()
    {
        return messageCreateAction;
    }

    public MessageEditAction getMessageEditAction()
    {
        return messageEditAction;
    }

    public List<AudioTrack> getMockTracks()
    {
        return new ArrayList<>(mockTracks);
    }
}
