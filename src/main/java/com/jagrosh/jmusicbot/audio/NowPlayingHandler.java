/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.MessageFormatter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class NowPlayingHandler
{
    private record NPLocation(long channelId, long messageId) {}
    private static final class GuildState
    {
        private long version;
        private boolean reconciling;
        private boolean pending;
        private boolean forceNewMessage;
        private NPLocation location;
    }

    private final Bot bot;
    private final Map<Long, GuildState> guildStates; // guild -> now playing reconcile state
    private final Set<Long> missingCommandChannelAlertedGuilds;
    
    public NowPlayingHandler(Bot bot)
    {
        this.bot = bot;
        this.guildStates = new ConcurrentHashMap<>();
        this.missingCommandChannelAlertedGuilds = ConcurrentHashMap.newKeySet();
    }
    
    public void init()
    {
        // Schedule the 10-second update thread only when progress bar updates are enabled.
        if (bot.getConfig().showNpProgressBar())
            bot.getThreadpool().scheduleWithFixedDelay(this::updateAll, 0, 10, TimeUnit.SECONDS);
    }
    
    public void setLastNPMessage(Message m)
    {
        long guildId = m.getGuild().getIdLong();
        GuildState state = getOrCreateState(guildId);
        synchronized (state)
        {
            state.location = new NPLocation(m.getChannel().getIdLong(), m.getIdLong());
            state.version++;
        }
    }
    
    public void clearLastNPMessage(Guild guild)
    {
        long guildId = guild.getIdLong();
        GuildState state = guildStates.get(guildId);
        if (state == null)
            return;
        synchronized (state)
        {
            state.location = null;
            state.version++;
        }
    }

    // "event"-based methods
    public void onTrackUpdate(long guildId, AudioTrack track)
    {
        // Track start should force a fresh message; stop should reconcile existing location.
        if (track != null)
        {
            requestReconcile(guildId, "track-start", true);
        }
        else
        {
            requestReconcile(guildId, "track-stop", false);
        }

        // update bot status if applicable
        if(bot.getConfig().getSongInStatus())
        {
            if(track != null)
            {
                String title = FormatUtil.getTrackTitle(track);
                bot.getJDA().getPresence().setActivity(Activity.listening(title));
            }
            else
            {
                bot.resetGame();
            }
        }
    }

    public void requestReconcile(long guildId)
    {
        requestReconcile(guildId, "manual", false);
    }

    public void requestReconcile(long guildId, String reason)
    {
        requestReconcile(guildId, reason, false);
    }

    public void refreshNowPlaying(long guildId)
    {
        requestReconcile(guildId, "refresh", false);
    }

    private void requestReconcile(long guildId, String reason, boolean forceNewMessage)
    {
        GuildState state = getOrCreateState(guildId);
        long versionToRun;
        synchronized (state)
        {
            state.version++;
            if (forceNewMessage)
                state.forceNewMessage = true;
            if (state.reconciling)
            {
                state.pending = true;
                return;
            }
            state.reconciling = true;
            versionToRun = state.version;
        }
        reconcile(guildId, versionToRun);
    }

    private GuildState getOrCreateState(long guildId)
    {
        return guildStates.computeIfAbsent(guildId, __ -> new GuildState());
    }

    private void reconcile(long guildId, long reconcileVersion)
    {
        GuildState state = guildStates.get(guildId);
        if (state == null)
            return;

        Guild guild = bot.getJDA().getGuildById(guildId);
        if(guild == null)
        {
            synchronized (state)
            {
                state.location = null;
            }
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        if (handler == null)
        {
            synchronized (state)
            {
                state.location = null;
            }
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        AudioTrack currentTrack = handler.getPlayer().getPlayingTrack();
        MessageCreateData playingMsg = handler.getNowPlaying(bot.getJDA());
        if (currentTrack != null && playingMsg == null)
        {
            // Track is active but rich NP payload may not be ready yet; render fallback NP.
            playingMsg = MessageFormatter.buildNowPlayingMessage(bot, handler.getNowPlayingInfo(bot.getJDA()));
        }
        boolean isPlaying = playingMsg != null;
        MessageCreateData targetMsg = isPlaying ? playingMsg : handler.getNoMusicPlaying(bot.getJDA());

        NPLocation loc;
        boolean forceNewMessage;
        synchronized (state)
        {
            loc = state.location;
            forceNewMessage = state.forceNewMessage;
            state.forceNewMessage = false;
        }

        if (!isPlaying && loc == null)
        {
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        if (isPlaying && (forceNewMessage || loc == null))
        {
            MessageCreateData msg = targetMsg;
            if (msg == null)
            {
                msg = MessageFormatter.buildNowPlayingMessage(bot, handler.getNowPlayingInfo(bot.getJDA()));
            }
            sendPlayingMessage(guildId, guild, currentTrack, loc, msg, reconcileVersion);
            return;
        }
        if (loc == null)
        {
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        GuildMessageChannel tc = resolveMessageChannel(guild, loc.channelId());
        if (tc == null)
        {
            synchronized (state)
            {
                if (state.version == reconcileVersion)
                    state.location = null;
            }
            if (isPlaying)
            {
                sendPlayingMessage(guildId, guild, currentTrack, null, targetMsg, reconcileVersion);
                return;
            }
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        boolean clearOnSuccess = !isPlaying;
        tc.editMessageById(loc.messageId(), asEditData(targetMsg)).queue(
                success -> {
                    if (clearOnSuccess)
                    {
                        GuildState currentState = guildStates.get(guildId);
                        if (currentState != null)
                        {
                            synchronized (currentState)
                            {
                                if (currentState.version == reconcileVersion)
                                    currentState.location = null;
                            }
                        }
                    }
                    finishReconcile(guildId, reconcileVersion);
                },
                throwable -> {
                    handleUpdateError(guildId, reconcileVersion, throwable);
                    finishReconcile(guildId, reconcileVersion);
                }
        );
    }

    private void sendPlayingMessage(long guildId, Guild guild, AudioTrack currentTrack, NPLocation previousLocation,
                                    MessageCreateData msg, long reconcileVersion)
    {
        GuildMessageChannel tc;
        if(previousLocation == null)
        {
            if (currentTrack.getUserData(RequestMetadata.class) != null)
            {
                long channelId = currentTrack.getUserData(RequestMetadata.class).channelId;
                tc = resolveMessageChannel(guild, channelId);
            }
            else
            {
                tc = null;
            }
        }
        else
        {
            tc = resolveMessageChannel(guild, previousLocation.channelId());
        }

        if (tc == null)
        {
            tc = resolveFallbackChannel(guild);
        }
        if (tc == null)
        {
            GuildState state = guildStates.get(guildId);
            if (state != null)
            {
                synchronized (state)
                {
                    if (state.version == reconcileVersion)
                        state.location = null;
                }
            }
            finishReconcile(guildId, reconcileVersion);
            return;
        }

        if (previousLocation != null)
            tc.deleteMessageById(previousLocation.messageId()).queue(s -> {}, t -> {});

        tc.sendMessage(msg).queue(
                m -> {
                    GuildState state = guildStates.get(guildId);
                    if (state != null)
                    {
                        synchronized (state)
                        {
                            if (state.version == reconcileVersion || state.location == null)
                                state.location = new NPLocation(m.getChannel().getIdLong(), m.getIdLong());
                        }
                    }
                    finishReconcile(guildId, reconcileVersion);
                },
                throwable -> {
                    handleUpdateError(guildId, reconcileVersion, throwable);
                    finishReconcile(guildId, reconcileVersion);
                }
        );
    }

    private TextChannel resolveFallbackChannel(Guild guild)
    {
        TextChannel commandChannel = bot.getSettingsManager().getSettings(guild).getTextChannel(guild);
        if (commandChannel == null)
        {
            notifyOwnerMissingCommandChannel(guild, "is not set");
            return null;
        }

        if (!guild.getSelfMember().hasPermission(commandChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))
        {
            notifyOwnerMissingCommandChannel(guild, "is set but the bot cannot send messages there");
            return null;
        }

        missingCommandChannelAlertedGuilds.remove(guild.getIdLong());
        return commandChannel;
    }

    /**
     * Resolves any guild channel that can receive messages, including a voice
     * channel's built-in text chat.
     */
    private GuildMessageChannel resolveMessageChannel(Guild guild, long channelId)
    {
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
        if (channel != null)
            return channel;
        return guild.getTextChannelById(channelId);
    }

    private void notifyOwnerMissingCommandChannel(Guild guild, String reason)
    {
        long guildId = guild.getIdLong();
        if (!missingCommandChannelAlertedGuilds.add(guildId))
            return;

        long ownerId = bot.getConfig().getOwnerId();
        if (ownerId <= 0L)
            return;

        String msg = "NowPlaying fallback failed for guild **" + guild.getName() + "** (`" + guild.getId() + "`): "
                + "the command channel " + reason + ". Please set it with `/settc`.";


        var ownerLookup = bot.getJDA().retrieveUserById(ownerId);

        ownerLookup.queue(
                user -> user.openPrivateChannel().queue(
                        pc -> pc.sendMessage(msg).queue(),
                        throwable -> {}
                ),
                throwable -> {}
        );
    }

    public void onMessageDelete(Guild guild, long messageId)
    {
        GuildState state = guildStates.get(guild.getIdLong());
        if (state == null)
            return;
        NPLocation loc;
        synchronized (state)
        {
            loc = state.location;
        }
        if(loc != null && loc.messageId() == messageId)
        {
            synchronized (state)
            {
                state.location = null;
                state.version++;
            }
        }
    }

    private void updateAll()
    {
        guildStates.keySet().forEach(guildId -> requestReconcile(guildId, "periodic", false));
    }

    private void finishReconcile(long guildId, long completedVersion)
    {
        GuildState state = guildStates.get(guildId);
        if (state == null)
            return;
        Long rerunVersion = null;
        synchronized (state)
        {
            if (state.pending)
            {
                state.pending = false;
                rerunVersion = state.version;
            }
            else
            {
                state.reconciling = false;
            }
        }
        if (rerunVersion != null)
            reconcile(guildId, rerunVersion);
    }

    private void handleUpdateError(long guildId, long reconcileVersion, Throwable t)
    {
        if (t instanceof ErrorResponseException ex)
        {
            ErrorResponse response = ex.getErrorResponse();
            switch (response)
            {
                // Permanent errors: Remove the tracking
                case UNKNOWN_MESSAGE:
                case UNKNOWN_CHANNEL:
                case MISSING_ACCESS:
                case MISSING_PERMISSIONS:
                    GuildState state = guildStates.get(guildId);
                    if (state != null)
                    {
                        synchronized (state)
                        {
                            if (state.version == reconcileVersion)
                                state.location = null;
                        }
                    }
                    break;

                // Transient errors: Do nothing, let the next loop or event try again
                default:
                    break;
            }
        }
    }

    private static MessageEditData asEditData(MessageCreateData source)
    {
        return new MessageEditBuilder()
                .setContent(source.getContent() == null ? "" : source.getContent())
                .setEmbeds(source.getEmbeds())
                .setComponents(source.getComponents())
                .build();
    }
}
