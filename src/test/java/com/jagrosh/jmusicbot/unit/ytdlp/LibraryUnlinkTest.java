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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v1.CommandFactory;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.commands.v1.dj.LibraryCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.LibrarySlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.LibraryUnlinkSlashCmd;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibrarySourceManager;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /library unlink}: DJs remove the link behind the song now playing.
 */
@DisplayName("/library unlink")
class LibraryUnlinkTest {

    private static final String LIB_ID = "LIBLIBLIB01";
    private static final String REQ_ID = "a_b-c_d1234";
    private static final String YT = "https://www.youtube.com/watch?v=";

    @TempDir
    Path tempDir;

    private YtDlpLibrary library;
    private YtDlpService service;
    private Bot bot;
    private Guild guild;
    private AudioPlayer player;

    @BeforeEach
    void setUp() throws IOException {
        Path folder = tempDir.resolve("lib");
        Files.createDirectories(folder);
        library = new YtDlpLibrary(folder, tempDir.resolve("pl.txt"));
        Path file = folder.resolve("Axel F [" + LIB_ID + "].webm");
        Files.writeString(file, "x");
        library.record(LIB_ID, file, new AudioTrackInfo("Crazy Frog - Axel F (Official Video)", "Crazy Frog",
                168_000L, LIB_ID, false, YT + LIB_ID));
        service = new YtDlpService(new YtDlpSettings("yt-dlp", Mode.PROMPT, folder, "ytdlp-library", 0, 300,
                List.of(), 120), library, mock(YtDlpDownloader.class), mock(ProcessRunner.class), Runnable::run);

        bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        when(config.getAliases(anyString())).thenReturn(new String[0]);
        when(bot.getConfig()).thenReturn(config);
        when(bot.getYtDlp()).thenReturn(service);
        guild = mock(Guild.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioHandler handler = mock(AudioHandler.class);
        player = mock(AudioPlayer.class);
        when(guild.getAudioManager()).thenReturn(audioManager);
        when(audioManager.getSendingHandler()).thenReturn(handler);
        when(handler.getPlayer()).thenReturn(player);
    }

    private static AudioTrack requested() {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("youtube");
        when(track.getSourceManager()).thenReturn(source);
        when(track.getInfo()).thenReturn(new AudioTrackInfo("Axel F (Lyrics)", "Lyrics Hub", 169_000L, REQ_ID, false,
                YT + REQ_ID));
        return track;
    }

    /** The library copy playing for the linked request, as prepareForPlayback builds it. */
    private AudioTrack linkedCopyPlaying() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        AudioTrack copy = service.prepareForPlayback(requested(), 77L);
        assertTrue(copy instanceof LibraryAudioTrack);
        when(player.getPlayingTrack()).thenReturn(copy);
        return copy;
    }

    // ---- the shared logic ----

    @Test
    @DisplayName("unlinking the library copy of a linked song removes its link")
    void unlink_linkedCopy() throws IOException {
        AudioTrack copy = linkedCopyPlaying();

        YtDlpService.Unlinked unlinked = service.unlink(copy).orElseThrow();

        assertEquals("Axel F (Lyrics)", unlinked.requested());
        assertEquals("Crazy Frog - Axel F (Official Video)", unlinked.librarySong());
        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
        assertTrue(library.find(LIB_ID).isPresent()); // the library song itself stays
    }

    @Test
    @DisplayName("a requested song whose key is linked (playing as itself) can be unlinked too")
    void unlink_rawLinkedTrack() throws IOException {
        library.alias(REQ_ID, LIB_ID);

        assertTrue(service.unlink(requested()).isPresent());
        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
    }

    @Test
    @DisplayName("nothing linked: nothing to unlink")
    void unlink_nothing() throws IOException {
        assertTrue(service.unlink(requested()).isEmpty());
        assertTrue(service.unlink(null).isEmpty());
        AudioTrack exact = service.prepareForPlayback(trackFor(LIB_ID), 77L);
        assertTrue(service.unlink(exact).isEmpty());
    }

    @Test
    @DisplayName("a library copy loaded straight from a link shows the library song's title, not the URL")
    void unlink_copyFromLink_namedByTitle() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        AudioSourceManager youtube = mock(AudioSourceManager.class);
        when(youtube.getSourceName()).thenReturn("youtube");
        AudioPlayerManager manager = mock(AudioPlayerManager.class);
        when(manager.getSourceManagers()).thenReturn(List.of(youtube));
        AudioItem item = new LibrarySourceManager(() -> service).loadItem(manager, new AudioReference(YT + REQ_ID, null));
        LibraryAudioTrack copy = assertInstanceOf(LibraryAudioTrack.class, item);

        YtDlpService.Unlinked unlinked = service.unlink(copy).orElseThrow();

        assertEquals("Crazy Frog - Axel F (Official Video)", unlinked.requested());
        assertFalse(unlinked.requested().contains("youtube.com"));
    }

    @Test
    @DisplayName("if saving fails, the link is kept in memory too")
    void unlink_saveFails_keepsAlias() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        // Make the atomic move onto library.json fail: replace it with a non-empty directory.
        Path index = tempDir.resolve("lib").resolve("library.json");
        Files.delete(index);
        Files.createDirectories(index.resolve("blocker"));

        assertThrows(IOException.class, () -> library.unalias(REQ_ID));

        assertEquals(LIB_ID, library.aliasTarget(REQ_ID).orElseThrow());
    }

    private static AudioTrack trackFor(String videoId) {
        AudioTrack track = requested();
        when(track.getInfo()).thenReturn(new AudioTrackInfo("t", "a", 1L, videoId, false, YT + videoId));
        return track;
    }

    // ---- the slash command ----

    private SlashCommandEvent slashEvent() {
        SlashCommandEvent event = mock(SlashCommandEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(mock(Member.class));
        CommandClient client = mock(CommandClient.class);
        when(client.getError()).thenReturn("❌");
        when(client.getSuccess()).thenReturn("✅");
        when(client.getWarning()).thenReturn("⚠️");
        when(event.getClient()).thenReturn(client);
        when(event.reply(anyString())).thenReturn(mock(ReplyCallbackAction.class, RETURNS_SELF));
        return event;
    }

    private void runSlash(SlashCommandEvent event, boolean dj) {
        try (MockedStatic<DJCommand> djCommand = mockStatic(DJCommand.class)) {
            djCommand.when(() -> DJCommand.checkDJPermission(any(Bot.class), any(), any())).thenReturn(dj);
            new LibraryUnlinkSlashCmd(bot).doCommand(event);
        }
    }

    @Test
    @DisplayName("a DJ unlinks the current song and gets an ephemeral reply saying what was unlinked")
    void slash_dj_unlinks() throws IOException {
        linkedCopyPlaying();
        SlashCommandEvent event = slashEvent();

        runSlash(event, true);

        verify(event).reply("✅ Unlinked **Axel F (Lyrics)** from library song **Crazy Frog - Axel F (Official Video)**");
        verify(event.reply("")).setEphemeral(true);
        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
    }

    @Test
    @DisplayName("a non-DJ is refused and nothing is unlinked")
    void slash_nonDj_refused() throws IOException {
        linkedCopyPlaying();
        SlashCommandEvent event = slashEvent();

        runSlash(event, false);

        verify(event).reply(argThat((String s) -> s.contains("You need to be a DJ")));
        assertEquals(LIB_ID, library.aliasTarget(REQ_ID).orElseThrow());
    }

    @Test
    @DisplayName("nothing linked (or nothing playing): says so")
    void slash_nothingLinked() {
        AudioTrack unlinked = requested();
        when(player.getPlayingTrack()).thenReturn(unlinked);
        SlashCommandEvent event = slashEvent();
        runSlash(event, true);
        verify(event).reply(argThat((String s) -> s.contains("The current song isn't linked to a library song")));

        when(player.getPlayingTrack()).thenReturn(null);
        SlashCommandEvent idle = slashEvent();
        runSlash(idle, true);
        verify(idle).reply(argThat((String s) -> s.contains("The current song isn't linked to a library song")));
    }

    @Test
    @DisplayName("registered as /library with an unlink subcommand")
    void slash_isSubcommandOfLibrary() {
        SlashCommandData data = (SlashCommandData) new LibrarySlashCmd(bot).buildCommandData();

        assertEquals("library", data.getName());
        assertEquals(List.of("unlink", "panel"), data.getSubcommands().stream().map(s -> s.getName()).toList());
    }

    @Test
    @DisplayName("only registered when yt-dlp is configured")
    void registeredOnlyWithYtDlp() {
        BotConfig config = mock(BotConfig.class);
        when(config.getYtDlpSettings()).thenReturn(YtDlpSettings.disabled());
        assertFalse(CommandFactory.hasYtDlpCommands(config));

        when(config.getYtDlpSettings()).thenReturn(new YtDlpSettings("yt-dlp", Mode.PROMPT, tempDir, "x", 0, 300,
                List.of(), 120));
        assertTrue(CommandFactory.hasYtDlpCommands(config));
    }

    // ---- the prefix command ----

    @Test
    @DisplayName("the prefix command \"library unlink\" unlinks too")
    void prefix_unlinks() throws IOException {
        linkedCopyPlaying();
        CommandEvent event = mock(CommandEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getArgs()).thenReturn("unlink");

        new LibraryCmd(bot).doCommand(event);

        verify(event).replySuccess("Unlinked **Axel F (Lyrics)** from library song **Crazy Frog - Axel F (Official Video)**");
        assertTrue(library.aliasTarget(REQ_ID).isEmpty());
    }

    @Test
    @DisplayName("the prefix command without \"unlink\" shows how to use it")
    void prefix_usage() throws IOException {
        linkedCopyPlaying();
        CommandEvent event = mock(CommandEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getArgs()).thenReturn("");

        new LibraryCmd(bot).doCommand(event);

        verify(event).replyError(argThat((String s) -> s.contains("unlink")));
        verify(event, never()).replySuccess(anyString());
        assertEquals(LIB_ID, library.aliasTarget(REQ_ID).orElseThrow());
    }
}
