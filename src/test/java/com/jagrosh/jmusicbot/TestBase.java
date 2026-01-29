package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.when;

public abstract class TestBase {

    @Mock
    protected Bot bot;
    @Mock
    protected BotConfig config;
    @Mock
    protected PlayerManager playerManager;
    @Mock
    protected SettingsManager settingsManager;
    @Mock
    protected Settings settings;
    @Mock
    protected Guild guild;
    @Mock
    protected Member member;
    @Mock
    protected User user;
    @Mock
    protected TextChannel textChannel;
    @Mock
    protected AudioManager audioManager;
    @Mock
    protected AudioHandler audioHandler;
    @Mock
    protected AudioPlayer audioPlayer;
    @Mock
    protected AudioTrack audioTrack;
    @Mock
    protected JDA jda;
    @Mock
    protected AudioChannelUnion audioChannel;
    @Mock
    protected ScheduledExecutorService threadpool;

    protected final long GUILD_ID = 123456789L;
    protected final long OWNER_ID = 123L;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Basic Bot relationships
        when(bot.getConfig()).thenReturn(config);
        when(bot.getPlayerManager()).thenReturn(playerManager);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(bot.getThreadpool()).thenReturn(threadpool);
        when(bot.getJDA()).thenReturn(jda);

        // PlayerManager relationships
        when(playerManager.getBot()).thenReturn(bot);

        // Guild and Audio relationships
        when(guild.getIdLong()).thenReturn(GUILD_ID);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(audioHandler);
        when(audioHandler.getPlayer()).thenReturn(audioPlayer);
        
        // Member and User relationships
        when(member.getUser()).thenReturn(user);
        when(member.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn(String.valueOf(OWNER_ID));
        when(user.getIdLong()).thenReturn(OWNER_ID);
        
        // Config defaults
        when(config.getOwnerId()).thenReturn(OWNER_ID);
        
        // Settings defaults
        when(settingsManager.getSettings(GUILD_ID)).thenReturn(settings);
    }
}
