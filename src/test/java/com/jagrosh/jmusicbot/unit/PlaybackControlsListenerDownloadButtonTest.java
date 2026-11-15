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
package com.jagrosh.jmusicbot.unit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.listener.PlaybackControlsListener;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpException;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The yt-dlp "Download" now-playing button ({@code np_ytdlp_save}): DJ check, the single
 * acknowledgement, progress edits, and that playback itself is never touched.
 */
@DisplayName("PlaybackControlsListener yt-dlp download button")
class PlaybackControlsListenerDownloadButtonTest {

    private static final long GUILD_ID = 1234L;
    private static final String KEY = "abcdefghijk";
    private static final String YT = "https://www.youtube.com/watch?v=";

    private Bot bot;
    private Guild guild;
    private Member member;
    private AudioHandler handler;
    private AudioPlayer player;
    private AudioTrack track;
    private YtDlpService ytDlp;
    private YtDlpLibrary library;
    private NowPlayingHandler nowPlayingHandler;
    private ButtonInteractionEvent event;
    private PlaybackControlsListener listener;

    @BeforeEach
    void setUp() {
        bot = mock(Bot.class);
        guild = mock(Guild.class);
        member = mock(Member.class);
        when(guild.getIdLong()).thenReturn(GUILD_ID);

        AudioManager audioManager = mock(AudioManager.class);
        handler = mock(AudioHandler.class);
        player = mock(AudioPlayer.class);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(handler);
        when(handler.getPlayer()).thenReturn(player);

        track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("youtube");
        when(track.getSourceManager()).thenReturn(source);
        when(track.getInfo()).thenReturn(new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, KEY, false, YT + KEY));
        when(player.getPlayingTrack()).thenReturn(track);

        ytDlp = mock(YtDlpService.class);
        library = mock(YtDlpLibrary.class);
        when(ytDlp.isEnabled()).thenReturn(true);
        when(ytDlp.library()).thenReturn(library);
        when(library.resolve(KEY)).thenReturn(Optional.empty());
        when(bot.getYtDlp()).thenReturn(ytDlp);

        nowPlayingHandler = mock(NowPlayingHandler.class);
        when(bot.getNowplayingHandler()).thenReturn(nowPlayingHandler);

        event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);

        listener = new PlaybackControlsListener(bot);
    }

    private ReplyCallbackAction stubReply() {
        ReplyCallbackAction action = mock(ReplyCallbackAction.class, RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(action);
        return action;
    }

    /** Stubs deferReply(true) so its onSuccess callback runs synchronously, returning the hook it hands out. */
    @SuppressWarnings("unchecked")
    private InteractionHook stubDeferReply() {
        ReplyCallbackAction defer = mock(ReplyCallbackAction.class, RETURNS_SELF);
        when(event.deferReply(true)).thenReturn(defer);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        doAnswer(inv -> {
            Consumer<InteractionHook> onSuccess = inv.getArgument(0);
            onSuccess.accept(hook);
            return null;
        }).when(defer).queue(any(), any());
        return hook;
    }

    private void pressAsDj(boolean isDj) {
        try (MockedStatic<DJCommand> dj = mockStatic(DJCommand.class)) {
            dj.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(isDj);
            listener.onButtonInteraction(event);
        }
    }

    @Test
    @DisplayName("a non-DJ is refused ephemerally, and nothing is requested")
    void nonDj_refusedEphemeral() {
        ReplyCallbackAction reply = stubReply();

        pressAsDj(false);

        verify(event).reply(argThat((String s) -> s.contains("DJ")));
        verify(reply).setEphemeral(true);
        verify(event, never()).deferReply(anyBoolean());
        verifyNoInteractions(ytDlp);
    }

    @Test
    @DisplayName("a DJ triggers exactly one ack, a request, a progress edit, then a success edit and a refresh")
    void dj_downloadsAndReportsSuccess() {
        InteractionHook hook = stubDeferReply();
        CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(future);

        pressAsDj(true);

        verify(event, times(1)).deferReply(true);
        verify(event, never()).reply(anyString());
        verify(ytDlp).requestDownload(track, GUILD_ID, false);
        verify(hook).editOriginal(argThat((String s) -> s.contains("Downloading") && s.contains("Axel F")));

        future.complete(mock(LibraryEntry.class));

        verify(hook).editOriginal(argThat((String s) -> s.contains("Saved") && s.contains("Axel F")
                && s.contains("library")));
        verify(nowPlayingHandler).refreshNowPlaying(GUILD_ID);

        // Playback itself is never touched beyond reading the current track.
        verify(player).getPlayingTrack();
        verifyNoMoreInteractions(player);
    }

    @Test
    @DisplayName("acknowledges before requesting the download, and edits the hook after")
    void ack_happensBeforeRequestAndEdit() {
        InteractionHook hook = stubDeferReply();
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(new CompletableFuture<>());

        pressAsDj(true);

        InOrder order = inOrder(event, ytDlp, hook);
        order.verify(event).deferReply(true);
        order.verify(ytDlp).requestDownload(track, GUILD_ID, false);
        order.verify(hook).editOriginal(anyString());
    }

    @Test
    @DisplayName("never cancels or completes the downloader's shared future")
    void neverCancelsOrCompletesTheFuture() {
        stubDeferReply();
        CompletableFuture<LibraryEntry> future = mock(CompletableFuture.class);
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(future);
        when(future.whenComplete(any())).thenReturn(future);

        pressAsDj(true);

        verify(future, never()).cancel(anyBoolean());
        verify(future, never()).complete(any());
        verify(future, never()).completeExceptionally(any());
    }

    @Test
    @DisplayName("a failed download edits the hook with the failure reason, and doesn't refresh now-playing")
    void downloadFails_editsFailureMessage() {
        InteractionHook hook = stubDeferReply();
        CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(future);

        pressAsDj(true);
        future.completeExceptionally(new YtDlpException("network unreachable"));

        verify(hook).editOriginal(eq("❌ yt-dlp failed: network unreachable"));
        verify(nowPlayingHandler, never()).refreshNowPlaying(anyLong());
    }

    @Test
    @DisplayName("a song that can't be downloaded (already in the library) is refused, with no ack")
    void alreadyInLibrary_refusedEphemeral() {
        when(library.resolve(KEY)).thenReturn(Optional.of(mock(LibraryEntry.class)));
        ReplyCallbackAction reply = stubReply();

        pressAsDj(true);

        verify(event).reply(argThat((String s) -> s.contains("can't be downloaded")));
        verify(reply).setEphemeral(true);
        verify(event, never()).deferReply(anyBoolean());
        verify(ytDlp, never()).requestDownload(any(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("nothing playing: refused, with no ack")
    void nothingPlaying_refusedEphemeral() {
        when(player.getPlayingTrack()).thenReturn(null);
        stubReply();

        pressAsDj(true);

        verify(event, never()).deferReply(anyBoolean());
        verify(ytDlp, never()).requestDownload(any(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("yt-dlp disabled: refused, with no ack")
    void ytDlpDisabled_refusedEphemeral() {
        when(ytDlp.isEnabled()).thenReturn(false);
        stubReply();

        pressAsDj(true);

        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    @DisplayName("no guild or member: acknowledged once with an ephemeral error, and nothing is requested")
    void noGuildOrMember_repliesOnce() {
        when(event.getGuild()).thenReturn(null);
        ReplyCallbackAction reply = stubReply();

        listener.onButtonInteraction(event);

        verifyNoInteractions(ytDlp);
        verify(event, never()).deferReply(anyBoolean());
        verify(event, times(1)).reply(anyString());
        verify(reply).setEphemeral(true);
    }

    @Test
    @DisplayName("a download withdrawn by /stop, Play now or leaving voice says it was cancelled")
    void downloadCancelled_saysCancelled() {
        InteractionHook hook = stubDeferReply();
        CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(future);

        pressAsDj(true);
        future.cancel(false); // as YtDlpDownloader.cancelGuild does

        verify(hook).editOriginal(eq("Download cancelled."));
        verify(hook, never()).editOriginal(argThat((String s) -> s.contains("CancellationException")));
        verify(nowPlayingHandler, never()).refreshNowPlaying(anyLong());
    }

    @Test
    @DisplayName("a cancellation wrapped in a CompletionException also says it was cancelled")
    void downloadCancelledWrapped_saysCancelled() {
        InteractionHook hook = stubDeferReply();
        CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        when(ytDlp.requestDownload(track, GUILD_ID, false)).thenReturn(future);

        pressAsDj(true);
        future.completeExceptionally(new java.util.concurrent.CompletionException(
                new java.util.concurrent.CancellationException()));

        verify(hook).editOriginal(eq("Download cancelled."));
    }

    @Test
    @DisplayName("sanity: the button id this test drives is the one the listener handles")
    void usesTheSpecifiedComponentId() {
        assertTrue(ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE.equals("np_ytdlp_save"));
    }
}
