/*
 * Copyright 2017 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.v1.owner;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.commands.v1.OwnerCommand;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.tools.PlayerLibrary;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class DebugCmd extends OwnerCommand 
{
    private static final String[] PROPERTIES = {
        "java.version", "java.vm.name", "java.vm.specification.version", 
        "java.runtime.name", "java.runtime.version", "java.specification.version", 
        "os.arch", "os.name"
    };
    
    private static final String SECTION_SEPARATOR = "=";
    private static final int SECTION_WIDTH = 40;
    
    private final Bot bot;
    
    public DebugCmd(Bot bot)
    {
        this.bot = bot;
        this.name = "debug";
        this.help = "shows debug info";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("```\n");
        
        appendSystemSection(sb);
        appendJMusicBotSection(sb);
        appendConfigurationSection(sb);
        appendAudioSourcesSection(sb);
        appendPlaylistsSection(sb);
        appendDependenciesSection(sb);
        appendRuntimeSection(sb, event.getJDA());
        appendDiscordSection(sb, event.getJDA());
        appendPlaybackSection(sb, event.getJDA());
        
        sb.append("```");

        if (event.isFromType(ChannelType.PRIVATE)
                || event.getSelfMember().hasPermission((TextChannel) event.getChannel(), Permission.MESSAGE_ATTACH_FILES))
            event.getChannel().sendFiles(FileUpload.fromData(sb.toString().getBytes(), "debug_information.txt")).queue();
        else
            event.reply("Debug Information: " + sb.toString());
    }
    
    // ========== Section Builders ==========
    
    private void appendSystemSection(StringBuilder sb)
    {
        appendSectionHeader(sb, "SYSTEM");
        for (String key : PROPERTIES)
        {
            sb.append("  ").append(key).append(" = ").append(System.getProperty(key)).append("\n");
        }
    }
    
    private void appendJMusicBotSection(StringBuilder sb)
    {
        appendSectionHeader(sb, "JMUSICBOT");
        
        String currentVersion = OtherUtil.getCurrentVersion();
        String latestVersion = OtherUtil.getLatestVersion();
        sb.append("  Version: ").append(currentVersion);
        if (latestVersion != null)
        {
            if (OtherUtil.isNewerVersion(currentVersion, latestVersion))
                sb.append(" (UPDATE AVAILABLE: ").append(latestVersion).append(")");
            else
                sb.append(" (latest)");
        }
        sb.append("\n");
        
        sb.append("  Uptime: ").append(formatUptime(bot.getStartTime())).append("\n");
        sb.append("  Config: ").append(bot.getConfig().getConfigLocation()).append("\n");
    }
    
    private void appendConfigurationSection(StringBuilder sb)
    {
        BotConfig config = bot.getConfig();
        appendSectionHeader(sb, "CONFIGURATION");
        
        sb.append("  Owner: ").append(config.getOwnerId()).append("\n");
        sb.append("  Prefix: ").append(config.getPrefix()).append("\n");
        sb.append("  Alt Prefix: ").append(config.getAltPrefix() == null ? "NONE" : config.getAltPrefix()).append("\n");
        sb.append("  Log Level: ").append(config.getLogLevel()).append("\n");
        sb.append("  Max Track Length: ").append(config.getMaxSeconds() <= 0 ? "unlimited" : config.getMaxTime()).append("\n");
        sb.append("  Max YT Playlist Pages: ").append(config.getMaxYTPlaylistPages()).append("\n");
        sb.append("  Skip Ratio: ").append(config.getSkipRatio()).append("\n");
        sb.append("  Stay In Channel: ").append(config.getStay()).append("\n");
        sb.append("  Alone Timeout: ").append(config.getAloneTimeUntilStop() <= 0 ? "disabled" : config.getAloneTimeUntilStop() + "s").append("\n");
        sb.append("  Song In Status: ").append(config.getSongInStatus()).append("\n");
        sb.append("  NP Images: ").append(config.useNPImages()).append("\n");
        sb.append("  Update Alerts: ").append(config.useUpdateAlerts()).append("\n");
        sb.append("  Eval Enabled: ").append(config.useEval()).append("\n");
    }
    
    private void appendAudioSourcesSection(StringBuilder sb)
    {
        BotConfig config = bot.getConfig();
        appendSectionHeader(sb, "AUDIO SOURCES");
        
        Set<AudioSource> enabledSources = config.getEnabledAudioSources();
        Set<AudioSource> allSources = Arrays.stream(AudioSource.values()).collect(Collectors.toSet());
        
        String enabled = enabledSources.stream()
                .map(AudioSource::getConfigName)
                .collect(Collectors.joining(", "));
        sb.append("  Enabled: ").append(enabled.isEmpty() ? "none" : enabled).append("\n");
        
        Set<AudioSource> disabledSources = allSources.stream()
                .filter(source -> !enabledSources.contains(source))
                .collect(Collectors.toSet());
        String disabled = disabledSources.stream()
                .map(AudioSource::getConfigName)
                .collect(Collectors.joining(", "));
        sb.append("  Disabled: ").append(disabled.isEmpty() ? "none" : disabled).append("\n");
        
        sb.append("  YouTube OAuth: ").append(formatYouTubeOAuthStatus(config)).append("\n");
    }
    
    private void appendPlaylistsSection(StringBuilder sb)
    {
        PlaylistLoader loader = bot.getPlaylistLoader();
        appendSectionHeader(sb, "PLAYLISTS");
        
        boolean folderExists = loader.folderExists();
        sb.append("  Folder: ").append(bot.getConfig().getPlaylistsFolder()).append("\n");
        sb.append("  Folder Exists: ").append(folderExists).append("\n");
        
        if (folderExists)
        {
            List<String> playlists = loader.getPlaylistNames();
            sb.append("  Playlists: ").append(playlists.size()).append("\n");
        }
    }
    
    private void appendDependenciesSection(StringBuilder sb)
    {
        appendSectionHeader(sb, "DEPENDENCIES");
        sb.append("  JDA: ").append(JDAInfo.VERSION).append("\n");
        sb.append("  JDA-Utilities: ").append(JDAUtilitiesInfo.VERSION).append("\n");
        sb.append("  Lavaplayer: ").append(PlayerLibrary.VERSION).append("\n");
    }
    
    private void appendRuntimeSection(StringBuilder sb, JDA jda)
    {
        appendSectionHeader(sb, "RUNTIME");
        
        Runtime runtime = Runtime.getRuntime();
        long totalMb = runtime.totalMemory() / 1024 / 1024;
        long usedMb = totalMb - (runtime.freeMemory() / 1024 / 1024);
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        
        sb.append("  Memory: ").append(usedMb).append(" MB used / ").append(totalMb).append(" MB total\n");
        sb.append("  Max Memory: ").append(maxMb).append(" MB\n");
        sb.append("  Processors: ").append(runtime.availableProcessors()).append("\n");
        sb.append("  Working Dir: ").append(System.getProperty("user.dir")).append("\n");
        sb.append("  Gateway Ping: ").append(jda.getGatewayPing()).append(" ms\n");
    }
    
    private void appendDiscordSection(StringBuilder sb, JDA jda)
    {
        appendSectionHeader(sb, "DISCORD");
        
        sb.append("  Bot ID: ").append(jda.getSelfUser().getId()).append("\n");
        sb.append("  Guilds: ").append(formatNumber(jda.getGuildCache().size())).append("\n");
        sb.append("  Users: ").append(formatNumber(jda.getUserCache().size())).append("\n");
        
        long voiceConnections = jda.getGuilds().stream()
                .filter(guild -> guild.getAudioManager().isConnected())
                .count();
        sb.append("  Voice Connections: ").append(voiceConnections).append("\n");
    }
    
    private void appendPlaybackSection(StringBuilder sb, JDA jda)
    {
        appendSectionHeader(sb, "PLAYBACK");
        
        int guildsPlaying = 0;
        int totalQueued = 0;
        
        for (var guild : jda.getGuilds())
        {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler != null)
            {
                if (handler.getPlayer().getPlayingTrack() != null)
                    guildsPlaying++;
                totalQueued += handler.getQueue().size();
            }
        }
        
        sb.append("  Guilds Playing: ").append(guildsPlaying).append("\n");
        sb.append("  Total Queued Tracks: ").append(formatNumber(totalQueued)).append("\n");
    }
    
    // ========== Formatting Helpers ==========
    
    private void appendSectionHeader(StringBuilder sb, String title)
    {
        int padding = (SECTION_WIDTH - title.length() - 2) / 2;
        String paddingStr = SECTION_SEPARATOR.repeat(padding);
        sb.append("\n").append(paddingStr).append(" ").append(title).append(" ").append(paddingStr);
        if ((SECTION_WIDTH - title.length()) % 2 != 0)
            sb.append(SECTION_SEPARATOR);
        sb.append("\n");
    }
    
    private String formatUptime(Instant startTime)
    {
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHours() % 24;
        long minutes = uptime.toMinutes() % 60;
        long seconds = uptime.getSeconds() % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        
        return sb.toString();
    }
    
    private String formatYouTubeOAuthStatus(BotConfig config)
    {
        if (!config.useYouTubeOauth())
            return "disabled";
        
        boolean tokenExists = Files.exists(OtherUtil.getPath("youtubetoken.txt"));
        return "enabled (" + (tokenExists ? "token present" : "no token") + ")";
    }
    
    private String formatNumber(long number)
    {
        return String.format("%,d", number);
    }
}
