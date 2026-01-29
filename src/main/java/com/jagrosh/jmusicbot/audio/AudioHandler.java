/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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

import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.MessageFormatter;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler 
{
    public final static String PLAY_EMOJI  = "\u25B6"; // ▶
    public final static String PAUSE_EMOJI = "\u23F8"; // ⏸
    public final static String STOP_EMOJI  = "\u23F9"; // ⏹

    private final static Logger LOGGER = LoggerFactory.getLogger(AudioHandler.class);

    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final PerformanceMetrics performanceMetrics;
    
    private AudioFrame lastFrame;
    private AbstractQueue<QueuedTrack> queue;

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player)
    {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.performanceMetrics = new PerformanceMetrics(guildId);

        this.setQueueType(manager.getBot().getSettingsManager().getSettings(guildId).getQueueType());
    }

    public void setQueueType(QueueType type)
    {
        queue = type.createInstance(queue);
    }

    public int addTrackToFront(QueuedTrack qtrack)
    {
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
        {
            queue.addAt(0, qtrack);
            return 0;
        }
    }
    
    public int addTrack(QueuedTrack qtrack)
    {
        if(audioPlayer.getPlayingTrack()==null)
        {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        }
        else
            return queue.add(qtrack);
    }
    
    public AbstractQueue<QueuedTrack> getQueue()
    {
        return queue;
    }
    
    public void stopAndClear()
    {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        //current = null;
    }

    public boolean isMusicPlaying(JDA jda)
    {
        // Check that the selfMember is connected to a channel where they can receive audio
        // Check that the audioPlayer has a playingTrack
        var isBotConnectedToVoice = jda.getGuildById(guildId).getSelfMember().getVoiceState().getChannel() != null;
        var isAudioPlaying = audioPlayer.getPlayingTrack() != null;
        return isBotConnectedToVoice && isAudioPlaying;
    }
    
    public Set<String> getVotes()
    {
        return votes;
    }
    
    public AudioPlayer getPlayer()
    {
        return audioPlayer;
    }
    
    public RequestMetadata getRequestMetadata()
    {
        if(audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }
    
    public boolean playFromDefault()
    {
        if(!defaultQueue.isEmpty())
        {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if(settings==null || settings.getDefaultPlaylist()==null)
            return false;
        
        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(settings.getDefaultPlaylist());
        if(pl==null || pl.getItems().isEmpty())
            return false;
        pl.loadTracks(manager, (at) -> 
        {
            if(audioPlayer.getPlayingTrack()==null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> 
        {
            if(pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }
    
    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) 
    {
        // Record track end for timeline
        String trackTitle = null;
        String trackUri = null;
        if (track != null && track.getInfo() != null) {
            trackTitle = track.getInfo().title;
            trackUri = track.getInfo().uri;
        }
        performanceMetrics.recordTrackEnd(trackTitle, trackUri);
        
        // Log track end with details for debugging
        if (endReason != AudioTrackEndReason.FINISHED) {
            LOGGER.debug("Track {} ended with reason: {} (Track: {})", 
                    track != null ? track.getIdentifier() : "null",
                    endReason.name(),
                    trackTitle != null ? trackTitle : "N/A");
        }
        
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        // if the track ended normally, and we're in repeat mode, re-add it to the queue
        if(endReason==AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF)
        {
            QueuedTrack clone = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
            if(repeatMode == RepeatMode.ALL)
                queue.add(clone);
            else
                queue.addAt(0, clone);
        }
        
        if(queue.isEmpty())
        {
            if(!playFromDefault())
            {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null);
                if(!manager.getBot().getConfig().getStay())
                    manager.getBot().closeAudioConnection(guildId);
                // unpause, in the case when the player was paused and the track has been skipped.
                // this is to prevent the player being paused next time it's being used.
                player.setPaused(false);
            }
        }
        else
        {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        // Record exception for timeline
        String trackTitle = null;
        String trackUri = null;
        if (track != null && track.getInfo() != null) {
            trackTitle = track.getInfo().title;
            trackUri = track.getInfo().uri;
        }
        performanceMetrics.recordTrackException(trackTitle, trackUri);
        
        // Build detailed error message with track information
        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("Track exception occurred:\n");
        errorDetails.append("  Track ID: ").append(track.getIdentifier()).append("\n");
        
        AudioTrackInfo info = track.getInfo();
        if (info != null) {
            errorDetails.append("  Title: ").append(info.title != null ? info.title : "N/A").append("\n");
            errorDetails.append("  URI: ").append(info.uri != null ? info.uri : "N/A").append("\n");
            errorDetails.append("  Author: ").append(info.author != null ? info.author : "N/A").append("\n");
            errorDetails.append("  Duration: ").append(info.length > 0 ? info.length + "ms" : "Unknown").append("\n");
            errorDetails.append("  Source: ").append(track.getSourceManager() != null ? track.getSourceManager().getSourceName() : "Unknown").append("\n");
        }
        
        errorDetails.append("  Exception Severity: ").append(exception.severity != null ? exception.severity.name() : "UNKNOWN").append("\n");
        errorDetails.append("  Exception Message: ").append(exception.getMessage() != null ? exception.getMessage() : "N/A").append("\n");
        
        // Log request metadata if available
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm != null && rm.user != null) {
            errorDetails.append("  Requested by: ").append(rm.user.username).append(" (ID: ").append(rm.user.id).append(")\n");
        }
        if (rm != null && rm.requestInfo != null) {
            errorDetails.append("  Original query: ").append(rm.requestInfo.query != null ? rm.requestInfo.query : "N/A").append("\n");
        }
        
        // Log root cause if available
        Throwable cause = exception.getCause();
        if (cause != null) {
            errorDetails.append("  Root Cause: ").append(cause.getClass().getSimpleName()).append(" - ").append(cause.getMessage()).append("\n");
            // Log specific error details for common issues
            if (cause instanceof IllegalStateException) {
                errorDetails.append("  IllegalStateException details: ").append(cause.getMessage()).append("\n");
            } else if (cause instanceof com.fasterxml.jackson.core.JsonParseException) {
                com.fasterxml.jackson.core.JsonParseException jsonEx = (com.fasterxml.jackson.core.JsonParseException) cause;
                errorDetails.append("  JSON Parse Error at line ").append(jsonEx.getLocation().getLineNr())
                           .append(", column ").append(jsonEx.getLocation().getColumnNr()).append("\n");
            }
        }
        
        // Special handling for YouTube OAuth errors
        if (exception.getMessage().equals("Sign in to confirm you're not a bot")
            || exception.getMessage().equals("Please sign in")
            || exception.getMessage().equals("This video requires login."))
        {
            LOGGER.error(
                    "Track {} has failed to play: {}. "
                            + "You will need to sign in to Google to play YouTube tracks. "
                            + "More info: https://jmusicbot.com/youtube-oauth2\n{}",
                    track.getIdentifier(),
                    exception.getMessage(),
                    errorDetails.toString()
            );
        }
        else {
            LOGGER.error("Track {} has failed to play\n{}", track.getIdentifier(), errorDetails.toString(), exception);
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) 
    {
        votes.clear();
        performanceMetrics.resetSession(); // Reset metrics for new track
        
        // Record track start for timeline and time-to-first-frame tracking
        String trackTitle = null;
        String trackUri = null;
        if (track != null && track.getInfo() != null) {
            trackTitle = track.getInfo().title;
            trackUri = track.getInfo().uri;
            
            LOGGER.debug("Starting track: {} (ID: {}, URI: {}, Source: {})",
                    trackTitle,
                    track.getIdentifier(),
                    trackUri,
                    track.getSourceManager() != null ? track.getSourceManager().getSourceName() : "Unknown");
        }
        performanceMetrics.recordTrackStart(trackTitle, trackUri);
        
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track);
    }
    
    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs)
    {
        // Record the stuck event in performance metrics
        String trackTitle = null;
        String trackUri = null;
        
        if (track != null && track.getInfo() != null) {
            trackTitle = track.getInfo().title;
            trackUri = track.getInfo().uri;
        }
        
        performanceMetrics.recordStuckEvent(thresholdMs, trackTitle, trackUri);
        
        // Build detailed log message
        StringBuilder details = new StringBuilder();
        details.append("Track stuck detected (decoder/stream stall):\n");
        details.append("  Threshold exceeded: ").append(thresholdMs).append("ms\n");
        
        if (track != null) {
            details.append("  Track ID: ").append(track.getIdentifier()).append("\n");
            AudioTrackInfo info = track.getInfo();
            if (info != null) {
                details.append("  Title: ").append(info.title != null ? info.title : "N/A").append("\n");
                details.append("  URI: ").append(info.uri != null ? info.uri : "N/A").append("\n");
                details.append("  Position: ").append(track.getPosition()).append("ms / ")
                       .append(info.length).append("ms\n");
                details.append("  Source: ").append(track.getSourceManager() != null 
                    ? track.getSourceManager().getSourceName() : "Unknown").append("\n");
            }
        }
        
        // Log request metadata if available
        RequestMetadata rm = track != null ? track.getUserData(RequestMetadata.class) : null;
        if (rm != null && rm.user != null) {
            details.append("  Requested by: ").append(rm.user.username)
                   .append(" (ID: ").append(rm.user.id).append(")\n");
        }
        
        LOGGER.warn("Track {} is stuck after {}ms\n{}", 
            track != null ? track.getIdentifier() : "null", 
            thresholdMs, 
            details.toString());
    }

    //
    public NowPlayingInfo getNowPlayingInfo(JDA jda)
    {
        return new NowPlayingInfo(
            audioPlayer.getPlayingTrack(),
            jda.getGuildById(guildId),
            audioPlayer.isPaused(),
            audioPlayer.getVolume()
        );
    }

    // Formatting
    public MessageCreateData getNowPlaying(JDA jda)
    {
        if(isMusicPlaying(jda))
            return MessageFormatter.buildNowPlayingMessage(manager.getBot(), getNowPlayingInfo(jda));
        return null;
    }

    public MessageCreateData getNoMusicPlaying(JDA jda)
    {
        return MessageFormatter.buildNoMusicPlayingMessage(manager.getBot(), getNowPlayingInfo(jda));
    }

    public String getStatusEmoji()
    {
        return audioPlayer.isPaused() ? PAUSE_EMOJI : PLAY_EMOJI;
    }
    
    // Audio Send Handler methods
    /*@Override
    public boolean canProvide() 
    {
        if (lastFrame == null)
            lastFrame = audioPlayer.provide();

        return lastFrame != null;
    }

    @Override
    public byte[] provide20MsAudio() 
    {
        if (lastFrame == null) 
            lastFrame = audioPlayer.provide();

        byte[] data = lastFrame != null ? lastFrame.getData() : null;
        lastFrame = null;

        return data;
    }*/
    
    @Override
    public boolean canProvide() 
    {
        long startNanos = System.nanoTime();
        lastFrame = audioPlayer.provide();
        long latencyNanos = System.nanoTime() - startNanos;
        
        boolean frameAvailable = lastFrame != null;
        performanceMetrics.recordFrame(frameAvailable, latencyNanos);
        
        return frameAvailable;
    }
    
    /**
     * Gets the performance metrics for this audio handler.
     *
     * @return the performance metrics instance
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }

    @Override
    public ByteBuffer provide20MsAudio() 
    {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() 
    {
        return true;
    }
}
