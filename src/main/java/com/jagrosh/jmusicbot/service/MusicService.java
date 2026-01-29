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
package com.jagrosh.jmusicbot.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unified service for all music operations including player control and queue management.
 * This service encapsulates all interactions with AudioHandler.
 */
public class MusicService
{
    private static final Logger LOG = LoggerFactory.getLogger(MusicService.class);

    private final Bot bot;

    public MusicService(Bot bot)
    {
        this.bot = bot;
        LOG.info("MusicService initialized");
    }

    // ========== Internal Helpers ==========

    /**
     * Gets the AudioHandler for a guild.
     *
     * @param guild The guild
     * @return The AudioHandler, or null if none exists
     */
    private AudioHandler getHandler(Guild guild)
    {
        return (AudioHandler) guild.getAudioManager().getSendingHandler();
    }

    /**
     * Gets the Settings for a guild.
     *
     * @param guild The guild
     * @return The guild's Settings
     */
    private Settings getSettings(Guild guild)
    {
        return bot.getSettingsManager().getSettings(guild);
    }

    /**
     * Checks if the member has DJ permission and sends an error if not.
     *
     * @param guild  The guild
     * @param member The member to check
     * @param output The output adapter for error messages
     * @param action Description of the action being attempted (for error message)
     * @return true if the member has DJ permission, false otherwise
     */
    private boolean requireDJPermission(Guild guild, Member member, OutputAdapter output, String action)
    {
        if (!DJCommand.checkDJPermission(bot, guild, member))
        {
            output.replyError("You need to be a DJ to " + action + "!");
            return false;
        }
        return true;
    }

    /**
     * Functional interface for track adding strategies.
     */
    @FunctionalInterface
    private interface TrackAdder
    {
        int add(AudioHandler handler, QueuedTrack track);
    }

    // ========== Shared Track Utilities ==========

    /**
     * Checks if a track exceeds the maximum allowed duration.
     *
     * @param track The track to check
     * @return true if the track is too long
     */
    public boolean isTooLong(AudioTrack track)
    {
        return bot.getConfig().isTooLong(track);
    }

    /**
     * Formats an error message for a track that is too long.
     *
     * @param track The track that is too long
     * @return Formatted error message
     */
    public String formatTooLongError(AudioTrack track)
    {
        String title = FormatUtil.getTrackTitle(track);
        return "This track (**" + title + "**) is longer than the allowed maximum: `"
                + TimeUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`";
    }

    /**
     * Formats a success message for a track that was added to the queue.
     *
     * @param title    The track title
     * @param duration The track duration in milliseconds
     * @param position The queue position (0 = now playing, >0 = queue position)
     * @return Formatted success message
     */
    public String formatTrackAddedMessage(String title, long duration, int position)
    {
        return "Added **" + FormatUtil.filter(title) + "** (`" + TimeUtil.formatTime(duration) + "`) "
                + (position == 0 ? "to begin playing" : " to the queue at position " + position);
    }

    /**
     * Internal helper that handles common track-add logic.
     *
     * @param guild       The guild
     * @param member      The member adding the track
     * @param track       The track to add
     * @param queryArgs   The original query/args used to find this track
     * @param channel     The text channel for request metadata
     * @param adder       The strategy for adding the track to the queue
     * @param reason      The reason to log (e.g., "added to the queue")
     * @param logLocation Description for logging (e.g., "queue" or "front of queue")
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    private TrackAddResult addTrackInternal(Guild guild, Member member, AudioTrack track,
                                            String queryArgs, TextChannel channel,
                                            TrackAdder adder, String reason, String logLocation)
    {
        LOG.debug("Adding track to {}: guild={}, user={}, track={}",
                logLocation, guild.getId(), member.getUser().getName(), track.getInfo().title);

        if (isTooLong(track))
        {
            LOG.warn("Track rejected (too long): {} - duration: {} > max: {}",
                    track.getInfo().title, TimeUtil.formatTime(track.getDuration()), bot.getConfig().getMaxTime());
            return null;
        }

        AudioHandler handler = getHandler(guild);
        handler.setLastReason(member.getUser().getName() + " " + reason);
        QueuedTrack queuedTrack = new QueuedTrack(track,
                new RequestMetadata(member.getUser(),
                        new RequestMetadata.RequestInfo(queryArgs, track.getInfo().uri),
                        channel.getIdLong()));
        int position = adder.add(handler, queuedTrack) + 1;

        String title = FormatUtil.getTrackTitle(track);
        String message = formatTrackAddedMessage(title, track.getDuration(), position);

        LOG.info("Track added to {}: guild={}, user={}, track=\"{}\", position={}",
                logLocation, guild.getId(), member.getUser().getName(), title, position);

        return new TrackAddResult(position, message, title);
    }

    /**
     * Adds a track to the queue and returns the result.
     *
     * @param guild     The guild
     * @param member    The member adding the track
     * @param track     The track to add
     * @param queryArgs The original query/args used to find this track
     * @param channel   The text channel for request metadata
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    public TrackAddResult addTrackToQueue(Guild guild, Member member, AudioTrack track,
                                          String queryArgs, TextChannel channel)
    {
        return addTrackInternal(guild, member, track, queryArgs, channel,
                AudioHandler::addTrack, "added to the queue.", "queue");
    }

    /**
     * Adds a track to the front of the queue and returns the result.
     *
     * @param guild     The guild
     * @param member    The member adding the track
     * @param track     The track to add
     * @param queryArgs The original query/args used to find this track
     * @param channel   The text channel for request metadata
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    public TrackAddResult addTrackToFront(Guild guild, Member member, AudioTrack track,
                                          String queryArgs, TextChannel channel)
    {
        return addTrackInternal(guild, member, track, queryArgs, channel,
                AudioHandler::addTrackToFront, "added to the front of the queue.", "front of queue");
    }

    // ========== Player Operations ==========

    public void playNext(Guild guild, Member member, String args, TextChannel channel, OutputAdapter output)
    {
        LOG.debug("PlayNext requested: guild={}, user={}, query={}",
                guild.getId(), member.getUser().getName(), args);

        if (args == null || args.isEmpty())
        {
            LOG.debug("PlayNext rejected: empty query");
            output.replyWarning("Please include a song title or URL!");
            return;
        }

        if (args.startsWith("<") && args.endsWith(">"))
            args = args.substring(1, args.length() - 1);

        LOG.info("Loading track for playNext: guild={}, user={}, query=\"{}\"",
                guild.getId(), member.getUser().getName(), args);

        bot.getPlayerManager().loadItemOrdered(guild, args,
                new AudioLoadResultHandlers.PlayNextResultHandler(this, bot, output, guild, member, args, false, channel));
    }

    public void play(Guild guild, Member member, String args, TextChannel channel, OutputAdapter output)
    {
        LOG.debug("Play requested: guild={}, user={}, args={}",
                guild.getId(), member.getUser().getName(), args);

        if (args != null && args.startsWith("\"") && args.endsWith("\""))
            args = args.substring(1, args.length() - 1);

        if (args == null || args.isEmpty())
        {
            AudioHandler handler = getHandler(guild);
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused())
            {
                if (DJCommand.checkDJPermission(bot, guild, member))
                {
                    handler.getPlayer().setPaused(false);
                    LOG.info("Playback resumed: guild={}, user={}, track=\"{}\"",
                            guild.getId(), member.getUser().getName(), handler.getPlayer().getPlayingTrack().getInfo().title);
                    output.replySuccess("Resumed **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**.");
                }
                else
                {
                    LOG.debug("Resume rejected: user lacks DJ permission");
                    output.replyError("Only DJs can unpause the player!");
                }
                return;
            }
            output.onShowHelp();
            return;
        }

        LOG.info("Loading track: guild={}, user={}, query=\"{}\"",
                guild.getId(), member.getUser().getName(), args);

        bot.getPlayerManager().loadItemOrdered(guild, args,
                new AudioLoadResultHandlers.PlayResultHandler(this, bot, output, guild, member, args, false, channel));
    }

    public void previous(Guild guild, Member member, OutputAdapter output)
    {
        LOG.debug("Previous track requested: guild={}, user={}",
                guild.getId(), member.getUser().getName());

        AudioHandler handler = getHandler(guild);
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        if (!isDJ && handler.getRequestMetadata().getOwner() != member.getIdLong())
        {
            LOG.debug("Previous rejected: user lacks permission");
            output.replyError("You need to be a DJ or the requester to go back!");
            return;
        }
        AudioTrack playing = handler.getPlayer().getPlayingTrack();

        if (playing != null && playing.getPosition() > 5000)
        {
            playing.setPosition(0);
            LOG.info("Track restarted: guild={}, track=\"{}\"", guild.getId(), playing.getInfo().title);
            output.replySuccess("Restarted **" + playing.getInfo().title + "**");
            return;
        }

        if (handler.getQueue().getHistory().isEmpty())
        {
            LOG.debug("Previous rejected: no history available");
            output.replyError("There are no previous tracks!");
            return;
        }

        AudioTrack currentlyPlaying = handler.getPlayer().getPlayingTrack();
        QueuedTrack currentQueued = currentlyPlaying != null
                ? new QueuedTrack(currentlyPlaying.makeClone(), handler.getRequestMetadata())
                : null;

        QueuedTrack previous = handler.getQueue().rewind(currentQueued);
        if (previous != null)
        {
            handler.getPlayer().playTrack(previous.getTrack());
            LOG.info("Went to previous track: guild={}, track=\"{}\"",
                    guild.getId(), previous.getTrack().getInfo().title);
            output.replySuccess("Went back to **" + previous.getTrack().getInfo().title + "**");
        }
        else
        {
            LOG.debug("Previous failed: no previous tracks in history");
            output.replyError("There are no previous tracks!");
        }
    }

    public void shuffle(Guild guild, Member member, int startIndex, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "use this button"))
            return;

        AudioHandler handler = getHandler(guild);
        int s = handler.getQueue().shuffle(startIndex);
        output.replySuccess("Shuffled " + s + " tracks!");
    }

    /**
     * Shuffles only the tracks added by a specific user.
     *
     * @param guild  The guild
     * @param userId The user ID whose tracks to shuffle
     * @return The number of tracks shuffled
     */
    public int shuffleUserTracks(Guild guild, long userId)
    {
        LOG.debug("Shuffling user tracks: guild={}, userId={}", guild.getId(), userId);

        AudioHandler handler = getHandler(guild);
        int shuffled = handler.getQueue().shuffle(userId);

        LOG.info("User tracks shuffled: guild={}, userId={}, count={}", guild.getId(), userId, shuffled);

        return shuffled;
    }

    public void cycleRepeatMode(Guild guild, Member member, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "use this button"))
            return;

        AudioHandler handler = getHandler(guild);
        RepeatMode mode = getSettings(guild).getRepeatMode();
        RepeatMode nextMode;
        switch (mode) {
            case OFF:
                nextMode = RepeatMode.ALL;
                break;
            case ALL:
                nextMode = RepeatMode.SINGLE;
                break;
            case SINGLE:
            default:
                nextMode = RepeatMode.OFF;
                break;
        }
        getSettings(guild).setRepeatMode(nextMode);
        output.editNowPlaying(handler);
    }

    /**
     * Gets the current repeat mode for a guild.
     *
     * @param guild The guild
     * @return The current RepeatMode
     */
    public RepeatMode getRepeatMode(Guild guild)
    {
        return getSettings(guild).getRepeatMode();
    }

    /**
     * Sets the repeat mode for a guild.
     *
     * @param guild The guild
     * @param mode  The repeat mode to set
     */
    public void setRepeatMode(Guild guild, RepeatMode mode)
    {
        LOG.info("Repeat mode changed: guild={}, mode={}", guild.getId(), mode.getUserFriendlyName());
        getSettings(guild).setRepeatMode(mode);
    }

    public void adjustVolume(Guild guild, Member member, int change, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "use this button"))
            return;

        AudioHandler handler = getHandler(guild);
        int newVol = handler.getPlayer().getVolume() + change;
        newVol = Math.max(0, Math.min(150, newVol));
        handler.getPlayer().setVolume(newVol);
        getSettings(guild).setVolume(newVol);
        output.editNowPlaying(handler);
    }

    /**
     * Gets the current volume for a guild.
     *
     * @param guild The guild
     * @return The current volume (0-150)
     */
    public int getVolume(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler.getPlayer().getVolume();
    }

    /**
     * Sets the volume to an absolute value.
     *
     * @param guild  The guild
     * @param volume The new volume (0-150)
     * @return VolumeResult containing the old and new volume, or null if invalid
     */
    public VolumeResult setVolume(Guild guild, int volume)
    {
        LOG.debug("Volume change requested: guild={}, volume={}", guild.getId(), volume);

        if (volume < 0 || volume > 150)
        {
            LOG.warn("Volume change rejected: invalid value {} (must be 0-150)", volume);
            return null;
        }

        AudioHandler handler = getHandler(guild);
        int oldVolume = handler.getPlayer().getVolume();
        handler.getPlayer().setVolume(volume);
        getSettings(guild).setVolume(volume);

        LOG.info("Volume changed: guild={}, oldVolume={}, newVolume={}", guild.getId(), oldVolume, volume);

        return new VolumeResult(oldVolume, volume);
    }

    /**
     * Result of a volume change operation.
     */
    public static class VolumeResult
    {
        public final int oldVolume;
        public final int newVolume;

        public VolumeResult(int oldVolume, int newVolume)
        {
            this.oldVolume = oldVolume;
            this.newVolume = newVolume;
        }
    }

    public void stop(Guild guild, Member member, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "use this button"))
            return;

        AudioHandler handler = getHandler(guild);
        handler.stopAndClear();
        guild.getAudioManager().closeAudioConnection();
        output.editNoMusic(handler);
    }

    /**
     * Stops playback and clears the queue (simple version without permission check).
     * Use this when DJ permission is already verified by the caller.
     *
     * @param guild The guild
     */
    public void stopAndClear(Guild guild)
    {
        LOG.info("Stopping playback and clearing queue: guild={}", guild.getId());

        AudioHandler handler = getHandler(guild);
        handler.stopAndClear();
        guild.getAudioManager().closeAudioConnection();

        LOG.debug("Audio connection closed: guild={}", guild.getId());
    }

    public void pause(Guild guild, Member member, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "use this button"))
            return;

        AudioHandler handler = getHandler(guild);
        handler.getPlayer().setPaused(!handler.getPlayer().isPaused());
        output.editNowPlaying(handler);
    }

    /**
     * Checks if the player is currently paused.
     *
     * @param guild The guild
     * @return true if paused, false otherwise
     */
    public boolean isPaused(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler.getPlayer().isPaused();
    }

    /**
     * Sets the paused state of the player.
     *
     * @param guild  The guild
     * @param paused true to pause, false to resume
     * @return The title of the currently playing track, or null if nothing is playing
     */
    public String setPaused(Guild guild, boolean paused)
    {
        AudioHandler handler = getHandler(guild);
        handler.getPlayer().setPaused(paused);
        AudioTrack track = handler.getPlayer().getPlayingTrack();
        String trackTitle = track != null ? track.getInfo().title : null;

        LOG.info("Player {} : guild={}, track=\"{}\"",
                paused ? "paused" : "resumed", guild.getId(), trackTitle);

        return trackTitle;
    }

    public void skip(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);

        RequestMetadata skipRm = handler.getRequestMetadata();
        if (!isDJ && skipRm.getOwner() != member.getIdLong())
        {
            output.replyError("You need to be a DJ or the requester to skip!");
            return;
        }
        if (getSettings(guild).getRepeatMode() == RepeatMode.ALL)
        {
            var track = handler.getPlayer().getPlayingTrack();
            if (track != null)
                handler.addTrack(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));
        }
        handler.setLastReason(member.getUser().getName() + " skipped forward.");
        handler.getPlayer().stopTrack();
        output.replySuccess("Skipped!");
    }

    /**
     * Force skips the currently playing track (no permission check).
     * Use this when DJ permission is already verified by the caller.
     *
     * @param guild The guild
     * @return ForceSkipResult containing track info and requester, or null if nothing playing
     */
    public ForceSkipResult forceSkip(Guild guild)
    {
        LOG.debug("Force skip requested: guild={}", guild.getId());

        AudioHandler handler = getHandler(guild);
        AudioTrack track = handler.getPlayer().getPlayingTrack();
        if (track == null)
        {
            LOG.debug("Force skip: nothing playing in guild={}", guild.getId());
            return null;
        }

        RequestMetadata rm = handler.getRequestMetadata();
        String trackTitle = track.getInfo().title;
        String requesterInfo = rm.getOwner() == 0L ? "(autoplay)" : "(requested by **" + FormatUtil.formatUsername(rm.user) + "**)";

        handler.getPlayer().stopTrack();

        LOG.info("Track force-skipped: guild={}, track=\"{}\"", guild.getId(), trackTitle);

        return new ForceSkipResult(trackTitle, requesterInfo);
    }

    /**
     * Result of a force skip operation.
     */
    public static class ForceSkipResult
    {
        public final String trackTitle;
        public final String requesterInfo;

        public ForceSkipResult(String trackTitle, String requesterInfo)
        {
            this.trackTitle = trackTitle;
            this.requesterInfo = requesterInfo;
        }
    }

    public void skipWithVote(Guild guild, Member member, int listeners, OutputAdapter output)
    {
        LOG.debug("Skip vote requested: guild={}, user={}, listeners={}",
                guild.getId(), member.getUser().getName(), listeners);

        AudioHandler handler = getHandler(guild);
        RequestMetadata rm = handler.getRequestMetadata();

        double skipRatio = getSettings(guild).getSkipRatio();
        if (skipRatio == -1)
        {
            skipRatio = bot.getConfig().getSkipRatio();
        }

        if (member.getIdLong() == rm.getOwner() || skipRatio == 0)
        {
            String trackTitle = handler.getPlayer().getPlayingTrack().getInfo().title;
            handler.getPlayer().stopTrack();
            LOG.info("Track skipped by owner/instant skip: guild={}, user={}, track=\"{}\"",
                    guild.getId(), member.getUser().getName(), trackTitle);
            output.replySuccess("Skipped **" + trackTitle + "**");
            return;
        }

        String oderId = member.getId();
        boolean alreadyVoted = handler.getVotes().contains(oderId);

        if (!alreadyVoted)
        {
            handler.getVotes().add(oderId);
        }

        int skippers = (int) handler.getVotes().stream()
                .filter(id -> guild.getMemberById(id) != null &&
                        guild.getMemberById(id).getVoiceState() != null &&
                        guild.getMemberById(id).getVoiceState().getChannel() != null)
                .count();
        int required = (int) Math.ceil(listeners * skipRatio);

        String voteStatus = "[" + skippers + " votes, " + required + "/" + listeners + " needed]";

        if (alreadyVoted)
        {
            LOG.debug("Duplicate skip vote: guild={}, user={}", guild.getId(), member.getUser().getName());
            output.replyWarning("You already voted to skip this song `" + voteStatus + "`");
        }
        else if (skippers >= required)
        {
            String trackTitle = handler.getPlayer().getPlayingTrack().getInfo().title;
            String requester = rm.getOwner() == 0L ? "(autoplay)" : "(requested by **" + FormatUtil.formatUsername(rm.user) + "**)";
            handler.getPlayer().stopTrack();
            LOG.info("Track skipped by vote: guild={}, track=\"{}\", votes={}/{}",
                    guild.getId(), trackTitle, skippers, required);
            output.replySuccess("You voted to skip the song `" + voteStatus + "`\nSkipped **" + trackTitle + "** " + requester);
        }
        else
        {
            LOG.debug("Skip vote registered: guild={}, user={}, votes={}/{}",
                    guild.getId(), member.getUser().getName(), skippers, required);
            output.replySuccess("You voted to skip the song `" + voteStatus + "`");
        }
    }

    public void seek(Guild guild, Member member, String timeString, OutputAdapter output)
    {
        LOG.debug("Seek requested: guild={}, user={}, time={}",
                guild.getId(), member.getUser().getName(), timeString);

        AudioHandler handler = getHandler(guild);
        AudioTrack playingTrack = handler.getPlayer().getPlayingTrack();

        if (playingTrack == null)
        {
            LOG.debug("Seek rejected: no track playing in guild={}", guild.getId());
            output.replyError("There is no track currently playing!");
            return;
        }

        if (!playingTrack.isSeekable())
        {
            LOG.debug("Seek rejected: track not seekable - track=\"{}\"", playingTrack.getInfo().title);
            output.replyError("This track is not seekable.");
            return;
        }

        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        RequestMetadata rm = playingTrack.getUserData(RequestMetadata.class);
        if (!isDJ && (rm == null || rm.getOwner() != member.getIdLong()))
        {
            LOG.debug("Seek rejected: user lacks permission - user={}, track=\"{}\"",
                    member.getUser().getName(), playingTrack.getInfo().title);
            output.replyError("You cannot seek **" + playingTrack.getInfo().title + "** because you didn't add it!");
            return;
        }

        TimeUtil.SeekTime seekTime = TimeUtil.parseTime(timeString);
        if (seekTime == null)
        {
            LOG.debug("Seek rejected: invalid time format - input=\"{}\"", timeString);
            output.replyError("Invalid seek! Expected format: [+ | -] <HH:MM:SS | MM:SS | SS> or <0h0m0s>\nExamples: `1:02:23` `+1:10` `-90`, `1h10m`, `+90s`");
            return;
        }

        long currentPosition = playingTrack.getPosition();
        long trackDuration = playingTrack.getDuration();
        long seekMilliseconds = seekTime.relative ? currentPosition + seekTime.milliseconds : seekTime.milliseconds;

        if (seekMilliseconds < 0)
        {
            seekMilliseconds = 0;
        }
        if (seekMilliseconds > trackDuration)
        {
            LOG.debug("Seek rejected: position {} exceeds track duration {}",
                    TimeUtil.formatTime(seekMilliseconds), TimeUtil.formatTime(trackDuration));
            output.replyError("Cannot seek to `" + TimeUtil.formatTime(seekMilliseconds) + "` because the current track is `" + TimeUtil.formatTime(trackDuration) + "` long!");
            return;
        }

        try
        {
            playingTrack.setPosition(seekMilliseconds);
            LOG.info("Seek successful: guild={}, user={}, track=\"{}\", position={}",
                    guild.getId(), member.getUser().getName(), playingTrack.getInfo().title,
                    TimeUtil.formatTime(playingTrack.getPosition()));
            output.replySuccess("Successfully seeked to `" + TimeUtil.formatTime(playingTrack.getPosition()) + "/" + TimeUtil.formatTime(trackDuration) + "`!");
        }
        catch (Exception e)
        {
            LOG.error("Seek failed: guild={}, track=\"{}\", error={}",
                    guild.getId(), playingTrack.getInfo().title, e.getMessage(), e);
            output.replyError("An error occurred while trying to seek: " + e.getMessage());
        }
    }

    // ========== Queue Operations ==========

    public void removeTrack(Guild guild, Member member, int position, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);

        if (!requireNonEmptyQueue(handler, output))
            return;

        if (!validateQueuePosition(handler, position, output))
            return;

        boolean isDJ = DJCommand.checkDJPermission(bot, guild, member);
        QueuedTrack qt = handler.getQueue().get(position - 1);

        if (qt.getIdentifier() == member.getIdLong())
        {
            handler.getQueue().remove(position - 1);
            output.replySuccess("Removed **" + qt.getTrack().getInfo().title + "** from the queue");
        }
        else if (isDJ)
        {
            handler.getQueue().remove(position - 1);
            User u = null;
            try
            {
                u = guild.getJDA().getUserById(qt.getIdentifier());
            }
            catch (Exception ignored) {}

            output.replySuccess("Removed **" + qt.getTrack().getInfo().title
                    + "** from the queue (requested by " + (u == null ? "someone" : "**" + u.getName() + "**") + ")");
        }
        else
        {
            output.replyError("You cannot remove **" + qt.getTrack().getInfo().title + "** because you didn't add it!");
        }
    }

    public void removeAllTracks(Guild guild, Member member, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);

        if (!requireNonEmptyQueue(handler, output))
            return;

        int count = handler.getQueue().removeAll(member.getIdLong());
        if (count == 0)
        {
            output.replyWarning("You don't have any songs in the queue!");
        }
        else
        {
            output.replySuccess("Successfully removed your " + count + " entries.");
        }
    }

    /**
     * Removes all tracks from a specific user (for DJ force remove).
     *
     * @param guild  The guild
     * @param userId The user ID whose tracks to remove
     * @return The number of tracks removed
     */
    public int removeAllTracksByUser(Guild guild, long userId)
    {
        LOG.debug("Removing all tracks by user: guild={}, userId={}", guild.getId(), userId);

        AudioHandler handler = getHandler(guild);
        int count = handler.getQueue().removeAll(userId);

        LOG.info("Removed {} tracks by user: guild={}, userId={}", count, guild.getId(), userId);

        return count;
    }

    /**
     * Checks if the queue is empty.
     *
     * @param guild The guild
     * @return true if the queue is empty
     */
    public boolean isQueueEmpty(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler == null || handler.getQueue().isEmpty();
    }

    public void moveTrack(Guild guild, Member member, int from, int to, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "move tracks"))
            return;

        if (from == to)
        {
            output.replyError("Can't move a track to the same position.");
            return;
        }

        AudioHandler handler = getHandler(guild);
        AbstractQueue<QueuedTrack> queue = handler.getQueue();

        if (isInvalidPosition(queue, from))
        {
            output.replyError("`" + from + "` is not a valid position in the queue!");
            return;
        }
        if (isInvalidPosition(queue, to))
        {
            output.replyError("`" + to + "` is not a valid position in the queue!");
            return;
        }

        QueuedTrack track = queue.moveItem(from - 1, to - 1);
        String trackTitle = track.getTrack().getInfo().title;
        output.replySuccess("Moved **" + trackTitle + "** from position `" + from + "` to `" + to + "`.");
    }

    /**
     * Moves a track from one position to another (no permission check).
     * Use this when DJ permission is already verified by the caller.
     *
     * @param guild The guild
     * @param from  The 1-based source position
     * @param to    The 1-based destination position
     * @return The title of the moved track, or null if invalid positions
     */
    public String moveTrackPosition(Guild guild, int from, int to)
    {
        LOG.debug("Moving track: guild={}, from={}, to={}", guild.getId(), from, to);

        AudioHandler handler = getHandler(guild);
        AbstractQueue<QueuedTrack> queue = handler.getQueue();

        if (isInvalidPosition(queue, from) || isInvalidPosition(queue, to))
        {
            LOG.debug("Move rejected: invalid position(s) - from={}, to={}, queueSize={}",
                    from, to, queue.size());
            return null;
        }

        QueuedTrack track = queue.moveItem(from - 1, to - 1);
        String title = track.getTrack().getInfo().title;

        LOG.info("Track moved: guild={}, track=\"{}\", from={}, to={}",
                guild.getId(), title, from, to);

        return title;
    }

    /**
     * Checks if a position is valid in the queue.
     *
     * @param guild    The guild
     * @param position The 1-based position to check
     * @return true if the position is valid
     */
    public boolean isValidQueuePosition(Guild guild, int position)
    {
        AudioHandler handler = getHandler(guild);
        return handler != null && position >= 1 && position <= handler.getQueue().size();
    }

    public void skipTo(Guild guild, Member member, int position, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "skip to a specific position"))
            return;

        AudioHandler handler = getHandler(guild);

        if (!validateQueuePosition(handler, position, output))
            return;

        handler.getQueue().skip(position - 1);
        String trackTitle = handler.getQueue().get(0).getTrack().getInfo().title;
        handler.getPlayer().stopTrack();
        output.replySuccess("Skipped to **" + trackTitle + "**");
    }

    /**
     * Skips to a specific position in the queue (no permission check).
     * Use this when DJ permission is already verified by the caller.
     *
     * @param guild    The guild
     * @param position The 1-based position to skip to
     * @return The title of the track skipped to, or null if invalid position
     */
    public String skipToPosition(Guild guild, int position)
    {
        LOG.debug("Skip to position: guild={}, position={}", guild.getId(), position);

        AudioHandler handler = getHandler(guild);
        int queueSize = handler.getQueue().size();

        if (position < 1 || position > queueSize)
        {
            LOG.debug("Skip to position rejected: invalid position {} (queueSize={})",
                    position, queueSize);
            return null;
        }

        handler.getQueue().skip(position - 1);
        String trackTitle = handler.getQueue().get(0).getTrack().getInfo().title;
        handler.getPlayer().stopTrack();

        LOG.info("Skipped to position: guild={}, position={}, track=\"{}\"",
                guild.getId(), position, trackTitle);

        return trackTitle;
    }

    /**
     * Gets the current queue size.
     *
     * @param guild The guild
     * @return The number of tracks in the queue
     */
    public int getQueueSize(Guild guild)
    {
        AudioHandler handler = getHandler(guild);
        return handler != null ? handler.getQueue().size() : 0;
    }

    // ========== Now Playing ==========

    /**
     * Gets the now playing message for a guild.
     *
     * @param guild The guild
     * @param jda   The JDA instance
     * @return NowPlayingInfo containing the message data, or null if no handler
     */
    public NowPlayingInfo getNowPlayingInfo(Guild guild, JDA jda)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            return null;
        }

        return new NowPlayingInfo(
                handler.getNowPlaying(jda),
                handler.getNoMusicPlaying(jda),
                handler.getPlayer().getPlayingTrack() != null
        );
    }

    /**
     * Data class containing now playing information.
     */
    public static class NowPlayingInfo
    {
        public final net.dv8tion.jda.api.utils.messages.MessageCreateData nowPlayingMessage;
        public final net.dv8tion.jda.api.utils.messages.MessageCreateData noMusicMessage;
        public final boolean isPlaying;

        public NowPlayingInfo(net.dv8tion.jda.api.utils.messages.MessageCreateData nowPlayingMessage,
                              net.dv8tion.jda.api.utils.messages.MessageCreateData noMusicMessage,
                              boolean isPlaying)
        {
            this.nowPlayingMessage = nowPlayingMessage;
            this.noMusicMessage = noMusicMessage;
            this.isPlaying = isPlaying;
        }
    }

    // ========== Queue Info ==========

    public QueueInfo getQueueInfo(Guild guild, JDA jda)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            return null;
        }

        List<QueuedTrack> list = handler.getQueue().getList();
        Settings settings = getSettings(guild);

        long totalDuration = 0;
        String[] trackStrings = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
        {
            totalDuration += list.get(i).getTrack().getDuration();
            trackStrings[i] = list.get(i).toString();
        }

        String nowPlayingTitle = null;
        String statusEmoji = handler.getStatusEmoji();
        if (handler.getPlayer().getPlayingTrack() != null)
        {
            nowPlayingTitle = handler.getPlayer().getPlayingTrack().getInfo().title;
        }

        return new QueueInfo(
                trackStrings,
                totalDuration,
                nowPlayingTitle,
                statusEmoji,
                settings.getRepeatMode(),
                settings.getQueueType(),
                handler.getNowPlaying(jda),
                handler.getNoMusicPlaying(jda)
        );
    }

    public String formatQueueTitle(QueueInfo info, String successEmoji)
    {
        StringBuilder sb = new StringBuilder();
        if (info.nowPlayingTitle != null)
        {
            sb.append(info.statusEmoji).append(" **").append(info.nowPlayingTitle).append("**\n");
        }

        return FormatUtil.filter(sb.append(successEmoji).append(" Current Queue | ").append(info.tracks.length)
                .append(" entries | `").append(TimeUtil.formatTime(info.totalDuration)).append("` ")
                .append("| ").append(info.queueType.getEmoji()).append(" `").append(info.queueType.getUserFriendlyName()).append('`')
                .append(info.repeatMode.getEmoji() != null ? " | " + info.repeatMode.getEmoji() : "").toString());
    }

    private boolean isInvalidPosition(AbstractQueue<QueuedTrack> queue, int position)
    {
        return position < 1 || position > queue.size();
    }

    /**
     * Validates a queue position and sends an error message if invalid.
     *
     * @param handler  The audio handler
     * @param position The 1-based position to validate
     * @param output   The output adapter for error messages
     * @return true if the position is valid, false otherwise
     */
    private boolean validateQueuePosition(AudioHandler handler, int position, OutputAdapter output)
    {
        int size = handler.getQueue().size();
        if (position < 1 || position > size)
        {
            output.replyError("Position must be a valid integer between 1 and " + size + "!");
            return false;
        }
        return true;
    }

    /**
     * Checks if the queue is non-empty and sends an error message if empty.
     *
     * @param handler The audio handler
     * @param output  The output adapter for error messages
     * @return true if the queue is non-empty, false otherwise
     */
    private boolean requireNonEmptyQueue(AudioHandler handler, OutputAdapter output)
    {
        if (handler.getQueue().isEmpty())
        {
            output.replyError("There is nothing in the queue!");
            return false;
        }
        return true;
    }

    // ========== Inner Classes ==========

    /**
     * Result of adding a track to the queue.
     */
    public static class TrackAddResult
    {
        public final int position;
        public final String formattedMessage;
        public final String trackTitle;

        public TrackAddResult(int position, String formattedMessage, String trackTitle)
        {
            this.position = position;
            this.formattedMessage = formattedMessage;
            this.trackTitle = trackTitle;
        }
    }

    /**
     * Data class containing queue information for display.
     */
    public static class QueueInfo
    {
        public final String[] tracks;
        public final long totalDuration;
        public final String nowPlayingTitle;
        public final String statusEmoji;
        public final RepeatMode repeatMode;
        public final QueueType queueType;
        public final Object nowPlayingMessage;
        public final Object noMusicMessage;

        public QueueInfo(String[] tracks, long totalDuration, String nowPlayingTitle, String statusEmoji,
                         RepeatMode repeatMode, QueueType queueType, Object nowPlayingMessage, Object noMusicMessage)
        {
            this.tracks = tracks;
            this.totalDuration = totalDuration;
            this.nowPlayingTitle = nowPlayingTitle;
            this.statusEmoji = statusEmoji;
            this.repeatMode = repeatMode;
            this.queueType = queueType;
            this.nowPlayingMessage = nowPlayingMessage;
            this.noMusicMessage = noMusicMessage;
        }

        public boolean isEmpty()
        {
            return tracks.length == 0;
        }
    }

    /**
     * Adapter interface for abstracting output operations.
     * <p>
     * This interface allows services to be command-type agnostic - the same service
     * methods work for text commands, slash commands, and button interactions.
     * Each command type provides its own implementation.
     *
     * @see com.jagrosh.jmusicbot.commands.BaseOutputAdapter
     * @see com.jagrosh.jmusicbot.commands.v1.TextOutputAdapters
     * @see com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters
     */
    public interface OutputAdapter
    {
        void replySuccess(String content);
        void replyError(String content);
        void replyWarning(String content);
        void editMessage(String content);
        void editMessage(String content, Consumer<Message> onSuccess);
        void editNowPlaying(AudioHandler handler);
        void editNoMusic(AudioHandler handler);
        void onShowHelp();
    }
}
