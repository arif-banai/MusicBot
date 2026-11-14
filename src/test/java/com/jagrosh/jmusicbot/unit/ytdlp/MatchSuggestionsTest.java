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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.MatchSuggestions;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
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
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The similar-song suggestion flow: when it posts, and what Switch and Not the same do.
 */
@DisplayName("MatchSuggestions Tests")
class MatchSuggestionsTest {

    private static final long GUILD = 77L;
    private static final long CHANNEL = 555L;
    private static final long REQUESTER = 42L;
    private static final long MESSAGE = 999L;
    private static final String LIB_ID = "LIBLIBLIB01";
    private static final String REQ_ID = "a_b-c_d1234";
    private static final String YT = "https://www.youtube.com/watch?v=";

    @TempDir
    Path tempDir;

    private Bot bot;
    private Guild guild;
    private GuildMessageChannel channel;
    private MessageCreateAction createAction;
    private MessageEditAction editAction;
    private AudioHandler handler;
    private AudioPlayer player;
    private ScheduledExecutorService scheduler;
    private final List<Runnable> tasks = new ArrayList<>();
    private ScheduledFuture<?> expiryFuture;
    private YtDlpPrompt prompt;
    private Path libraryFolder;
    private YtDlpLibrary library;

    @BeforeEach
    void setUp() throws IOException {
        bot = mock(Bot.class);
        when(bot.getConfig()).thenReturn(mock(BotConfig.class));
        JDA jda = mock(JDA.class);
        when(bot.getJDA()).thenReturn(jda);
        guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(jda.getGuildById(GUILD)).thenReturn(guild);

        channel = mock(GuildMessageChannel.class);
        when(channel.getIdLong()).thenReturn(CHANNEL);
        when(channel.canTalk()).thenReturn(true);
        when(guild.getChannelById(GuildMessageChannel.class, CHANNEL)).thenReturn(channel);
        createAction = mock(MessageCreateAction.class, RETURNS_SELF);
        when(channel.sendMessage(any(CharSequence.class))).thenReturn(createAction);
        editAction = mock(MessageEditAction.class, RETURNS_SELF);
        when(channel.editMessageById(anyLong(), any(CharSequence.class))).thenReturn(editAction);

        AudioManager audioManager = mock(AudioManager.class);
        handler = mock(AudioHandler.class);
        player = mock(AudioPlayer.class);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(handler);
        when(handler.getPlayer()).thenReturn(player);
        when(handler.replaceWithLibraryCopy(any(), any())).thenReturn(true);

        scheduler = mock(ScheduledExecutorService.class);
        org.mockito.Mockito.doAnswer(inv -> tasks.add(inv.getArgument(0))).when(scheduler).execute(any(Runnable.class));
        expiryFuture = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> expiryFuture);
        prompt = mock(YtDlpPrompt.class);

        libraryFolder = tempDir.resolve("lib");
        Files.createDirectories(libraryFolder);
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("pl.txt"));
        Path libFile = libraryFolder.resolve("Axel F [" + LIB_ID + "].webm");
        Files.writeString(libFile, "x");
        library.record(LIB_ID, libFile, new AudioTrackInfo("Crazy Frog - Axel F (Official Video)", "Crazy Frog",
                168_000L, LIB_ID, false, YT + LIB_ID, "https://img/lib.jpg", null));
    }

    private YtDlpSettings settings(String path, boolean matching) {
        return new YtDlpSettings(path, Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300, List.of(), 120,
                true, matching, 5, 0.85);
    }

    private MatchSuggestions suggestions(YtDlpSettings settings) {
        YtDlpService service = new YtDlpService(settings, library, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class), Runnable::run);
        return new MatchSuggestions(bot, service, prompt, scheduler);
    }

    private MatchSuggestions suggestions() {
        return suggestions(settings("yt-dlp", true));
    }

    private static RequestMetadata requestMetadata() {
        return new RequestMetadata(new RequestMetadata.UserInfo(REQUESTER, "user", "0000", null),
                new RequestMetadata.RequestInfo("axel f", YT + REQ_ID), CHANNEL);
    }

    private static AudioTrack track(String sourceName, String identifier, String title, long length, boolean stream) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn(sourceName);
        when(track.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo(title, "Lyrics Hub", length, identifier, stream, YT + identifier,
                "https://img/req.jpg", null);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(identifier);
        RequestMetadata rm = requestMetadata();
        when(track.getUserData()).thenReturn(rm);
        when(track.getUserData(RequestMetadata.class)).thenReturn(rm);
        return track;
    }

    /** A different upload of the library's song, now playing. */
    private AudioTrack playingRequest() {
        AudioTrack track = track("youtube", REQ_ID, "Crazy Frog - Axel F (Lyrics)", 169_000L, false);
        when(player.getPlayingTrack()).thenReturn(track);
        return track;
    }

    private void runTasks() {
        List<Runnable> now = new ArrayList<>(tasks);
        tasks.clear();
        now.forEach(Runnable::run);
    }

    /** Runs consider's background task and completes the message send. */
    @SuppressWarnings("unchecked")
    private void suggest(MatchSuggestions suggestions, AudioTrack track) {
        suggestions.consider(GUILD, track);
        runTasks();
        ArgumentCaptor<Consumer<Message>> sent = ArgumentCaptor.forClass(Consumer.class);
        verify(createAction).queue(sent.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE);
        sent.getValue().accept(message);
    }

    private ButtonInteractionEvent pressEvent(long memberId, long messageId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        Member member = mock(Member.class);
        when(member.getIdLong()).thenReturn(memberId);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(event.getMessageIdLong()).thenReturn(messageId);
        MessageEditCallbackAction edit = mock(MessageEditCallbackAction.class, RETURNS_SELF);
        when(event.editMessage(anyString())).thenReturn(edit);
        when(event.editComponents()).thenReturn(edit);
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class, RETURNS_SELF));
        when(event.reply(anyString())).thenReturn(mock(ReplyCallbackAction.class, RETURNS_SELF));
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(mock(WebhookMessageCreateAction.class, RETURNS_SELF));
        when(hook.editOriginal(anyString())).thenReturn(mock(WebhookMessageEditAction.class, RETURNS_SELF));
        return event;
    }

    private static ComponentIdParsers.YtDlpMatchButtonId switchId() {
        return new ComponentIdParsers.YtDlpMatchButtonId("switch", REQUESTER);
    }

    private static ComponentIdParsers.YtDlpMatchButtonId rejectId() {
        return new ComponentIdParsers.YtDlpMatchButtonId("reject", REQUESTER);
    }

    // ---- posting ----

    @Test
    @DisplayName("a similar library song is suggested once, in the request channel, with two buttons")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void suggestion_postedOnce() {
        MatchSuggestions suggestions = suggestions();

        suggest(suggestions, playingRequest());

        verify(channel, times(1)).sendMessage(argThat((CharSequence s) -> s.toString().equals(
                "🎵 Looks like **Crazy Frog - Axel F (Official Video)** by Crazy Frog (02:48) from your library.")));
        ArgumentCaptor<Collection> rows = ArgumentCaptor.forClass(Collection.class);
        verify(createAction).setComponents(rows.capture());
        ActionRow row = (ActionRow) rows.getValue().iterator().next();
        List<String> ids = row.getButtons().stream().map(Button::getCustomId).toList();
        assertEquals(List.of("ytdlp_match_switch_42", "ytdlp_match_reject_42"), ids);
        verify(scheduler).schedule(any(Runnable.class), eq(10L), eq(TimeUnit.MINUTES));
    }

    private void assertNothingPosted(MatchSuggestions suggestions, AudioTrack track) {
        suggestions.consider(GUILD, track);
        runTasks();
        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("feature off (no yt-dlp path): nothing is scheduled or posted")
    void featureOff_nothing() {
        suggestions(settings("", true)).consider(GUILD, playingRequest());

        verifyNoInteractions(scheduler);
        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("matching turned off: nothing is scheduled or posted")
    void matchingOff_nothing() {
        suggestions(settings("yt-dlp", false)).consider(GUILD, playingRequest());

        verifyNoInteractions(scheduler);
    }

    @Test
    @DisplayName("no suggestion for a live stream")
    void stream_nothing() {
        AudioTrack stream = track("youtube", REQ_ID, "Crazy Frog - Axel F (Lyrics)", 169_000L, true);
        when(player.getPlayingTrack()).thenReturn(stream);

        assertNothingPosted(suggestions(), stream);
    }

    @Test
    @DisplayName("no suggestion for a library track (including a prompt's hold)")
    void libraryTrack_nothing() {
        LibraryAudioTrack lib = new LibraryAudioTrack(new AudioTrackInfo("Crazy Frog - Axel F (Lyrics)",
                "Lyrics Hub", 169_000L, REQ_ID, false, YT + REQ_ID), REQ_ID,
                () -> new java.util.concurrent.CompletableFuture<LibraryEntry>(), null, null);
        when(player.getPlayingTrack()).thenReturn(lib);

        suggestions().consider(GUILD, lib);

        verifyNoInteractions(scheduler);
    }

    @Test
    @DisplayName("no suggestion while a yt-dlp prompt is open in the guild")
    void promptOpen_nothing() {
        when(prompt.isOpen(GUILD)).thenReturn(true);

        assertNothingPosted(suggestions(), playingRequest());
    }

    @Test
    @DisplayName("no suggestion for a rejected pair")
    void rejectedPair_nothing() throws IOException {
        library.reject(REQ_ID, LIB_ID);

        assertNothingPosted(suggestions(), playingRequest());
    }

    @Test
    @DisplayName("no suggestion for a key that is already linked, or already in the library")
    void aliasedOrInLibrary_nothing() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        assertNothingPosted(suggestions(), playingRequest());

        AudioTrack exact = track("youtube", LIB_ID, "Crazy Frog - Axel F (Official Video)", 168_000L, false);
        when(player.getPlayingTrack()).thenReturn(exact);
        assertNothingPosted(suggestions(), exact);
    }

    @Test
    @DisplayName("no suggestion for a different song, or once the track stopped playing")
    void noMatchOrNotPlaying_nothing() {
        AudioTrack other = track("youtube", REQ_ID, "Crazy Frog - Popcorn", 169_000L, false);
        when(player.getPlayingTrack()).thenReturn(other);
        assertNothingPosted(suggestions(), other);

        AudioTrack request = track("youtube", REQ_ID, "Crazy Frog - Axel F (Lyrics)", 169_000L, false);
        when(player.getPlayingTrack()).thenReturn(null);
        assertNothingPosted(suggestions(), request);
    }

    @Test
    @DisplayName("an unsupported source (a local file or HTTP stream) is never considered")
    void unsupportedSource_nothing() {
        AudioTrack http = track("http", "https://example.com/a.mp3", "Crazy Frog - Axel F", 169_000L, false);

        suggestions().consider(GUILD, http);

        verifyNoInteractions(scheduler);
    }

    // ---- Switch ----

    @Test
    @DisplayName("Switch links the songs, replaces the stream at the current position, and acknowledges once")
    void switch_linksAndReplaces() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        when(request.getPosition()).thenReturn(42_000L);
        suggest(suggestions, request);
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);

        suggestions.handlePress(event, switchId());

        assertEquals(LIB_ID, library.aliasTarget(REQ_ID).orElseThrow());
        ArgumentCaptor<AudioTrack> replacement = ArgumentCaptor.forClass(AudioTrack.class);
        verify(handler).replaceWithLibraryCopy(eq(request), replacement.capture());
        LibraryAudioTrack copy = (LibraryAudioTrack) replacement.getValue();
        assertEquals(LIB_ID, copy.getKey());
        assertEquals(REQ_ID, copy.getLinkedFrom());
        assertEquals(42_000L, copy.getPosition());
        assertEquals(requestMetadata().getOwner(), copy.getUserData(RequestMetadata.class).getOwner());
        verify(event, times(1)).deferEdit();
        verify(event.getHook()).editOriginal(argThat((String s) -> s.contains("Switched to the library copy")));
        verify(event, never()).editMessage(anyString());
        verify(event, never()).reply(anyString());
        verify(event, never()).editComponents();
        verify(expiryFuture).cancel(false);
    }

    @Test
    @DisplayName("Switch acknowledges the press before it touches the player")
    void switch_acknowledgesBeforeReplacing() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);
        MessageEditCallbackAction ack = event.deferEdit();

        suggestions.handlePress(event, switchId());

        InOrder order = inOrder(ack, handler, event.getHook());
        order.verify(ack).queue(any(), any());
        order.verify(handler).replaceWithLibraryCopy(eq(request), any());
        order.verify(event.getHook()).editOriginal(anyString());
    }

    @Test
    @DisplayName("a suggestion whose track ended during the scan is dropped, so no live buttons linger")
    void endedDuringScan_notPosted() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        AudioTrack next = mock(AudioTrack.class);
        // Playing when the scan starts, gone by the time the suggestion is registered.
        when(player.getPlayingTrack()).thenReturn(request, next);

        suggestions.consider(GUILD, request);
        runTasks();

        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("Switch caps the start position below the library copy's length")
    void switch_capsPosition() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        when(request.getPosition()).thenReturn(168_500L);
        suggest(suggestions, request);

        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchId());

        ArgumentCaptor<AudioTrack> replacement = ArgumentCaptor.forClass(AudioTrack.class);
        verify(handler).replaceWithLibraryCopy(eq(request), replacement.capture());
        assertTrue(replacement.getValue().getPosition() < 168_000L);
    }

    @Test
    @DisplayName("Switch is allowed for a DJ who didn't request the song")
    void switch_byDj() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);
        ButtonInteractionEvent event = pressEvent(8L, MESSAGE);

        try (MockedStatic<DJCommand> dj = mockStatic(DJCommand.class)) {
            dj.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(true);
            suggestions.handlePress(event, switchId());
        }

        verify(handler).replaceWithLibraryCopy(eq(request), any());
    }

    @Test
    @DisplayName("Switch onto an imported entry fills in its missing details from the requested song")
    void switch_fillsInImportedEntry() throws IOException {
        Path imported = libraryFolder.resolve("Axel F - Crazy Frog.mp3");
        Files.writeString(imported, "x");
        YtDlpLibrary.Prober prober = file -> java.util.Optional.of(new YtDlpLibrary.ProbeResult(null, null, 169_500L));
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("pl.txt"), prober);
        library.load();
        library.reject(REQ_ID, LIB_ID); // only the imported file is left to suggest
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);

        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchId());

        String fileKey = "file:Axel F - Crazy Frog.mp3";
        assertEquals(fileKey, library.aliasTarget(REQ_ID).orElseThrow());
        LibraryEntry filled = library.find(fileKey).orElseThrow();
        assertEquals("Lyrics Hub", filled.author());
        assertEquals("https://img/req.jpg", filled.artworkUrl());
        assertEquals(169_500L, filled.lengthMs());
    }

    @Test
    @DisplayName("Switch when the song already changed: nothing is linked or replaced")
    void switch_afterTrackChanged_expired() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);

        suggestions.handlePress(event, switchId());

        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
        verify(handler, never()).replaceWithLibraryCopy(any(), any());
        verify(event, times(1)).deferEdit();
        verify(event.getHook()).editOriginal(argThat((String s) -> s.contains("expired")));
    }

    // ---- Not the same ----

    @Test
    @DisplayName("Not the same records a rejection, and the pair is never suggested again")
    void reject_recordsRejection() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);

        suggestions.handlePress(event, rejectId());

        assertTrue(library.isRejected(REQ_ID, LIB_ID));
        verify(event, times(1)).editMessage(argThat((String s) -> s.contains("won't suggest that again")));
        verify(handler, never()).replaceWithLibraryCopy(any(), any());

        suggestions.consider(GUILD, request);
        runTasks();
        verify(channel, times(1)).sendMessage(any(CharSequence.class));
    }

    // ---- refusals and expiry ----

    @Test
    @DisplayName("someone who isn't the requester or a DJ is refused, and the suggestion stays open")
    void notRequesterOrDj_refused() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);
        ButtonInteractionEvent event = pressEvent(8L, MESSAGE);

        try (MockedStatic<DJCommand> dj = mockStatic(DJCommand.class)) {
            dj.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(false);
            suggestions.handlePress(event, switchId());
        }

        verify(event).reply(argThat((String s) -> s.contains("Only the person")));
        verify(event, never()).editMessage(anyString());
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchId());
        verify(handler).replaceWithLibraryCopy(eq(request), any());
    }

    private static void assertExpiredAck(ButtonInteractionEvent event) {
        verify(event).editComponents();
        verify(event.getHook()).sendMessage(argThat((String s) -> s.contains("expired")));
        verify(event, never()).editMessage(anyString());
        verify(event, never()).reply(anyString());
    }

    @Test
    @DisplayName("a second press is expired: the first one already acted")
    void doublePress_expired() {
        MatchSuggestions suggestions = suggestions();
        suggest(suggestions, playingRequest());
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchId());

        ButtonInteractionEvent second = pressEvent(REQUESTER, MESSAGE);
        suggestions.handlePress(second, rejectId());

        assertExpiredAck(second);
        assertFalse(library.isRejected(REQ_ID, LIB_ID));
        verify(handler, times(1)).replaceWithLibraryCopy(any(), any());
    }

    @Test
    @DisplayName("a press on another message (an older suggestion, or after a restart) is expired")
    void foreignPress_expired() {
        MatchSuggestions suggestions = suggestions();
        suggest(suggestions, playingRequest());

        ButtonInteractionEvent foreign = pressEvent(REQUESTER, 1234L);
        suggestions.handlePress(foreign, switchId());
        assertExpiredAck(foreign);

        ButtonInteractionEvent afterRestart = pressEvent(REQUESTER, MESSAGE);
        suggestions(settings("yt-dlp", true)).handlePress(afterRestart, switchId());
        assertExpiredAck(afterRestart);

        verify(handler, never()).replaceWithLibraryCopy(any(), any());
        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
    }

    @Test
    @DisplayName("the buttons expire when the track ends")
    void trackEnd_expires() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);

        suggestions.onTrackEnded(GUILD, request);

        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Looks like")));
        verify(editAction).setComponents();
        verify(expiryFuture).cancel(false);
        ButtonInteractionEvent late = pressEvent(REQUESTER, MESSAGE);
        suggestions.handlePress(late, switchId());
        assertExpiredAck(late);
    }

    @Test
    @DisplayName("another track's end leaves the suggestion open")
    void otherTrackEnd_keepsSuggestion() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggest(suggestions, request);

        suggestions.onTrackEnded(GUILD, mock(AudioTrack.class));

        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class));
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchId());
        verify(handler).replaceWithLibraryCopy(eq(request), any());
    }

    @Test
    @DisplayName("the buttons expire after 10 minutes")
    void tenMinutes_expires() {
        MatchSuggestions suggestions = suggestions();
        suggest(suggestions, playingRequest());
        ArgumentCaptor<Runnable> expiry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(expiry.capture(), eq(10L), eq(TimeUnit.MINUTES));

        expiry.getValue().run();

        verify(editAction).setComponents();
        ButtonInteractionEvent late = pressEvent(REQUESTER, MESSAGE);
        suggestions.handlePress(late, switchId());
        assertExpiredAck(late);
    }

    @Test
    @DisplayName("if the track ends before the message is sent, the sent message loses its buttons")
    @SuppressWarnings("unchecked")
    void endedBeforeSent_stripsButtons() {
        MatchSuggestions suggestions = suggestions();
        AudioTrack request = playingRequest();
        suggestions.consider(GUILD, request);
        runTasks();
        suggestions.onTrackEnded(GUILD, request);
        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class)); // no message ID yet

        ArgumentCaptor<Consumer<Message>> sent = ArgumentCaptor.forClass(Consumer.class);
        verify(createAction).queue(sent.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE);
        MessageEditAction strip = mock(MessageEditAction.class, RETURNS_SELF);
        when(message.editMessageComponents()).thenReturn(strip);
        sent.getValue().accept(message);

        verify(message).editMessageComponents();
        verify(strip).queue(any(), any());
    }
}
