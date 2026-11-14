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
package com.jagrosh.jmusicbot.unit.ytdlp;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts.PressOutcome;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpException;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.AllClientsFailedException;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prompt-mode decisions and the at-most-once rules between the timeout, skips and presses.
 */
class YtDlpPromptTest {

    private static final long GUILD = 77L;
    private static final long CHANNEL = 555L;
    private static final long REQUESTER = 42L;
    private static final long MESSAGE = 999L;
    private static final String VIDEO_ID = "a_b-c_d1234";
    private static final String YT = "https://www.youtube.com/watch?v=";

    @TempDir
    Path tempDir;

    private Bot bot;
    private JDA jda;
    private Guild guild;
    private GuildMessageChannel channel;
    private MessageCreateAction createAction;
    private MessageEditAction editAction;
    private AudioPlayer player;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timeoutFuture;
    private PendingPrompts state;
    private YtDlpDownloader downloader;
    private NowPlayingHandler nowPlayingHandler;

    @BeforeEach
    void setUp() {
        bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(mock(BotConfig.class));
        nowPlayingHandler = mock(NowPlayingHandler.class);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);
        jda = mock(JDA.class);
        when(bot.getJDA()).thenReturn(jda);
        guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(jda.getGuildById(GUILD)).thenReturn(guild);

        channel = mock(GuildMessageChannel.class);
        when(channel.getIdLong()).thenReturn(CHANNEL);
        when(channel.canTalk()).thenReturn(true);
        createAction = mock(MessageCreateAction.class, RETURNS_SELF);
        when(channel.sendMessage(any(CharSequence.class))).thenReturn(createAction);
        editAction = mock(MessageEditAction.class, RETURNS_SELF);
        when(channel.editMessageById(anyLong(), any(CharSequence.class))).thenReturn(editAction);

        AudioManager audioManager = mock(AudioManager.class);
        AudioHandler handler = mock(AudioHandler.class);
        player = mock(AudioPlayer.class);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(handler);
        when(handler.getPlayer()).thenReturn(player);

        scheduler = mock(ScheduledExecutorService.class);
        timeoutFuture = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> timeoutFuture);
        state = new PendingPrompts();
        downloader = mock(YtDlpDownloader.class);
    }

    private YtDlpService service(String path, Mode mode) {
        YtDlpSettings settings = new YtDlpSettings(path, mode, tempDir.resolve("lib"), "ytdlp-library", 0, 300,
                List.of(), 120);
        YtDlpLibrary library = new YtDlpLibrary(tempDir.resolve("lib"), tempDir.resolve("pl.txt"));
        return new YtDlpService(settings, library, downloader, mock(ProcessRunner.class));
    }

    private YtDlpPrompt prompt(Mode mode) {
        return new YtDlpPrompt(bot, service("yt-dlp", mode), state, scheduler);
    }

    private static RequestMetadata requestMetadata() {
        return new RequestMetadata(new RequestMetadata.UserInfo(REQUESTER, "user", "0000", null),
                new RequestMetadata.RequestInfo("some song", "https://www.youtube.com/watch?v=" + VIDEO_ID), CHANNEL);
    }

    private static AudioTrack track(String sourceName) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn(sourceName);
        when(track.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo("Blocked Song", "Author", 200_000L, VIDEO_ID, false,
                "https://www.youtube.com/watch?v=" + VIDEO_ID);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(VIDEO_ID);
        RequestMetadata rm = requestMetadata();
        when(track.getUserData()).thenReturn(rm);
        when(track.getUserData(RequestMetadata.class)).thenReturn(rm);
        return track;
    }

    /** A YouTube track whose clone is another YouTube track with the same info and user data. */
    private static AudioTrack blockedYoutubeTrack() {
        AudioTrack track = track("youtube");
        AudioTrack clone = track("youtube");
        when(track.makeClone()).thenReturn(clone);
        return track;
    }

    private static FriendlyException allClientsFailed() {
        return new FriendlyException("This video is unavailable", Severity.SUSPICIOUS,
                new AllClientsFailedException(List.of()));
    }

    private static LibraryAudioTrack libraryTrack(CompletableFuture<Void> gate) {
        LibraryAudioTrack t = new LibraryAudioTrack(track("youtube").getInfo(), VIDEO_ID,
                () -> new CompletableFuture<LibraryEntry>(), gate, null);
        t.setUserData(requestMetadata());
        return t;
    }

    /** Opens a prompt through holdFor, completes the message send, and returns the timeout task. */
    @SuppressWarnings("unchecked")
    private Runnable openPrompt(YtDlpPrompt prompt) {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        Optional<AudioTrack> hold = prompt.holdFor(guild, blockedYoutubeTrack());
        assertTrue(hold.isPresent());
        when(player.getPlayingTrack()).thenReturn(hold.get());

        ArgumentCaptor<Consumer<Message>> sent = ArgumentCaptor.forClass(Consumer.class);
        verify(createAction).queue(sent.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE);
        sent.getValue().accept(message);

        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(timeout.capture(), eq(120L), eq(TimeUnit.SECONDS));
        return timeout.getValue();
    }

    @Test
    void shouldOffer_onlyForAllClientsFailedInPromptMode() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        AudioTrack youtube = track("youtube");

        assertTrue(prompt.shouldOffer(youtube, allClientsFailed()));
        assertTrue(prompt.shouldOffer(youtube, new AllClientsFailedException(List.of())));
        assertFalse(prompt.shouldOffer(youtube, new FriendlyException("other", Severity.COMMON, null)));

        assertFalse(prompt(Mode.ALWAYS).shouldOffer(youtube, allClientsFailed()));
        assertFalse(new YtDlpPrompt(bot, service("", Mode.PROMPT), state, scheduler)
                .shouldOffer(youtube, allClientsFailed()));
        assertFalse(prompt.shouldOffer(track("soundcloud"), allClientsFailed()));
        assertFalse(prompt.shouldOffer(libraryTrack(null), allClientsFailed()));
    }

    @Test
    void canPress_requesterOrDj() {
        Member requester = mock(Member.class);
        when(requester.getIdLong()).thenReturn(REQUESTER);
        Member dj = mock(Member.class);
        when(dj.getIdLong()).thenReturn(7L);
        Member other = mock(Member.class);
        when(other.getIdLong()).thenReturn(8L);

        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(bot, guild, requester)).thenReturn(false);
            djCommand.when(() -> DJCommand.checkDJPermission(bot, guild, dj)).thenReturn(true);
            djCommand.when(() -> DJCommand.checkDJPermission(bot, guild, other)).thenReturn(false);

            assertTrue(YtDlpPrompt.canPress(bot, guild, requester, REQUESTER));
            assertTrue(YtDlpPrompt.canPress(bot, guild, dj, REQUESTER));
            assertFalse(YtDlpPrompt.canPress(bot, guild, other, REQUESTER));
        }
    }

    @Test
    void holdFor_unreachableChannel_noPause() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(null);

        Optional<AudioTrack> hold = prompt(Mode.PROMPT).holdFor(guild, blockedYoutubeTrack());

        assertTrue(hold.isEmpty());
        assertTrue(state.get(GUILD).isEmpty());
        verifyNoInteractions(scheduler);
    }

    @Test
    void holdFor_reachable_opensPromptWithGatedHold() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);

        var pending = state.get(GUILD).orElseThrow();
        assertEquals(VIDEO_ID, pending.videoId());
        assertEquals(REQUESTER, pending.requesterId());
        assertEquals(CHANNEL, pending.channelId());
        assertEquals(MESSAGE, pending.messageId());
        LibraryAudioTrack hold = assertInstanceOf(LibraryAudioTrack.class, pending.hold());
        assertTrue(hold.isAwaitingGate());
        assertEquals("Blocked Song", hold.getInfo().title);
        verify(createAction).setComponents(any(java.util.Collection.class));
        // The download is only requested once someone presses a button.
        verifyNoInteractions(downloader);
    }

    @Test
    void timeout_afterButtonWon_doesNothing() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        Runnable timeout = openPrompt(prompt);

        assertEquals(PressOutcome.ACCEPTED, state.press(GUILD, VIDEO_ID)); // a button won
        timeout.run();

        verify(player, never()).stopTrack();
        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class));
    }

    @Test
    void timeout_first_skipsHoldOnce() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        Runnable timeout = openPrompt(prompt);

        timeout.run();

        verify(player).stopTrack();
        assertTrue(state.get(GUILD).isEmpty());
        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Skipped")));

        // stopTrack ends the hold, which reports back; that must not act a second time.
        prompt.onHoldEnded(GUILD);
        timeout.run();
        verify(player, times(1)).stopTrack();
        verify(channel, times(1)).editMessageById(anyLong(), any(CharSequence.class));
        assertEquals(PressOutcome.EXPIRED, state.press(GUILD, VIDEO_ID));
    }

    @Test
    void holdClone_downloadsOnlyAfterPromptAccepted() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        var pending = state.get(GUILD).orElseThrow();
        when(downloader.request(any(), any(), any(), anyLong(), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(new YtDlpException("stub")));

        // e.g. "previous" while the prompt is still open: the clone has no gate of its own
        LibraryAudioTrack unaccepted = (LibraryAudioTrack) pending.hold().makeClone();
        FriendlyException refused = assertThrows(FriendlyException.class,
                () -> unaccepted.process(mock(LocalAudioTrackExecutor.class)));
        assertTrue(refused.getMessage().contains("prompt wasn't accepted"), refused.getMessage());
        verifyNoInteractions(downloader);

        pending.gate().complete(null); // someone pressed a button
        LibraryAudioTrack accepted = (LibraryAudioTrack) pending.hold().makeClone();
        assertThrows(FriendlyException.class, () -> accepted.process(mock(LocalAudioTrackExecutor.class)));
        verify(downloader).request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendFailure_beforeHoldStarts_releasesHold() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        LibraryAudioTrack hold = (LibraryAudioTrack) prompt.holdFor(guild, blockedYoutubeTrack()).orElseThrow();
        ArgumentCaptor<Consumer<Throwable>> failed = ArgumentCaptor.forClass(Consumer.class);
        verify(createAction).queue(any(), failed.capture());

        failed.getValue().accept(new RuntimeException("Missing permission"));

        assertTrue(state.get(GUILD).isEmpty());
        assertFalse(hold.isAwaitingGate()); // a hold started later ends at once instead of waiting forever
        verify(timeoutFuture).cancel(false);
        verify(player, never()).stopTrack(); // it wasn't playing yet
    }

    @Test
    void cancel_beforeHoldEnds_saysCancelledNotSkipped() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        Runnable timeout = openPrompt(prompt);

        prompt.cancel(GUILD);      // stop: called before the player stops the hold
        prompt.onHoldEnded(GUILD); // the hold's STOPPED end
        timeout.run();

        assertTrue(state.get(GUILD).isEmpty());
        verify(timeoutFuture).cancel(false);
        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Cancelled")));
        verify(channel, never()).editMessageById(anyLong(),
                argThat((CharSequence s) -> s.toString().contains("Skipped")));
        verify(channel, times(1)).editMessageById(anyLong(), any(CharSequence.class));
        verify(player, never()).stopTrack(); // AudioHandler does the stopping
    }

    @Test
    void cancel_beforeHoldStarts_releasesHold() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        LibraryAudioTrack hold = (LibraryAudioTrack) state.get(GUILD).orElseThrow().hold();

        prompt.cancel(GUILD); // e.g. a stop between the blocked track's end and its hold starting

        // a hold started later must end at once, not wait on a prompt whose timer is already gone
        assertFalse(hold.isAwaitingGate());
    }

    @Test
    void isCandidate_notForLiveStreams() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);

        assertTrue(prompt.isCandidate(youtube("abcdefghijk", false)));
        assertFalse(prompt.isCandidate(youtube("abcdefghijk", true)));
        assertFalse(prompt.shouldOffer(youtube("abcdefghijk", true), allClientsFailed()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendFailsAtOnce_timeoutIsNotLeaked() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        doAnswer(inv -> {
            ((Consumer<Throwable>) inv.getArgument(1)).accept(new RuntimeException("Missing permission"));
            return null;
        }).when(createAction).queue(any(), any());

        prompt(Mode.PROMPT).holdFor(guild, blockedYoutubeTrack());

        assertTrue(state.get(GUILD).isEmpty());
        verify(timeoutFuture).cancel(false);
    }

    @Test
    void cancel_afterPress_doesNothing() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        assertEquals(PressOutcome.ACCEPTED, state.press(GUILD, VIDEO_ID)); // a button won

        prompt.cancel(GUILD);

        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class));
    }

    @Test
    void onHoldEnded_beforePress_closesPromptAndCancelsTimeout() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        Runnable timeout = openPrompt(prompt);

        prompt.onHoldEnded(GUILD);

        assertTrue(state.get(GUILD).isEmpty());
        verify(timeoutFuture).cancel(false);
        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Skipped")));

        timeout.run(); // already cancelled; even if it runs, it must not act
        verify(player, never()).stopTrack();
        verify(channel, times(1)).editMessageById(anyLong(), any(CharSequence.class));
    }

    private ButtonInteractionEvent pressEvent(long memberId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        Member member = mock(Member.class);
        when(member.getIdLong()).thenReturn(memberId);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getMessageIdLong()).thenReturn(MESSAGE);
        MessageEditCallbackAction edit = mock(MessageEditCallbackAction.class, RETURNS_SELF);
        when(event.editMessage(anyString())).thenReturn(edit);
        when(event.editComponents()).thenReturn(edit);
        when(event.reply(anyString())).thenReturn(mock(ReplyCallbackAction.class, RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(mock(WebhookMessageCreateAction.class, RETURNS_SELF));
        return event;
    }

    /** Runs the success callback of the press's acknowledgement edit. */
    @SuppressWarnings("unchecked")
    private static void acknowledge(ButtonInteractionEvent event) {
        MessageEditCallbackAction edit = event.editMessage("");
        ArgumentCaptor<Consumer<InteractionHook>> ok = ArgumentCaptor.forClass(Consumer.class);
        verify(edit).queue(ok.capture(), any());
        ok.getValue().accept(null);
    }

    private static LibraryEntry entry(String videoId) {
        return new LibraryEntry(videoId, Path.of("song [" + videoId + "].webm"), "Blocked Song", "Author", 1L,
                null, "https://www.youtube.com/watch?v=" + videoId);
    }

    @Test
    void handlePress_one_opensGateAndReportsPlaying() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        LibraryAudioTrack hold = state.get(GUILD).orElseThrow().hold();
        CompletableFuture<LibraryEntry> download = new CompletableFuture<>();
        when(downloader.request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true))).thenReturn(download);
        ButtonInteractionEvent event = pressEvent(REQUESTER);

        prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID));

        assertFalse(hold.isAwaitingGate());
        assertTrue(state.get(GUILD).isEmpty());
        verify(timeoutFuture).cancel(false);
        verify(event).editMessage(argThat((String s) -> s.contains("Downloading") && s.contains("Blocked Song")));

        acknowledge(event);
        download.complete(entry(VIDEO_ID));
        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Playing")));
    }

    @Test
    void handlePress_one_refreshesNowPlaying_sinceNoTrackStartFollowsTheSameHold() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        LibraryAudioTrack hold = state.get(GUILD).orElseThrow().hold();
        when(downloader.request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true))).thenReturn(new CompletableFuture<>());
        ButtonInteractionEvent event = pressEvent(REQUESTER);
        // Pins the ordering: the gate must already be open by the time the refresh runs,
        // or the refreshed message would still render the waiting line.
        AtomicBoolean gateOpenAtRefresh = new AtomicBoolean(false);
        doAnswer(inv -> {
            gateOpenAtRefresh.set(!hold.isAwaitingGate());
            return null;
        }).when(nowPlayingHandler).refreshNowPlaying(GUILD);

        prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID));

        // The hold keeps playing the same track object (no onTrackStart), so the now-playing
        // message would otherwise be stuck showing the yt-dlp waiting line forever.
        verify(nowPlayingHandler).refreshNowPlaying(GUILD);
        assertTrue(gateOpenAtRefresh.get());
    }

    @Test
    void handlePress_refreshesNowPlayingOnlyAfterQueueingTheAcknowledgement() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        when(downloader.request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true))).thenReturn(new CompletableFuture<>());
        ButtonInteractionEvent event = pressEvent(REQUESTER);
        MessageEditCallbackAction ack = event.editMessage("");

        prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID));

        InOrder order = inOrder(ack, nowPlayingHandler);
        order.verify(ack).queue(any(), any());
        order.verify(nowPlayingHandler).refreshNowPlaying(GUILD);
    }

    @Test
    void handlePress_nowPlayingRefreshThrows_stillAcknowledgesAndReportsTheDownload() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        CompletableFuture<LibraryEntry> download = new CompletableFuture<>();
        when(downloader.request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true))).thenReturn(download);
        ButtonInteractionEvent event = pressEvent(REQUESTER);
        org.mockito.Mockito.doThrow(new IllegalStateException("reconcile failed"))
                .when(nowPlayingHandler).refreshNowPlaying(GUILD);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID)));

        acknowledge(event); // fails if the acknowledging edit was never queued
        download.complete(entry(VIDEO_ID));
        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Playing")));
    }

    @Test
    void handlePress_downloadFails_reportsReason() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        CompletableFuture<LibraryEntry> download = new CompletableFuture<>();
        when(downloader.request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true))).thenReturn(download);
        ButtonInteractionEvent event = pressEvent(REQUESTER);

        prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID));
        download.completeExceptionally(new YtDlpException("ERROR: Sign in to confirm"));
        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class)); // waits for the ack
        acknowledge(event);

        verify(channel).editMessageById(eq(MESSAGE),
                argThat((CharSequence s) -> s.toString().contains("yt-dlp failed: ERROR: Sign in to confirm")));
    }

    @Test
    void handlePress_secondPress_isExpired() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        when(downloader.request(any(), any(), any(), anyLong(), anyBoolean())).thenReturn(new CompletableFuture<>());
        ComponentIdParsers.YtDlpButtonId id = new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID);

        prompt.handlePress(pressEvent(REQUESTER), id);
        ButtonInteractionEvent second = pressEvent(REQUESTER);
        prompt.handlePress(second, id);

        verify(second).editComponents();
        verify(second.getHook()).sendMessage(argThat((String s) -> s.contains("expired")));
        verify(second, never()).editMessage(anyString());
        verify(downloader, times(1)).request(any(), any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void handlePress_notRequesterOrDj_refusedAndPromptStaysOpen() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        ButtonInteractionEvent event = pressEvent(8L);

        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(false);
            prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", REQUESTER, VIDEO_ID));
        }

        verify(event).reply(argThat((String s) -> s.contains("Only the person")));
        assertTrue(state.get(GUILD).isPresent());
        assertTrue(state.get(GUILD).orElseThrow().hold().isAwaitingGate());
        verifyNoInteractions(downloader);
    }

    @Test
    void handlePress_all_wrapsQueuedYoutubeTracksInOrder() {
        YtDlpPrompt prompt = prompt(Mode.PROMPT);
        openPrompt(prompt);
        java.util.Map<String, CompletableFuture<LibraryEntry>> downloads = new java.util.HashMap<>();
        when(downloader.request(any(), any(), any(), anyLong(), anyBoolean()))
                .thenAnswer(inv -> downloads.computeIfAbsent(inv.getArgument(0), k -> new CompletableFuture<>()));

        AbstractQueue<QueuedTrack> queue = QueueType.LINEAR.createInstance(null, 10);
        AudioTrack first = youtube("first000001", false);
        AudioTrack other = track("soundcloud");
        AudioTrack stream = youtube("stream00001", true);
        AudioTrack second = youtube("second00002", false);
        for (AudioTrack t : List.of(first, other, stream, second)) {
            queue.add(new QueuedTrack(t, requestMetadata()));
        }
        QueuedTrack otherQueued = queue.get(1);
        QueuedTrack streamQueued = queue.get(2);
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        when(handler.getQueue()).thenReturn(queue);
        ButtonInteractionEvent event = pressEvent(REQUESTER);

        prompt.handlePress(event, new ComponentIdParsers.YtDlpButtonId("all", REQUESTER, VIDEO_ID));

        assertEquals("first000001", assertInstanceOf(LibraryAudioTrack.class, queue.get(0).getTrack()).getKey());
        assertEquals(otherQueued, queue.get(1));
        assertEquals(streamQueued, queue.get(2));
        assertEquals("second00002", assertInstanceOf(LibraryAudioTrack.class, queue.get(3).getTrack()).getKey());
        assertEquals(requestMetadata().getOwner(), queue.get(0).getRequestMetadata().getOwner());
        InOrder order = inOrder(downloader);
        order.verify(downloader).request(eq(VIDEO_ID), eq(YT + VIDEO_ID), any(), eq(GUILD), eq(true));
        order.verify(downloader, org.mockito.Mockito.atLeastOnce()).request(eq("first000001"), eq(YT + "first000001"), any(), eq(GUILD), eq(false));
        order.verify(downloader, org.mockito.Mockito.atLeastOnce()).request(eq("second00002"), eq(YT + "second00002"), any(), eq(GUILD), eq(false));
        verify(event).editMessage(argThat((String s) -> s.contains("Downloaded 0/2")));

        acknowledge(event);
        downloads.get("first000001").complete(entry("first000001"));
        downloads.get("second00002").completeExceptionally(new YtDlpException("nope"));
        verify(channel).editMessageById(eq(MESSAGE),
                argThat((CharSequence s) -> s.toString().contains("Downloaded 1/2, 1 failed")));
    }

    private static AudioTrack youtube(String videoId, boolean stream) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("youtube");
        when(track.getSourceManager()).thenReturn(source);
        when(track.getInfo()).thenReturn(new AudioTrackInfo("Song " + videoId, "Author", 1000L, videoId, stream,
                "https://www.youtube.com/watch?v=" + videoId));
        when(track.getIdentifier()).thenReturn(videoId);
        return track;
    }

    @Test
    void notifyDownloadFailed_cancelled_postsNothing() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        FriendlyException cancelled = new FriendlyException("yt-dlp download was cancelled", Severity.COMMON,
                new CancellationException());

        prompt(Mode.ALWAYS).notifyDownloadFailed(guild, libraryTrack(null), cancelled);

        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    void notifyDownloadFailed_nestedCancellation_postsNothing() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        FriendlyException cancelled = new FriendlyException("yt-dlp download was cancelled", Severity.COMMON,
                new RuntimeException("wrapper", new CancellationException()));

        prompt(Mode.ALWAYS).notifyDownloadFailed(guild, libraryTrack(null), cancelled);

        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    void notifyDownloadFailed_failure_postsReasonInRequestChannel() {
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        FriendlyException failed = new FriendlyException("yt-dlp couldn't download this track: ERROR: blocked",
                Severity.COMMON, new YtDlpException("ERROR: blocked"));

        prompt(Mode.ALWAYS).notifyDownloadFailed(guild, libraryTrack(null), failed);

        verify(channel).sendMessage(argThat((CharSequence s) -> s.toString().contains("Blocked Song")
                && s.toString().contains("ERROR: blocked")));
        verify(createAction).queue();
    }
}
