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
        when(downloader.request(any(), any(), anyLong(), anyBoolean())).thenReturn(new CompletableFuture<>());
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
        assertEquals(HIT_ID, wrapped.getVideoId());
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
        assertEquals(HIT_ID, wrapped.getVideoId());
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
        assertEquals(MISS_ID, wrapped.getVideoId());
        assertEquals("YouTube Title", wrapped.getInfo().title);
        verify(downloader).request(eq(MISS_ID), same(info), eq(GUILD), eq(true));
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
    void alreadyWrapped_unchanged() {
        LibraryAudioTrack wrapped = new LibraryAudioTrack(youtube(MISS_ID).getInfo(), MISS_ID,
                () -> CompletableFuture.<LibraryEntry>completedFuture(null), null, null);
        assertSame(wrapped, always().prepareForPlayback(wrapped, GUILD));
        verifyNoInteractions(downloader);
    }

    @Test
    void prompt_miss_unchanged() {
        AudioTrack yt = youtube(MISS_ID);
        assertSame(yt, service("yt-dlp", Mode.PROMPT).prepareForPlayback(yt, GUILD));
        verifyNoInteractions(downloader);
    }
}
