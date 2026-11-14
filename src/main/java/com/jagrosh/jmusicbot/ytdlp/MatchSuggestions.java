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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
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
 * Suggests a library song that is probably the same as the requested song now playing, and
 * handles the "Switch to library copy" and "Not the same" buttons. See the v2 design's
 * "Similar-song matching" section.
 * <p>
 * Threading: {@link #consider} and {@link #onTrackEnded} are called from player events (which
 * lavaplayer delivers while holding its track-switch lock), so they only touch a
 * {@link ConcurrentHashMap} and queue Discord calls; the library scan runs on the scheduler.
 * A press claims its suggestion with an atomic remove, so each suggestion is answered at most
 * once, and only then calls into the player, holding no lock of its own.
 *
 * @author Arif Banai (arif-banai)
 */
public final class MatchSuggestions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchSuggestions.class);
    /** How long the buttons stay usable if the track hasn't ended first. */
    static final long EXPIRY_MINUTES = 10;
    static final String EXPIRED = "This suggestion has expired.";

    private final Bot bot;
    private final YtDlpService service;
    private final @Nullable YtDlpPrompt prompt;
    private final ScheduledExecutorService scheduler;
    /** The open suggestion per guild. Compared by identity. */
    private final ConcurrentHashMap<Long, Suggestion> open = new ConcurrentHashMap<>();
    /**
     * Per guild, the (requested key, library key) pairs offered this run. A pair is offered at most
     * once per run, whatever came of it, so repeat mode doesn't post the same suggestion every loop.
     */
    private final ConcurrentHashMap<Long, Set<List<String>>> offered = new ConcurrentHashMap<>();

    /** One posted suggestion: the requested track now playing and the library song offered for it. */
    private static final class Suggestion {
        final long guildId;
        final AudioTrack track;
        final String requestedKey;
        final LibraryEntry entry;
        final long requesterId;
        final long channelId;
        final String text;
        volatile long messageId;
        volatile @Nullable ScheduledFuture<?> expiry;

        Suggestion(long guildId, AudioTrack track, String requestedKey, LibraryEntry entry, long requesterId,
                   long channelId, String text) {
            this.guildId = guildId;
            this.track = track;
            this.requestedKey = requestedKey;
            this.entry = entry;
            this.requesterId = requesterId;
            this.channelId = channelId;
            this.text = text;
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

    /** Called when a track ends: its suggestion's buttons expire. */
    public void onTrackEnded(long guildId, AudioTrack track) {
        Suggestion current = open.get(guildId);
        if (current != null && current.track == track && open.remove(guildId, current)) {
            close(current);
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
        Optional<LibraryEntry> match = bestMatch(key, track.getInfo());
        if (match.isEmpty()) {
            return;
        }

        LibraryEntry entry = match.get();
        List<String> pair = List.of(key, entry.key());
        if (offered.getOrDefault(guildId, Set.of()).contains(pair)) {
            return; // already offered this run (e.g. the previous loop of a repeated song)
        }
        long requesterId = rm.getOwner();
        Suggestion suggestion = new Suggestion(guildId, track, key, entry, requesterId, channel.getIdLong(),
                text(entry));
        Suggestion previous = open.put(guildId, suggestion);
        if (previous != null) {
            close(previous);
        }
        if (handler.getPlayer().getPlayingTrack() != track) {
            // It ended during the scan, before its end could find this suggestion.
            open.remove(guildId, suggestion);
            return;
        }
        if (!offered.computeIfAbsent(guildId, id -> ConcurrentHashMap.newKeySet()).add(pair)) {
            open.remove(guildId, suggestion);
            return;
        }
        LOGGER.info("Suggesting library song {} for {} in guild {}", entry.key(), key, guildId);
        List<ActionRow> rows = List.of(ActionRow.of(
                Button.primary(ComponentIdParsers.ytDlpMatchButtonId("switch", requesterId), "Switch to library copy"),
                Button.secondary(ComponentIdParsers.ytDlpMatchButtonId("reject", requesterId), "Not the same")));
        try {
            suggestion.expiry = scheduler.schedule(() -> expire(suggestion), EXPIRY_MINUTES, TimeUnit.MINUTES);
            channel.sendMessage(suggestion.text).setComponents(rows).queue(
                    message -> onSent(suggestion, message),
                    error -> {
                        LOGGER.debug("Couldn't post the library suggestion: {}", error.toString());
                        if (open.remove(guildId, suggestion)) {
                            cancelExpiry(suggestion);
                        }
                    });
        } catch (RuntimeException ex) {
            LOGGER.debug("Couldn't post the library suggestion: {}", ex.toString());
            if (open.remove(guildId, suggestion)) {
                cancelExpiry(suggestion);
            }
        }
    }

    /** The best library song for the requested track that matches, isn't rejected for it, and whose file exists. */
    private Optional<LibraryEntry> bestMatch(String requestedKey, AudioTrackInfo info) {
        YtDlpLibrary library = service.library();
        SongMatcher.Song requested = SongMatcher.of(info);
        long toleranceMs = service.settings().durationToleranceSeconds() * 1000L;
        double minScore = service.settings().minScore();
        LibraryEntry best = null;
        double bestScore = -1.0;
        for (LibraryEntry entry : library.entries()) {
            if (entry.key().equals(requestedKey) || library.isRejected(requestedKey, entry.key())) {
                continue;
            }
            OptionalDouble score = SongMatcher.matchScore(requested, entry, toleranceMs, minScore);
            if (score.isPresent() && score.getAsDouble() > bestScore && library.find(entry.key()).isPresent()) {
                best = entry;
                bestScore = score.getAsDouble();
            }
        }
        return Optional.ofNullable(best);
    }

    private void onSent(Suggestion suggestion, Message message) {
        suggestion.messageId = message.getIdLong();
        if (open.get(suggestion.guildId) != suggestion) {
            // It closed (track ended) before the message arrived: don't leave live buttons.
            message.editMessageComponents().queue(null, error -> { });
        }
    }

    private void expire(Suggestion suggestion) {
        if (open.remove(suggestion.guildId, suggestion)) {
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
        Suggestion suggestion = open.get(guildId);
        boolean sameMessage = suggestion != null && suggestion.requesterId == id.requesterId()
                && (suggestion.messageId == 0L || suggestion.messageId == event.getMessageIdLong());
        if (!sameMessage || !open.remove(guildId, suggestion)) {
            event.editComponents().queue(null, error -> { });
            event.getHook().sendMessage(EXPIRED).setEphemeral(true).queue(null, error -> { });
            return;
        }
        // This press won: the track's end and the expiry timer now find nothing to close.
        cancelExpiry(suggestion);

        if ("reject".equals(id.action())) {
            event.editMessage("👍 Got it, won't suggest that again").setComponents().queue(null, error -> { });
            service.rememberRejection(suggestion.requestedKey, suggestion.entry.key());
            return;
        }
        // Acknowledge before touching the player; the outcome follows through the hook.
        event.deferEdit().queue(null, error -> { });
        String reply = switchToLibraryCopy(guild, suggestion);
        event.getHook().editOriginal(reply).setComponents().queue(null, error -> { });
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
        return "🔁 Switched to the library copy (linked for next time)";
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
