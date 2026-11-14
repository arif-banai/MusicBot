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
import com.jagrosh.jmusicbot.testutil.listener.InteractionAckTracker;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
 * ({@link LibraryInteractionListener}).
 */
@DisplayName("/library panel")
class LibraryPanelTest {

    @TempDir
    Path tempDir;

    private YtDlpLibrary library;
    private YtDlpService service;
    private ListenerTestFixture fixture;
    private LibraryInteractionListener listener;

    @BeforeEach
    void setUp() throws IOException {
        Path folder = tempDir.resolve("lib");
        Files.createDirectories(folder);
        library = new YtDlpLibrary(folder, tempDir.resolve("pl.txt"));
        service = new YtDlpService(new YtDlpSettings("yt-dlp", Mode.PROMPT, folder, "ytdlp-library", 0, 300,
                List.of(), 120), library, mock(YtDlpDownloader.class), mock(ProcessRunner.class), Runnable::run);

        fixture = ListenerTestFixture.create();
        listener = new LibraryInteractionListener(fixture.getBot());
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
        String componentId = ComponentIdParsers.libComponentId("pick", ownerUserId, ComponentIdParsers.libPageQueryArg(1, "song"));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, RETURNS_SELF);

        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        doReturn(fixture.getGuild()).when(event).getGuild();
        doReturn(fixture.getMember()).when(event).getMember();
        doReturn(fixture.getUser()).when(event).getUser();
        doReturn(componentId).when(event).getComponentId();
        doReturn(replyAction).when(event).reply(anyString());

        runWithDj(event, false);

        verify(event).reply(argThat((String s) -> s.contains("Only DJs can manage the library.")));
    }

    // ---- remove confirm flow ----

    @Test
    @DisplayName("rmidx removes the entry but keeps the file")
    void onButtonInteraction_rmidx_removesIndexOnly() throws IOException {
        record("removeidx1", "Remove Me");
        fixture.withButtonId(ComponentIdParsers.libComponentId(
                "rmidx", fixture.getUser().getIdLong(), "removeidx1"));
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
        fixture.withButtonId(ComponentIdParsers.libComponentId(
                "rmfile", fixture.getUser().getIdLong(), "removefil1"));
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
        fixture.withButtonId(ComponentIdParsers.libComponentId(
                "rmfile", fixture.getUser().getIdLong(), "removeerr1"));
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

        List<com.jagrosh.jmusicbot.ytdlp.LibraryEntry> page1 =
                LibraryPanelSlashCmd.pageOf(service.searchLibrary("page song", 100), 1);
        List<com.jagrosh.jmusicbot.ytdlp.LibraryEntry> page2 =
                LibraryPanelSlashCmd.pageOf(service.searchLibrary("page song", 100), 2);

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
        fixture.withButtonId(ComponentIdParsers.libComponentId("page", fixture.getUser().getIdLong(),
                ComponentIdParsers.libPageQueryArg(2, "page button")));
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        runWithDj(fixture.getButtonInteractionEvent(), true);

        assertEquals(List.of("editMessageEmbeds"), acks.acks());
    }
}
