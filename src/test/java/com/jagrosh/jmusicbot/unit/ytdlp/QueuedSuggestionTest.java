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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.jagrosh.jmusicbot.queue.LinearQueue;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Queue-time suggestions: posted while a song is still waiting behind others, and what Switch
 * does to the queue slot (as opposed to the current track; see {@code MatchSuggestionsTest}).
 * See docs/ytdlp-library.md, "When suggestions are posted".
 */
@DisplayName("Queued suggestion Tests")
class QueuedSuggestionTest {

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
    private AbstractQueue<QueuedTrack> queue;
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
        queue = new LinearQueue<>(null, 0);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(handler);
        when(handler.getPlayer()).thenReturn(player);
        when(handler.getQueue()).thenReturn(queue);
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

    private MatchSuggestions suggestions() {
        YtDlpSettings settings = new YtDlpSettings("yt-dlp", Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120, true, true, 5, 0.85, true);
        YtDlpService service = new YtDlpService(settings, library, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class), Runnable::run);
        return new MatchSuggestions(bot, service, prompt, scheduler, new DirectExecutor());
    }

    /** Runs every task synchronously, on the calling thread: these tests don't need real alignment
     *  threading, only that the play-time Switch path still runs when a queued entry is playing. */
    private static final class DirectExecutor extends AbstractExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private static RequestMetadata requestMetadata() {
        return new RequestMetadata(new RequestMetadata.UserInfo(REQUESTER, "user", "0000", null),
                new RequestMetadata.RequestInfo("axel f", YT + REQ_ID), CHANNEL);
    }

    private static AudioTrack track(String identifier, String title, long length) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("youtube");
        when(track.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo(title, "Lyrics Hub", length, identifier, false, YT + identifier,
                "https://img/req.jpg", null);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(identifier);
        RequestMetadata rm = requestMetadata();
        when(track.getUserData()).thenReturn(rm);
        when(track.getUserData(RequestMetadata.class)).thenReturn(rm);
        return track;
    }

    /** A different upload of the library's song, still waiting in the queue. */
    private AudioTrack matchingTrack() {
        return track(REQ_ID, "Crazy Frog - Axel F (Lyrics)", 169_000L);
    }

    private static QueuedTrack queuedTrack(AudioTrack track) {
        return new QueuedTrack(track, requestMetadata());
    }

    private void runTasks() {
        List<Runnable> now = new ArrayList<>(tasks);
        tasks.clear();
        now.forEach(Runnable::run);
    }

    /** Runs considerQueued's background task, completes the message send, and returns its suggestion id. */
    @SuppressWarnings("unchecked")
    private long suggestQueued(MatchSuggestions suggestions, QueuedTrack queued) {
        suggestions.considerQueued(GUILD, queued);
        runTasks();
        ArgumentCaptor<Consumer<Message>> sent = ArgumentCaptor.forClass(Consumer.class);
        verify(createAction).queue(sent.capture(), any());
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(MESSAGE);
        sent.getValue().accept(message);

        ArgumentCaptor<Collection> rows = ArgumentCaptor.forClass(Collection.class);
        verify(createAction).setComponents(rows.capture());
        ActionRow row = (ActionRow) rows.getValue().iterator().next();
        String switchButtonId = row.getButtons().get(0).getCustomId();
        return ComponentIdParsers.parseYtDlpMatchButtonId(switchButtonId).orElseThrow().suggestionId();
    }

    private static ComponentIdParsers.YtDlpMatchButtonId switchIdFor(long suggestionId) {
        return new ComponentIdParsers.YtDlpMatchButtonId("switch", REQUESTER, suggestionId);
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
        when(hook.editOriginalComponents()).thenReturn(mock(WebhookMessageEditAction.class, RETURNS_SELF));
        return event;
    }

    @Test
    @DisplayName("a song queued behind a playing one is suggested right away, with buttons carrying a suggestion id")
    void queued_postsImmediately() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class)); // something else is playing
        AudioTrack b = matchingTrack();
        QueuedTrack qb = queuedTrack(b);
        queue.add(qb);

        long id = suggestQueued(suggestions, qb);

        verify(channel, times(1)).sendMessage(argThat((CharSequence s) -> s.toString().contains("from your library")));
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("Switch on a queued suggestion swaps only that slot, keeping metadata and recording the alias")
    void queued_switchSwapsSameSlot() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        QueuedTrack qa = queuedTrack(track("aaaaaaaaaaa", "Some Other Song", 180_000L));
        QueuedTrack qb = queuedTrack(matchingTrack());
        QueuedTrack qc = queuedTrack(track("ccccccccccc", "Yet Another Song", 150_000L));
        queue.add(qa);
        queue.add(qb);
        queue.add(qc);

        long id = suggestQueued(suggestions, qb);
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchIdFor(id));

        List<QueuedTrack> list = queue.getList();
        assertSame(qa, list.get(0), "slot before the match is untouched");
        assertSame(qc, list.get(2), "slot after the match is untouched");
        QueuedTrack swapped = list.get(1);
        assertNotSame(qb, swapped);
        assertTrue(swapped.getTrack() instanceof LibraryAudioTrack, "the matched slot now holds the library copy");
        LibraryAudioTrack copy = (LibraryAudioTrack) swapped.getTrack();
        assertEquals(LIB_ID, copy.getKey());
        assertEquals(0L, copy.getPosition(), "a queued entry's copy starts from the beginning");
        assertSame(qb.getRequestMetadata(), swapped.getRequestMetadata(), "request metadata carries over unchanged");
        assertEquals(LIB_ID, library.aliasTarget(REQ_ID).orElseThrow(), "the alias is recorded");
    }

    @Test
    @DisplayName("Switch replies with the switched message once the queued entry's slot was swapped")
    void queued_switchSwapsSameSlot_reply() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        QueuedTrack qb = queuedTrack(matchingTrack());
        queue.add(qb);
        long id = suggestQueued(suggestions, qb);
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);

        suggestions.handlePress(event, switchIdFor(id));

        verify(event.getHook()).editOriginal(eq("🔁 Switched to the library copy (linked for next time)"));
    }

    @Test
    @DisplayName("Switch once the queued entry has started playing uses the play-time path")
    void queued_switchWhenStarted_usesPlayTimePath() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        AudioTrack b = matchingTrack();
        QueuedTrack qb = queuedTrack(b);
        queue.add(qb);
        long id = suggestQueued(suggestions, qb);

        when(player.getPlayingTrack()).thenReturn(b); // B is now the current track
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchIdFor(id));

        // The play-time path may align (here it can't: the mocked handler has no ring); it's enough
        // that this IS the play-time path.
        verify(handler).replaceWithLibraryCopy(eq(b), any());
    }

    @Test
    @DisplayName("Switch when the queued entry was removed: nothing is linked or changed")
    void queued_switchWhenRemoved() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        QueuedTrack qb = queuedTrack(matchingTrack());
        queue.add(qb);
        long id = suggestQueued(suggestions, qb);
        queue.remove(0); // e.g. someone removed it, or it was skipped out of the queue
        ButtonInteractionEvent event = pressEvent(REQUESTER, MESSAGE);

        suggestions.handlePress(event, switchIdFor(id));

        verify(event.getHook()).sendMessage(eq("That song is no longer in the queue."));
        verify(event.getHook()).editOriginalComponents();
        verify(event.getHook(), never()).editOriginal(anyString());
        assertTrue(queue.getList().isEmpty(), "the queue (already empty) is unchanged");
        assertTrue(library.aliasTarget(REQ_ID).isEmpty(), "no alias is recorded");
    }

    @Test
    @DisplayName("Switch after the queue was reordered finds the match by identity, not position")
    void queued_switchAfterShuffle_findsByIdentity() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        QueuedTrack qa = queuedTrack(track("aaaaaaaaaaa", "Some Other Song", 180_000L));
        QueuedTrack qb = queuedTrack(matchingTrack());
        QueuedTrack qc = queuedTrack(track("ccccccccccc", "Yet Another Song", 150_000L));
        queue.add(qa);
        queue.add(qb);
        queue.add(qc);
        long id = suggestQueued(suggestions, qb);

        queue.moveItem(1, 0); // reorder: [B, A, C] -- B is no longer at the position it was suggested at

        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchIdFor(id));

        List<QueuedTrack> list = queue.getList();
        assertTrue(list.get(0).getTrack() instanceof LibraryAudioTrack, "the slot that moved with B's identity is the one swapped");
        assertSame(qa, list.get(1));
        assertSame(qc, list.get(2));
    }

    @Test
    @DisplayName("once the queued entry starts, consider() posts nothing new; the open suggestion stays usable")
    void queued_thenStarts_noSecondPost() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        AudioTrack b = matchingTrack();
        QueuedTrack qb = queuedTrack(b);
        queue.add(qb);
        long id = suggestQueued(suggestions, qb);

        when(player.getPlayingTrack()).thenReturn(b);
        suggestions.consider(GUILD, b);
        runTasks();

        verify(channel, times(1)).sendMessage(any(CharSequence.class)); // still just the queue-time post

        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchIdFor(id));
        verify(handler).replaceWithLibraryCopy(eq(b), any());
    }

    @Test
    @DisplayName("always mode: a queued YouTube song is never suggested (it plays from the library anyway)")
    void alwaysMode_queuedYoutube_notConsidered() {
        YtDlpSettings settings = new YtDlpSettings("yt-dlp", Mode.ALWAYS, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120, true, true, 5, 0.85, true);
        YtDlpService service = new YtDlpService(settings, library, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class), Runnable::run);
        MatchSuggestions suggestions = new MatchSuggestions(bot, service, prompt, scheduler, new DirectExecutor());
        QueuedTrack qb = queuedTrack(matchingTrack());
        queue.add(qb);

        suggestions.considerQueued(GUILD, qb);

        assertTrue(tasks.isEmpty(), "nothing was scheduled");
        verify(channel, never()).sendMessage(any(CharSequence.class));
    }

    @Test
    @DisplayName("clearing the queue (play now) leaves a queue-time suggestion whose song is now playing")
    void queueCleared_keepsSuggestionForPlayingTrack() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        AudioTrack b = matchingTrack();
        QueuedTrack qb = queuedTrack(b);
        queue.add(qb);
        long id = suggestQueued(suggestions, qb);
        when(player.getPlayingTrack()).thenReturn(b); // B started, then the rest of the queue was cleared

        suggestions.onQueueCleared(GUILD);

        verify(channel, never()).editMessageById(anyLong(), any(CharSequence.class));
        suggestions.handlePress(pressEvent(REQUESTER, MESSAGE), switchIdFor(id));
        verify(handler).replaceWithLibraryCopy(eq(b), any());
    }

    @Test
    @DisplayName("clearing the queue closes its open suggestions' buttons")
    void queueCleared_closesQueued() {
        MatchSuggestions suggestions = suggestions();
        when(player.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
        QueuedTrack qb = queuedTrack(matchingTrack());
        queue.add(qb);
        suggestQueued(suggestions, qb);

        suggestions.onQueueCleared(GUILD);

        verify(channel).editMessageById(eq(MESSAGE), argThat((CharSequence s) -> s.toString().contains("Looks like")));
        verify(editAction).setComponents();

        ButtonInteractionEvent late = pressEvent(REQUESTER, MESSAGE);
        suggestions.handlePress(late, switchIdFor(1L));
        verify(late.getHook()).sendMessage(argThat((String s) -> s.contains("expired")));
    }
}
