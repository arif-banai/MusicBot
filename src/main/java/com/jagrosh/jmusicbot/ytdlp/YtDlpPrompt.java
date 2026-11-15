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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts.Pending;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts.PressOutcome;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.AllClientsFailedException;
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
 * Prompt mode: when YouTube blocks a song, the guild's queue pauses on a "hold" track and
 * the bot asks in the request channel whether to download the song with yt-dlp.
 * <p>
 * The hold is a gated {@link LibraryAudioTrack}: it sits on the player, producing no audio,
 * until a button opens its gate. Four things can end a prompt: a button press, the timeout,
 * the hold ending (skip, stop, replace), or the prompt message failing to send. Each of them
 * first claims the prompt with {@link PendingPrompts#press}; only the one that gets ACCEPTED
 * acts, so every prompt is answered at most once.
 * <p>
 * Discord calls are always queued, never awaited, because several of these methods run on
 * the player thread or on the downloader's single worker thread.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpPrompt {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpPrompt.class);
    private static final long PROGRESS_INTERVAL_MS = 5_000L;
    private static final int MAX_REASON_LENGTH = 300;

    private final Bot bot;
    private final YtDlpService service;
    private final PendingPrompts state;
    private final ScheduledExecutorService scheduler;
    /** Each open prompt's timeout task, by the prompt's gate (unique per prompt, compared by identity). */
    private final ConcurrentHashMap<CompletableFuture<Void>, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();

    public YtDlpPrompt(Bot bot, YtDlpService service, PendingPrompts state, ScheduledExecutorService scheduler) {
        this.bot = bot;
        this.service = service;
        this.state = state;
        this.scheduler = scheduler;
    }

    /**
     * True when the feature is on in prompt mode, the track is a (not yet wrapped) YouTube
     * track, and the exception is, or is caused by, youtube-source's {@code AllClientsFailedException}.
     */
    public boolean shouldOffer(AudioTrack track, FriendlyException e) {
        return isCandidate(track) && hasCause(e, AllClientsFailedException.class);
    }

    /**
     * Whether a failure of this track could open a prompt: the feature is on in prompt mode and
     * the track is a (not yet wrapped) YouTube track that isn't a live stream (yt-dlp can't
     * download one into the library). False whenever the feature is off.
     */
    public boolean isCandidate(AudioTrack track) {
        if (!service.isEnabled() || service.mode() != YtDlpSettings.Mode.PROMPT) {
            return false;
        }
        return track != null && !(track instanceof LibraryAudioTrack) && YtDlpService.isYoutube(track)
                && track.getInfo() != null && !track.getInfo().isStream;
    }

    /** Whether the guild has a prompt open (pressed or not). */
    public boolean isOpen(long guildId) {
        return state.get(guildId).isPresent();
    }

    /**
     * Runs {@code task} once after {@code delay} on the prompt scheduler (a single daemon thread).
     *
     * @throws java.util.concurrent.RejectedExecutionException if the scheduler is shut down
     */
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    /**
     * Stop, or the bot leaving: if no button was pressed yet, closes the guild's prompt and
     * says it was cancelled. Called before the player stops the hold, so the hold's own end
     * then finds the prompt already answered and doesn't also say "Skipped".
     */
    public void cancel(long guildId) {
        Optional<Pending> current = state.get(guildId);
        if (current.isEmpty()) {
            return;
        }
        Pending claimed = claim(guildId, current.get().gate());
        if (claimed != null) {
            edit(guildId, claimed.channelId(), claimed.messageId(), "⏹ Cancelled **" + title(claimed.hold()) + "**");
            claimed.gate().cancel(false); // a hold that hasn't started yet must not wait on it
        }
    }

    /**
     * Opens a prompt for a blocked track and returns the hold track to play in its place.
     * Returns empty, so the queue moves on as before, if the request channel can't be reached,
     * or the guild already has a prompt open.
     */
    public Optional<AudioTrack> holdFor(Guild guild, AudioTrack failed) {
        if (guild == null || failed == null || failed.getInfo() == null) {
            return Optional.empty();
        }
        RequestMetadata rm = failed.getUserData(RequestMetadata.class);
        if (rm == null || rm.channelId == 0L) {
            return Optional.empty();
        }
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, rm.channelId);
        if (channel == null || !channel.canTalk()) {
            return Optional.empty();
        }
        AudioTrack clone = failed.makeClone();
        if (clone == null || clone.getInfo() == null) {
            return Optional.empty();
        }

        long guildId = guild.getIdLong();
        long channelId = channel.getIdLong();
        String videoId = failed.getInfo().identifier;
        long requesterId = rm.getOwner();
        CompletableFuture<Void> gate = new CompletableFuture<>();
        LibraryAudioTrack hold = service.wrap(clone, guildId, true, gate);
        if (!state.open(new Pending(guildId, videoId, requesterId, channelId, hold, gate, 0L))) {
            return Optional.empty(); // one prompt per guild; this song is skipped as before
        }

        String title = title(hold);
        String text = emoji(BotConfig::getWarning, "⚠️") + " YouTube blocked **" + title
                + "**. Download it with yt-dlp? (skipping in " + formatSeconds(promptTimeoutSeconds()) + ")";
        List<ActionRow> rows = List.of(ActionRow.of(
                Button.primary(ComponentIdParsers.ytDlpButtonId("one", requesterId, videoId), "Download & play"),
                Button.secondary(ComponentIdParsers.ytDlpButtonId("all", requesterId, videoId),
                        "Download all YouTube songs in queue")));
        try {
            // Register the timeout first, so a send that fails at once cancels it through expire().
            timeouts.put(gate, scheduler.schedule(() -> expire(guildId, gate, true),
                    promptTimeoutSeconds(), TimeUnit.SECONDS));
            channel.sendMessage(text).setComponents(rows).queue(
                    message -> onPromptSent(guildId, gate, message, title),
                    error -> onPromptSendFailed(guildId, gate, error));
        } catch (RuntimeException ex) {
            // e.g. missing permissions thrown before the request, or the scheduler is shut down
            LOGGER.warn("Couldn't post the yt-dlp prompt for {}: {}", videoId, ex.toString());
            closeIfCurrent(guildId, gate);
            cancelTimeout(gate);
            return Optional.empty();
        }
        return Optional.of(hold);
    }

    /**
     * Called when a hold track ends for any reason other than starting its download (skip,
     * stop, replaced), or when the blocked track ended before its hold could start. If no
     * button was pressed yet, the prompt is closed and its message says the song was skipped.
     */
    public void onHoldEnded(long guildId) {
        Optional<Pending> current = state.get(guildId);
        if (current.isEmpty()) {
            return;
        }
        Pending claimed = claim(guildId, current.get().gate());
        if (claimed != null) {
            edit(guildId, claimed.channelId(), claimed.messageId(), skipped(claimed.hold()));
            claimed.gate().cancel(false); // the hold is gone; nothing may open its gate any more
        }
    }

    /**
     * Posts "yt-dlp couldn't download <title>: <reason>" in the track's request channel, if it
     * can be reached. Posts nothing for a cancelled download (stop, leaving, shutdown), nor for a
     * prompted hold's download failure, which its prompt message already reports.
     */
    public void notifyDownloadFailed(Guild guild, AudioTrack track, FriendlyException e) {
        if (guild == null || track == null || e == null || hasCause(e, CancellationException.class)) {
            return;
        }
        if (track instanceof LibraryAudioTrack library && library.isGated() && hasCause(e, YtDlpException.class)) {
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
        channel.sendMessage(emoji(BotConfig::getError, "❌") + " yt-dlp couldn't download **" + title(track)
                + "**: " + reason(e)).queue();
    }

    /**
     * Handles a press of "Download & play" ({@code one}) or "Download all YouTube songs in
     * queue" ({@code all}).
     */
    public void handlePress(ButtonInteractionEvent event, ComponentIdParsers.YtDlpButtonId id) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!canPress(bot, guild, member, id.requesterId())) {
            event.reply("Only the person who requested this song, or a DJ, can use these buttons.")
                    .setEphemeral(true).queue();
            return;
        }

        long guildId = guild.getIdLong();
        Pending before = state.get(guildId).orElse(null);
        boolean sameMessage = before != null
                && (before.messageId() == 0L || before.messageId() == event.getMessageIdLong());
        if (!sameMessage || state.press(guildId, id.videoId()) != PressOutcome.ACCEPTED) {
            event.editComponents().queue(null, error -> { });
            event.getHook().sendMessage("This prompt has expired.").setEphemeral(true).queue(null, error -> { });
            return;
        }

        // This press won. Only it acts from here on; the timeout and skip paths now get EXPIRED.
        Pending pending = state.get(guildId).filter(p -> p.gate() == before.gate()).orElse(before);
        closeIfCurrent(guildId, pending.gate());
        cancelTimeout(pending.gate());

        LibraryAudioTrack hold = pending.hold();
        String title = title(hold);
        PromptMessage message = new PromptMessage(guildId, pending.channelId(), event.getMessageIdLong(),
                emoji(BotConfig::getLoading, "⏳") + " Downloading **" + title + "**…");
        CompletableFuture<LibraryEntry> download = service.requestDownload(hold, guildId, true);
        pending.gate().complete(null);

        if ("all".equals(id.action())) {
            downloadQueue(guild, guildId, message);
        }

        // Acknowledge by editing the prompt itself. Later edits wait for this one, so they land after it.
        event.editMessage(message.render()).setComponents().queue(
                ok -> message.markReady(), error -> message.markReady());

        // The hold keeps playing the same track object, so no onTrackStart fires to refresh the
        // now-playing message; without this it would keep showing the yt-dlp waiting line forever.
        // After the acknowledgement, and guarded, so a failure here can't leave the click unanswered.
        try {
            bot.getNowplayingHandler().refreshNowPlaying(guildId);
        } catch (RuntimeException ex) {
            LOGGER.warn("Couldn't refresh the now-playing message after a yt-dlp prompt press in guild {}: {}",
                    guildId, ex.toString());
        }

        download.whenComplete((entry, error) -> {
            if (error == null) {
                message.setHead(isPlaying(guildId, hold)
                        ? "▶️ Playing **" + title + "**"
                        : emoji(BotConfig::getSuccess, "✅") + " Downloaded **" + title + "**");
            } else if (hasCause(error, CancellationException.class)) {
                message.setHead("⏹ Cancelled **" + title + "**");
            } else {
                message.setHead(emoji(BotConfig::getError, "❌") + " yt-dlp failed: " + reason(error));
            }
        });
    }

    /** Only the requester, or a member passing the DJ check, may press a prompt button. */
    public static boolean canPress(Bot bot, Guild guild, Member member, long requesterId) {
        if (member == null) {
            return false;
        }
        return member.getIdLong() == requesterId || DJCommand.checkDJPermission(bot, guild, member);
    }

    // ---- download all ----

    /**
     * Replaces every YouTube song still waiting in the guild's queue (not streams, not library
     * hits) with a library track, in queue order, and requests their downloads in that order.
     */
    private void downloadQueue(Guild guild, long guildId, PromptMessage message) {
        AudioHandler handler = handler(guild);
        if (handler == null) {
            return;
        }
        List<CompletableFuture<LibraryEntry>> downloads = new ArrayList<>();
        handler.getQueue().replaceAll(queued -> {
            AudioTrack track = queued.getTrack();
            if (!service.isDownloadable(track)) {
                return queued;
            }
            try {
                QueuedTrack replaced = new QueuedTrack(service.wrap(track, guildId, false, null),
                        queued.getRequestMetadata());
                downloads.add(service.requestDownload(track, guildId, false));
                return replaced;
            } catch (RuntimeException ex) {
                LOGGER.warn("Couldn't queue a yt-dlp download for {}: {}", track.getIdentifier(), ex.toString());
                return queued;
            }
        });

        if (downloads.isEmpty()) {
            message.setProgress("No other YouTube songs in the queue to download.", true);
            return;
        }
        Progress progress = new Progress(downloads.size(), message);
        message.setProgress(progress.render(), true);
        for (CompletableFuture<LibraryEntry> download : downloads) {
            download.whenComplete((entry, error) -> progress.record(error));
        }
    }

    /** Counts finished downloads for "Downloaded n/N, f failed". */
    private static final class Progress {
        private final int total;
        private final PromptMessage message;
        private int done;
        private int failed;
        private int cancelled;

        Progress(int total, PromptMessage message) {
            this.total = total;
            this.message = message;
        }

        void record(@Nullable Throwable error) {
            String text;
            boolean last;
            synchronized (this) {
                if (error == null) {
                    done++;
                } else if (hasCause(error, CancellationException.class)) {
                    cancelled++;
                } else {
                    failed++;
                }
                text = render();
                last = done + failed + cancelled >= total;
            }
            message.setProgress(text, last);
        }

        synchronized String render() {
            StringBuilder sb = new StringBuilder("Downloaded ").append(done).append('/').append(total);
            if (failed > 0) {
                sb.append(", ").append(failed).append(" failed");
            }
            if (cancelled > 0) {
                sb.append(", ").append(cancelled).append(" cancelled");
            }
            return sb.toString();
        }
    }

    /**
     * The prompt message after a press: a head line (the pressed song) and an optional
     * progress line (download all). Edits are queued in order, and only after the press's own
     * acknowledgement edit; progress edits are throttled to one every 5 seconds, with a
     * trailing edit so the final count is always shown.
     */
    private final class PromptMessage {
        private final long guildId;
        private final long channelId;
        private final long messageId;
        private String head;
        private @Nullable String progress;
        private boolean ready;
        private boolean dirty;
        private long lastProgressEdit;
        private boolean trailingScheduled;

        PromptMessage(long guildId, long channelId, long messageId, String head) {
            this.guildId = guildId;
            this.channelId = channelId;
            this.messageId = messageId;
            this.head = head;
        }

        synchronized String render() {
            return progress == null ? head : head + "\n" + progress;
        }

        synchronized void markReady() {
            ready = true;
            if (dirty) {
                flush();
            }
        }

        synchronized void setHead(String text) {
            head = text;
            flush();
        }

        /** @param force true for the first and the final count, which are never throttled */
        synchronized void setProgress(String text, boolean force) {
            progress = text;
            long now = System.currentTimeMillis();
            long wait = lastProgressEdit + PROGRESS_INTERVAL_MS - now;
            if (force || wait <= 0) {
                lastProgressEdit = now;
                flush();
            } else if (!trailingScheduled) {
                trailingScheduled = true;
                try {
                    scheduler.schedule(this::trailingEdit, wait, TimeUnit.MILLISECONDS);
                } catch (RuntimeException ex) {
                    trailingScheduled = false; // scheduler shut down; the next update will try again
                }
            }
        }

        private synchronized void trailingEdit() {
            trailingScheduled = false;
            lastProgressEdit = System.currentTimeMillis();
            flush();
        }

        private void flush() {
            if (!ready) {
                dirty = true;
                return;
            }
            dirty = false;
            edit(guildId, channelId, messageId, render());
        }
    }

    // ---- claiming and closing ----

    private void onPromptSent(long guildId, CompletableFuture<Void> gate, Message message, String title) {
        if (currentFor(guildId, gate) != null && state.withMessageId(guildId, message.getIdLong()) != null) {
            return;
        }
        // The prompt closed before its message arrived (skipped, stopped): don't leave live buttons.
        message.editMessage("⏭ Skipped **" + title + "**").setComponents().queue(null, error -> { });
    }

    private void onPromptSendFailed(long guildId, CompletableFuture<Void> gate, Throwable error) {
        LOGGER.warn("Couldn't post the yt-dlp prompt: {}", error.toString());
        expire(guildId, gate, false);
    }

    /**
     * Timeout (or a prompt nobody can see): if nothing else answered this prompt yet, close it,
     * say it was skipped, and stop the hold so the queue moves on.
     */
    private void expire(long guildId, CompletableFuture<Void> gate, boolean editMessage) {
        Pending claimed = claim(guildId, gate);
        if (claimed == null) {
            return;
        }
        if (editMessage) {
            edit(guildId, claimed.channelId(), claimed.messageId(), skipped(claimed.hold()));
        }
        AudioHandler handler = handler(guildId);
        if (handler != null && handler.getPlayer().getPlayingTrack() == claimed.hold()) {
            handler.getPlayer().stopTrack();
        }
        // If the hold hasn't started yet (a send failure can beat the player to it), it must
        // not wait on a prompt that no longer exists: it will end at once and the queue moves on.
        claimed.gate().cancel(false);
    }

    /**
     * Claims the guild's prompt if it is still the one behind {@code gate}. Returns it if this
     * caller won (ACCEPTED), null if a press or another path already answered it. Either way
     * the prompt is closed and its timeout cancelled, so the guild can get a new prompt.
     */
    private @Nullable Pending claim(long guildId, CompletableFuture<Void> gate) {
        Pending current = currentFor(guildId, gate);
        if (current == null) {
            return null;
        }
        boolean won = state.press(guildId, current.videoId()) == PressOutcome.ACCEPTED;
        Pending latest = currentFor(guildId, gate); // may carry a message ID recorded meanwhile
        closeIfCurrent(guildId, gate);
        cancelTimeout(gate);
        return won ? (latest != null ? latest : current) : null;
    }

    private @Nullable Pending currentFor(long guildId, CompletableFuture<Void> gate) {
        return state.get(guildId).filter(p -> p.gate() == gate).orElse(null);
    }

    private void closeIfCurrent(long guildId, CompletableFuture<Void> gate) {
        if (currentFor(guildId, gate) != null) {
            state.close(guildId);
        }
    }

    private void cancelTimeout(CompletableFuture<Void> gate) {
        ScheduledFuture<?> timeout = timeouts.remove(gate);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    // ---- helpers ----

    /** Edits a prompt message and removes its buttons. Does nothing if it can't be reached. */
    private void edit(long guildId, long channelId, long messageId, String content) {
        if (messageId == 0L) {
            return;
        }
        JDA jda = bot.getJDA();
        Guild guild = jda == null ? null : jda.getGuildById(guildId);
        GuildMessageChannel channel = guild == null ? null : guild.getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            return;
        }
        try {
            channel.editMessageById(messageId, content).setComponents().queue(null,
                    error -> LOGGER.debug("Couldn't edit the yt-dlp prompt: {}", error.toString()));
        } catch (RuntimeException ex) {
            LOGGER.debug("Couldn't edit the yt-dlp prompt: {}", ex.toString());
        }
    }

    private boolean isPlaying(long guildId, AudioTrack track) {
        AudioHandler handler = handler(guildId);
        return handler != null && handler.getPlayer().getPlayingTrack() == track;
    }

    private @Nullable AudioHandler handler(long guildId) {
        JDA jda = bot.getJDA();
        return handler(jda == null ? null : jda.getGuildById(guildId));
    }

    private static @Nullable AudioHandler handler(@Nullable Guild guild) {
        if (guild == null) {
            return null;
        }
        return guild.getAudioManager().getSendingHandler() instanceof AudioHandler handler ? handler : null;
    }

    private int promptTimeoutSeconds() {
        return Math.max(1, service.settings().promptTimeoutSeconds());
    }

    private String emoji(java.util.function.Function<BotConfig, String> getter, String fallback) {
        BotConfig config = bot.getConfig();
        String value = config == null ? null : getter.apply(config);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String skipped(AudioTrack hold) {
        return "⏭ Skipped **" + title(hold) + "**";
    }

    private static String title(AudioTrack track) {
        AudioTrackInfo info = track == null ? null : track.getInfo();
        String title = info == null || info.title == null || info.title.isBlank() ? "this song" : info.title;
        return MarkdownSanitizer.escape(FormatUtil.filter(title));
    }

    private static String formatSeconds(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** The yt-dlp error if there is one in the chain, else the exception's own message; trimmed. */
    private static String reason(Throwable error) {
        String reason = null;
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof YtDlpException) {
                reason = t.getMessage();
                break;
            }
        }
        if (reason == null) {
            Throwable t = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            reason = t.getMessage() != null ? t.getMessage() : t.toString();
        }
        reason = FormatUtil.filter(reason.strip());
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) + "…" : reason;
    }

    /** Whether {@code error} or anything in its cause chain is a {@code type}. */
    static boolean hasCause(@Nullable Throwable error, Class<? extends Throwable> type) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 32; t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
