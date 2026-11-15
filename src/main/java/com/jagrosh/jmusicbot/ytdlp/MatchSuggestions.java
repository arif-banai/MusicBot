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
package com.jagrosh.jmusicbot.ytdlp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RecentFrames;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suggests a library song that is probably the same as a song now playing, or queued behind
 * others, and handles the "Switch to library copy" and "Not the same" buttons. See
 * docs/ytdlp-library.md, "Matching".
 * <p>
 * Threading: {@link #consider}, {@link #considerQueued} and {@link #onTrackEnded} are called
 * from player events (which lavaplayer delivers while holding its track-switch lock), so they
 * only touch a {@link ConcurrentHashMap} and queue Discord calls; the library scan runs on the
 * scheduler. A press claims its suggestion with an atomic remove, so each suggestion is answered
 * at most once, and only then calls into the player, holding no lock of its own.
 *
 * @author Arif Banai (arif-banai)
 */
public final class MatchSuggestions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchSuggestions.class);
    /** How long the buttons stay usable if the track hasn't ended first. */
    static final long EXPIRY_MINUTES = 10;
    static final String EXPIRED = "This suggestion has expired.";
    private static final String SWITCHED = "🔁 Switched to the library copy (linked for next time)";
    private static final String NOT_IN_QUEUE = "That song is no longer in the queue.";

    private final Bot bot;
    private final YtDlpService service;
    private final @Nullable YtDlpPrompt prompt;
    private final ScheduledExecutorService scheduler;
    /** Runs the alignment job off the caller's (JDA event) thread; owned by {@link YtDlpService}. */
    private final ExecutorService alignmentExecutor;
    private final Aligner aligner;
    /**
     * The open suggestions per guild, keyed by suggestion id: several can be open at once (one
     * per queued song that matched, plus at most one for the currently playing track).
     */
    private final ConcurrentHashMap<Long, Map<Long, Suggestion>> open = new ConcurrentHashMap<>();
    /** Suggestion ids. Starts handing out ids at 1; id 0 always means expired (the old button format). */
    private final AtomicLong nextId = new AtomicLong();
    /**
     * Per guild, the (requested key, library key) pairs offered this run. A pair is offered at most
     * once per run, whatever came of it, so repeat mode doesn't post the same suggestion every loop.
     */
    private final ConcurrentHashMap<Long, Set<List<String>>> offered = new ConcurrentHashMap<>();

    /** One posted suggestion: the requested track and the library song offered for it. */
    private static final class Suggestion {
        final long id;
        final long guildId;
        final AudioTrack track;
        final String requestedKey;
        final LibraryEntry entry;
        final long requesterId;
        final long channelId;
        final String text;
        /** Whether this was posted for a track still waiting in the queue, rather than playing. */
        final boolean queued;
        volatile long messageId;
        volatile @Nullable ScheduledFuture<?> expiry;

        Suggestion(long id, long guildId, AudioTrack track, String requestedKey, LibraryEntry entry,
                   long requesterId, long channelId, String text, boolean queued) {
            this.id = id;
            this.guildId = guildId;
            this.track = track;
            this.requestedKey = requestedKey;
            this.entry = entry;
            this.requesterId = requesterId;
            this.channelId = channelId;
            this.text = text;
            this.queued = queued;
        }
    }

    /** What a Switch (or queued-removed) press tells the user: a public edit of the suggestion
     *  message, or (queued-gone only) an ephemeral reply that leaves its text alone and clears
     *  its buttons separately. */
    private record SwitchOutcome(String reply, boolean ephemeralOnly) {
        static SwitchOutcome publicEdit(String reply) {
            return new SwitchOutcome(reply, false);
        }

        static SwitchOutcome ephemeral(String reply) {
            return new SwitchOutcome(reply, true);
        }
    }

    /**
     * Seam for the Switch alignment job (see docs/ytdlp-library.md, "Switch alignment"),
     * so tests can stub it without touching the real decode. The default just calls
     * {@link AlignmentJob#run}.
     */
    @FunctionalInterface
    public interface Aligner {
        Aligner DEFAULT = AlignmentJob::run;

        Optional<SwitchAligner.Alignment> run(RecentFrames.Snapshot snapshot, Path libraryFile,
                                              long streamLengthMs, long libraryLengthMs);
    }

    public MatchSuggestions(Bot bot, YtDlpService service, @Nullable YtDlpPrompt prompt,
                            ScheduledExecutorService scheduler, ExecutorService alignmentExecutor) {
        this(bot, service, prompt, scheduler, alignmentExecutor, Aligner.DEFAULT);
    }

    /** @param aligner lets tests stub the alignment job; production always passes {@link Aligner#DEFAULT}. */
    public MatchSuggestions(Bot bot, YtDlpService service, @Nullable YtDlpPrompt prompt,
                            ScheduledExecutorService scheduler, ExecutorService alignmentExecutor, Aligner aligner) {
        this.bot = bot;
        this.service = service;
        this.prompt = prompt;
        this.scheduler = scheduler;
        this.alignmentExecutor = alignmentExecutor;
        this.aligner = aligner;
    }

    /**
     * Called when a track starts. If yt-dlp and matching are on and the track is a requested
     * song from a supported source (not a library copy, not a stream), looks for a similar
     * library song in the background and posts at most one suggestion. Never blocks.
     */
    public void consider(long guildId, AudioTrack track) {
        if (!service.isEnabled() || !service.settings().matchingEnabled() || track == null
                || track instanceof LibraryAudioTrack) {
            return;
        }
        AudioTrackInfo info = track.getInfo();
        if (info == null || info.isStream || LibraryKeys.of(track).isEmpty()) {
            return;
        }
        try {
            scheduler.execute(() -> {
                try {
                    suggest(guildId, track);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Couldn't look for a similar library song: {}", ex.toString());
                }
            });
        } catch (RuntimeException ex) { // e.g. the scheduler is shut down
            LOGGER.debug("Skipped looking for a similar library song: {}", ex.toString());
        }
    }

    /**
     * Called when a single track is queued behind others (never for a playlist load). Looks for
     * a similar library song in the background and posts a suggestion right away, so Switch can
     * swap the queue slot before the stream ever plays. Never blocks.
     */
    public void considerQueued(long guildId, QueuedTrack queued) {
        if (!service.isEnabled() || !service.settings().matchingEnabled() || queued == null) {
            return;
        }
        AudioTrack track = queued.getTrack();
        if (track == null || track instanceof LibraryAudioTrack) {
            return;
        }
        AudioTrackInfo info = track.getInfo();
        if (info == null || info.isStream || LibraryKeys.of(track).isEmpty()) {
            return;
        }
        if (service.mode() == YtDlpSettings.Mode.ALWAYS && YtDlpService.isYoutube(track)) {
            // Always mode plays this from the library anyway (prepareForPlayback swaps it), so the
            // suggestion could never be answered for the track it was posted for.
            return;
        }
        try {
            scheduler.execute(() -> {
                try {
                    suggestQueued(guildId, track);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Couldn't look for a similar library song (queued): {}", ex.toString());
                }
            });
        } catch (RuntimeException ex) { // e.g. the scheduler is shut down
            LOGGER.debug("Skipped looking for a similar library song (queued): {}", ex.toString());
        }
    }

    /** Called when a track ends: every open suggestion for it (there's normally at most one) expires. */
    public void onTrackEnded(long guildId, AudioTrack track) {
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions == null) {
            return;
        }
        for (Suggestion suggestion : List.copyOf(guildSuggestions.values())) {
            if (suggestion.track == track && guildSuggestions.remove(suggestion.id, suggestion)) {
                close(suggestion);
            }
        }
    }

    /**
     * Called when the queue is cleared (a full stop, or "play now"): every suggestion for a song
     * that was still waiting in the queue closes, since it will never start now. A suggestion for
     * the currently playing track (including one posted while it was queued) is left for
     * {@link #onTrackEnded} to close once it actually ends.
     */
    public void onQueueCleared(long guildId) {
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions == null) {
            return;
        }
        AudioHandler handler = handler(guild(guildId));
        AudioTrack current = handler == null ? null : handler.getPlayer().getPlayingTrack();
        for (Suggestion suggestion : List.copyOf(guildSuggestions.values())) {
            if (suggestion.queued && current != suggestion.track && guildSuggestions.remove(suggestion.id, suggestion)) {
                close(suggestion);
            }
        }
    }

    // ---- posting ----

    private void suggest(long guildId, AudioTrack track) {
        Guild guild = guild(guildId);
        AudioHandler handler = handler(guild);
        if (handler == null || handler.getPlayer().getPlayingTrack() != track) {
            return;
        }
        if (prompt != null && prompt.isOpen(guildId)) {
            return;
        }
        if (hasOpenSuggestionFor(guildId, track)) {
            // Already offered while it was queued; it just started. Keep that suggestion (and its
            // still-live buttons) instead of posting (and logging a decision) again.
            return;
        }
        String key = LibraryKeys.of(track).orElse(null);
        if (key == null || service.library().resolve(key).isPresent()) {
            return;
        }
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm == null || rm.channelId == 0L) {
            return;
        }
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, rm.channelId);
        if (channel == null || !channel.canTalk()) {
            return;
        }
        MatchDecision decision = decide(key, track.getInfo(), guildId);
        LOGGER.debug(decision.logLine(key));
        LibraryEntry entry = decision.entry();
        if (decision.reason() != MatchDecision.Reason.SUGGESTED || entry == null) {
            return;
        }
        postSuggestion(guildId, channel, track, key, entry, rm.getOwner(), false,
                () -> handler.getPlayer().getPlayingTrack() == track);
    }

    private void suggestQueued(long guildId, AudioTrack track) {
        Guild guild = guild(guildId);
        if (guild == null) {
            return;
        }
        String key = LibraryKeys.of(track).orElse(null);
        if (key == null || service.library().resolve(key).isPresent()) {
            return;
        }
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm == null || rm.channelId == 0L) {
            return;
        }
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, rm.channelId);
        if (channel == null || !channel.canTalk()) {
            return;
        }
        MatchDecision decision = decide(key, track.getInfo(), guildId);
        LOGGER.debug(decision.logLine(key));
        LibraryEntry entry = decision.entry();
        if (decision.reason() != MatchDecision.Reason.SUGGESTED || entry == null) {
            return;
        }
        postSuggestion(guildId, channel, track, key, entry, rm.getOwner(), true, () -> true);
    }

    /** True if some open suggestion in this guild already points at this exact track instance. */
    private boolean hasOpenSuggestionFor(long guildId, AudioTrack track) {
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions == null) {
            return false;
        }
        for (Suggestion suggestion : guildSuggestions.values()) {
            if (suggestion.track == track) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers and posts a new suggestion, unless the pair was already offered this run, or
     * {@code stillValid} says the moment has already passed (the track ended during the scan, for
     * a play-time suggestion).
     */
    private void postSuggestion(long guildId, GuildMessageChannel channel, AudioTrack track, String key,
                                 LibraryEntry entry, long requesterId, boolean queued, BooleanSupplier stillValid) {
        List<String> pair = List.of(key, entry.key());
        long id = nextId.incrementAndGet();
        Suggestion suggestion = new Suggestion(id, guildId, track, key, entry, requesterId, channel.getIdLong(),
                text(entry), queued);
        Map<Long, Suggestion> guildSuggestions = open.computeIfAbsent(guildId, g -> new ConcurrentHashMap<>());
        guildSuggestions.put(id, suggestion);
        if (!stillValid.getAsBoolean()) {
            // It ended (or was removed) during the scan, before its end could find this suggestion.
            guildSuggestions.remove(id, suggestion);
            return;
        }
        if (!offered.computeIfAbsent(guildId, gid -> ConcurrentHashMap.newKeySet()).add(pair)) {
            guildSuggestions.remove(id, suggestion);
            return;
        }
        LOGGER.info("Suggesting library song {} for {} in guild {}{}", entry.key(), key, guildId,
                queued ? " (queued)" : "");
        List<ActionRow> rows = List.of(ActionRow.of(
                Button.primary(ComponentIdParsers.ytDlpMatchButtonId("switch", requesterId, id), "Switch to library copy"),
                Button.secondary(ComponentIdParsers.ytDlpMatchButtonId("reject", requesterId, id), "Not the same")));
        try {
            suggestion.expiry = scheduler.schedule(() -> expire(guildId, suggestion), EXPIRY_MINUTES, TimeUnit.MINUTES);
            channel.sendMessage(suggestion.text).setComponents(rows).queue(
                    message -> onSent(guildId, suggestion, message),
                    error -> {
                        LOGGER.debug("Couldn't post the library suggestion: {}", error.toString());
                        if (guildSuggestions.remove(id, suggestion)) {
                            cancelExpiry(suggestion);
                        }
                    });
        } catch (RuntimeException ex) {
            LOGGER.debug("Couldn't post the library suggestion: {}", ex.toString());
            if (guildSuggestions.remove(id, suggestion)) {
                cancelExpiry(suggestion);
            }
        }
    }

    /** A library entry that actually matches the requested track, with its score. */
    private record Scored(LibraryEntry entry, double score) {
    }

    /**
     * Decides whether the requested track should be offered a similar library song, and why not
     * when it shouldn't: the library's best actual match for it whose file still exists, unless
     * that pair was rejected or already offered this run, or else the closest candidate found (by
     * title/artist similarity, ignoring duration and version words) with {@link SongMatcher#whyNot}
     * explaining it, or {@link MatchDecision.Reason#NO_CANDIDATE} when no library entry shares a
     * word with it. Files are checked only for matches, best first, so a scan does no disk IO for
     * the rest of the library.
     */
    private MatchDecision decide(String requestedKey, AudioTrackInfo info, long guildId) {
        YtDlpLibrary library = service.library();
        SongMatcher.Song requested = SongMatcher.of(info);
        long floorMs = service.settings().durationToleranceSeconds() * 1000L;
        double minScore = service.settings().minScore();
        List<LibraryEntry> entries = library.entries();

        List<Scored> matches = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            if (entry.key().equals(requestedKey) || library.isRejected(requestedKey, entry.key())) {
                continue;
            }
            OptionalDouble score = SongMatcher.matchScore(requested, entry, floorMs, minScore);
            if (score.isPresent()) {
                matches.add(new Scored(entry, score.getAsDouble()));
            }
        }
        // Stable: among equal scores, library order still decides.
        matches.sort(Comparator.comparingDouble(Scored::score).reversed());
        for (Scored match : matches) {
            if (library.find(match.entry().key()).isEmpty()) {
                continue;
            }
            List<String> pair = List.of(requestedKey, match.entry().key());
            if (offered.getOrDefault(guildId, Set.of()).contains(pair)) {
                return new MatchDecision(match.entry().key(), match.score(), MatchDecision.Reason.ALREADY_OFFERED,
                        null);
            }
            return new MatchDecision(match.entry().key(), match.score(), MatchDecision.Reason.SUGGESTED, match.entry());
        }

        // Nothing matched (or every match was excluded above): find the closest candidate to report.
        String closestKey = null;
        double closestRaw = 0.0;
        SongMatcher.Song closestSong = null;
        for (LibraryEntry entry : entries) {
            if (entry.key().equals(requestedKey)) {
                continue;
            }
            for (SongMatcher.Song candidate : SongMatcher.interpretations(entry)) {
                double raw = SongMatcher.rawScore(requested, candidate);
                if (raw > closestRaw) {
                    closestRaw = raw;
                    closestKey = entry.key();
                    closestSong = candidate;
                }
            }
        }
        if (closestKey == null) {
            return new MatchDecision(null, 0.0, MatchDecision.Reason.NO_CANDIDATE, null);
        }
        if (library.isRejected(requestedKey, closestKey)) {
            return new MatchDecision(closestKey, closestRaw, MatchDecision.Reason.REJECTED, null);
        }
        if (library.find(closestKey).isEmpty()) {
            return new MatchDecision(closestKey, closestRaw, MatchDecision.Reason.MISSING_FILE, null);
        }
        return new MatchDecision(closestKey, closestRaw, SongMatcher.whyNot(requested, closestSong, floorMs, minScore),
                null);
    }

    private void onSent(long guildId, Suggestion suggestion, Message message) {
        suggestion.messageId = message.getIdLong();
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions == null || guildSuggestions.get(suggestion.id) != suggestion) {
            // It closed (track ended, queue cleared) before the message arrived: don't leave live buttons.
            message.editMessageComponents().queue(null, error -> { });
        }
    }

    private void expire(long guildId, Suggestion suggestion) {
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions != null && guildSuggestions.remove(suggestion.id, suggestion)) {
            close(suggestion);
        }
    }

    /** Removes the buttons of a suggestion that has already been taken out of {@link #open}. */
    private void close(Suggestion suggestion) {
        cancelExpiry(suggestion);
        long messageId = suggestion.messageId;
        if (messageId == 0L) {
            return; // not sent yet; onSent removes the buttons
        }
        GuildMessageChannel channel = channel(suggestion);
        if (channel == null) {
            return;
        }
        try {
            channel.editMessageById(messageId, suggestion.text).setComponents().queue(null,
                    error -> LOGGER.debug("Couldn't expire the library suggestion: {}", error.toString()));
        } catch (RuntimeException ex) {
            LOGGER.debug("Couldn't expire the library suggestion: {}", ex.toString());
        }
    }

    private static void cancelExpiry(Suggestion suggestion) {
        ScheduledFuture<?> expiry = suggestion.expiry;
        if (expiry != null) {
            expiry.cancel(false);
        }
    }

    // ---- presses ----

    /** Handles a press of "Switch to library copy" ({@code switch}) or "Not the same" ({@code reject}). */
    public void handlePress(ButtonInteractionEvent event, ComponentIdParsers.YtDlpMatchButtonId id) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!YtDlpPrompt.canPress(bot, guild, member, id.requesterId())) {
            event.reply("Only the person who requested this song, or a DJ, can use these buttons.")
                    .setEphemeral(true).queue();
            return;
        }

        long guildId = guild.getIdLong();
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        Suggestion suggestion = guildSuggestions == null ? null : guildSuggestions.get(id.suggestionId());
        boolean sameMessage = suggestion != null && suggestion.requesterId == id.requesterId()
                && (suggestion.messageId == 0L || suggestion.messageId == event.getMessageIdLong());
        if (!sameMessage || !guildSuggestions.remove(id.suggestionId(), suggestion)) {
            event.editComponents().queue(null, error -> { });
            event.getHook().sendMessage(EXPIRED).setEphemeral(true).queue(null, error -> { });
            return;
        }
        // This press won: the track's end, a queue clear and the expiry timer now find nothing to close.
        cancelExpiry(suggestion);

        if ("reject".equals(id.action())) {
            event.editMessage("👍 Got it, won't suggest that again").setComponents().queue(null, error -> { });
            service.rememberRejection(suggestion.requestedKey, suggestion.entry.key());
            return;
        }
        // Acknowledge before touching the player/queue; the outcome follows through the hook.
        event.deferEdit().queue(null, error -> { });
        dispatchSwitch(guild, suggestion, event);
    }

    /**
     * Picks the right Switch path: if the suggested track is now playing (whether it was posted
     * at queue time or at play time), it tries to line up the library copy with the stream
     * before swapping, off this (caller's) thread (see {@link #switchPlaying}); the outcome
     * reaches the hook later. A queued suggestion whose track hasn't started yet swaps the queue
     * slot instead, starting at 0 (never aligned). A play-time suggestion whose track isn't
     * playing anymore has nothing left to switch: expired.
     */
    private void dispatchSwitch(Guild guild, Suggestion suggestion, ButtonInteractionEvent event) {
        AudioHandler handler = handler(guild);
        AudioTrack current = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if (handler != null && current == suggestion.track) {
            switchPlaying(guild, handler, current, suggestion, event);
            return;
        }
        if (suggestion.queued) {
            respond(event, switchQueuedEntry(handler, suggestion));
            return;
        }
        respond(event, SwitchOutcome.publicEdit(EXPIRED));
    }

    /**
     * Replaces the currently playing {@code current} with its library copy. Snapshots the ring
     * (when alignment is on) and kicks the alignment job off this thread, with a hard 1 s
     * timeout; the result reaches {@link #finishSwitchPlaying} later, which reads the stream's
     * position only then.
     */
    private void switchPlaying(Guild guild, AudioHandler handler, AudioTrack current, Suggestion suggestion,
                               ButtonInteractionEvent event) {
        Optional<LibraryEntry> entry = service.library().find(suggestion.entry.key());
        if (entry.isEmpty()) {
            respond(event, SwitchOutcome.publicEdit("The library copy is gone."));
            return;
        }
        LibraryEntry target = entry.get();
        AudioTrackInfo info = current.getInfo();
        long streamLengthMs = info == null ? 0L : info.length;
        long libraryLengthMs = target.lengthMs();
        boolean alignOn = service.settings().alignSwitch();
        RecentFrames.Snapshot snapshot = alignOn ? handler.snapshotRecentFrames() : null;
        Path libraryFile = target.file();
        try {
            alignmentExecutor.execute(() -> runAlignmentAndFinish(guild, suggestion, target, streamLengthMs,
                    libraryLengthMs, alignOn, snapshot, libraryFile, event));
        } catch (RuntimeException ex) { // e.g. the alignment executor is shut down
            LOGGER.debug("Couldn't schedule Switch alignment for {}: {}", suggestion.requestedKey, ex.toString());
            finishSwitchPlaying(guild, suggestion, target, Optional.empty(), "error", event);
        }
    }

    /**
     * Runs off the caller's thread: tries the alignment job (unless {@code alignOn} is false)
     * with a hard 1 s timeout, cancelling it on a timeout so its decode stops, then hands off to
     * {@link #finishSwitchPlaying} with the alignment (if any) and why there is none otherwise.
     */
    private void runAlignmentAndFinish(Guild guild, Suggestion suggestion, LibraryEntry target,
                                       long streamLengthMs, long libraryLengthMs, boolean alignOn,
                                       @Nullable RecentFrames.Snapshot snapshot, @Nullable Path libraryFile,
                                       ButtonInteractionEvent event) {
        Optional<SwitchAligner.Alignment> alignment = Optional.empty();
        String reason = "off";
        if (alignOn) {
            // Too little contiguous history means the job can't even try; say so rather than "rejected".
            reason = snapshot == null || snapshot.frames().size() < AlignmentJob.MIN_FRAMES
                    ? "short history" : "rejected";
            try {
                Future<Optional<SwitchAligner.Alignment>> future = alignmentExecutor.submit(
                        () -> aligner.run(snapshot, libraryFile, streamLengthMs, libraryLengthMs));
                try {
                    alignment = future.get(1, TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    future.cancel(true);
                    reason = "timeout";
                } catch (ExecutionException ex) {
                    future.cancel(true);
                    reason = "error";
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    reason = "interrupted";
                }
            } catch (RuntimeException ex) { // e.g. the alignment executor is shut down
                reason = "error";
            }
        }
        finishSwitchPlaying(guild, suggestion, target, alignment, reason, event);
    }

    /**
     * Re-checks that {@code suggestion.track} is still playing (it may have changed while the
     * alignment job ran): if so, reads its position now, right before the swap (the stream kept
     * playing while the job ran), turns the alignment into a start position (0 without one, or
     * when it lands out of range), logs the one summary line for this press, and replaces the
     * track with the library copy; otherwise nothing changes, and the press is reported as expired.
     *
     * @param noAlignmentReason why {@code alignment} is empty ("off", "rejected", "timeout", ...)
     */
    private void finishSwitchPlaying(Guild guild, Suggestion suggestion, LibraryEntry target,
                                     Optional<SwitchAligner.Alignment> alignment, String noAlignmentReason,
                                     ButtonInteractionEvent event) {
        AudioHandler handler = handler(guild);
        AudioTrack current = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if (handler == null || current != suggestion.track) {
            logAlignment(suggestion, alignment, "expired");
            respond(event, SwitchOutcome.publicEdit(EXPIRED));
            return;
        }
        long startMs = 0L;
        String outcome;
        if (alignment.isPresent()) {
            OptionalLong t = AlignmentJob.targetPosition(safePosition(current), alignment.get(), target.lengthMs());
            if (t.isPresent()) {
                startMs = t.getAsLong();
                outcome = "accepted";
            } else {
                outcome = "fallback(out-of-range)";
            }
        } else {
            outcome = "fallback(" + noAlignmentReason + ")";
        }
        logAlignment(suggestion, alignment, outcome);
        boolean replaced;
        try {
            LibraryAudioTrack copy = service.linkedCopy(current, suggestion.requestedKey, target);
            copy.setPosition(startMs);
            replaced = handler.replaceWithLibraryCopy(current, copy);
        } catch (RuntimeException ex) {
            LOGGER.warn("Couldn't switch to library song {}: {}", target.key(), ex.toString());
            respond(event, SwitchOutcome.publicEdit("Couldn't switch to the library copy."));
            return;
        }
        if (!replaced) {
            respond(event, SwitchOutcome.publicEdit(EXPIRED));
            return;
        }
        service.rememberLink(suggestion.requestedKey, target.key());
        LOGGER.info("Switched {} to library song {} in guild {}", suggestion.requestedKey, target.key(),
                suggestion.guildId);
        respond(event, SwitchOutcome.publicEdit(SWITCHED));
    }

    /** The one summary line logged per Switch press on a playing track. */
    private static void logAlignment(Suggestion suggestion, Optional<SwitchAligner.Alignment> alignment, String outcome) {
        LOGGER.info("Switch alignment for {}: r={} margin={} offset={} -> {}", suggestion.requestedKey,
                alignment.map(a -> String.format(Locale.ROOT, "%.2f", a.r())).orElse("-"),
                alignment.map(a -> String.format(Locale.ROOT, "%.2f", a.margin())).orElse("-"),
                alignment.map(a -> String.valueOf(a.offsetMs())).orElse("-"), outcome);
    }

    /**
     * Swaps the library copy into the queue slot that still holds the suggested track, keeping
     * its position and the requester's metadata, so the stream is never played. The copy starts
     * from the beginning: there's no "current position" for a song that hasn't started (and a
     * queued Switch never aligns). The swap only touches the slot found to hold the exact track
     * instance the suggestion was made for.
     */
    private SwitchOutcome switchQueuedEntry(@Nullable AudioHandler handler, Suggestion suggestion) {
        AbstractQueue<QueuedTrack> queue = handler == null ? null : handler.getQueue();
        if (queue == null) {
            return SwitchOutcome.ephemeral(NOT_IN_QUEUE);
        }
        Optional<LibraryEntry> entry = service.library().find(suggestion.entry.key());
        if (entry.isEmpty()) {
            return SwitchOutcome.publicEdit("The library copy is gone.");
        }
        AtomicBoolean swapped = new AtomicBoolean(false);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        queue.replaceAll(qt -> {
            if (swapped.get() || failure.get() != null || qt.getTrack() != suggestion.track) {
                return qt;
            }
            try {
                LibraryAudioTrack copy = service.linkedCopy(suggestion.track, suggestion.requestedKey, entry.get());
                QueuedTrack replacement = new QueuedTrack(copy, qt.getRequestMetadata());
                copy.setPosition(0L); // after wrapping: the QueuedTrack constructor may re-apply a start timestamp
                swapped.set(true);
                return replacement;
            } catch (RuntimeException ex) {
                failure.set(ex);
                return qt;
            }
        });
        RuntimeException ex = failure.get();
        if (ex != null) {
            LOGGER.warn("Couldn't switch queued song to library song {}: {}", suggestion.entry.key(), ex.toString());
            return SwitchOutcome.publicEdit("Couldn't switch to the library copy.");
        }
        if (!swapped.get()) {
            // E.g. it was removed, or skipped out of the queue, between the suggestion and this press.
            return SwitchOutcome.ephemeral(NOT_IN_QUEUE);
        }
        service.rememberLink(suggestion.requestedKey, entry.get().key());
        LOGGER.info("Switched queued {} to library song {} in guild {}", suggestion.requestedKey, entry.get().key(),
                suggestion.guildId);
        return SwitchOutcome.publicEdit(SWITCHED);
    }

    /** Sends a {@link SwitchOutcome}: a public edit of the suggestion message, or (queued-gone
     *  only) an ephemeral reply with the message's buttons cleared but its text left alone. */
    private static void respond(ButtonInteractionEvent event, SwitchOutcome outcome) {
        if (outcome.ephemeralOnly()) {
            event.getHook().editOriginalComponents().queue(null, error -> { });
            event.getHook().sendMessage(outcome.reply()).setEphemeral(true).queue(null, error -> { });
            return;
        }
        event.getHook().editOriginal(outcome.reply()).setComponents().queue(null, error -> { });
    }

    /** {@code track}'s position, or 0 if it can't be read. */
    private static long safePosition(AudioTrack track) {
        try {
            return Math.max(0L, track.getPosition());
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    // ---- helpers ----

    private static String text(LibraryEntry entry) {
        StringBuilder sb = new StringBuilder("🎵 Looks like **").append(escape(entry.title())).append("**");
        if (!entry.author().isBlank()) {
            sb.append(" by ").append(escape(entry.author()));
        }
        if (entry.lengthMs() > 0) {
            sb.append(" (").append(TimeUtil.formatTime(entry.lengthMs())).append(')');
        }
        return sb.append(" from your library.").toString();
    }

    private static String escape(String text) {
        return MarkdownSanitizer.escape(FormatUtil.filter(text));
    }

    private @Nullable Guild guild(long guildId) {
        JDA jda = bot.getJDA();
        return jda == null ? null : jda.getGuildById(guildId);
    }

    private @Nullable GuildMessageChannel channel(Suggestion suggestion) {
        Guild guild = guild(suggestion.guildId);
        return guild == null ? null : guild.getChannelById(GuildMessageChannel.class, suggestion.channelId);
    }

    private static @Nullable AudioHandler handler(@Nullable Guild guild) {
        if (guild == null) {
            return null;
        }
        return guild.getAudioManager().getSendingHandler() instanceof AudioHandler handler ? handler : null;
    }
}
