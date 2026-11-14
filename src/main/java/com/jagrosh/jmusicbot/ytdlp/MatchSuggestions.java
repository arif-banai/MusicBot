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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
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
 * others, and handles the "Switch to library copy" and "Not the same" buttons. See the v2
 * design's "Similar-song matching" section and the 0.8.0 addendum's "Queue-time suggestions".
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

    public MatchSuggestions(Bot bot, YtDlpService service, @Nullable YtDlpPrompt prompt,
                            ScheduledExecutorService scheduler) {
        this.bot = bot;
        this.service = service;
        this.prompt = prompt;
        this.scheduler = scheduler;
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
     * the currently playing track is left for {@link #onTrackEnded} to close once it actually ends.
     */
    public void onQueueCleared(long guildId) {
        Map<Long, Suggestion> guildSuggestions = open.get(guildId);
        if (guildSuggestions == null) {
            return;
        }
        for (Suggestion suggestion : List.copyOf(guildSuggestions.values())) {
            if (suggestion.queued && guildSuggestions.remove(suggestion.id, suggestion)) {
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

    /**
     * Decides whether the requested track should be offered a similar library song, and why not
     * when it shouldn't: the library's best actual match for it, unless that pair was rejected,
     * already offered this run, or its file is gone, or else the closest candidate found (by raw
     * title/artist score, ignoring duration) with {@link SongMatcher#whyNot} explaining it, or
     * {@link MatchDecision.Reason#NO_CANDIDATE} when no library entry shares a word with it.
     */
    private MatchDecision decide(String requestedKey, AudioTrackInfo info, long guildId) {
        YtDlpLibrary library = service.library();
        SongMatcher.Song requested = SongMatcher.of(info);
        long floorMs = service.settings().durationToleranceSeconds() * 1000L;
        double minScore = service.settings().minScore();

        LibraryEntry bestEntry = null;
        double bestScore = -1.0;
        for (LibraryEntry entry : library.entries()) {
            if (entry.key().equals(requestedKey) || library.isRejected(requestedKey, entry.key())
                    || library.find(entry.key()).isEmpty()) {
                continue;
            }
            OptionalDouble score = SongMatcher.matchScore(requested, entry, floorMs, minScore);
            if (score.isPresent() && score.getAsDouble() > bestScore) {
                bestEntry = entry;
                bestScore = score.getAsDouble();
            }
        }
        if (bestEntry != null) {
            List<String> pair = List.of(requestedKey, bestEntry.key());
            if (offered.getOrDefault(guildId, Set.of()).contains(pair)) {
                return new MatchDecision(bestEntry.key(), bestScore, MatchDecision.Reason.ALREADY_OFFERED, null);
            }
            return new MatchDecision(bestEntry.key(), bestScore, MatchDecision.Reason.SUGGESTED, bestEntry);
        }

        // Nothing matched (or every match was excluded above): find the closest candidate to report.
        String closestKey = null;
        double closestRaw = 0.0;
        SongMatcher.Song closestSong = null;
        for (LibraryEntry entry : library.entries()) {
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
        String reply = switchSuggestion(guild, suggestion);
        event.getHook().editOriginal(reply).setComponents().queue(null, error -> { });
    }

    /**
     * Picks the right Switch path: if the suggested track is now playing (whether it was posted
     * at queue time or at play time), the play-time swap applies, same timestamp and all. A queued
     * suggestion whose track hasn't started yet swaps the queue slot instead. A play-time
     * suggestion whose track isn't playing anymore has nothing left to switch: expired.
     */
    private String switchSuggestion(Guild guild, Suggestion suggestion) {
        AudioHandler handler = handler(guild);
        AudioTrack current = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if (current == suggestion.track) {
            return switchToLibraryCopy(guild, suggestion);
        }
        if (suggestion.queued) {
            return switchQueuedEntry(handler, suggestion);
        }
        return switchToLibraryCopy(guild, suggestion);
    }

    /** Replaces the requested track with the library copy; returns the message to show. */
    private String switchToLibraryCopy(Guild guild, Suggestion suggestion) {
        AudioHandler handler = handler(guild);
        AudioTrack current = handler == null ? null : handler.getPlayer().getPlayingTrack();
        if (current != suggestion.track) {
            return EXPIRED;
        }
        Optional<LibraryEntry> entry = service.library().find(suggestion.entry.key());
        if (entry.isEmpty()) {
            return "The library copy is gone.";
        }
        boolean replaced;
        try {
            LibraryAudioTrack copy = service.linkedCopy(current, suggestion.requestedKey, entry.get());
            copy.setPosition(startPosition(current, entry.get()));
            replaced = handler.replaceWithLibraryCopy(current, copy);
        } catch (RuntimeException ex) {
            LOGGER.warn("Couldn't switch to library song {}: {}", suggestion.entry.key(), ex.toString());
            return "Couldn't switch to the library copy.";
        }
        if (!replaced) {
            return EXPIRED;
        }
        service.rememberLink(suggestion.requestedKey, entry.get().key());
        LOGGER.info("Switched {} to library song {} in guild {}", suggestion.requestedKey, entry.get().key(),
                suggestion.guildId);
        return SWITCHED;
    }

    /**
     * Swaps the library copy into the queue slot that still holds the suggested track, keeping
     * its position and the requester's metadata, so the stream is never played. The copy starts
     * from the beginning: there's no "current position" for a song that hasn't started. The swap
     * only touches the slot found to hold the exact track instance the suggestion was made for.
     */
    private String switchQueuedEntry(@Nullable AudioHandler handler, Suggestion suggestion) {
        AbstractQueue<QueuedTrack> queue = handler == null ? null : handler.getQueue();
        if (queue == null) {
            return NOT_IN_QUEUE;
        }
        Optional<LibraryEntry> entry = service.library().find(suggestion.entry.key());
        if (entry.isEmpty()) {
            return "The library copy is gone.";
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
            return "Couldn't switch to the library copy.";
        }
        if (!swapped.get()) {
            return NOT_IN_QUEUE;
        }
        service.rememberLink(suggestion.requestedKey, entry.get().key());
        LOGGER.info("Switched queued {} to library song {} in guild {}", suggestion.requestedKey, entry.get().key(),
                suggestion.guildId);
        return SWITCHED;
    }

    /** The requested track's position, kept below the library copy's length so it doesn't end at once. */
    private static long startPosition(AudioTrack current, LibraryEntry entry) {
        long position;
        try {
            position = Math.max(0L, current.getPosition());
        } catch (RuntimeException ex) {
            position = 0L;
        }
        long length = entry.lengthMs();
        if (length > 0 && position >= length - 1_000L) {
            position = Math.max(0L, length - 1_000L);
        }
        return position;
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
