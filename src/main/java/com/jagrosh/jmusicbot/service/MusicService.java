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
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.queue.PlaybackHistory;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Unified service for all music operations including player control and queue management.
 * This service encapsulates all interactions with AudioHandler.
 */
public class MusicService
{
    public static final String HISTORY_DISABLED_MESSAGE =
            "Playback history is disabled by config (playback.maxHistorySize = 0).";
    private static final String FAVORITES_PLAYLIST_NAME = "favorites";
    private static final Pattern WINDOWS_DRIVE_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final boolean WINDOWS_RUNTIME =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final Logger LOG = LoggerFactory.getLogger(MusicService.class);

    private final Bot bot;
    private final Map<PlaylistDraftKey, PlaylistDraftState> playlistDrafts = new ConcurrentHashMap<>();

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
     * @param channel     The message-capable channel for request metadata
     * @param adder       The strategy for adding the track to the queue
     * @param reason      The reason to log (e.g., "added to the queue")
     * @param logLocation Description for logging (e.g., "queue" or "front of queue")
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    private TrackAddResult addTrackInternal(Guild guild, Member member, AudioTrack track,
                                            String queryArgs, GuildMessageChannel channel,
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
     * @param channel   The message-capable channel for request metadata
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    public TrackAddResult addTrackToQueue(Guild guild, Member member, AudioTrack track,
                                          String queryArgs, GuildMessageChannel channel)
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
     * @param channel   The message-capable channel for request metadata
     * @return TrackAddResult containing position and formatted message, or null if track is too long
     */
    public TrackAddResult addTrackToFront(Guild guild, Member member, AudioTrack track,
                                          String queryArgs, GuildMessageChannel channel)
    {
        return addTrackInternal(guild, member, track, queryArgs, channel,
                AudioHandler::addTrackToFront, "added to the front of the queue.", "front of queue");
    }

    // ========== Player Operations ==========

    public void playNext(Guild guild, Member member, String args, GuildMessageChannel channel, OutputAdapter output)
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
                bot.getAudioLoadWrapper().wrap(args, new AudioLoadResultHandlers.PlayNextResultHandler(this, bot, output, guild, member, args, false, channel)));
    }

    public void play(Guild guild, Member member, String args, GuildMessageChannel channel, OutputAdapter output)
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
                    String resumedTitle = FormatUtil.getTrackTitle(handler.getPlayer().getPlayingTrack());
                    output.replySuccess("Resumed **" + resumedTitle + "**.");
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
                bot.getAudioLoadWrapper().wrap(args, new AudioLoadResultHandlers.PlayResultHandler(this, bot, output, guild, member, args, false, channel)));
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
            output.replySuccess("Restarted **" + FormatUtil.getTrackTitle(playing) + "**");
            return;
        }

        var history = handler.getQueue().getHistory();
        if (playing != null && !history.isEmpty())
        {
            QueuedTrack mostRecent = history.get(0);
            AudioTrack mostRecentTrack = mostRecent != null ? mostRecent.getTrack() : null;
            if (mostRecentTrack != null
                    && Objects.equals(mostRecentTrack.getIdentifier(), playing.getIdentifier()))
            {
                handler.getQueue().removeFromHistoryAt(0);
            }
        }

        if (history.isEmpty())
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
            output.replySuccess("Went back to **" + FormatUtil.getTrackTitle(previous.getTrack()) + "**");
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
        handler.stopAndClearQueuePreserveHistory();
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
        handler.stopAndClearQueuePreserveHistory();
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
        String trackTitle = track != null ? FormatUtil.getTrackTitle(track) : null;

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
        String trackTitle = FormatUtil.getTrackTitle(track);
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
            String trackTitle = FormatUtil.getTrackTitle(handler.getPlayer().getPlayingTrack());
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
            String trackTitle = FormatUtil.getTrackTitle(handler.getPlayer().getPlayingTrack());
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

    public void addCurrentTrackToFavorites(Guild guild, Member member, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "add the current track to favorites"))
            return;

        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        AudioTrack currentTrack = handler.getPlayer().getPlayingTrack();
        if (currentTrack == null)
        {
            output.replyError("There is no track currently playing!");
            return;
        }

        String favoriteEntry = currentTrack.getInfo().uri;
        if (favoriteEntry == null || favoriteEntry.isBlank())
        {
            output.replyError("The current track does not have a valid URI or absolute path to save.");
            return;
        }
        if (!isSupportedFavoriteEntry(favoriteEntry))
        {
            output.replyError("Only URI references or absolute file paths for this OS can be added to favorites.");
            return;
        }

        PlaylistLoader.PlaylistResult<PlaylistLoader.AppendIfAbsentResult> appendResult =
                bot.getPlaylistLoader().appendItemIfAbsentResult(FAVORITES_PLAYLIST_NAME, favoriteEntry);
        if (!appendResult.isSuccess())
        {
            output.replyError(mapPlaylistErrorToMessage(appendResult.getError()));
            return;
        }

        if (appendResult.getValue().getStatus() == PlaylistLoader.AppendIfAbsentStatus.ALREADY_PRESENT)
        {
            handler.markCurrentTrackFavorited(favoriteEntry);
            output.editNowPlaying(handler);
            output.replyWarning("This track is already favorited");
            return;
        }

        handler.markCurrentTrackFavorited(favoriteEntry);
        output.editNowPlaying(handler);
        output.replySuccess("Added " + FormatUtil.filter(FormatUtil.getTrackTitle(currentTrack)) + " to Favorites");
    }

    private static boolean isSupportedFavoriteEntry(String value)
    {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty())
            return false;
        return isUriLikeReference(candidate) || isOsNativeAbsolutePath(candidate);
    }

    private static boolean isUriLikeReference(String value)
    {
        // Avoid treating Windows drive paths (e.g. C:\music\song.mp3) as URI schemes.
        if (isWindowsAbsolutePathSyntax(value))
            return false;
        int colonIndex = value.indexOf(':');
        if (colonIndex <= 0)
            return false;
        for (int i = 0; i < colonIndex; i++)
        {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.')
                return false;
        }
        return true;
    }

    private static boolean isOsNativeAbsolutePath(String value)
    {
        try
        {
            if (!Path.of(value).isAbsolute())
                return false;
        }
        catch (RuntimeException ex)
        {
            return false;
        }

        if (WINDOWS_RUNTIME)
            return isWindowsAbsolutePathSyntax(value);
        return value.startsWith("/");
    }

    private static boolean isWindowsAbsolutePathSyntax(String value)
    {
        return WINDOWS_DRIVE_ABSOLUTE_PATH.matcher(value).matches() || value.startsWith("\\\\");
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
            output.replyError("You cannot seek **" + FormatUtil.getTrackTitle(playingTrack) + "** because you didn't add it!");
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
            output.replySuccess("Removed **" + FormatUtil.getTrackTitle(qt.getTrack()) + "** from the queue");
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

            output.replySuccess("Removed **" + FormatUtil.getTrackTitle(qt.getTrack())
                    + "** from the queue (requested by " + (u == null ? "someone" : "**" + u.getName() + "**") + ")");
        }
        else
        {
            output.replyError("You cannot remove **" + FormatUtil.getTrackTitle(qt.getTrack()) + "** because you didn't add it!");
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
        String trackTitle = FormatUtil.getTrackTitle(track.getTrack());
        output.replySuccess("Moved **" + trackTitle + "** from position `" + from + "` to `" + to + "`.");
    }

    /**
     * Moves a track to position 1, making it play next.
     * Convenience method that wraps moveTrack.
     *
     * @param guild    The guild
     * @param member   The member executing the command
     * @param position The 1-based position of the track to move
     * @param output   The output adapter for responses
     */
    public void playNext(Guild guild, Member member, int position, OutputAdapter output)
    {
        if (position == 1)
        {
            output.replyWarning("This track is already next!");
            return;
        }
        moveTrack(guild, member, position, 1, output);
    }

    /**
     * Plays a track from the queue immediately while preserving the rest of the queue.
     * Moves the track to position 1 and skips the currently playing track.
     *
     * @param guild    The guild
     * @param member   The member executing the command
     * @param position The 1-based position of the track to play now
     * @param output   The output adapter for responses
     */
    public void playNow(Guild guild, Member member, int position, OutputAdapter output)
    {
        if (!requireDJPermission(guild, member, output, "play a track immediately"))
            return;

        AudioHandler handler = getHandler(guild);
        AbstractQueue<QueuedTrack> queue = handler.getQueue();

        if (isInvalidPosition(queue, position))
        {
            output.replyError("`" + position + "` is not a valid position in the queue!");
            return;
        }

        // Get the track info before moving
        QueuedTrack queuedTrack = queue.get(position - 1);
        String trackTitle = FormatUtil.getTrackTitle(queuedTrack.getTrack());

        // Move the track to position 1
        if (position > 1)
        {
            queue.moveItem(position - 1, 0);
        }

        // Skip the currently playing track to start the moved track immediately
        handler.getPlayer().stopTrack();

        output.replySuccess("Now playing **" + trackTitle + "**");
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
        String title = FormatUtil.getTrackTitle(track.getTrack());

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
        String trackTitle = FormatUtil.getTrackTitle(handler.getQueue().get(0).getTrack());
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
        String trackTitle = FormatUtil.getTrackTitle(handler.getQueue().get(0).getTrack());
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
            nowPlayingTitle = FormatUtil.getTrackTitle(handler.getPlayer().getPlayingTrack());
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

    // ========== History Info ==========

    /**
     * Gets playback history information for display (e.g. history command embed).
     * History index 0 is most recent. Does not require music to be playing.
     *
     * @param guild The guild
     * @param jda   The JDA instance (for consistency with getQueueInfo)
     * @return HistoryInfo, or null if no handler exists
     */
    public HistoryInfo getHistoryInfo(Guild guild, JDA jda)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            return null;
        }

        List<QueuedTrack> list = handler.getPreviousTracks();
        int maxSize = handler.getQueue().getHistory().getMaxSize();
        boolean disabled = maxSize == 0;

        long totalDuration = 0;
        String[] trackStrings = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
        {
            totalDuration += list.get(i).getTrack().getDuration();
            trackStrings[i] = list.get(i).toString();
        }

        return new HistoryInfo(trackStrings, totalDuration, maxSize, disabled);
    }

    /**
     * Adds the track at the given 1-based history position to the queue, and removes it from history (de-dup).
     *
     * @param guild           The guild
     * @param member          The member adding the track
     * @param historyPosition 1-based position (1 = most recent)
     * @param channel         The message-capable channel for request metadata
     * @param output          The output adapter
     */
    public void queueFromHistory(Guild guild, Member member, int historyPosition, GuildMessageChannel channel, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        var history = handler.getQueue().getHistory();
        if (!requireHistoryEnabled(history, output))
        {
            return;
        }
        if (history.isEmpty())
        {
            output.replyError("Playback history is empty!");
            return;
        }
        if (historyPosition < 1 || historyPosition > history.size())
        {
            output.replyError("Position must be between 1 and " + history.size() + "!");
            return;
        }

        int index = historyPosition - 1;
        QueuedTrack qt = history.get(index);
        handler.getQueue().removeFromHistoryAt(index);

        AudioTrack track = qt.getTrack().makeClone();
        if (isTooLong(track))
        {
            output.replyError(formatTooLongError(track));
            return;
        }

        RequestMetadata rm = new RequestMetadata(member.getUser(),
                new RequestMetadata.RequestInfo(qt.getTrack().getInfo().uri, qt.getTrack().getInfo().uri),
                channel.getIdLong());
        QueuedTrack newQt = new QueuedTrack(track, rm);
        handler.setLastReason(member.getUser().getName() + " added from history.");
        int position = handler.addTrack(newQt) + 1;
        String title = FormatUtil.getTrackTitle(track);
        output.replySuccess(formatTrackAddedMessage(title, track.getDuration(), position));
    }

    /**
     * Adds all tracks from playback history to the queue, removing them from history (de-dup).
     * Tracks that exceed the max duration are skipped. Order in queue is most recent first (playback will be chronological).
     *
     * @param guild   The guild
     * @param member  The member adding the tracks
     * @param channel The message-capable channel for request metadata
     * @param output  The output adapter
     */
    public void queueAllFromHistory(Guild guild, Member member, GuildMessageChannel channel, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        var history = handler.getQueue().getHistory();
        if (!requireHistoryEnabled(history, output))
        {
            return;
        }
        if (history.isEmpty())
        {
            output.replyError("Playback history is empty!");
            return;
        }

        handler.setLastReason(member.getUser().getName() + " added entire history to queue.");
        int added = 0;
        int skipped = 0;

        // Work from a stable snapshot and clear source entries before adding tracks so
        // onTrackStart history updates cannot be accidentally removed by this operation.
        List<QueuedTrack> historySnapshot = new ArrayList<>(history.getList());
        handler.getQueue().clearHistory();
        for (QueuedTrack qt : historySnapshot)
        {
            AudioTrack track = qt.getTrack().makeClone();
            if (isTooLong(track))
            {
                skipped++;
                continue;
            }

            RequestMetadata rm = new RequestMetadata(member.getUser(),
                    new RequestMetadata.RequestInfo(qt.getTrack().getInfo().uri, qt.getTrack().getInfo().uri),
                    channel.getIdLong());
            QueuedTrack newQt = new QueuedTrack(track, rm);
            handler.addTrack(newQt);
            added++;
        }

        if (added == 0 && skipped > 0)
        {
            output.replyError("All " + skipped + " track(s) were too long to add.");
            return;
        }
        String message = "Added **" + added + "** track(s) to the queue.";
        if (skipped > 0)
        {
            message += " Skipped " + skipped + " track(s) (too long).";
        }
        output.replySuccess(message);
        if (handler.getPlayer().getPlayingTrack() != null)
        {
            bot.getNowplayingHandler().requestReconcile(guild.getIdLong(), "history-queueall-loaded");
        }
    }

    /**
     * Plays the track at the given 1-based history position immediately, and removes it from history (de-dup).
     *
     * @param guild           The guild
     * @param member          The member
     * @param historyPosition 1-based position (1 = most recent)
     * @param channel         The message-capable channel for request metadata
     * @param output          The output adapter
     */
    public void playFromHistoryNow(Guild guild, Member member, int historyPosition, GuildMessageChannel channel, OutputAdapter output)
    {
        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        var history = handler.getQueue().getHistory();
        if (!requireHistoryEnabled(history, output))
        {
            return;
        }
        if (history.isEmpty())
        {
            output.replyError("Playback history is empty!");
            return;
        }
        if (historyPosition < 1 || historyPosition > history.size())
        {
            output.replyError("Position must be between 1 and " + history.size() + "!");
            return;
        }

        int index = historyPosition - 1;
        QueuedTrack qt = history.get(index);
        handler.getQueue().removeFromHistoryAt(index);

        AudioTrack track = qt.getTrack().makeClone();
        if (isTooLong(track))
        {
            output.replyError(formatTooLongError(track));
            return;
        }

        RequestMetadata rm = new RequestMetadata(member.getUser(),
                new RequestMetadata.RequestInfo(qt.getTrack().getInfo().uri, qt.getTrack().getInfo().uri),
                channel.getIdLong());
        QueuedTrack newQt = new QueuedTrack(track, rm);
        handler.setLastReason(member.getUser().getName() + " playing from history.");
        handler.addTrackToFront(newQt);
        if (handler.getPlayer().getPlayingTrack() != null)
        {
            handler.getPlayer().stopTrack();
        }
        String title = FormatUtil.getTrackTitle(track);
        output.replySuccess("Now playing **" + FormatUtil.filter(title) + "**");
    }

    private String mapPlaylistErrorToMessage(PlaylistLoader.PlaylistError error)
    {
        if (error == null)
        {
            return "Playlist storage is unavailable.";
        }
        String path = error.getConfiguredPath() == null ? "(not configured)" : error.getConfiguredPath();
        return switch (error.getType())
        {
            case INVALID_CONFIG ->
                    "Playlists storage is misconfigured (`" + path + "`). " + error.getMessage();
            case STORAGE_UNAVAILABLE ->
                    "Playlists storage is unavailable (`" + path + "`). " + error.getMessage();
            case PLAYLIST_NOT_FOUND -> error.getMessage();
        };
    }

    public PlaylistNamesInfo getAvailablePlaylistNames()
    {
        PlaylistLoader.PlaylistResult<List<String>> result = bot.getPlaylistLoader().getPlaylistNamesResult();
        if (!result.isSuccess())
        {
            return PlaylistNamesInfo.error(mapPlaylistErrorToMessage(result.getError()));
        }
        return PlaylistNamesInfo.success(result.getValue());
    }

    /**
     * Queues all tracks from a named saved playlist.
     *
     * @param guild        The guild
     * @param member       The member requesting playback
     * @param playlistName The saved playlist name
     * @param channel      The message-capable channel for request metadata
     * @param output       The output adapter
     */
    public void queuePlaylist(Guild guild, Member member, String playlistName, GuildMessageChannel channel, OutputAdapter output)
    {
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(playlistName);
        if (!playlistResult.isSuccess())
        {
            output.replyError(mapPlaylistErrorToMessage(playlistResult.getError()));
            return;
        }
        Playlist playlist = playlistResult.getValue();

        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            handler = bot.getPlayerManager().setUpHandler(guild);
        }
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        AudioHandler finalHandler = handler;
        playlist.loadTracks(bot.getPlayerManager(), at -> {
            RequestMetadata metadata = new RequestMetadata(member.getUser(),
                    new RequestMetadata.RequestInfo("playlist " + playlist.getName(), at.getInfo().uri),
                    channel.getIdLong());
            finalHandler.addTrack(new QueuedTrack(at, metadata));
        }, () -> {
            int loadedCount = playlist.getTracks().size();
            int errorCount = playlist.getErrors().size();
            if (loadedCount == 0)
            {
                output.replyWarning("No tracks were loaded from playlist `" + playlist.getName() + "`!");
                return;
            }

            StringBuilder msg = new StringBuilder("Queued **")
                    .append(loadedCount)
                    .append("** track(s) from playlist `")
                    .append(playlist.getName())
                    .append("`.");
            if (errorCount > 0)
            {
                msg.append(" Failed to load ").append(errorCount).append(" item(s).");
            }
            output.replySuccess(msg.toString());
            if (finalHandler.getPlayer().getPlayingTrack() != null)
            {
                bot.getNowplayingHandler().requestReconcile(guild.getIdLong(), "playlist-loaded");
            }
        });
    }

    /**
     * Clears current playback queue and starts playing the selected saved playlist immediately.
     *
     * @param guild        The guild
     * @param member       The member requesting playback
     * @param playlistName The saved playlist name
     * @param channel      The message-capable channel for request metadata
     * @param output       The output adapter
     */
    public void playPlaylistNow(Guild guild, Member member, String playlistName, GuildMessageChannel channel, OutputAdapter output)
    {
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(playlistName);
        if (!playlistResult.isSuccess())
        {
            output.replyError(mapPlaylistErrorToMessage(playlistResult.getError()));
            return;
        }

        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            handler = bot.getPlayerManager().setUpHandler(guild);
        }
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        handler.stopAndClearQueuePreserveHistory();
        queuePlaylist(guild, member, playlistName, channel, new OutputAdapter()
        {
            @Override
            public void replySuccess(String content)
            {
                output.replySuccess("Now playing playlist `" + playlistName + "` (queue replaced). " + content);
            }

            @Override
            public void replyError(String content)
            {
                output.replyError(content);
            }

            @Override
            public void replyWarning(String content)
            {
                output.replyWarning(content);
            }

            @Override
            public void editMessage(String content)
            {
            }

            @Override
            public void editMessage(String content, Consumer<Message> onSuccess)
            {
            }

            @Override
            public void editNowPlaying(AudioHandler handler)
            {
            }

            @Override
            public void editNoMusic(AudioHandler handler)
            {
            }

            @Override
            public void onShowHelp()
            {
            }
        });
    }

    /**
     * Gets lightweight metadata for a saved playlist.
     *
     * @param playlistName The saved playlist name
     * @return playlist details, or null if playlist does not exist
     */
    public PlaylistDetailsInfo getPlaylistDetails(String playlistName)
    {
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(playlistName);
        if (!playlistResult.isSuccess())
        {
            return null;
        }
        Playlist playlist = playlistResult.getValue();

        List<String> items = playlist.getItems();
        int previewLimit = Math.min(5, items.size());
        List<String> preview = new ArrayList<>(items.subList(0, previewLimit));
        boolean hasMore = items.size() > previewLimit;
        return new PlaylistDetailsInfo(playlist.getName(), items.size(), preview, hasMore);
    }

    /**
     * Returns true when the member is allowed to edit playlists in interactive details view.
     * Policy: bot owner OR DJ permission.
     */
    public boolean canEditPlaylistEntries(Guild guild, Member member)
    {
        return bot.getConfig().getOwnerId() == member.getIdLong()
                || DJCommand.checkDJPermission(bot, guild, member);
    }

    public PlaylistDraftContext buildPlaylistDraftContext(long guildId, long channelId, long messageId,
                                                          long userId, String playlistName)
    {
        return new PlaylistDraftContext(guildId, channelId, messageId, userId, playlistName);
    }

    public PlaylistDraftMutationResult removePlaylistDraftItem(PlaylistDraftContext context, int position)
    {
        PlaylistDraftState state = getOrCreatePlaylistDraftState(context);
        if (state == null)
        {
            return PlaylistDraftMutationResult.error("Playlist no longer exists.");
        }
        if (position < 1 || position > state.items.size())
        {
            return PlaylistDraftMutationResult.error("Position must be between 1 and " + state.items.size() + ".");
        }
        state.items.remove(position - 1);
        state.dirty = true;
        state.revision++;
        return PlaylistDraftMutationResult.success(state.items.size(), true, state.revision);
    }

    public PlaylistDraftMutationResult movePlaylistDraftItem(PlaylistDraftContext context, int fromPosition, int toPosition)
    {
        PlaylistDraftState state = getOrCreatePlaylistDraftState(context);
        if (state == null)
        {
            return PlaylistDraftMutationResult.error("Playlist no longer exists.");
        }
        int size = state.items.size();
        if (fromPosition < 1 || fromPosition > size || toPosition < 1 || toPosition > size)
        {
            return PlaylistDraftMutationResult.error("Positions must be between 1 and " + size + ".");
        }
        if (fromPosition == toPosition)
        {
            return PlaylistDraftMutationResult.success(size, state.dirty, state.revision);
        }
        String item = state.items.remove(fromPosition - 1);
        state.items.add(toPosition - 1, item);
        state.dirty = true;
        state.revision++;
        return PlaylistDraftMutationResult.success(state.items.size(), true, state.revision);
    }

    public PlaylistDraftMutationResult savePlaylistDraft(PlaylistDraftContext context)
    {
        PlaylistDraftState state = playlistDrafts.get(toDraftKey(context));
        if (state == null)
        {
            return PlaylistDraftMutationResult.error("There are no unsaved changes.");
        }
        if (!state.dirty)
        {
            return PlaylistDraftMutationResult.error("There are no unsaved changes.");
        }
        String content = String.join(System.lineSeparator(), state.items);
        PlaylistLoader.PlaylistResult<Void> writeResult = bot.getPlaylistLoader()
                .writePlaylistResult(context.playlistName(), content);
        if (!writeResult.isSuccess())
        {
            return PlaylistDraftMutationResult.error(mapPlaylistErrorToMessage(writeResult.getError()));
        }
        state.dirty = false;
        state.revision++;
        return PlaylistDraftMutationResult.success(state.items.size(), false, state.revision);
    }

    public void discardPlaylistDraft(PlaylistDraftContext context)
    {
        playlistDrafts.remove(toDraftKey(context));
    }

    public boolean isPlaylistDraftDirty(PlaylistDraftContext context)
    {
        PlaylistDraftState state = playlistDrafts.get(toDraftKey(context));
        return state != null && state.dirty;
    }

    public int getPlaylistTrackCount(PlaylistDraftContext context)
    {
        PlaylistDraftState state = playlistDrafts.get(toDraftKey(context));
        if (state != null)
        {
            return state.items.size();
        }
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(context.playlistName());
        if (!playlistResult.isSuccess())
        {
            return -1;
        }
        return playlistResult.getValue().getItems().size();
    }

    private PlaylistDraftKey toDraftKey(PlaylistDraftContext context)
    {
        return new PlaylistDraftKey(
                context.guildId(),
                context.channelId(),
                context.messageId(),
                context.userId(),
                context.playlistName()
        );
    }

    private PlaylistDraftState getOrCreatePlaylistDraftState(PlaylistDraftContext context)
    {
        PlaylistDraftKey key = toDraftKey(context);
        PlaylistDraftState existing = playlistDrafts.get(key);
        if (existing != null)
        {
            return existing;
        }

        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader()
                .getPlaylistResult(context.playlistName());
        if (!playlistResult.isSuccess())
        {
            return null;
        }
        Playlist playlist = playlistResult.getValue();
        PlaylistDraftState created = new PlaylistDraftState(new ArrayList<>(playlist.getItems()));
        PlaylistDraftState raced = playlistDrafts.putIfAbsent(key, created);
        return raced != null ? raced : created;
    }

    private static final String COULD_NOT_LOAD_LINE = "`[?:??]` **Could not load**";

    /**
     * Asynchronously loads the first N playlist URLs and invokes the callback with formatted track lines
     * (same style as Queue/History: duration + linked title). Uses a unique load identifier per index so
     * loads run in parallel. If the playlist does not exist, the callback is invoked with null.
     *
     * @param playlistName name of the saved playlist
     * @param maxPreview   maximum number of preview entries to load (e.g. 5)
     * @param callback     invoked with the result, or null if playlist not found
     */
    public void loadPlaylistPreviewWithTracks(String playlistName, int maxPreview,
                                             Consumer<PlaylistPreviewWithTracks> callback)
    {
        loadPlaylistLines(playlistName, 0, maxPreview, false, result ->
        {
            if (result == null)
            {
                callback.accept(null);
                return;
            }
            callback.accept(new PlaylistPreviewWithTracks(
                    result.playlistName,
                    result.totalItems,
                    result.hasMore,
                    result.formattedLines
            ));
        });
    }

    /**
     * Asynchronously loads one page of playlist URLs and resolves each to a formatted track line
     * (same style as Queue/History: duration + linked title).
     *
     * @param playlistName playlist name
     * @param page         1-based page number
     * @param pageSize     page size (e.g. 10)
     * @param callback     invoked with page result, or null when playlist does not exist
     */
    public void loadPlaylistPageWithTracks(String playlistName, int page, int pageSize,
                                           Consumer<PlaylistTracksPageInfo> callback)
    {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int startIndex = (safePage - 1) * safePageSize;
        loadPlaylistLines(playlistName, startIndex, safePageSize, false, callback);
    }

    /**
     * Same as {@link #loadPlaylistPageWithTracks(String, int, int, Consumer)} but reads from the current
     * interaction draft when one exists for the given context.
     */
    public void loadPlaylistPageWithTracks(PlaylistDraftContext context, int page, int pageSize,
                                           Consumer<PlaylistTracksPageInfo> callback)
    {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int startIndex = (safePage - 1) * safePageSize;

        PlaylistDraftState draft = playlistDrafts.get(toDraftKey(context));
        if (draft != null)
        {
            loadPlaylistLines(context.playlistName(), draft.items, startIndex, safePageSize, draft.dirty, callback);
            return;
        }
        loadPlaylistLines(context.playlistName(), startIndex, safePageSize, false, callback);
    }

    /**
     * Returns the playlist item URL at a 1-based position, or null when missing.
     */
    public String getPlaylistTrackUrlAtPosition(String playlistName, int position)
    {
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(playlistName);
        if (!playlistResult.isSuccess())
        {
            return null;
        }
        List<String> items = playlistResult.getValue().getItems();
        if (position < 1 || position > items.size())
        {
            return null;
        }
        return items.get(position - 1);
    }

    /**
     * Returns the draft-aware playlist item URL at a 1-based position, or null when missing.
     */
    public String getPlaylistTrackUrlAtPosition(PlaylistDraftContext context, int position)
    {
        PlaylistDraftState draft = playlistDrafts.get(toDraftKey(context));
        if (draft != null)
        {
            if (position < 1 || position > draft.items.size())
            {
                return null;
            }
            return draft.items.get(position - 1);
        }
        return getPlaylistTrackUrlAtPosition(context.playlistName(), position);
    }

    /**
     * Loads a URL and adds it to the front of queue, then starts it immediately.
     */
    public void playNowFromUrl(Guild guild, Member member, String url, GuildMessageChannel channel, OutputAdapter output)
    {
        if (url == null || url.isBlank())
        {
            output.replyError("That playlist entry could not be loaded.");
            return;
        }
        bot.getPlayerManager().loadItemOrdered(guild, url, bot.getAudioLoadWrapper().wrap(url, new AudioLoadResultHandler()
        {
            private void loadSingle(AudioTrack track)
            {
                TrackAddResult result = addTrackToFront(guild, member, track, url, channel);
                if (result == null)
                {
                    output.replyError(formatTooLongError(track));
                    return;
                }
                AudioHandler handler = getHandler(guild);
                if (handler != null && handler.getPlayer().getPlayingTrack() != null)
                {
                    handler.getPlayer().stopTrack();
                }
                output.replySuccess("Now playing **" + FormatUtil.filter(FormatUtil.getTrackTitle(track)) + "**");
            }

            @Override
            public void trackLoaded(AudioTrack track)
            {
                loadSingle(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist ap)
            {
                AudioTrack single;
                if (ap.isSearchResult() && !ap.getTracks().isEmpty())
                {
                    single = ap.getTracks().get(0);
                }
                else if (ap.getSelectedTrack() != null)
                {
                    single = ap.getSelectedTrack();
                }
                else if (!ap.getTracks().isEmpty())
                {
                    single = ap.getTracks().get(0);
                }
                else
                {
                    output.replyError("That playlist entry could not be loaded.");
                    return;
                }
                loadSingle(single);
            }

            @Override
            public void noMatches()
            {
                output.replyError("No matches found for that playlist entry.");
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                output.replyError("Error loading playlist entry: " + exception.getMessage());
            }
        }));
    }

    private void loadPlaylistLines(String playlistName, int startIndex, int maxCount,
                                   boolean dirty, Consumer<PlaylistTracksPageInfo> callback)
    {
        PlaylistLoader.PlaylistResult<Playlist> playlistResult = bot.getPlaylistLoader().getPlaylistResult(playlistName);
        if (!playlistResult.isSuccess())
        {
            callback.accept(null);
            return;
        }
        Playlist playlist = playlistResult.getValue();
        loadPlaylistLines(playlist.getName(), playlist.getItems(), startIndex, maxCount, dirty, callback);
    }

    private void loadPlaylistLines(String playlistName, List<String> sourceItems, int startIndex, int maxCount,
                                   boolean dirty, Consumer<PlaylistTracksPageInfo> callback)
    {
        List<String> items = sourceItems == null ? List.of() : sourceItems;
        int safeStartIndex = Math.max(0, startIndex);
        if (safeStartIndex >= items.size())
        {
            callback.accept(new PlaylistTracksPageInfo(
                    playlistName, items.size(), safeStartIndex, false, dirty, new ArrayList<>()));
            return;
        }

        int toLoad = Math.min(Math.max(0, maxCount), items.size() - safeStartIndex);
        if (toLoad == 0)
        {
            callback.accept(new PlaylistTracksPageInfo(
                    playlistName, items.size(), safeStartIndex, items.size() > safeStartIndex, dirty, new ArrayList<>()));
            return;
        }

        String[] lines = new String[toLoad];
        AtomicInteger completed = new AtomicInteger(0);
        Runnable maybeDone = () ->
        {
            if (completed.incrementAndGet() == toLoad)
            {
                callback.accept(new PlaylistTracksPageInfo(
                        playlistName,
                        items.size(),
                        safeStartIndex,
                        safeStartIndex + toLoad < items.size(),
                        dirty,
                        Arrays.asList(lines)
                ));
            }
        };

        for (int i = 0; i < toLoad; i++)
        {
            int index = i;
            String url = items.get(safeStartIndex + i);
            Object orderingId = "playlist-lines-" + playlistName + "-" + (safeStartIndex + i);
            bot.getPlayerManager().loadItemOrdered(orderingId, url, new AudioLoadResultHandler()
            {
                @Override
                public void trackLoaded(AudioTrack track)
                {
                    lines[index] = FormatUtil.formatTrackLineForEmbed(track);
                    maybeDone.run();
                }

                @Override
                public void playlistLoaded(AudioPlaylist ap)
                {
                    AudioTrack single;
                    if (ap.isSearchResult() && !ap.getTracks().isEmpty())
                    {
                        single = ap.getTracks().get(0);
                    }
                    else if (ap.getSelectedTrack() != null)
                    {
                        single = ap.getSelectedTrack();
                    }
                    else if (!ap.getTracks().isEmpty())
                    {
                        single = ap.getTracks().get(0);
                    }
                    else
                    {
                        lines[index] = COULD_NOT_LOAD_LINE;
                        maybeDone.run();
                        return;
                    }
                    lines[index] = FormatUtil.formatTrackLineForEmbed(single);
                    maybeDone.run();
                }

                @Override
                public void noMatches()
                {
                    lines[index] = COULD_NOT_LOAD_LINE;
                    maybeDone.run();
                }

                @Override
                public void loadFailed(FriendlyException exception)
                {
                    lines[index] = COULD_NOT_LOAD_LINE;
                    maybeDone.run();
                }
            });
        }
    }

    /**
     * Saves the current playback history as a playlist file. Rejects if a playlist with the name already exists.
     * Requires DJ permission or bot owner.
     *
     * @param guild        The guild
     * @param member       The member
     * @param playlistName The name for the new playlist (sanitized)
     * @param output       The output adapter
     */
    public void saveHistoryAsPlaylist(Guild guild, Member member, String playlistName, OutputAdapter output)
    {
        if (!canSaveHistoryAsPlaylist(guild, member))
        {
            output.replyError("You need to be a DJ or the bot owner to save history as a playlist!");
            return;
        }

        AudioHandler handler = getHandler(guild);
        if (handler == null)
        {
            output.replyError("There is no player in this server!");
            return;
        }

        var history = handler.getQueue().getHistory();
        if (!requireHistoryEnabled(history, output))
        {
            return;
        }

        List<QueuedTrack> previous = handler.getPreviousTracks();
        List<String> uris = extractHttpUrisFromHistory(previous);
        if (uris.isEmpty())
        {
            output.replyWarning("No valid track URLs in history to save (only http(s) sources are stored).");
            return;
        }

        String sanitized = playlistName.replaceAll("\\s+", "_").replaceAll("[*?|\\/\":<>]", "");
        if (sanitized.isEmpty())
        {
            output.replyError("Please provide a valid playlist name!");
            return;
        }

        if (bot.getPlaylistLoader().getPlaylistResult(sanitized).isSuccess())
        {
            output.replyError("Playlist `" + sanitized + "` already exists! Choose a different name.");
            return;
        }

        PlaylistLoader.PlaylistResult<Void> createResult = bot.getPlaylistLoader().createPlaylistResult(sanitized);
        if (!createResult.isSuccess())
        {
            output.replyError(mapPlaylistErrorToMessage(createResult.getError()));
            return;
        }

        String content = String.join("\r\n", uris);
        PlaylistLoader.PlaylistResult<Void> writeResult = bot.getPlaylistLoader().writePlaylistResult(sanitized, content);
        if (!writeResult.isSuccess())
        {
            output.replyError(mapPlaylistErrorToMessage(writeResult.getError()));
            return;
        }
        output.replySuccess("Saved " + uris.size() + " tracks from history to playlist `" + sanitized + "`!");
    }

    private boolean canSaveHistoryAsPlaylist(Guild guild, Member member)
    {
        return bot.getConfig().getOwnerId() == member.getIdLong()
                || DJCommand.checkDJPermission(bot, guild, member);
    }

    /**
     * Extracts http(s) URIs from history tracks for playlist persistence.
     */
    private static List<String> extractHttpUrisFromHistory(List<QueuedTrack> previous)
    {
        List<String> uris = new ArrayList<>();
        for (QueuedTrack qt : previous)
        {
            String uri = qt.getTrack().getInfo().uri;
            if (uri != null && uri.startsWith("http"))
            {
                uris.add(uri);
            }
        }
        return uris;
    }

    private boolean isInvalidPosition(AbstractQueue<QueuedTrack> queue, int position)
    {
        return position < 1 || position > queue.size();
    }

    private boolean requireHistoryEnabled(PlaybackHistory<QueuedTrack> history, OutputAdapter output)
    {
        if (history.getMaxSize() == 0)
        {
            output.replyError(HISTORY_DISABLED_MESSAGE);
            return false;
        }
        return true;
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
     * Data class containing playback history information for display.
     */
    public static class HistoryInfo
    {
        public final String[] tracks;
        public final long totalDuration;
        public final int maxSize;
        public final boolean disabled;

        public HistoryInfo(String[] tracks, long totalDuration, int maxSize)
        {
            this(tracks, totalDuration, maxSize, maxSize == 0);
        }

        public HistoryInfo(String[] tracks, long totalDuration, int maxSize, boolean disabled)
        {
            this.tracks = tracks;
            this.totalDuration = totalDuration;
            this.maxSize = maxSize;
            this.disabled = disabled;
        }

        public boolean isEmpty()
        {
            return tracks.length == 0;
        }

        public boolean isDisabled()
        {
            return disabled;
        }
    }

    /**
     * Result for listing playlists where storage errors are explicit.
     */
    public static class PlaylistNamesInfo
    {
        public final List<String> names;
        public final String errorMessage;

        private PlaylistNamesInfo(List<String> names, String errorMessage)
        {
            this.names = names;
            this.errorMessage = errorMessage;
        }

        public static PlaylistNamesInfo success(List<String> names)
        {
            return new PlaylistNamesInfo(names, null);
        }

        public static PlaylistNamesInfo error(String errorMessage)
        {
            return new PlaylistNamesInfo(List.of(), errorMessage);
        }

        public boolean hasError()
        {
            return errorMessage != null;
        }
    }

    /**
     * Metadata used for playlist details previews in interactions.
     */
    public static class PlaylistDetailsInfo
    {
        public final String playlistName;
        public final int totalItems;
        public final List<String> previewItems;
        public final boolean hasMore;

        public PlaylistDetailsInfo(String playlistName, int totalItems, List<String> previewItems, boolean hasMore)
        {
            this.playlistName = playlistName;
            this.totalItems = totalItems;
            this.previewItems = previewItems;
            this.hasMore = hasMore;
        }
    }

    /**
     * Result of loading playlist preview with resolved track lines (duration + title, same style as Queue/History).
     */
    public static class PlaylistPreviewWithTracks
    {
        public final String playlistName;
        public final int totalItems;
        public final boolean hasMore;
        public final List<String> formattedLines;

        public PlaylistPreviewWithTracks(String playlistName, int totalItems, boolean hasMore, List<String> formattedLines)
        {
            this.playlistName = playlistName;
            this.totalItems = totalItems;
            this.hasMore = hasMore;
            this.formattedLines = formattedLines;
        }
    }

    /**
     * Result for one page of playlist tracks resolved to formatted lines.
     */
    public static class PlaylistTracksPageInfo
    {
        public final String playlistName;
        public final int totalItems;
        public final int startIndex;
        public final boolean hasMore;
        public final boolean dirty;
        public final List<String> formattedLines;

        public PlaylistTracksPageInfo(String playlistName, int totalItems, int startIndex,
                                      boolean hasMore, boolean dirty, List<String> formattedLines)
        {
            this.playlistName = playlistName;
            this.totalItems = totalItems;
            this.startIndex = startIndex;
            this.hasMore = hasMore;
            this.dirty = dirty;
            this.formattedLines = formattedLines;
        }
    }

    /**
     * Identifies a playlist details draft session for one interaction message and user.
     */
    public record PlaylistDraftContext(long guildId, long channelId, long messageId, long userId, String playlistName)
    {
    }

    private record PlaylistDraftKey(long guildId, long channelId, long messageId, long userId, String playlistName)
    {
    }

    private static class PlaylistDraftState
    {
        private final List<String> items;
        private boolean dirty;
        private int revision;

        private PlaylistDraftState(List<String> items)
        {
            this.items = items;
            this.dirty = false;
            this.revision = 0;
        }
    }

    public static class PlaylistDraftMutationResult
    {
        public final boolean success;
        public final String errorMessage;
        public final int totalItems;
        public final boolean dirty;
        public final int revision;

        private PlaylistDraftMutationResult(boolean success, String errorMessage, int totalItems,
                                            boolean dirty, int revision)
        {
            this.success = success;
            this.errorMessage = errorMessage;
            this.totalItems = totalItems;
            this.dirty = dirty;
            this.revision = revision;
        }

        public static PlaylistDraftMutationResult success(int totalItems, boolean dirty, int revision)
        {
            return new PlaylistDraftMutationResult(true, null, totalItems, dirty, revision);
        }

        public static PlaylistDraftMutationResult error(String errorMessage)
        {
            return new PlaylistDraftMutationResult(false, errorMessage, 0, false, 0);
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
