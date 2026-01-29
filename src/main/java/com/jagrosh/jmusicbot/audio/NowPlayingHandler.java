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
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class NowPlayingHandler
{
    private record NPLocation(long channelId, long messageId) {}

    private final Bot bot;
    private final Map<Long, NPLocation> lastNP; // guild -> channel,message
    
    public NowPlayingHandler(Bot bot)
    {
        this.bot = bot;
        this.lastNP = new ConcurrentHashMap<>();
    }
    
    public void init()
    {
        if(!bot.getConfig().useNPImages())
            bot.getThreadpool().scheduleWithFixedDelay(this::updateAll, 0, 10, TimeUnit.SECONDS);
    }
    
    public void setLastNPMessage(Message m)
    {
        lastNP.put(m.getGuild().getIdLong(), new NPLocation(m.getChannel().getIdLong(), m.getIdLong()));
    }
    
    public void clearLastNPMessage(Guild guild)
    {
        lastNP.remove(guild.getIdLong());
    }

    // "event"-based methods
    public void onTrackUpdate(long guildId, AudioTrack track)
    {
        // For track updates (start/stop), we want to potentially send a NEW message
        if (track != null)
        {
            // Send new message
            sendNewMessage(guildId);
        }
        else
        {
            // Track stopped, just update UI (which will probably clear it or show "No music playing")
            updateSingleGuild(guildId);
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

    private void sendNewMessage(long guildId)
    {
        Guild guild = bot.getJDA().getGuildById(guildId);
        if(guild == null)
        {
            lastNP.remove(guildId);
            return;
        }

        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        MessageCreateData msg = handler.getNowPlaying(bot.getJDA());
        if (msg == null) return;

        NPLocation loc = lastNP.get(guildId);
        TextChannel tc;
        if(loc == null)
        {
            // If we don't have a last NP message, try to use the channel from the current track's metadata
            AudioTrack track = handler.getPlayer().getPlayingTrack();
            if (track != null && track.getUserData(RequestMetadata.class) != null)
            {
                long channelId = track.getUserData(RequestMetadata.class).channelId;
                tc = guild.getTextChannelById(channelId);
            }
            else
            {
                tc = null;
            }
        }
        else
        {
            tc = guild.getTextChannelById(loc.channelId());
        }

        if (tc == null) {
            lastNP.remove(guildId);
            return;
        }

        // Clean up previous message if it exists
        if (loc != null)
            tc.deleteMessageById(loc.messageId()).queue(s -> {}, t -> {});

        tc.sendMessage(msg).queue(
                m -> setLastNPMessage(m),
                throwable -> handleUpdateError(guildId, throwable)
        );
    }

    public void onMessageDelete(Guild guild, long messageId)
    {
        NPLocation loc = lastNP.get(guild.getIdLong());
        if(loc != null && loc.messageId() == messageId)
            lastNP.remove(guild.getIdLong());
    }

    private void updateAll()
    {
        lastNP.keySet().forEach(this::updateSingleGuild);
    }

    private void updateSingleGuild(long guildId)
    {
        Guild guild = bot.getJDA().getGuildById(guildId);
        if(guild == null)
        {
            lastNP.remove(guildId);
            return;
        }

        NPLocation loc = lastNP.get(guildId);
        if(loc == null)
            return;
        TextChannel tc = guild.getTextChannelById(loc.channelId());
        if (tc == null) {
            lastNP.remove(guildId);
            return;
        }
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        MessageCreateData msg = handler.getNowPlaying(bot.getJDA());
        if (msg == null) {
            msg = handler.getNoMusicPlaying(bot.getJDA());
            lastNP.remove(guildId);
        }
        tc.editMessageById(loc.messageId(), MessageEditData.fromCreateData(msg)).queue(
                success -> {},
                throwable -> handleUpdateError(guildId, throwable)
        );
    }

    private void handleUpdateError(long guildId, Throwable t)
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
                    lastNP.remove(guildId);
                    break;

                // Transient errors: Do nothing, let the next loop or event try again
                default:
                    break;
            }
        }
    }
}
