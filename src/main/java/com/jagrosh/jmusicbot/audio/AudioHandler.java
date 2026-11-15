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
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.MatchSuggestions;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    /**
     * yt-dlp prompt mode: how long a prompt candidate's audio-less FINISHED end waits for an
     * exception callback before the queue moves on anyway.
     */
    private static final long FINISHED_WITHOUT_AUDIO_FALLBACK_SECONDS = 5;
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    /** Listener for audio metrics events. Uses NO_OP implementation in no-GUI mode. */
    private final AudioMetricsListener metricsListener;
    /**
     * The last 30 s of Opus frames sent to Discord, used to line up "Switch to library copy"
     * with the stream (see docs/ytdlp-library.md, "Switch alignment"). Only filled
     * in while yt-dlp matching and alignment are both on.
     */
    private final RecentFrames recentFrames = new RecentFrames(1500);

    private AudioFrame lastFrame;
    private AbstractQueue<QueuedTrack> queue;
    private String lastReason = null;
    private volatile String favoritedTrackUri = null;
    /**
     * yt-dlp prompt mode: where a prompt candidate's end and its exception callback meet.
     * lavaplayer may deliver either first (it marks the track as ending, then logs the failure,
     * and only then records and reports the exception), so whichever side comes first leaves its
     * part here and the second side acts. An end that comes first is LOAD_FAILED, or FINISHED
     * when it beat the exception being recorded; with no audio received, both mean the track
     * may have failed, and the queue waits for the exception side.
     * Every change is a compareAndSet, so exactly one side plays the hold or pulls the queue.
     */
    private final AtomicReference<Handoff> handoff = new AtomicReference<>();
    /**
     * Guards only the pure in-memory state touched by a "play now" queue replacement —
     * {@link #activeReplacementToken}, {@code queue}, {@code defaultQueue} — never a call into
     * the player ({@code playTrack}/{@code stopTrack}/{@code setPaused}) or
     * {@code closeAudioConnection}. lavaplayer dispatches every track event (including
     * {@code onTrackEnd}, which reaches {@link #continueAfterEnd}) under its own internal
     * track-switch lock, so code that holds <em>this</em> monitor must never call back into the
     * player: doing so risks a lock-order inversion (the player thread blocked on this monitor
     * inside {@code continueAfterEnd}, while this thread is blocked on the player's lock inside
     * {@code playTrack}) and a deadlock. Every method below follows the same shape: decide what
     * to do (and which track, if any, to start) while holding this lock, then act on that
     * decision after releasing it.
     */
    private final Object replacementLock = new Object();
    /**
     * Identifies the "play now" queue replacement currently in flight (see
     * {@link #beginQueueReplacement()}), or {@code null} if none is. While non-null,
     * {@code continueAfterEnd} neither re-queues a repeat clone nor applies the idle-queue
     * disconnect/default-playlist behaviour for a track end, since the queue is only
     * momentarily empty while the replacement's first track is still loading over the network.
     * A token (rather than a boolean) so a late call from a <em>superseded</em> replacement
     * (e.g. two "Play now" clicks in a row, or a loaded-track callback that fires after a newer
     * replacement already began) can only resolve the one it was handed, never a newer one.
     * Guarded by {@link #replacementLock}.
     */
    private Object activeReplacementToken;

    private enum HandoffKind
    {
        /** Exception first: a prompt was opened; the end should play {@code hold}. */
        OFFERED,
        /** Exception first: no prompt; the end should move on as usual. */
        DECLINED,
        /**
         * LOAD_FAILED end first, or FINISHED with no audio: the queue waits; the exception side
         * plays the hold or pulls (for FINISHED, a fallback moves on if no exception comes).
         */
        AWAITING,
        /** Any other end first (or a stop): the queue already moved on; a late hold is withdrawn. */
        ENDED
    }

    private record Handoff(AudioTrack track, HandoffKind kind, AudioTrack hold) {}

    /**
     * Similar-song Switch: the library copy replacing the requested track. Set just before the
     * replacing {@code playTrack} and cleared right after; lavaplayer delivers the start inline, so
     * only that event sees it, and the copy isn't added to history again. (The requested track's
     * REPLACED end needs no flag: {@code continueAfterEnd} ignores every REPLACED end.)
     */
    private final AtomicReference<AudioTrack> switchingTo = new AtomicReference<>();

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player)
    {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        // Use NO_OP listener in no-GUI mode to avoid memory allocation
        this.metricsListener = manager.getBot().isNoGUI() 
            ? AudioMetricsListener.NO_OP 
            : new PerformanceMetrics(guildId);

        int maxHistorySize = manager.getBot().getConfig().getMaxHistorySize();
        QueueType queueType = manager.getBot().getSettingsManager().getSettings(guildId).getQueueType();
        this.queue = queueType.createInstance(null, maxHistorySize);
    }

    public void setQueueType(QueueType type)
    {
        // History is preserved when switching queue types
        int maxHistorySize = manager.getBot().getConfig().getMaxHistorySize();
        queue = type.createInstance(queue, maxHistorySize);
    }

    public void setLastReason(String reason)
    {
        this.lastReason = reason;
    }

    public int addTrackToFront(QueuedTrack qtrack)
    {
        qtrack = wrapForQueue(qtrack);
        if(audioPlayer.getPlayingTrack()==null)
        {
            playPrepared(qtrack.getTrack());
            return -1;
        }
        else
        {
            LOGGER.debug("Added track to front of queue: {}", qtrack.getTrack().getInfo().title);
            queue.addAt(0, qtrack);
            return 0;
        }
    }

    public int addTrack(QueuedTrack qtrack)
    {
        qtrack = wrapForQueue(qtrack);
        if(audioPlayer.getPlayingTrack()==null)
        {
            playPrepared(qtrack.getTrack());
            return -1;
        }

        LOGGER.debug("Added track to queue: {}", qtrack.getTrack().getInfo().title);
        return queue.add(qtrack);
    }

    /**
     * Swaps in the library's {@link LibraryAudioTrack} for a local track whose file is already
     * in the yt-dlp library index, so the queue, history and saved playlists show the YouTube
     * title as soon as the track is queued (not just once it starts playing). Every other track
     * (YouTube, a local file not in the index, or one already wrapped) is returned unchanged.
     */
    private QueuedTrack wrapForQueue(QueuedTrack qtrack)
    {
        YtDlpService ytDlp = ytDlp();
        if (ytDlp == null || qtrack == null)
            return qtrack;
        AudioTrack track = qtrack.getTrack();
        AudioTrack wrapped;
        try
        {
            wrapped = ytDlp.wrapLibraryFile(track);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("yt-dlp library couldn't check track {} against the library index: {}",
                    track == null ? "null" : track.getIdentifier(), ex.toString());
            return qtrack;
        }
        if (wrapped == track)
            return qtrack;
        return new QueuedTrack(wrapped, qtrack.getRequestMetadata());
    }
    
    /**
     * Gets the playback history from the queue.
     * Most recent tracks are at index 0.
     * 
     * @return A list of previously played tracks
     */
    public List<QueuedTrack> getPreviousTracks()
    {
        return queue.getHistory().getList();
    }

    public AbstractQueue<QueuedTrack> getQueue()
    {
        return queue;
    }
    
    public void stopAndClear()
    {
        LOGGER.debug("Stopping and clearing queue");
        queue.clearAll();
        defaultQueue.clear();
        resetYtDlpHandoff();
        cancelYtDlpPrompt();
        audioPlayer.stopTrack();
        cancelYtDlpDownloads();
        clearQueuedMatchSuggestions();
        //current = null;
    }

    /**
     * Stops playback and clears only active queues while preserving history.
     * Playback history is maintained from track start events.
     */
    public void stopAndClearQueuePreserveHistory()
    {
        LOGGER.debug("Stopping playback and clearing queue (preserving history)");
        queue.clear();
        defaultQueue.clear();
        resetYtDlpHandoff();
        cancelYtDlpPrompt();
        audioPlayer.stopTrack();
        cancelYtDlpDownloads();
        clearQueuedMatchSuggestions();
    }

    /**
     * Begins a "play now" queue replacement: clears the active queues (preserving history),
     * cancels any open yt-dlp prompt/handoff for the currently playing track — the same way
     * {@link #stopAndClearQueuePreserveHistory()} does — and arms a guard that suppresses the
     * idle-queue disconnect (and repeat-mode re-queue) in {@code continueAfterEnd} while the
     * replacement's tracks are still loading over the network. The currently playing track is
     * NOT stopped here.
     * <p>
     * Callers must follow up, for every replacement track as it loads, with
     * {@link #replaceCurrentTrackIfStillPlaying(Object, AudioTrack)} (passing the token
     * returned here and the track that was playing, or {@code null}, when this method was
     * called), and once loading is done — or if loading fails before any track calls that —
     * with {@link #endQueueReplacement(Object)}.
     *
     * @return a token identifying this replacement; pass it to the two follow-up calls
     */
    public Object beginQueueReplacement()
    {
        LOGGER.debug("Beginning queue replacement (preserving history, deferring the empty-queue check)");
        // No player call here, so no ordering constraint with replacementLock; done first so a
        // prompt/handoff tied to the about-to-be-replaced track is settled before anything else.
        resetYtDlpHandoff();
        cancelYtDlpPrompt();
        cancelYtDlpDownloads();
        Object token = new Object();
        synchronized (replacementLock)
        {
            activeReplacementToken = token;
            queue.clear();
            defaultQueue.clear();
        }
        clearQueuedMatchSuggestions();
        return token;
    }

    /**
     * The loaded-track half of a "play now" replacement begun with
     * {@link #beginQueueReplacement()}. Safe to call once per loaded replacement track (only
     * the first call for the given {@code token}, while it's still the active replacement, does
     * anything); later calls — and any call for a {@code token} a newer {@code beginQueueReplacement()}
     * has since superseded — are no-ops.
     * <p>
     * If {@code original} (the track captured before the replacement began, or {@code null} if
     * nothing was playing then) is still the currently playing track, pulls the queue head
     * (the replacement track just queued by the caller) and starts it directly through
     * {@link #playPrepared(AudioTrack)} — the old track then ends with REPLACED, which
     * {@code continueAfterEnd} ignores entirely (no pull, no disconnect, no repeat re-queue).
     * If {@code original} is {@code null} or no longer playing (it already ended naturally
     * during the load, and {@link #addTrack(QueuedTrack)}'s own idle-start, or a normal
     * {@code continueAfterEnd} pull, already started the right track), this does nothing but
     * retire the token. Either way (when this call was the one that found the token still
     * active), unpauses the player: "Play now" means play.
     * <p>
     * Decides what to do while holding {@link #replacementLock}; the player call (if any)
     * happens after releasing it, so this never risks the lock-order inversion described there.
     *
     * @return true if this call actually replaced the current track
     */
    public boolean replaceCurrentTrackIfStillPlaying(Object token, AudioTrack original)
    {
        QueuedTrack head = null;
        boolean isActive;
        synchronized (replacementLock)
        {
            isActive = activeReplacementToken == token;
            if (isActive)
            {
                activeReplacementToken = null;
                if (original != null && audioPlayer.getPlayingTrack() == original)
                    head = queue.pull();
            }
        }
        if (!isActive)
            return false;
        boolean replaced = false;
        if (head != null)
        {
            playPrepared(head.getTrack());
            replaced = true;
        }
        audioPlayer.setPaused(false);
        return replaced;
    }

    /**
     * Ends a "play now" replacement begun with {@link #beginQueueReplacement()}, once loading
     * is done (whether or not any track was actually loaded) — or failed outright. A no-op if
     * {@link #replaceCurrentTrackIfStillPlaying(Object, AudioTrack)} already resolved
     * {@code token}, or if a newer replacement has since superseded it. Otherwise (e.g. the
     * playlist loaded zero tracks), if nothing ended up playing, applies the normal empty-queue
     * behaviour (default playlist, then disconnect unless staying) that was deferred while the
     * guard was armed. Decides under {@link #replacementLock}; the player/connection call (if
     * any) happens after releasing it.
     */
    public void endQueueReplacement(Object token)
    {
        boolean isActive;
        boolean shouldApplyEmptyQueueBehaviour = false;
        synchronized (replacementLock)
        {
            isActive = activeReplacementToken == token;
            if (isActive)
            {
                activeReplacementToken = null;
                if (audioPlayer.getPlayingTrack() == null && queue.isEmpty())
                    shouldApplyEmptyQueueBehaviour = true;
            }
        }
        if (shouldApplyEmptyQueueBehaviour)
            applyEmptyQueueBehaviour();
    }

    /** Cancels any open yt-dlp prompt/handoff for the currently playing track, without
     * stopping it or touching the queue — the same way a full stop would. Call before
     * explicitly replacing the current track outside of {@link #beginQueueReplacement()} (e.g.
     * a synchronous "play now" such as playing from history or a URL), so an open download
     * prompt for a gated hold doesn't linger after its track is replaced. */
    public void cancelCurrentTrackYtDlpPrompt()
    {
        resetYtDlpHandoff();
        cancelYtDlpPrompt();
    }

    /**
     * Starts {@code qtrack} immediately, replacing whatever is currently playing (that track
     * then ends with REPLACED, which {@code continueAfterEnd} ignores entirely — no pull, no
     * disconnect, no repeat re-queue) and unpausing the player ("Play now" means play). Wraps a
     * local library file the same way {@link #addTrack(QueuedTrack)}/
     * {@link #addTrackToFront(QueuedTrack)} do. Doesn't touch the queue. For a synchronous
     * "play now" (playing from history or a URL) that already knows something is playing — use
     * {@link #addTrackToFront(QueuedTrack)} instead when nothing is.
     */
    public void playNowReplacingCurrent(QueuedTrack qtrack)
    {
        qtrack = wrapForQueue(qtrack);
        playPrepared(qtrack.getTrack());
        audioPlayer.setPaused(false);
    }

    /**
     * Starts a track on the player, after giving the yt-dlp library a chance to swap in its
     * library copy. Every path that hands a track to the player goes through here.
     */
    public void playPrepared(AudioTrack track)
    {
        audioPlayer.playTrack(prepareForPlayback(track));
    }

    /**
     * Similar-song Switch: replaces {@code expected}, if it is still the playing track, with its
     * library copy through {@link #playPrepared}. Uses the player's replace semantics: the queue
     * isn't pulled and the bot doesn't leave, even with an empty queue. Takes no lock of its own.
     *
     * @return false (and nothing changes) if {@code expected} is no longer playing
     */
    public boolean replaceWithLibraryCopy(AudioTrack expected, AudioTrack replacement)
    {
        if (expected == null || replacement == null || audioPlayer.getPlayingTrack() != expected)
            return false;
        switchingTo.set(replacement);
        try
        {
            playPrepared(replacement);
        }
        finally
        {
            switchingTo.compareAndSet(replacement, null);
        }
        return true;
    }

    private AudioTrack prepareForPlayback(AudioTrack track)
    {
        YtDlpService ytDlp = ytDlp();
        if (ytDlp == null || track == null)
            return track;
        try
        {
            AudioTrack prepared = ytDlp.prepareForPlayback(track, guildId);
            if (prepared != track)
            {
                // The original never plays (its library copy does), so it never ends either: close
                // any suggestion posted for it while it was queued.
                endMatchSuggestion(track);
            }
            return prepared;
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("yt-dlp library couldn't prepare track {}, playing it as is: {}", track.getIdentifier(), ex.toString());
            return track;
        }
    }

    /** The yt-dlp service, or null when the bot has none (e.g. a mocked bot in tests). */
    private YtDlpService ytDlp()
    {
        return manager.getBot().getYtDlp();
    }

    private YtDlpPrompt ytDlpPrompt()
    {
        YtDlpService ytDlp = ytDlp();
        return ytDlp == null ? null : ytDlp.getPrompt();
    }

    private Guild guild()
    {
        JDA jda = manager.getBot().getJDA();
        return jda == null ? null : jda.getGuildById(guildId);
    }

    private void cancelYtDlpDownloads()
    {
        YtDlpService ytDlp = ytDlp();
        if (ytDlp != null)
            ytDlp.cancelGuild(guildId);
    }

    /**
     * Stop/leave: forgets any handoff in progress. A queue still waiting on a blocked track's
     * exception (AWAITING) becomes ENDED instead, so that late exception withdraws its prompt
     * rather than playing a hold or pulling the next song after the stop; it also makes a pending
     * FINISHED fallback do nothing. Runs before {@code stopTrack()}: the exception side runs under
     * the player's track-switch lock, so it either already played the hold (which the stop then
     * stops) or sees this.
     */
    private void resetYtDlpHandoff()
    {
        handoff.getAndUpdate(current -> current != null && current.kind() == HandoffKind.AWAITING
                ? new Handoff(current.track(), HandoffKind.ENDED, null)
                : null);
    }

    /** Stop/leave: claims an open prompt as "Cancelled" before the hold ends (and would say "Skipped"). */
    private void cancelYtDlpPrompt()
    {
        YtDlpPrompt prompt = ytDlpPrompt();
        if (prompt == null)
            return;
        try
        {
            prompt.cancel(guildId);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("yt-dlp couldn't cancel the prompt: {}", ex.toString());
        }
    }

    private void endYtDlpPrompt()
    {
        YtDlpPrompt prompt = ytDlpPrompt();
        if (prompt != null)
            prompt.onHoldEnded(guildId);
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
            playPrepared(defaultQueue.remove(0));
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
                playPrepared(at);
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
        metricsListener.onTrackEnd(trackTitle, trackUri);
        
        // Log track end with details for debugging
        if (endReason != AudioTrackEndReason.FINISHED) {
            LOGGER.debug("Track {} ended with reason: {} (Track: {})", 
                    track != null ? track.getIdentifier() : "null",
                    endReason.name(),
                    trackTitle != null ? trackTitle : "N/A");
        }
        else if (track != null && track.getInfo() != null) {
            LOGGER.debug("Track ended: {} Reason: {}", track.getInfo().title, endReason);
        }

        endMatchSuggestion(track);

        // yt-dlp prompt mode: a blocked YouTube track pauses the queue on its hold track
        if (resolveYtDlpHandoff(player, track, endReason))
            return;
        if (track instanceof LibraryAudioTrack libraryTrack && libraryTrack.isGated())
            endYtDlpPrompt();

        continueAfterEnd(player, track, endReason);
    }

    /**
     * The end side of the yt-dlp handoff (see {@link #handoff}).
     *
     * @return true if this end is fully handled: the hold now plays, or the queue waits for
     *         the exception callback to decide
     */
    private boolean resolveYtDlpHandoff(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
    {
        YtDlpPrompt prompt = ytDlpPrompt();
        while (true)
        {
            Handoff current = handoff.get();
            if (current != null && current.track() == track
                    && (current.kind() == HandoffKind.OFFERED || current.kind() == HandoffKind.DECLINED))
            {
                if (!handoff.compareAndSet(current, null))
                    continue;
                if (current.kind() == HandoffKind.DECLINED)
                    return false;
                // Normally LOAD_FAILED. FINISHED when the track played some audio before it failed
                // (lavaplayer reports LOAD_FAILED only for a failure before the first frame).
                if (endReason == AudioTrackEndReason.LOAD_FAILED || endReason == AudioTrackEndReason.FINISHED)
                {
                    player.playTrack(current.hold());
                    return true;
                }
                endYtDlpPrompt(); // the blocked track was stopped before its hold could start
                return false;
            }
            if (!isPromptCandidate(prompt, track))
                return false;
            // This end came before the exception callback (or it's a normal end of a candidate).
            // lavaplayer marks a failing track as ending, logs the failure (a full stack trace),
            // and only then records the exception, so an end in that window says FINISHED even
            // though the track failed. With no audio received, wait for the exception side too.
            boolean finishedWithoutAudio = endReason == AudioTrackEndReason.FINISHED && producedNoAudio(track);
            boolean await = endReason == AudioTrackEndReason.LOAD_FAILED || finishedWithoutAudio;
            Handoff mine = new Handoff(track, await ? HandoffKind.AWAITING : HandoffKind.ENDED, null);
            if (!handoff.compareAndSet(current, mine))
                continue;
            if (finishedWithoutAudio && !scheduleFinishedFallback(prompt, player, track, mine))
                return !handoff.compareAndSet(mine, null); // couldn't schedule: move on now
            return await;
        }
    }

    /**
     * A FINISHED end with no audio usually means the exception callback is on its way, but a
     * track can also just end empty. If no exception has settled the handoff after a few
     * seconds, the queue moves on (unless something else started playing meanwhile).
     *
     * @return false if the fallback couldn't be scheduled (the caller then moves on at once)
     */
    private boolean scheduleFinishedFallback(YtDlpPrompt prompt, AudioPlayer player, AudioTrack track, Handoff mine)
    {
        try
        {
            prompt.schedule(() -> {
                try
                {
                    if (handoff.compareAndSet(mine, null) && audioPlayer.getPlayingTrack() == null)
                        continueAfterEnd(player, track, AudioTrackEndReason.FINISHED);
                }
                catch (RuntimeException ex)
                {
                    LOGGER.warn("yt-dlp couldn't move on after track {} ended without audio: {}",
                            track.getIdentifier(), ex.toString());
                }
            }, FINISHED_WITHOUT_AUDIO_FALLBACK_SECONDS, TimeUnit.SECONDS);
            return true;
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp couldn't schedule the fallback for track {}: {}", track.getIdentifier(), ex.toString());
            return false;
        }
    }

    /**
     * Whether lavaplayer's frame buffer for this track never received audio. False if that
     * can't be told (not a lavaplayer track, or its executor is already gone).
     */
    private static boolean producedNoAudio(AudioTrack track)
    {
        try
        {
            return track instanceof InternalAudioTrack internal
                    && !internal.getActiveExecutor().getAudioBuffer().hasReceivedFrames();
        }
        catch (RuntimeException ex)
        {
            return false;
        }
    }

    /**
     * The exception side of the yt-dlp handoff (see {@link #handoff}), called once per failed
     * prompt candidate with the hold to play, or null if no prompt was opened.
     */
    private void settleYtDlpHandoff(YtDlpPrompt prompt, AudioTrack blocked, AudioTrack hold)
    {
        Handoff decision = new Handoff(blocked, hold != null ? HandoffKind.OFFERED : HandoffKind.DECLINED, hold);
        while (true)
        {
            Handoff current = handoff.get();
            if (current != null && current.track() == blocked && current.kind() == HandoffKind.AWAITING)
            {
                if (!handoff.compareAndSet(current, null))
                    continue;
                // The end came first and left the queue waiting for this decision. If something
                // else started on the idle player meanwhile (e.g. a play command), leave it be.
                if (audioPlayer.getPlayingTrack() != null)
                {
                    if (hold != null)
                        prompt.onHoldEnded(guildId);
                }
                else if (hold != null)
                    audioPlayer.playTrack(hold);
                else
                    continueAfterEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
                return;
            }
            if (current != null && current.track() == blocked && current.kind() == HandoffKind.ENDED)
            {
                if (!handoff.compareAndSet(current, null))
                    continue;
                // It ended some other way first (stopped, or the FINISHED race); the queue moved on.
                if (hold != null)
                    prompt.onHoldEnded(guildId);
                return;
            }
            if (handoff.compareAndSet(current, decision))
                return;
        }
    }

    private static boolean isPromptCandidate(YtDlpPrompt prompt, AudioTrack track)
    {
        try
        {
            return prompt != null && track != null && prompt.isCandidate(track);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp candidate check failed: {}", ex.toString());
            return false;
        }
    }

    /**
     * What happens after a track ends: repeat, then the next song (or default playlist, or
     * leaving). Decides what to do (and which track, if any, to start) while holding
     * {@link #replacementLock} — touching only {@code queue}/{@code activeReplacementToken},
     * never calling into the player — then acts on that decision after releasing it. This
     * method itself is NOT synchronized: it runs on lavaplayer's own event-dispatch thread,
     * under lavaplayer's internal track-switch lock, so it must never block waiting for a lock
     * that a replacement call (on another thread) might be holding while itself waiting on the
     * track-switch lock inside a player call — see {@link #replacementLock}.
     */
    private void continueAfterEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
    {
        if (endReason == AudioTrackEndReason.REPLACED)
        {
            // The new track was already started directly by an explicit replace (e.g.
            // "Play now" — see replaceCurrentTrackIfStillPlaying); nothing more to do here.
            return;
        }

        QueuedTrack toPlay = null;
        boolean shouldApplyEmptyQueueBehaviour = false;
        synchronized (replacementLock)
        {
            // A "play now" queue replacement is loading its first track: the queue is only
            // momentarily empty (or about to be discarded), so neither the repeat re-queue nor
            // the idle-queue behaviour below should run yet. See beginQueueReplacement()/endQueueReplacement().
            boolean replacementPending = activeReplacementToken != null;

            RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
            // if the track ended normally, and we're in repeat mode, re-add it to the queue
            if(!replacementPending && endReason==AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF)
            {
                QueuedTrack clone = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
                if(repeatMode == RepeatMode.ALL)
                {
                    queue.add(clone);
                    lastReason = "Repeating the queue.";
                }
                else
                {
                    queue.addAt(0, clone);
                    lastReason = "Repeating the song.";
                }
            }

            if(queue.isEmpty())
            {
                if (!replacementPending)
                    shouldApplyEmptyQueueBehaviour = true;
            }
            else
            {
                toPlay = queue.pull();
                if (lastReason == null || (!lastReason.startsWith("Repeating") && !lastReason.startsWith("Skipped")))
                    lastReason = "Playing next song.";
            }
        }

        // Act outside the lock: playPrepared/applyEmptyQueueBehaviour call into the player
        // (and possibly closeAudioConnection), which must never happen while replacementLock is held.
        if (toPlay != null)
            playPrepared(toPlay.getTrack());
        else if (shouldApplyEmptyQueueBehaviour)
            applyEmptyQueueBehaviour();
    }

    /** The idle-queue fallback: play the default playlist, or (unless staying) disconnect. */
    private void applyEmptyQueueBehaviour()
    {
        if(!playFromDefault())
        {
            lastReason = null;
            manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null);
            if(!manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
            // unpause, in the case when the player was paused and the track has been skipped.
            // this is to prevent the player being paused next time it's being used.
            audioPlayer.setPaused(false);
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
        metricsListener.onTrackException(trackTitle, trackUri);
        handleYtDlpException(track, exception);
        
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

    /** yt-dlp: offer a download for a blocked YouTube track, or report a failed library track. */
    private void handleYtDlpException(AudioTrack track, FriendlyException exception)
    {
        YtDlpPrompt prompt = ytDlpPrompt();
        if (prompt == null)
            return;
        if (track instanceof LibraryAudioTrack)
        {
            try
            {
                prompt.notifyDownloadFailed(guild(), track, exception);
            }
            catch (RuntimeException ex)
            {
                LOGGER.warn("yt-dlp couldn't report the failure of track {}: {}", track.getIdentifier(), ex.toString());
            }
            return;
        }
        if (!isPromptCandidate(prompt, track))
            return;
        AudioTrack hold = null;
        try
        {
            if (prompt.shouldOffer(track, exception))
                hold = prompt.holdFor(guild(), track).orElse(null);
        }
        catch (RuntimeException ex)
        {
            LOGGER.warn("yt-dlp couldn't handle the failure of track {}: {}", track.getIdentifier(), ex.toString());
        }
        // Always settle, prompt or not: an end that came first may be waiting on this decision.
        settleYtDlpHandoff(prompt, track, hold);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track)
    {
        recentFrames.clear();

        // Access the metadata object
        var info = track.getInfo();

        LOGGER.debug("Track Started Details:");
        LOGGER.debug(" - Title:      {}", info.title);
        LOGGER.debug(" - Author:     {}", info.author);
        LOGGER.debug(" - Duration:   {} ms", info.length);
        LOGGER.debug(" - Identifier: {}", info.identifier);
        LOGGER.debug(" - URI:        {}", info.uri);
        LOGGER.debug(" - Is Stream:  {}", info.isStream);
        LOGGER.debug(" - Source:     {}", track.getSourceManager() != null ? track.getSourceManager().getSourceName() : "unknown");
        LOGGER.debug(" - Player Vol: {}", player.getVolume());
        LOGGER.debug(" - Is Paused:  {}", player.isPaused());
        votes.clear();
        metricsListener.onSessionReset(); // Reset metrics for new track

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
        metricsListener.onTrackStart(trackTitle, trackUri);
        // A yt-dlp prompt's hold isn't recorded: the blocked YouTube track it stands in for
        // already is, and replaying that asks again instead of downloading unasked. Nor is a
        // library copy switched in for the requested song (replaying that follows its link).
        boolean switchedIn = track != null && switchingTo.compareAndSet(track, null);
        if (track != null && !switchedIn && !(track instanceof LibraryAudioTrack libraryTrack && libraryTrack.isGated()))
        {
            QueuedTrack startedTrack = new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class));
            queue.addToHistory(startedTrack);
        }

        if (lastReason == null)
            lastReason = "Playing next song.";

        prefetchNext();

        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track);

        considerMatchSuggestion(track);
    }

    /** Similar-song matching: may suggest a library copy of this track (in the background). */
    private void considerMatchSuggestion(AudioTrack track)
    {
        MatchSuggestions suggestions = matchSuggestions();
        if (suggestions == null)
            return;
        try
        {
            suggestions.consider(guildId, track);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp couldn't consider a library suggestion: {}", ex.toString());
        }
    }

    /** Similar-song matching: a suggestion for this track expires when it ends. */
    private void endMatchSuggestion(AudioTrack track)
    {
        MatchSuggestions suggestions = matchSuggestions();
        if (suggestions == null || track == null)
            return;
        try
        {
            suggestions.onTrackEnded(guildId, track);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp couldn't expire a library suggestion: {}", ex.toString());
        }
    }

    /**
     * Similar-song matching: may suggest a library copy (in the background) for a single track
     * just queued behind others. Callers pass the raw add result's queue position ({@code >= 0});
     * a track that started immediately never reaches here. Not called for a playlist load.
     */
    public void considerQueuedSuggestion(QueuedTrack queued)
    {
        MatchSuggestions suggestions = matchSuggestions();
        if (suggestions == null || queued == null)
            return;
        try
        {
            suggestions.considerQueued(guildId, queued);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp couldn't consider a queued library suggestion: {}", ex.toString());
        }
    }

    /** Similar-song matching: queued suggestions close when the queue they were posted for is cleared. */
    private void clearQueuedMatchSuggestions()
    {
        MatchSuggestions suggestions = matchSuggestions();
        if (suggestions == null)
            return;
        try
        {
            suggestions.onQueueCleared(guildId);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp couldn't clear queued library suggestions: {}", ex.toString());
        }
    }

    private MatchSuggestions matchSuggestions()
    {
        YtDlpService ytDlp = ytDlp();
        return ytDlp == null ? null : ytDlp.getMatchSuggestions();
    }
    
    /** yt-dlp {@code always} mode: start downloading the next song while this one plays. */
    private void prefetchNext()
    {
        YtDlpService ytDlp = ytDlp();
        if (ytDlp == null || !ytDlp.isEnabled() || ytDlp.mode() != YtDlpSettings.Mode.ALWAYS || queue.isEmpty())
            return;
        try
        {
            ytDlp.prefetch(queue.get(0).getTrack(), guildId);
        }
        catch (RuntimeException ex)
        {
            LOGGER.debug("yt-dlp prefetch skipped: {}", ex.toString());
        }
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs)
    {
        // A library track waits (for a prompt answer or its download) before its first frame;
        // that is expected, not a decoder stall.
        if (track instanceof LibraryAudioTrack && track.getPosition() == 0)
        {
            LOGGER.debug("Library track {} is waiting for its yt-dlp download", track.getIdentifier());
            return;
        }
        // Record the stuck event in performance metrics
        String trackTitle = null;
        String trackUri = null;
        
        if (track != null && track.getInfo() != null) {
            trackTitle = track.getInfo().title;
            trackUri = track.getInfo().uri;
        }
        
        metricsListener.onTrackStuck(thresholdMs, trackTitle, trackUri);
        
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
            audioPlayer.getVolume(),
            queue.size(),
            queue.getHistory().size(),
            isCurrentTrackFavorited(),
            lastReason
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
        // A yt-dlp prompt's hold track is "playing" but waits for an answer: show it as paused.
        if (audioPlayer.getPlayingTrack() instanceof LibraryAudioTrack libraryTrack && libraryTrack.isAwaitingGate())
            return PAUSE_EMOJI;
        return audioPlayer.isPaused() ? PAUSE_EMOJI : PLAY_EMOJI;
    }

    public void markCurrentTrackFavorited(String uri)
    {
        favoritedTrackUri = uri;
    }

    public boolean isCurrentTrackFavorited()
    {
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();
        if (currentTrack == null || currentTrack.getInfo() == null)
            return false;
        String currentUri = currentTrack.getInfo().uri;
        return currentUri != null && currentUri.equals(favoritedTrackUri);
    }
    
    // Audio Send Handler methods
    @Override
    public boolean canProvide()
    {
        long startNanos = System.nanoTime();
        lastFrame = audioPlayer.provide();
        long latencyNanos = System.nanoTime() - startNanos;

        boolean frameAvailable = lastFrame != null;
        metricsListener.onFrameProvided(frameAvailable, latencyNanos);
        if (frameAvailable && shouldTrackRecentFrames())
        {
            recentFrames.add(lastFrame.getData(), lastFrame.getTimecode());
        }

        return frameAvailable;
    }

    /** Whether the Switch alignment ring should be filled in: yt-dlp matching and alignment both on. */
    private boolean shouldTrackRecentFrames()
    {
        YtDlpService ytDlp = ytDlp();
        return ytDlp != null && ytDlp.isEnabled() && ytDlp.settings().matchingEnabled()
                && ytDlp.settings().alignSwitch();
    }

    /** A snapshot of the ring of recently sent Opus frames, for Switch alignment. */
    public RecentFrames.Snapshot snapshotRecentFrames()
    {
        return recentFrames.snapshot();
    }

    /**
     * Gets the performance metrics for this audio handler.
     *
     * @return the performance metrics instance, or null if running in no-GUI mode
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return metricsListener instanceof PerformanceMetrics
            ? (PerformanceMetrics) metricsListener
            : null;
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
