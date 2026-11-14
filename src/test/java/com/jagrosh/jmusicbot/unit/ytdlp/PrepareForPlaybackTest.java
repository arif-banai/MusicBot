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
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The {@code prepareForPlayback} decision table: one test per row.
 */
class PrepareForPlaybackTest {

    private static final long GUILD = 77L;
    private static final String HIT_ID = "HITHITHIT01";
    private static final String MISS_ID = "a_b-c_d1234";

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private YtDlpLibrary library;
    private YtDlpDownloader downloader;
    private ProcessRunner runner;
    private Path hitFile;

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        Files.createDirectories(libraryFolder);
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("Playlists").resolve("ytdlp-library.txt"));
        hitFile = libraryFolder.resolve("Some Song [" + HIT_ID + "].webm");
        Files.writeString(hitFile, "not really audio");
        library.record(HIT_ID, hitFile, new AudioTrackInfo("Indexed Title", "Indexed Author", 123_000L, HIT_ID,
                false, "https://www.youtube.com/watch?v=" + HIT_ID));
        downloader = mock(YtDlpDownloader.class);
        when(downloader.request(any(), any(), any(), anyLong(), anyBoolean())).thenReturn(new CompletableFuture<>());
        runner = mock(ProcessRunner.class);
    }

    private YtDlpService service(String path, Mode mode) {
        YtDlpSettings settings = new YtDlpSettings(path, mode, libraryFolder, "ytdlp-library", 0, 300,
                List.of(), 120);
        return new YtDlpService(settings, library, downloader, runner);
    }

    private YtDlpService disabled() {
        return service("", Mode.PROMPT);
    }

    private YtDlpService always() {
        return service("yt-dlp", Mode.ALWAYS);
    }

    private static AudioTrack track(String sourceName, String identifier, boolean stream) {
        AudioTrack track = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn(sourceName);
        when(track.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo("YouTube Title", "YouTube Author", 200_000L, identifier, stream,
                "https://www.youtube.com/watch?v=" + identifier);
        when(track.getInfo()).thenReturn(info);
        when(track.getIdentifier()).thenReturn(identifier);
        return track;
    }

    private static AudioTrack youtube(String videoId) {
        return track("youtube", videoId, false);
    }

    @Test
    void disabled_youtubeMiss_unchanged() {
        AudioTrack yt = youtube(MISS_ID);
        assertSame(yt, disabled().prepareForPlayback(yt, GUILD));
        verifyNoInteractions(downloader);
    }

    @Test
    void disabled_youtubeHit_wrapped() {
        AudioTrack yt = youtube(HIT_ID);
        Object userData = new Object();
        when(yt.getUserData()).thenReturn(userData);

        AudioTrack result = disabled().prepareForPlayback(yt, GUILD);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals(HIT_ID, wrapped.getKey());
        assertEquals("YouTube Title", wrapped.getInfo().title);
        assertSame(yt.getSourceManager(), wrapped.getSourceManager());
        assertSame(userData, wrapped.getUserData());
        verifyNoInteractions(downloader);
    }

    @Test
    void localFileInIndex_wrappedWithIndexTitle() {
        AudioTrack local = track("local", hitFile.toString(), false);
        Object userData = new Object();
        when(local.getUserData()).thenReturn(userData);

        AudioTrack result = disabled().prepareForPlayback(local, GUILD);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals(HIT_ID, wrapped.getKey());
        assertEquals("Indexed Title", wrapped.getInfo().title);
        assertEquals("Indexed Author", wrapped.getInfo().author);
        assertSame(userData, wrapped.getUserData());
        verifyNoInteractions(downloader);
    }

    @Test
    void localFileNotInIndex_unchanged() {
        AudioTrack local = track("local", tempDir.resolve("other.mp3").toString(), false);
        assertSame(local, always().prepareForPlayback(local, GUILD));
    }

    @Test
    void always_miss_wrappedAndRequestedNextUp() {
        AudioTrack yt = youtube(MISS_ID);
        AudioTrackInfo info = yt.getInfo();

        AudioTrack result = always().prepareForPlayback(yt, GUILD);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals(MISS_ID, wrapped.getKey());
        assertEquals("YouTube Title", wrapped.getInfo().title);
        verify(downloader).request(eq(MISS_ID), eq("https://www.youtube.com/watch?v=" + MISS_ID), same(info), eq(GUILD), eq(true));
    }

    @Test
    void always_stream_unchanged() {
        AudioTrack stream = track("youtube", MISS_ID, true);
        assertSame(stream, always().prepareForPlayback(stream, GUILD));
        verifyNoInteractions(downloader);
    }

    @Test
    void nonYoutube_unchanged() {
        AudioTrack other = track("soundcloud", MISS_ID, false);
        assertSame(other, always().prepareForPlayback(other, GUILD));
        verifyNoInteractions(downloader);
    }

    @Test
    void nonYoutube_exactKeyHit_playsFromLibrary() throws IOException {
        String permalink = "https://soundcloud.com/crazyfrog/axel-f";
        Path scFile = Files.writeString(libraryFolder.resolve("Axel F [soundcloud-1].mp3"), "not really audio");
        library.record("soundcloud:" + permalink, scFile, new AudioTrackInfo("Axel F", "Crazy Frog", 170_000L,
                "O:https://x", false, permalink));
        AudioTrack sc = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("soundcloud");
        when(sc.getSourceManager()).thenReturn(source);
        when(sc.getInfo()).thenReturn(new AudioTrackInfo("Axel F", "Crazy Frog", 170_000L, "O:https://x", false,
                permalink));

        AudioTrack result = disabled().prepareForPlayback(sc, GUILD);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals("soundcloud:" + permalink, wrapped.getKey());
        assertSame(source, wrapped.getSourceManager());
        assertSame(sc.getInfo(), wrapped.getInfo());
        verifyNoInteractions(downloader);
    }

    @Test
    void requestDownload_nonYoutube_usesCompositeKeyAndTrackUri() {
        String permalink = "https://soundcloud.com/a/b";
        AudioTrack sc = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("soundcloud");
        when(sc.getSourceManager()).thenReturn(source);
        AudioTrackInfo info = new AudioTrackInfo("B", "A", 1000L, "O:https://x", false, permalink);
        when(sc.getInfo()).thenReturn(info);

        always().requestDownload(sc, GUILD, false);

        verify(downloader).request(eq("soundcloud:" + permalink), eq(permalink), same(info), eq(GUILD), eq(false));
    }

    @Test
    void requestDownload_unsupportedTrack_failsWithoutRequesting() {
        AudioTrack local = track("local", tempDir.resolve("x.mp3").toString(), false);

        CompletableFuture<LibraryEntry> future = always().requestDownload(local, GUILD, false);

        assertTrue(future.isCompletedExceptionally());
        verifyNoInteractions(downloader);
    }

    @Test
    void alreadyWrapped_unchanged() {
        LibraryAudioTrack wrapped = new LibraryAudioTrack(youtube(MISS_ID).getInfo(), MISS_ID,
                () -> CompletableFuture.<LibraryEntry>completedFuture(null), null, null);
        assertSame(wrapped, always().prepareForPlayback(wrapped, GUILD));
        verifyNoInteractions(downloader);
    }

    @Test
    void adoptedHit_recordsYoutubeMetadataInBackground() throws IOException {
        String adoptedId = "ADOPTED0001";
        Path adoptedFile = libraryFolder.resolve("Hand Named [" + adoptedId + "].webm");
        Files.writeString(adoptedFile, "not really audio");
        library.load(); // adopts the file with a title guessed from its name
        assertEquals("", library.find(adoptedId).orElseThrow().author());
        List<Runnable> background = new ArrayList<>();
        YtDlpService service = new YtDlpService(
                new YtDlpSettings("", Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300, List.of(), 120),
                library, downloader, runner, background::add);

        AudioTrack result = service.prepareForPlayback(youtube(adoptedId), GUILD);

        assertInstanceOf(LibraryAudioTrack.class, result);
        // Nothing was written on the calling (player/audio) thread.
        assertEquals("", library.find(adoptedId).orElseThrow().author());
        assertEquals(1, background.size());

        background.forEach(Runnable::run);
        LibraryEntry recorded = library.find(adoptedId).orElseThrow();
        assertEquals("YouTube Title", recorded.title());
        assertEquals("YouTube Author", recorded.author());
        assertEquals(200_000L, recorded.lengthMs());
        assertEquals(adoptedFile.toAbsolutePath().normalize(), recorded.file());

        // Once it has metadata, further hits don't record again.
        background.clear();
        service.prepareForPlayback(youtube(adoptedId), GUILD);
        assertTrue(background.isEmpty());
    }

    @Test
    void indexedHit_doesNotRecordAgain() {
        List<Runnable> background = new ArrayList<>();
        YtDlpService service = new YtDlpService(
                new YtDlpSettings("", Mode.PROMPT, libraryFolder, "ytdlp-library", 0, 300, List.of(), 120),
                library, downloader, runner, background::add);

        assertInstanceOf(LibraryAudioTrack.class, service.prepareForPlayback(youtube(HIT_ID), GUILD));

        assertTrue(background.isEmpty());
        assertEquals("Indexed Title", library.find(HIT_ID).orElseThrow().title());
    }

    @Test
    void prompt_miss_unchanged() {
        AudioTrack yt = youtube(MISS_ID);
        assertSame(yt, service("yt-dlp", Mode.PROMPT).prepareForPlayback(yt, GUILD));
        verifyNoInteractions(downloader);
    }

    // ==================== wrapLibraryFile (queue-time wrapping) ====================

    @Test
    void wrapLibraryFile_localFileInIndex_wrapped() {
        AudioTrack local = track("local", hitFile.toString(), false);
        Object userData = new Object();
        when(local.getUserData()).thenReturn(userData);

        AudioTrack result = disabled().wrapLibraryFile(local);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals(HIT_ID, wrapped.getKey());
        assertEquals("Indexed Title", wrapped.getInfo().title);
        assertEquals("Indexed Author", wrapped.getInfo().author);
        assertSame(userData, wrapped.getUserData());
        verifyNoInteractions(downloader);
    }

    @Test
    void wrapLibraryFile_adoptedFile_keepsTheLocalTracksLengthAndAuthor() throws IOException {
        // An adopted file (hand-placed, no index entry) has no length or author in the index.
        Path adoptedFile = libraryFolder.resolve("Adopted Song [ADOPTED0001].webm");
        Files.writeString(adoptedFile, "not really audio");
        library.load();
        AudioTrack local = mock(AudioTrack.class);
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("local");
        when(local.getSourceManager()).thenReturn(source);
        when(local.getInfo()).thenReturn(new AudioTrackInfo("Unknown title", "Local Author", 245_000L,
                adoptedFile.toString(), false, adoptedFile.toString()));

        AudioTrack result = disabled().wrapLibraryFile(local);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, result);
        assertEquals("Adopted Song", wrapped.getInfo().title);
        assertEquals(245_000L, wrapped.getInfo().length);
        assertEquals(245_000L, wrapped.getDuration());
        assertEquals("Local Author", wrapped.getInfo().author);
        assertEquals("https://www.youtube.com/watch?v=ADOPTED0001", wrapped.getInfo().uri);
    }

    @Test
    void wrapLibraryFile_indexedFile_keepsTheIndexLength() {
        AudioTrack local = track("local", hitFile.toString(), false);

        LibraryAudioTrack wrapped = assertInstanceOf(LibraryAudioTrack.class, disabled().wrapLibraryFile(local));

        assertEquals(123_000L, wrapped.getInfo().length);
        assertEquals("Indexed Author", wrapped.getInfo().author);
    }

    @Test
    void wrapLibraryFile_localFileNotInIndex_unchanged() {
        AudioTrack local = track("local", tempDir.resolve("other.mp3").toString(), false);
        assertSame(local, disabled().wrapLibraryFile(local));
        verifyNoInteractions(downloader);
    }

    @Test
    void wrapLibraryFile_youtubeTrack_unchanged() {
        // Queue-time wrapping never decides YouTube hits/misses; that's still prepareForPlayback's job.
        AudioTrack yt = youtube(HIT_ID);
        assertSame(yt, disabled().wrapLibraryFile(yt));
        verifyNoInteractions(downloader);
    }

    @Test
    void wrapLibraryFile_alreadyWrapped_unchanged() {
        LibraryAudioTrack wrapped = new LibraryAudioTrack(youtube(MISS_ID).getInfo(), MISS_ID,
                () -> CompletableFuture.<LibraryEntry>completedFuture(null), null, null);
        assertSame(wrapped, disabled().wrapLibraryFile(wrapped));
        verifyNoInteractions(downloader);
    }

    @Test
    void wrapLibraryFile_null_unchanged() {
        assertSame(null, disabled().wrapLibraryFile(null));
    }
}
