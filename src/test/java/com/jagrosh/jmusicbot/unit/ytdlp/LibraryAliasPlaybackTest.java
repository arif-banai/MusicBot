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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.LibrarySourceManager;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Aliases in playback: once a requested song is linked to a library song, it plays the library
 * copy directly (prepareForPlayback, queue-time wrapping, and YouTube links), but only while
 * yt-dlp is enabled.
 */
@DisplayName("Library aliases in playback")
class LibraryAliasPlaybackTest {

    private static final long GUILD = 77L;
    private static final String LIB_ID = "LIBLIBLIB01";
    private static final String REQ_ID = "a_b-c_d1234";
    private static final String YT = "https://www.youtube.com/watch?v=";
    private static final String SC_URL = "https://soundcloud.com/crazyfrog/axel-f";

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private YtDlpLibrary library;
    private YtDlpDownloader downloader;
    private Path libFile;
    private final List<Runnable> background = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        Files.createDirectories(libraryFolder);
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("pl.txt"));
        libFile = libraryFolder.resolve("Axel F [" + LIB_ID + "].webm");
        Files.writeString(libFile, "not really audio");
        library.record(LIB_ID, libFile, new AudioTrackInfo("Crazy Frog - Axel F (Official Video)", "Crazy Frog",
                168_000L, LIB_ID, false, YT + LIB_ID, "https://img/lib.jpg", null));
        downloader = mock(YtDlpDownloader.class);
        when(downloader.request(any(), any(), any(), anyLong(), anyBoolean())).thenReturn(new CompletableFuture<>());
    }

    private YtDlpService service(String path, Mode mode) {
        YtDlpSettings settings = new YtDlpSettings(path, mode, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120);
        return new YtDlpService(settings, library, downloader, mock(ProcessRunner.class), background::add);
    }

    private YtDlpService enabled() {
        return service("yt-dlp", Mode.PROMPT);
    }

    private YtDlpService disabled() {
        return service("", Mode.PROMPT);
    }

    private static AudioTrack track(String sourceName, String identifier, String uri) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn(sourceName);
        when(track.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo("Axel F (Lyrics)", "Lyrics Hub", 169_000L, identifier, false, uri,
                "https://img/req.jpg", null);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(identifier);
        return track;
    }

    private static AudioTrack requested() {
        return track("youtube", REQ_ID, YT + REQ_ID);
    }

    @Test
    @DisplayName("an aliased YouTube request plays the library copy, shown with the library title")
    void aliasedYoutube_playsLibraryCopy() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        AudioTrack req = requested();
        Object userData = new Object();
        when(req.getUserData()).thenReturn(userData);

        LibraryAudioTrack result = assertInstanceOf(LibraryAudioTrack.class, enabled().prepareForPlayback(req, GUILD));

        assertEquals(LIB_ID, result.getKey());
        assertEquals(REQ_ID, result.getLinkedFrom());
        assertEquals("Axel F (Lyrics)", result.getLinkedFromTitle());
        assertEquals("Crazy Frog - Axel F (Official Video)", result.getInfo().title);
        assertEquals("Crazy Frog", result.getInfo().author);
        // The requested track's identity is kept, so queue links, history and saved playlists
        // still point at what was asked for (and replaying it follows the alias again).
        assertEquals(REQ_ID, result.getInfo().identifier);
        assertEquals(YT + REQ_ID, result.getInfo().uri);
        assertSame(req.getSourceManager(), result.getSourceManager());
        assertSame(userData, result.getUserData());
        verifyNoInteractions(downloader);
    }

    @Test
    @DisplayName("a clone keeps where the library copy was linked from")
    void clone_keepsLinkedFrom() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        LibraryAudioTrack result = (LibraryAudioTrack) enabled().prepareForPlayback(requested(), GUILD);

        LibraryAudioTrack clone = assertInstanceOf(LibraryAudioTrack.class, result.makeClone());

        assertEquals(REQ_ID, clone.getLinkedFrom());
        assertEquals("Axel F (Lyrics)", clone.getLinkedFromTitle());
    }

    @Test
    @DisplayName("feature off: aliases are ignored and the request plays as before")
    void disabled_aliasIgnored() throws IOException {
        library.alias(REQ_ID, LIB_ID);
        AudioTrack req = requested();

        assertSame(req, disabled().prepareForPlayback(req, GUILD));
        assertSame(req, disabled().wrapLibraryFile(req));
    }

    @Test
    @DisplayName("always mode: an aliased request plays the library copy and downloads nothing")
    void always_aliasedNotDownloaded() throws IOException {
        library.alias(REQ_ID, LIB_ID);

        AudioTrack result = service("yt-dlp", Mode.ALWAYS).prepareForPlayback(requested(), GUILD);

        assertEquals(LIB_ID, assertInstanceOf(LibraryAudioTrack.class, result).getKey());
        verifyNoInteractions(downloader);
    }

    @Test
    @DisplayName("an aliased request from another source (SoundCloud) plays the library copy")
    void aliasedSoundcloud_playsLibraryCopy() throws IOException {
        library.alias("soundcloud:" + SC_URL, LIB_ID);
        AudioTrack sc = track("soundcloud", "O:https://api-v2.soundcloud.com/media/x/stream/hls", SC_URL);

        LibraryAudioTrack result = assertInstanceOf(LibraryAudioTrack.class, enabled().prepareForPlayback(sc, GUILD));

        assertEquals(LIB_ID, result.getKey());
        assertEquals("soundcloud:" + SC_URL, result.getLinkedFrom());
        assertSame(sc.getSourceManager(), result.getSourceManager());
    }

    @Test
    @DisplayName("an exact library hit wins over an alias")
    void exactHitWinsOverAlias() throws IOException {
        Path other = libraryFolder.resolve("Other [" + REQ_ID + "].webm");
        Files.writeString(other, "x");
        library.record(REQ_ID, other, new AudioTrackInfo("Own Copy", "Me", 169_000L, REQ_ID, false, YT + REQ_ID));
        library.alias(REQ_ID, LIB_ID);

        LibraryAudioTrack result = assertInstanceOf(LibraryAudioTrack.class,
                enabled().prepareForPlayback(requested(), GUILD));

        assertEquals(REQ_ID, result.getKey());
        assertNull(result.getLinkedFrom());
    }

    @Test
    @DisplayName("a linked song isn't downloaded by \"download all\" or prefetch (it plays the library copy)")
    void isDownloadable_linked_false() throws IOException {
        assertTrue(enabled().isDownloadable(requested()));

        library.alias(REQ_ID, LIB_ID);

        org.junit.jupiter.api.Assertions.assertFalse(enabled().isDownloadable(requested()));
        // Feature off: aliases are ignored, as everywhere else.
        assertTrue(disabled().isDownloadable(requested()));
    }

    @Test
    @DisplayName("queue-time wrapping shows an aliased request with the library title")
    void wrapLibraryFile_aliased_wrapped() throws IOException {
        library.alias(REQ_ID, LIB_ID);

        AudioTrack result = enabled().wrapLibraryFile(requested());

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals(LIB_ID, wrapped.getKey());
        assertEquals(REQ_ID, wrapped.getLinkedFrom());
        assertEquals("Crazy Frog - Axel F (Official Video)", wrapped.getInfo().title);
    }

    @Test
    @DisplayName("queue-time wrapping leaves a YouTube track with an exact hit or no alias alone")
    void wrapLibraryFile_notAliased_unchanged() {
        AudioTrack miss = requested();
        AudioTrack exact = track("youtube", LIB_ID, YT + LIB_ID);

        assertSame(miss, enabled().wrapLibraryFile(miss));
        assertSame(exact, enabled().wrapLibraryFile(exact));
    }

    @Test
    @DisplayName("an alias target missing details is filled in from the requested track and persisted")
    void aliasTargetMissingDetails_filledInFromRequest() throws IOException {
        Path imported = libraryFolder.resolve("Axel F - Crazy Frog.mp3");
        Files.writeString(imported, "x");
        String fileKey = "file:Axel F - Crazy Frog.mp3";
        // An imported entry: title and length from the file, no author or artwork.
        writeIndexWithImportedEntry(fileKey, imported);
        library.alias(REQ_ID, fileKey);

        LibraryAudioTrack result = assertInstanceOf(LibraryAudioTrack.class,
                enabled().prepareForPlayback(requested(), GUILD));

        // Shown with the request's details where the entry has none.
        assertEquals("Axel F - Crazy Frog", result.getInfo().title);
        assertEquals("Lyrics Hub", result.getInfo().author);
        assertEquals("https://img/req.jpg", result.getInfo().artworkUrl);
        assertEquals(1, background.size());
        background.forEach(Runnable::run);

        YtDlpLibrary reloaded = new YtDlpLibrary(libraryFolder, tempDir.resolve("pl.txt"));
        reloaded.load();
        LibraryEntry filled = reloaded.find(fileKey).orElseThrow();
        assertEquals("Axel F - Crazy Frog", filled.title());
        assertEquals("", filled.author()); // an imported entry never takes the uploader as its artist
        assertEquals(170_000L, filled.lengthMs()); // its own length is kept
        assertEquals("https://img/req.jpg", filled.artworkUrl());
        assertEquals(List.of("Axel F", "Crazy Frog"), filled.nameParts());
    }

    private void writeIndexWithImportedEntry(String fileKey, Path file) throws IOException {
        String json = "{\"version\":2,\"entries\":{"
                + "\"" + LIB_ID + "\":{\"file\":" + quote(libFile.toAbsolutePath().normalize().toString())
                + ",\"title\":\"Crazy Frog - Axel F (Official Video)\",\"author\":\"Crazy Frog\",\"lengthMs\":168000,"
                + "\"uri\":\"" + YT + LIB_ID + "\",\"artworkUrl\":\"https://img/lib.jpg\",\"adopted\":false},"
                + "\"" + fileKey + "\":{\"file\":" + quote(file.toAbsolutePath().normalize().toString())
                + ",\"title\":\"Axel F - Crazy Frog\",\"author\":\"\",\"lengthMs\":170000,"
                + "\"nameParts\":[\"Axel F\",\"Crazy Frog\"],\"adopted\":true}},"
                + "\"aliases\":{},\"rejections\":[]}";
        Files.writeString(libraryFolder.resolve("library.json"), json);
        library.load();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\") + "\"";
    }

    // ---- fillMissing ----

    @Test
    @DisplayName("fillMissing fills only what is missing and keeps title, key, file, nameParts and adopted")
    void fillMissing_onlyMissing() throws IOException {
        Path imported = libraryFolder.resolve("Axel F - Crazy Frog.mp3");
        Files.writeString(imported, "x");
        String fileKey = "file:Axel F - Crazy Frog.mp3";
        writeIndexWithImportedEntry(fileKey, imported);

        library.fillMissing(fileKey, new AudioTrackInfo("Other Title", "Crazy Frog", 1L, "x", false, "u",
                "https://img/a.jpg", null));
        library.fillMissing(LIB_ID, new AudioTrackInfo("Other Title", "Other Author", 1L, "x", false, "u",
                "https://img/b.jpg", null));

        LibraryEntry filled = library.find(fileKey).orElseThrow();
        assertEquals("Axel F - Crazy Frog", filled.title());
        assertEquals("", filled.author(), "an imported (file:) entry's author is never filled in");
        assertEquals(170_000L, filled.lengthMs());
        assertEquals("https://img/a.jpg", filled.artworkUrl());
        assertEquals(List.of("Axel F", "Crazy Frog"), filled.nameParts());
        assertTrue(filled.adopted());
        assertEquals(filled, library.findByFile(imported).orElseThrow());
        LibraryEntry full = library.find(LIB_ID).orElseThrow();
        assertEquals("Crazy Frog", full.author());
        assertEquals(168_000L, full.lengthMs());
        assertEquals("https://img/lib.jpg", full.artworkUrl()); // already had artwork
        assertTrue(library.fillMissing("missing-key", new AudioTrackInfo("t", "a", 1L, "x", false, "u")).isEmpty());
    }

    // ---- LibrarySourceManager ----

    private AudioItem loadLink(YtDlpService service, String url) {
        AudioSourceManager youtube = mock(AudioSourceManager.class);
        when(youtube.getSourceName()).thenReturn("youtube");
        AudioPlayerManager manager = mock(AudioPlayerManager.class);
        when(manager.getSourceManagers()).thenReturn(List.of(youtube));
        return new LibrarySourceManager(() -> service).loadItem(manager, new AudioReference(url, null));
    }

    @Test
    @DisplayName("a YouTube link that is aliased loads the library copy directly")
    void link_aliased_loadsLibraryCopy() throws IOException {
        library.alias(REQ_ID, LIB_ID);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, loadLink(enabled(), YT + REQ_ID));

        assertEquals(LIB_ID, track.getKey());
        assertEquals(REQ_ID, track.getLinkedFrom());
        assertEquals("Crazy Frog - Axel F (Official Video)", track.getInfo().title);
        assertEquals(REQ_ID, track.getInfo().identifier);
        assertEquals(YT + REQ_ID, track.getInfo().uri);
        assertEquals("youtube", track.getSourceManager().getSourceName());
    }

    @Test
    @DisplayName("feature off: an aliased YouTube link is not intercepted")
    void link_aliased_disabled_null() throws IOException {
        library.alias(REQ_ID, LIB_ID);

        assertNull(loadLink(disabled(), YT + REQ_ID));
    }

    @Test
    @DisplayName("a link aliased to an entry without an author or length goes through YouTube once")
    void link_aliasedToEntryMissingDetails_null() throws IOException {
        Path imported = libraryFolder.resolve("Axel F - Crazy Frog.mp3");
        Files.writeString(imported, "x");
        String fileKey = "file:Axel F - Crazy Frog.mp3";
        writeIndexWithImportedEntry(fileKey, imported);
        library.alias(REQ_ID, fileKey);

        assertNull(loadLink(enabled(), YT + REQ_ID));
    }

    @Test
    @DisplayName("a link aliased to a tagged import (artist and length, no artwork) loads it directly")
    void link_aliasedToTaggedImportWithoutArtwork_loadsLibraryCopy() throws IOException {
        Path imported = libraryFolder.resolve("Axel F.mp3");
        Files.writeString(imported, "x");
        String fileKey = "file:Axel F.mp3";
        library.record(fileKey, imported, new AudioTrackInfo("Axel F", "Crazy Frog", 170_000L, imported.toString(),
                false, imported.toString(), null, null));
        library.alias(REQ_ID, fileKey);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, loadLink(enabled(), YT + REQ_ID));

        assertEquals(fileKey, track.getKey());
        assertEquals("Crazy Frog", track.getInfo().author);
    }

    @Test
    @DisplayName("a link aliased to an imported entry with its length and artwork loads it directly, with no author")
    void link_aliasedToImportedEntryWithDetails_loadsLibraryCopy() throws IOException {
        Path imported = libraryFolder.resolve("Axel F - Crazy Frog.mp3");
        Files.writeString(imported, "x");
        String fileKey = "file:Axel F - Crazy Frog.mp3";
        writeIndexWithImportedEntry(fileKey, imported);
        library.alias(REQ_ID, fileKey);
        library.fillMissing(fileKey, new AudioTrackInfo("Axel F (Lyrics)", "Lyrics Hub", 169_000L, REQ_ID, false,
                YT + REQ_ID, "https://img/req.jpg", null));

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, loadLink(enabled(), YT + REQ_ID));

        assertEquals(fileKey, track.getKey());
        assertEquals(REQ_ID, track.getLinkedFrom());
        assertEquals("", track.getInfo().author);
    }
}
