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

import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
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
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LibrarySourceManager}: the lavaplayer source manager that lets a YouTube link
 * already in the yt-dlp library play from disk without lavaplayer ever asking YouTube.
 */
class LibrarySourceManagerTest {

    private static final String HIT_ID = "HITHITHIT01";
    private static final String MISS_ID = "a_b-c_d1234";

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private YtDlpLibrary library;
    private Path hitFile;
    private YtDlpService service;
    private AudioPlayerManager playerManager;
    private AudioSourceManager youtubeSource;
    private LibrarySourceManager librarySourceManager;

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        Files.createDirectories(libraryFolder);
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("Playlists").resolve("ytdlp-library.txt"));
        hitFile = libraryFolder.resolve("Some Song [" + HIT_ID + "].webm");
        Files.writeString(hitFile, "not really audio");
        library.record(HIT_ID, hitFile, new AudioTrackInfo("Indexed Title", "Indexed Author", 123_000L, HIT_ID,
                false, "https://www.youtube.com/watch?v=" + HIT_ID));

        YtDlpDownloader downloader = mock(YtDlpDownloader.class);
        ProcessRunner runner = mock(ProcessRunner.class);
        YtDlpSettings settings = new YtDlpSettings("", Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120);
        service = new YtDlpService(settings, library, downloader, runner);

        youtubeSource = mock(AudioSourceManager.class);
        when(youtubeSource.getSourceName()).thenReturn("youtube");
        playerManager = mock(AudioPlayerManager.class);
        when(playerManager.getSourceManagers()).thenReturn(List.of(youtubeSource));

        librarySourceManager = new LibrarySourceManager(() -> service);
    }

    private AudioItem loadItem(String identifier) {
        return librarySourceManager.loadItem(playerManager, new AudioReference(identifier, null));
    }

    @Test
    void watchUrlInLibrary_returnsLibraryTrackWithIndexInfo() {
        AudioItem item = loadItem("https://www.youtube.com/watch?v=" + HIT_ID);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, item);
        assertEquals(HIT_ID, track.getKey());
        assertEquals("Indexed Title", track.getInfo().title);
        assertEquals("Indexed Author", track.getInfo().author);
        assertSame(youtubeSource, track.getSourceManager());
    }

    @Test
    void shortUrlAndNoScheme_inLibrary_returnsTrack() {
        AudioItem item = loadItem("youtu.be/" + HIT_ID);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, item);
        assertEquals(HIT_ID, track.getKey());
        assertEquals("Indexed Title", track.getInfo().title);
    }

    @Test
    void notInLibrary_returnsNull() {
        assertNull(loadItem("https://www.youtube.com/watch?v=" + MISS_ID));
    }

    @Test
    void playlistUrlWithVideo_returnsNull() {
        assertNull(loadItem("https://www.youtube.com/watch?v=" + HIT_ID + "&list=PLsomePlaylistId12345"));
    }

    @Test
    void searchQuery_returnsNull() {
        assertNull(loadItem("ytsearch:never gonna give you up"));
        assertNull(loadItem("never gonna give you up"));

        // Cheap: a plain search query never even looks at the player manager's source list.
        verifyNoInteractions(playerManager);
    }

    @Test
    void missingFile_returnsNull() throws IOException {
        Files.delete(hitFile);
        assertNull(loadItem("https://www.youtube.com/watch?v=" + HIT_ID));
    }

    @Test
    void adoptedEntry_returnsNull() throws IOException {
        // A hand-placed file with no recorded metadata yet (title guessed from the filename,
        // no author, no length). The first direct link to it must still go through YouTube
        // once, so prepareForPlayback's library-hit branch can fill in real metadata; only
        // after that is the entry no longer "adopted" and later links can short-circuit here.
        String adoptedId = "ADOPTED0001";
        Path adoptedFile = libraryFolder.resolve("Hand Named [" + adoptedId + "].webm");
        Files.writeString(adoptedFile, "not really audio");
        library.load();
        assertEquals("", library.find(adoptedId).orElseThrow().author());

        assertNull(loadItem("https://www.youtube.com/watch?v=" + adoptedId));
    }

    static List<String> hitUrlForms() {
        return List.of(
                "youtube.com/watch?v=" + HIT_ID,                              // scheme-less (the E2 form)
                "http://www.youtube.com/watch?v=" + HIT_ID,                   // http scheme
                "https://m.youtube.com/watch?v=" + HIT_ID,                    // mobile subdomain
                "https://music.youtube.com/watch?v=" + HIT_ID,                // music subdomain
                "https://www.youtube.com/watch?feature=share&v=" + HIT_ID + "&t=30s", // v= not first param
                "https://www.youtube.com/shorts/" + HIT_ID,                   // shorts
                "youtu.be/" + HIT_ID + "?si=abc123"                           // youtu.be with a tracking param
        );
    }

    @ParameterizedTest
    @MethodSource("hitUrlForms")
    void variousUrlForms_inLibrary_returnTrack(String url) {
        AudioItem item = loadItem(url);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, item, "for URL: " + url);
        assertEquals(HIT_ID, track.getKey(), "for URL: " + url);
    }

    static List<String> nonMatchingUrlForms() {
        return List.of(
                "https://www.youtube.com/watch?v=ABCDEFGHIJKL",                     // 12-character ID
                "https://youtu.be/" + HIT_ID + "?list=PLsomePlaylistId12345",        // youtu.be with list=
                "https://www.notyoutube.com/watch?v=" + HIT_ID                       // not actually YouTube
        );
    }

    @ParameterizedTest
    @MethodSource("nonMatchingUrlForms")
    void nonMatchingUrlForms_returnNull(String url) {
        assertNull(loadItem(url), "for URL: " + url);
    }

    @Test
    void getSourceName_isYtdlpLibrary() {
        assertEquals("ytdlp-library", librarySourceManager.getSourceName());
    }

    @Test
    void isTrackEncodable_isFalse() {
        assertFalse(librarySourceManager.isTrackEncodable(null));
    }

    @Test
    void decodeTrack_isNull() throws IOException {
        assertNull(librarySourceManager.decodeTrack(null, null));
    }

    @Test
    void encodeTrack_doesNotThrow() throws IOException {
        librarySourceManager.encodeTrack(null, null);
    }

    @Test
    void shutdown_doesNotThrow() {
        librarySourceManager.shutdown();
    }

    @Test
    void featureOff_libraryHitStillReturnsTrack() throws IOException {
        // Same "library hits play from disk in any mode, even with the feature off" rule.
        YtDlpSettings offSettings = new YtDlpSettings("", Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120);
        YtDlpService offService = new YtDlpService(offSettings, library, mock(YtDlpDownloader.class),
                mock(ProcessRunner.class));
        LibrarySourceManager manager = new LibrarySourceManager(() -> offService);

        AudioItem item = manager.loadItem(playerManager, new AudioReference(
                "https://www.youtube.com/watch?v=" + HIT_ID, null));

        assertInstanceOf(LibraryAudioTrack.class, item);
    }

    @Test
    void noYoutubeSourceRegistered_hitStillReturnedWithNullDisplaySource() {
        when(playerManager.getSourceManagers()).thenReturn(List.of());

        AudioItem item = loadItem("https://www.youtube.com/watch?v=" + HIT_ID);

        LibraryAudioTrack track = assertInstanceOf(LibraryAudioTrack.class, item);
        assertNull(track.getSourceManager());
    }
}
