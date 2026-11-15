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
import java.util.List;
import java.util.Optional;

import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.commands.v2.dj.LibraryPanelSlashCmd;
import com.jagrosh.jmusicbot.listener.LibraryInteractionListener;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.LibraryPanelTokens;
import com.jagrosh.jmusicbot.testutil.listener.InteractionAckTracker;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /library panel}: the DJ-only interactive library management panel
 * ({@link LibraryInteractionListener}). Component IDs never carry a search query or a library
 * key directly (both can exceed Discord's 100-character custom-ID limit); they carry a
 * {@link LibraryPanelTokens} token instead, resolved server-side.
 */
@DisplayName("/library panel")
class LibraryPanelTest {

    @TempDir
    Path tempDir;

    private YtDlpLibrary library;
    private YtDlpService service;
    private ListenerTestFixture fixture;
    private LibraryPanelTokens tokens;
    private LibraryInteractionListener listener;

    @BeforeEach
    void setUp() throws IOException {
        Path folder = tempDir.resolve("lib");
        Files.createDirectories(folder);
        library = new YtDlpLibrary(folder, tempDir.resolve("pl.txt"));
        service = new YtDlpService(new YtDlpSettings("yt-dlp", Mode.PROMPT, folder, "ytdlp-library", 0, 300,
                List.of(), 120), library, mock(YtDlpDownloader.class), mock(ProcessRunner.class), Runnable::run);

        fixture = ListenerTestFixture.create();
        tokens = new LibraryPanelTokens();
        listener = new LibraryInteractionListener(fixture.getBot(), tokens);
        when(fixture.getBot().getYtDlp()).thenReturn(service);
        when(fixture.getButtonInteractionEvent().getUser()).thenReturn(fixture.getUser());

        // A library-reply action, distinct from fixture.getReplyAction(), for assertions that
        // don't go through InteractionAckTracker.
        when(fixture.getButtonInteractionEvent().reply(anyString())).thenReturn(fixture.getReplyAction());
        when(fixture.getReplyAction().setEphemeral(anyBoolean())).thenReturn(fixture.getReplyAction());
        doNothing().when(fixture.getReplyAction()).queue();
    }

    private void record(String key, String title) throws IOException {
        Path folder = tempDir.resolve("lib");
        Path file = Files.createFile(folder.resolve(title + " [" + key + "].webm"));
        library.record(key, file, new AudioTrackInfo(title, "Author " + key, 120_000L, key, false,
                "https://www.youtube.com/watch?v=" + key));
    }

    /** As {@link #record}, but the file name never derives from {@code key}: a key may be far too long for a path. */
    private void recordWithKey(String key, String title) throws IOException {
        Path folder = tempDir.resolve("lib");
        Path file = Files.createFile(folder.resolve(title + " [" + Integer.toHexString(key.hashCode()) + "].webm"));
        library.record(key, file, new AudioTrackInfo(title, "Author", 120_000L, key, false,
                "https://example.com/" + Integer.toHexString(key.hashCode())));
    }

    private void runWithDj(ButtonInteractionEvent event, boolean dj) {
        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(dj);
            listener.onButtonInteraction(event);
        }
    }

    private void runWithDj(StringSelectInteractionEvent event, boolean dj) {
        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(dj);
            listener.onStringSelectInteraction(event);
        }
    }

    private void runWithDj(ModalInteractionEvent event, boolean dj) {
        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(dj);
            listener.onModalInteraction(event);
        }
    }

    private StringSelectInteractionEvent mockSelectEvent(String componentId, List<String> values) {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        doReturn(fixture.getGuild()).when(event).getGuild();
        doReturn(fixture.getMember()).when(event).getMember();
        doReturn(fixture.getUser()).when(event).getUser();
        doReturn(componentId).when(event).getComponentId();
        doReturn(values).when(event).getValues();
        doReturn(mock(ReplyCallbackAction.class, RETURNS_SELF)).when(event).reply(anyString());
        doReturn(mock(MessageEditCallbackAction.class, RETURNS_SELF)).when(event).deferEdit();
        doReturn(mockHook()).when(event).getHook();
        return event;
    }

    /** A hook whose edits and follow-ups can be verified (each returns a self-returning action). */
    @SuppressWarnings("unchecked")
    private static InteractionHook mockHook() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        doReturn(edit).when(hook).editOriginalEmbeds(any(MessageEmbed[].class));
        doReturn(edit).when(hook).editOriginalEmbeds(anyCollection());
        doReturn(edit).when(hook).editOriginal(anyString());
        doReturn(mock(WebhookMessageCreateAction.class, RETURNS_SELF)).when(hook).sendMessage(anyString());
        return hook;
    }

    private ModalInteractionEvent mockModalEvent(long ownerUserId, String query) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        doReturn(fixture.getGuild()).when(event).getGuild();
        doReturn(fixture.getMember()).when(event).getMember();
        doReturn(fixture.getUser()).when(event).getUser();
        doReturn(ComponentIdParsers.libSearchModalId(ownerUserId)).when(event).getModalId();
        ModalMapping mapping = mock(ModalMapping.class);
        doReturn("query").when(mapping).getCustomId();
        doReturn(query).when(mapping).getAsString();
        doReturn(List.of(mapping)).when(event).getValues();
        doReturn(mock(ReplyCallbackAction.class, RETURNS_SELF)).when(event).reply(anyString());
        doReturn(mock(MessageEditCallbackAction.class, RETURNS_SELF)).when(event).deferEdit();
        doReturn(mockHook()).when(event).getHook();
        return event;
    }

    /** Every button/select-menu custom ID (and select option value) in {@code rows}, flattened. */
    private static List<String> componentIds(List<ActionRow> rows) {
        return rows.stream()
                .flatMap(row -> row.getComponents().stream())
                .flatMap(component -> {
                    if (component instanceof Button button) {
                        return java.util.stream.Stream.of(button.getCustomId());
                    }
                    if (component instanceof StringSelectMenu menu) {
                        return java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(menu.getCustomId()),
                                menu.getOptions().stream().map(net.dv8tion.jda.api.components.selections.SelectOption::getValue));
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    // ---- DJ / owner checks ----

    @Test
    @DisplayName("a non-DJ press is refused and nothing in the library is touched")
    void onButtonInteraction_nonDj_refusedAndNoLibraryCall() {
        fixture.withButtonId(ComponentIdParsers.libComponentId("rescan", fixture.getUser().getIdLong()));

        runWithDj(fixture.getButtonInteractionEvent(), false);

        verify(fixture.getButtonInteractionEvent())
                .reply(argThat((String s) -> s.contains("Only DJs can manage the library.")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        // The rescan action never even starts: no deferEdit (its first step) was ever queued.
        verify(fixture.getButtonInteractionEvent(), never()).deferEdit();
    }

    @Test
    @DisplayName("a press from someone other than the DJ who opened the panel is refused")
    void onButtonInteraction_wrongOwner_refused() {
        fixture.withButtonId(ComponentIdParsers.libComponentId("rescan", fixture.getUser().getIdLong() + 1));

        runWithDj(fixture.getButtonInteractionEvent(), true);

        verify(fixture.getButtonInteractionEvent())
                .reply(argThat((String s) -> s.contains("This panel belongs to someone else.")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    @DisplayName("a non-DJ select-menu pick is refused the same way")
    void onStringSelectInteraction_nonDj_refused() throws IOException {
        record("songone01", "Song One");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "song", 1, List.of("songone01"));
        String componentId = ComponentIdParsers.libComponentId("pick", ownerUserId, token);
        StringSelectInteractionEvent event = mockSelectEvent(componentId, List.of("0"));

        runWithDj(event, false);

        verify(event).reply(argThat((String s) -> s.contains("Only DJs can manage the library.")));
    }

    @Test
    @DisplayName("a wrong-owner select-menu pick is refused")
    void onStringSelectInteraction_wrongOwner_refused() throws IOException {
        record("songone01", "Song One");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "song", 1, List.of("songone01"));
        String componentId = ComponentIdParsers.libComponentId("pick", ownerUserId + 1, token);
        StringSelectInteractionEvent event = mockSelectEvent(componentId, List.of("0"));

        runWithDj(event, true);

        verify(event).reply(argThat((String s) -> s.contains("This panel belongs to someone else.")));
    }

    @Test
    @DisplayName("a non-DJ search-modal submit is refused")
    void onModalInteraction_nonDj_refused() {
        long ownerUserId = fixture.getUser().getIdLong();
        ModalInteractionEvent event = mockModalEvent(ownerUserId, "song");

        runWithDj(event, false);

        verify(event).reply(argThat((String s) -> s.contains("Only DJs can manage the library.")));
    }

    @Test
    @DisplayName("a wrong-owner search-modal submit is refused")
    void onModalInteraction_wrongOwner_refused() {
        long ownerUserId = fixture.getUser().getIdLong();
        // The modal ID itself names a different owner than the presser.
        ModalInteractionEvent event = mockModalEvent(ownerUserId + 1, "song");

        runWithDj(event, true);

        verify(event).reply(argThat((String s) -> s.contains("This panel belongs to someone else.")));
    }

    // ---- expired / unknown tokens ----

    @Test
    @DisplayName("an unknown token gives the ephemeral 'panel has expired' message")
    void onButtonInteraction_unknownToken_givesExpiredMessage() {
        fixture.withButtonId(ComponentIdParsers.libComponentId(
                "play", fixture.getUser().getIdLong(), "doesNotExist", 0));

        runWithDj(fixture.getButtonInteractionEvent(), true);

        verify(fixture.getButtonInteractionEvent())
                .reply(argThat((String s) -> s.contains("This panel has expired; run /library panel again.")));
    }

    @Test
    @DisplayName("a genuinely expired token gives the same message")
    void onButtonInteraction_expiredToken_givesExpiredMessage() {
        LibraryPanelTokens expiredImmediately = new LibraryPanelTokens(-1L, System::currentTimeMillis);
        LibraryInteractionListener shortLivedListener = new LibraryInteractionListener(fixture.getBot(), expiredImmediately);
        String token = expiredImmediately.create(fixture.getUser().getIdLong(), "q", 1, List.of("k"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("play", fixture.getUser().getIdLong(), token, 0));

        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(), any(), any())).thenReturn(true);
            shortLivedListener.onButtonInteraction(fixture.getButtonInteractionEvent());
        }

        verify(fixture.getButtonInteractionEvent())
                .reply(argThat((String s) -> s.contains("This panel has expired; run /library panel again.")));
    }

    @Test
    @DisplayName("a token minted for another user's panel counts as expired")
    void onButtonInteraction_otherUsersToken_givesExpiredMessage() throws IOException {
        record("othertok01", "Other Token");
        long presser = fixture.getUser().getIdLong();
        String othersToken = tokens.create(presser + 1, "other", 1, List.of("othertok01"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("play", presser, othersToken, 0));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("reply"), acks.acks());
        verify(fixture.getButtonInteractionEvent())
                .reply(argThat((String s) -> s.contains("This panel has expired; run /library panel again.")));
    }

    // ---- Play/Queue feedback ----

    @Test
    @DisplayName("Queue's \"Added ...\" outcome (reported via editMessage) reaches the DJ as an ephemeral follow-up")
    void libraryReplyAdapter_editMessageAfterDefer_isEphemeralFollowup() {
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());
        fixture.getButtonInteractionEvent().deferEdit().queue(); // the listener defers before playing
        var output = com.jagrosh.jmusicbot.listener.interaction.OutputAdapters.forLibraryReply(fixture.getButtonInteractionEvent());

        output.editMessage("Added **Song** to the queue at position 2");
        output.editMessage("No results found", message -> { });

        verify(acks.hook()).sendMessage("Added **Song** to the queue at position 2");
        verify(acks.hook()).sendMessage("No results found");
        verify(acks.followup(), org.mockito.Mockito.times(2)).setEphemeral(true);
        assertEquals(List.of("deferEdit"), acks.acks());
    }

    @Test
    @DisplayName("Queue defers, then queues the library file through the music service with the library adapter")
    void onButtonInteraction_queue_defersThenQueuesFile() throws IOException {
        record("queueok001", "Queue Ok");
        Path file = library.find("queueok001").orElseThrow().file();
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "queue ok", 1, List.of("queueok001"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("queue", ownerUserId, token, 0)).withMemberInVoiceChannel();
        net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion channel =
                mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion.class);
        doReturn(fixture.getTextChannel()).when(channel).asGuildMessageChannel();
        doReturn(channel).when(fixture.getButtonInteractionEvent()).getChannel();
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        verify(fixture.getMusicService()).play(any(), any(), eq(file.toAbsolutePath().toString()), any(), any());
    }

    // ---- Info view and embed limits ----

    private LibraryEntry entryWith(String title, String author, @org.jetbrains.annotations.Nullable String artist,
                                   String uri) throws IOException {
        Path file = Files.createFile(tempDir.resolve("lib").resolve("Entry " + Math.abs(title.hashCode()) + ".webm"));
        return new LibraryEntry("abcdefghijk", file, title, author, 120_000L, null, uri, null, List.of(), false,
                artist, null, null, null, null);
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream().filter(f -> name.equals(f.getName())).findFirst()
                .map(MessageEmbed.Field::getValue).orElse(null);
    }

    @Test
    @DisplayName("Info shows the sidecar artist, the uploader beside it when different, and the alias keys")
    void infoEmbed_showsArtistUploaderAndAliasKeys() throws IOException {
        LibraryEntry entry = entryWith("Song", "Lyrics Channel", "Real Artist", "https://example.com/s");

        MessageEmbed embed = LibraryPanelSlashCmd.buildInfoEmbed(entry, List.of("req00000001", "soundcloud:abc"), null);

        assertEquals("Real Artist", fieldValue(embed, "Artist"));
        assertEquals("Lyrics Channel", fieldValue(embed, "Uploader"));
        assertEquals("`req00000001`, `soundcloud:abc`", fieldValue(embed, "Aliases"));

        MessageEmbed noArtist = LibraryPanelSlashCmd.buildInfoEmbed(
                entryWith("Other", "Uploader Only", null, "https://example.com/o"), List.of(), null);
        assertEquals("Uploader Only", fieldValue(noArtist, "Artist"));
        assertEquals(null, fieldValue(noArtist, "Uploader"));
        assertEquals("none", fieldValue(noArtist, "Aliases"));
    }

    @Test
    @DisplayName("many aliases are cut to fit the 1024-character field, saying how many more there are")
    void infoEmbed_manyAliases_fitInOneField() throws IOException {
        List<String> aliases = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            aliases.add("soundcloud:artist-" + i + "/a-fairly-long-track-name");
        }
        LibraryEntry entry = entryWith("Song", "Author", null, "https://example.com/s");

        MessageEmbed embed = assertDoesNotThrow(() -> LibraryPanelSlashCmd.buildInfoEmbed(entry, aliases, null));

        String value = fieldValue(embed, "Aliases");
        assertTrue(value.length() <= MessageEmbed.VALUE_MAX_LENGTH, String.valueOf(value.length()));
        assertTrue(value.matches("(?s).*\\(\\+\\d+ more\\)$"), value);
    }

    @Test
    @DisplayName("a 300-character title and a 2000-character link fit Discord's embed limits")
    void detailEmbed_longTitleAndLink_fitLimits() throws IOException {
        LibraryEntry entry = entryWith("t".repeat(300), "Author", null, "https://example.com/" + "u".repeat(2000));

        MessageEmbed detail = assertDoesNotThrow(() -> LibraryPanelSlashCmd.buildDetailEmbed(entry, null));
        MessageEmbed info = assertDoesNotThrow(() -> LibraryPanelSlashCmd.buildInfoEmbed(entry, List.of(), null));

        assertTrue(detail.getTitle().length() <= MessageEmbed.TITLE_MAX_LENGTH);
        assertTrue(fieldValue(detail, "Link").length() <= MessageEmbed.VALUE_MAX_LENGTH);
        assertTrue(fieldValue(info, "Link").length() <= MessageEmbed.VALUE_MAX_LENGTH);
    }

    @Test
    @DisplayName("a search result with no author has no select-option description (null, not \"\")")
    void searchList_noAuthor_optionHasNoDescription() throws IOException {
        LibraryEntry entry = entryWith("No Author", "", null, "https://example.com/n");

        List<ActionRow> rows = LibraryPanelSlashCmd.buildSearchListComponents(1L, "tok", List.of(entry), 1, 1);

        StringSelectMenu menu = (StringSelectMenu) rows.get(0).getComponents().get(0);
        assertEquals(null, menu.getOptions().get(0).getDescription());
    }

    // ---- off the event thread ----

    /** Points the bot at a service whose library work is only queued, so a test can see what ran before it. */
    private List<Runnable> queueLibraryWork() {
        List<Runnable> queued = new java.util.ArrayList<>();
        YtDlpService queuing = new YtDlpService(new YtDlpSettings("yt-dlp", Mode.PROMPT, tempDir.resolve("lib"),
                "ytdlp-library", 0, 300, List.of(), 120), library, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class), Runnable::run, queued::add);
        when(fixture.getBot().getYtDlp()).thenReturn(queuing);
        return queued;
    }

    @Test
    @DisplayName("Info defers first; the library is read later, off the event thread, and the view goes through the hook")
    void onButtonInteraction_info_defersBeforeLibraryWork() {
        List<Runnable> queued = queueLibraryWork();
        fixture.withButtonId(ComponentIdParsers.libComponentId("info", fixture.getUser().getIdLong()));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        assertEquals(1, queued.size(), "the library work was handed off, not run on the event thread");
        verify(acks.hook(), never()).editOriginalEmbeds(any(MessageEmbed[].class));
        queued.forEach(Runnable::run);
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        assertEquals(List.of("deferEdit"), acks.acks(), "still exactly one acknowledgement");
    }

    @Test
    @DisplayName("picking a result defers first and looks the song up off the event thread")
    void onStringSelectInteraction_pick_defersBeforeLibraryWork() throws IOException {
        record("pickdefer1", "Pick Defer");
        List<Runnable> queued = queueLibraryWork();
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "pick", 1, List.of("pickdefer1"));
        StringSelectInteractionEvent event = mockSelectEvent(ComponentIdParsers.libComponentId("pick", ownerUserId, token),
                List.of("0"));

        runWithDj(event, true);

        verify(event).deferEdit();
        verify(event, never()).reply(anyString());
        verify(event.getHook(), never()).editOriginalEmbeds(any(MessageEmbed[].class));
        queued.forEach(Runnable::run);
        verify(event.getHook()).editOriginalEmbeds(any(MessageEmbed[].class));
    }

    @Test
    @DisplayName("a song removed after the panel rendered: the deferred lookup says so through the hook")
    void onButtonInteraction_queue_songGone_saysSoThroughHook() throws IOException {
        record("gonesong01", "Gone Song");
        library.remove("gonesong01", false);
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "gone", 1, List.of("gonesong01"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("remove", ownerUserId, token, 0));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).sendMessage("That song is no longer in the library.");
        verify(acks.followup()).setEphemeral(true);
    }

    // ---- remove confirm flow ----

    @Test
    @DisplayName("rmidx removes the entry but keeps the file")
    void onButtonInteraction_rmidx_removesIndexOnly() throws IOException {
        record("removeidx1", "Remove Me");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "remove me", 1, List.of("removeidx1"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("rmidx", ownerUserId, token, 0));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        assertTrue(library.find("removeidx1").isEmpty());
        verify(acks.hook()).editOriginal(argThat((String s) -> s.contains("Removed from the library") && !s.contains("deleted the file")));
    }

    @Test
    @DisplayName("rmfile removes the entry and deletes the file")
    void onButtonInteraction_rmfile_removesAndDeletesFile() throws IOException {
        record("removefil1", "Remove File");
        Path file = library.find("removefil1").orElseThrow().file();
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "remove file", 1, List.of("removefil1"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("rmfile", ownerUserId, token, 0));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertTrue(library.find("removefil1").isEmpty());
        assertTrue(Files.notExists(file));
        verify(acks.hook()).editOriginal(argThat((String s) -> s.contains("Removed from the library") && s.contains("deleted the file")));
    }

    @Test
    @DisplayName("a failed delete shows the problem and changes nothing")
    void onButtonInteraction_rmfile_deleteFails_showsProblem() throws IOException {
        Path folder = tempDir.resolve("lib");
        Path file = Files.createFile(folder.resolve("Locked [removeerr1].webm"));
        YtDlpLibrary lockedLibrary = new YtDlpLibrary(folder, tempDir.resolve("pl.txt"),
                f -> Optional.empty(), f -> { throw new IOException("locked"); });
        lockedLibrary.record("removeerr1", file, new AudioTrackInfo("Locked", "Author", 1000L, "removeerr1",
                false, "https://www.youtube.com/watch?v=removeerr1"));
        YtDlpService lockedService = new YtDlpService(new YtDlpSettings("yt-dlp", Mode.PROMPT, folder,
                "ytdlp-library", 0, 300, List.of(), 120), lockedLibrary, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class), Runnable::run);
        when(fixture.getBot().getYtDlp()).thenReturn(lockedService);
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "locked", 1, List.of("removeerr1"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("rmfile", ownerUserId, token, 0));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertTrue(lockedLibrary.find("removeerr1").isPresent());
        assertTrue(Files.exists(file));
        verify(acks.hook()).editOriginal(argThat((String s) -> s.contains("locked")));
    }

    // ---- search pagination ----

    @Test
    @DisplayName("search results paginate at the Playlists panel's page size")
    void search_paginatesAtPlaylistsPageSize() throws IOException {
        int total = LibraryPanelSlashCmd.PAGE_SIZE + 3;
        for (int i = 0; i < total; i++) {
            record("pagesong" + i, "Page Song " + i);
        }

        List<LibraryEntry> page1 = LibraryPanelSlashCmd.pageOf(service.searchLibrary("page song", 100), 1);
        List<LibraryEntry> page2 = LibraryPanelSlashCmd.pageOf(service.searchLibrary("page song", 100), 2);

        assertEquals(LibraryPanelSlashCmd.PAGE_SIZE, page1.size());
        assertEquals(total - LibraryPanelSlashCmd.PAGE_SIZE, page2.size());
        assertEquals(2, LibraryPanelSlashCmd.totalPages(total));
    }

    @Test
    @DisplayName("the page button moves the select menu to the next page of the same query")
    void onButtonInteraction_page_rendersNextPage() throws IOException {
        int total = LibraryPanelSlashCmd.PAGE_SIZE + 3;
        for (int i = 0; i < total; i++) {
            record("pagebtn" + i, "Page Button " + i);
        }
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "page button", 1,
                LibraryPanelSlashCmd.pageOf(service.searchLibrary("page button", 100), 1)
                        .stream().map(LibraryEntry::key).toList());
        fixture.withButtonId(ComponentIdParsers.libComponentId("page", ownerUserId, token, 1));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
    }

    @Test
    @DisplayName("Back from the detail view returns to the same search results page")
    void onButtonInteraction_backWithToken_returnsToSameSearchPage() throws IOException {
        record("backtest1", "Back Test One");
        record("backtest2", "Back Test Two");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "back test", 1, List.of("backtest1", "backtest2"));
        fixture.withButtonId(ComponentIdParsers.libComponentId("back", ownerUserId, token));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("deferEdit"), acks.acks());
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(acks.hook()).editOriginalEmbeds(embedCaptor.capture());
        assertTrue(embedCaptor.getValue().getDescription().contains("back test"),
                embedCaptor.getValue().getDescription());
    }

    // ---- pick -> detail ----

    @Test
    @DisplayName("picking a result opens its detail view")
    void onStringSelectInteraction_pick_opensDetail() throws IOException {
        record("pickone01", "Pick One");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "pick", 1, List.of("pickone01"));
        String componentId = ComponentIdParsers.libComponentId("pick", ownerUserId, token);
        StringSelectInteractionEvent event = mockSelectEvent(componentId, List.of("0"));

        runWithDj(event, true);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).deferEdit();
        verify(event.getHook()).editOriginalEmbeds(embedCaptor.capture());
        assertEquals("Pick One", embedCaptor.getValue().getTitle());
    }

    // ---- the long-key / long-query regression (the fix this round addresses) ----

    @Test
    @DisplayName("building views for a 150-char file: key and a 100-char query never throws, and every id stays <= 100 chars")
    void buildViews_longKeyAndLongQuery_doNotThrowAndStayWithinIdLimits() throws IOException {
        String longQuery = "x".repeat(100);
        String longKey = "file:" + "y".repeat(150);
        Path file = Files.createFile(tempDir.resolve("lib").resolve("Long.webm"));
        LibraryEntry entry = new LibraryEntry(longKey, file, "Long Title", "Author", 120_000L, null,
                "https://example.com/long");
        long ownerUserId = Long.MAX_VALUE;
        String token = tokens.create(ownerUserId, longQuery, 1, List.of(longKey));

        List<ActionRow> searchComponents = assertDoesNotThrow(() -> {
            LibraryPanelSlashCmd.buildSearchListEmbed(longQuery, List.of(entry), 1, 1, 1, null);
            return LibraryPanelSlashCmd.buildSearchListComponents(ownerUserId, token, List.of(entry), 1, 1);
        });
        List<ActionRow> detailComponents = assertDoesNotThrow(() -> {
            LibraryPanelSlashCmd.buildDetailEmbed(entry, null);
            return LibraryPanelSlashCmd.buildDetailComponents(ownerUserId, token, 0);
        });
        List<ActionRow> confirmComponents = assertDoesNotThrow(() -> {
            LibraryPanelSlashCmd.buildConfirmEmbed(entry, null);
            return LibraryPanelSlashCmd.buildConfirmComponents(ownerUserId, token, 0);
        });

        for (String id : componentIds(searchComponents)) {
            assertTrue(id.length() <= 100, id);
        }
        for (String id : componentIds(detailComponents)) {
            assertTrue(id.length() <= 100, id);
        }
        for (String id : componentIds(confirmComponents)) {
            assertTrue(id.length() <= 100, id);
        }
    }

    @Test
    @DisplayName("picking a long-key entry opens its detail (no truncation-caused 'no longer in the library')")
    void onStringSelectInteraction_longKeyEntry_opensDetail() throws IOException {
        String longKey = "soundcloud:" + "y".repeat(150);
        recordWithKey(longKey, "Long Key Song");
        long ownerUserId = fixture.getUser().getIdLong();
        String token = tokens.create(ownerUserId, "long", 1, List.of(longKey));
        String componentId = ComponentIdParsers.libComponentId("pick", ownerUserId, token);
        StringSelectInteractionEvent event = mockSelectEvent(componentId, List.of("0"));

        runWithDj(event, true);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).deferEdit();
        verify(event.getHook()).editOriginalEmbeds(embedCaptor.capture());
        assertEquals("Long Key Song", embedCaptor.getValue().getTitle());
    }

    @Test
    @DisplayName("submitting a 100-char search query renders results without throwing, whether or not it matches anything")
    void onModalInteraction_longQuery_rendersWithoutThrowing() throws IOException {
        String longQuery = "x".repeat(100);
        long ownerUserId = fixture.getUser().getIdLong();
        ModalInteractionEvent event = mockModalEvent(ownerUserId, longQuery);

        assertDoesNotThrow(() -> runWithDj(event, true));

        verify(event).deferEdit();
        verify(event.getHook()).editOriginalEmbeds(any(MessageEmbed[].class));
    }
}
