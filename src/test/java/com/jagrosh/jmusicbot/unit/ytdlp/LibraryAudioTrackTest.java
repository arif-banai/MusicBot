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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpException;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.TrackStateListener;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class LibraryAudioTrackTest {

    private static final String VIDEO_ID = "a_b-c_d1234";

    @TempDir
    Path tempDir;

    private static AudioTrackInfo youtubeInfo() {
        return new AudioTrackInfo("Some Title", "Some Author", 999_999L, VIDEO_ID, false,
                "https://www.youtube.com/watch?v=" + VIDEO_ID, "https://i.ytimg.com/vi/" + VIDEO_ID + "/hq.jpg", null);
    }

    private static LibraryAudioTrack track(Supplier<CompletableFuture<LibraryEntry>> fileSource,
                                           CompletableFuture<Void> gate) {
        return new LibraryAudioTrack(youtubeInfo(), VIDEO_ID, fileSource, gate, null);
    }

    private static Supplier<CompletableFuture<LibraryEntry>> failing(String message) {
        return () -> CompletableFuture.failedFuture(new YtDlpException(message));
    }

    @Test
    @DisplayName("info is copied from the source YouTube track")
    void info_copiedFromSource() {
        AudioTrackInfo in = youtubeInfo();
        LibraryAudioTrack track = new LibraryAudioTrack(in, VIDEO_ID, failing("x"), null, null);

        AudioTrackInfo out = track.getInfo();
        assertEquals(in.title, out.title);
        assertEquals(in.author, out.author);
        assertEquals(in.length, out.length);
        assertEquals(in.identifier, out.identifier);
        assertEquals(in.uri, out.uri);
        assertEquals(in.artworkUrl, out.artworkUrl);
        assertEquals(VIDEO_ID, track.getKey());
    }

    @Test
    @DisplayName("source manager is the display source, so the YouTube URL is recorded")
    void sourceManager_isDisplaySource() {
        AudioSourceManager youtube = mock(AudioSourceManager.class);
        LibraryAudioTrack track = new LibraryAudioTrack(youtubeInfo(), VIDEO_ID, failing("x"), null, youtube);
        assertSame(youtube, track.getSourceManager());
        assertSame(youtube, track.makeClone().getSourceManager());
    }

    @Test
    @DisplayName("clone keeps info, video ID and user data, and drops the gate")
    void clone_keepsInfoAndDropsGate() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        LibraryAudioTrack track = track(failing("x"), gate);
        Object userData = new Object();
        track.setUserData(userData);
        assertTrue(track.isGated());
        assertTrue(track.isAwaitingGate());

        AudioTrack clone = track.makeClone();

        LibraryAudioTrack libClone = assertInstanceOf(LibraryAudioTrack.class, clone);
        assertNotSame(track, libClone);
        assertEquals(track.getInfo().title, libClone.getInfo().title);
        assertEquals(track.getInfo().author, libClone.getInfo().author);
        assertEquals(track.getInfo().length, libClone.getInfo().length);
        assertEquals(track.getInfo().identifier, libClone.getInfo().identifier);
        assertEquals(track.getInfo().uri, libClone.getInfo().uri);
        assertEquals(VIDEO_ID, libClone.getKey());
        assertFalse(libClone.isGated());
        assertFalse(libClone.isAwaitingGate());
        assertSame(userData, libClone.getUserData());
    }

    @Test
    @DisplayName("isAwaitingGate is true only while the gate is incomplete")
    void awaitingGate_tracksGateCompletion() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        LibraryAudioTrack track = track(failing("x"), gate);
        assertTrue(track.isAwaitingGate());
        gate.complete(null);
        assertTrue(track.isGated());
        assertFalse(track.isAwaitingGate());

        LibraryAudioTrack ungated = track(failing("x"), null);
        assertFalse(ungated.isGated());
        assertFalse(ungated.isAwaitingGate());
    }

    @Test
    @DisplayName("a failed download surfaces as a COMMON FriendlyException with yt-dlp's reason")
    void failedDownload_isFriendlyException() {
        LibraryAudioTrack track = track(failing("HTTP Error 403"), null);

        FriendlyException ex = assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class)));
        assertTrue(ex.getMessage().contains("403"), ex.getMessage());
        assertTrue(ex.getMessage().startsWith("yt-dlp couldn't download this track: "), ex.getMessage());
        assertEquals(FriendlyException.Severity.COMMON, ex.severity);
        assertInstanceOf(YtDlpException.class, ex.getCause());
    }

    @Test
    @DisplayName("process does not ask for the file until the gate completes")
    void gate_blocksUntilCompleted() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        AtomicBoolean asked = new AtomicBoolean();
        LibraryAudioTrack track = track(() -> {
            asked.set(true);
            return CompletableFuture.failedFuture(new YtDlpException("stop here"));
        }, gate);

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                track.process(mock(LocalAudioTrackExecutor.class));
            } catch (Throwable e) {
                thrown.set(e);
            } finally {
                done.countDown();
            }
        });
        t.start();

        assertFalse(done.await(200, TimeUnit.MILLISECONDS), "process returned before the gate opened");
        assertFalse(asked.get(), "fileSource was asked before the gate opened");

        gate.complete(null);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(asked.get());
        assertInstanceOf(FriendlyException.class, thrown.get());
    }

    @Test
    @DisplayName("an interrupt while waiting on the gate (track stopped) returns quietly")
    void interruptWhileGated_returnsQuietly() throws Exception {
        AtomicBoolean asked = new AtomicBoolean();
        LibraryAudioTrack track = track(() -> {
            asked.set(true);
            return new CompletableFuture<>();
        }, new CompletableFuture<>());

        assertQuietOnInterrupt(track);
        assertFalse(asked.get());
    }

    @Test
    @DisplayName("an interrupt while waiting on the download (track stopped) returns quietly")
    void interruptWhileDownloading_returnsQuietly() throws Exception {
        CompletableFuture<LibraryEntry> shared = new CompletableFuture<>();
        LibraryAudioTrack track = track(() -> shared, null);

        assertQuietOnInterrupt(track);
        assertFalse(shared.isDone(), "the shared download future must not be cancelled or completed");
    }

    private static void assertQuietOnInterrupt(LibraryAudioTrack track) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                track.process(mock(LocalAudioTrackExecutor.class));
                interruptRestored.set(Thread.currentThread().isInterrupted());
            } catch (Throwable e) {
                thrown.set(e);
            } finally {
                done.countDown();
            }
        });
        t.start();
        assertFalse(done.await(100, TimeUnit.MILLISECONDS));
        t.interrupt();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertNull(thrown.get(), () -> "expected a quiet return, got " + thrown.get());
        assertTrue(interruptRestored.get(), "the interrupt flag must be restored after a quiet return");
    }

    private static void assertCancelledFriendly(FriendlyException ex) {
        assertEquals("yt-dlp download was cancelled", ex.getMessage());
        assertEquals(FriendlyException.Severity.COMMON, ex.severity);
        boolean found = false;
        for (Throwable c = ex.getCause(); c != null; c = c.getCause()) {
            found |= c instanceof CancellationException;
        }
        assertTrue(found, "cause chain must contain a CancellationException");
    }

    @Test
    @DisplayName("a cancelled download throws a COMMON FriendlyException caused by the cancellation")
    void cancelledDownload_isFriendlyException() {
        CompletableFuture<LibraryEntry> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        LibraryAudioTrack track = track(() -> cancelled, null);

        assertCancelledFriendly(assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class))));
    }

    @Test
    @DisplayName("a cancelled gate throws a COMMON FriendlyException without asking for the file")
    void cancelledGate_isFriendlyException() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        gate.cancel(false);
        AtomicBoolean asked = new AtomicBoolean();
        LibraryAudioTrack track = track(() -> {
            asked.set(true);
            return new CompletableFuture<>();
        }, gate);

        assertCancelledFriendly(assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class))));
        assertFalse(asked.get());
    }

    @Test
    @DisplayName("a gate that fails throws the cancelled FriendlyException with the failure as cause")
    void failedGate_isFriendlyException() {
        IllegalStateException failure = new IllegalStateException("prompt expired");
        LibraryAudioTrack track = track(failing("x"), CompletableFuture.failedFuture(failure));

        FriendlyException ex = assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class)));
        assertEquals("yt-dlp download was cancelled", ex.getMessage());
        assertEquals(FriendlyException.Severity.COMMON, ex.severity);
        assertSame(failure, ex.getCause());
    }

    @Test
    @DisplayName("a download failure without a message never reads ': null'")
    void failedDownloadWithoutMessage_usesToString() {
        LibraryAudioTrack track = track(() -> CompletableFuture.failedFuture(new IllegalStateException()), null);

        FriendlyException ex = assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class)));
        assertFalse(ex.getMessage().endsWith("null"), ex.getMessage());
        assertTrue(ex.getMessage().contains("IllegalStateException"), ex.getMessage());
    }

    @Test
    @DisplayName("a cancelled gate ends the track in a real executor: terminator frame and track exception")
    void cancelledGate_realExecutorTerminates() throws Exception {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        gate.cancel(false);
        LibraryAudioTrack track = track(failing("x"), gate);

        AudioConfiguration config = new AudioConfiguration();
        config.setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_BE);
        LocalAudioTrackExecutor executor =
                new LocalAudioTrackExecutor(track, config, new AudioPlayerOptions(), false, 5000);
        TrackStateListener listener = mock(TrackStateListener.class);

        Thread t = new Thread(() -> executor.execute(listener));
        t.start();
        t.join(2000);
        assertFalse(t.isAlive());

        AudioFrame frame = executor.provide(1, TimeUnit.SECONDS);
        assertNotNull(frame);
        assertTrue(frame.isTerminator(), "the player only ends a track on a terminator frame");
        assertEquals(AudioTrackState.FINISHED, executor.getState());
        verify(listener).onTrackException(eq(track), any(FriendlyException.class));
    }

    @Test
    @DisplayName("a downloaded file that has gone missing is a COMMON FriendlyException")
    void missingFile_isFriendlyException() {
        Path missing = tempDir.resolve("gone [" + VIDEO_ID + "].webm");
        LibraryEntry entry = new LibraryEntry(VIDEO_ID, missing, "t", "a", 1000, null, "u");
        LibraryAudioTrack track = track(() -> CompletableFuture.completedFuture(entry), null);

        FriendlyException ex = assertThrows(FriendlyException.class,
                () -> track.process(mock(LocalAudioTrackExecutor.class)));
        assertEquals(FriendlyException.Severity.COMMON, ex.severity);
    }

    @Test
    @DisplayName("a real local file is decoded through the delegated local track")
    void realFile_isDelegatedAndDecoded() throws Exception {
        Path wav = tempDir.resolve("tone [" + VIDEO_ID + "].wav");
        writeWav(wav, 44_100, 2, 4410); // 100 ms
        LibraryEntry entry = new LibraryEntry(VIDEO_ID, wav, "t", "a", 100, null, "u");
        LibraryAudioTrack track = track(() -> CompletableFuture.completedFuture(entry), null);

        AudioConfiguration config = new AudioConfiguration();
        config.setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_BE);
        LocalAudioTrackExecutor executor =
                new LocalAudioTrackExecutor(track, config, new AudioPlayerOptions(), false, 5000);
        TrackStateListener listener = mock(TrackStateListener.class);

        Thread t = new Thread(() -> executor.execute(listener));
        t.start();
        try {
            AudioFrame frame = executor.provide(3, TimeUnit.SECONDS);
            assertNotNull(frame, "no frame was decoded from the library file");
            assertTrue(frame.getDataLength() > 0);
            // Duration now comes from the decoded file, not the YouTube info (999,999 ms -> 100 ms).
            assertEquals(100, track.getDuration(), 5);
        } finally {
            executor.stop();
            t.join(2000);
        }
        assertFalse(t.isAlive());
        verify(listener, never()).onTrackException(any(), any());
    }

    /** Writes a PCM16 little-endian WAV of a quiet square wave. */
    private static void writeWav(Path file, int sampleRate, int channels, int frames) throws IOException {
        int dataLength = frames * channels * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataLength).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * 2)
                .putShort((short) (channels * 2)).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataLength);
        for (int i = 0; i < frames; i++) {
            short v = (short) ((i / 50) % 2 == 0 ? 1000 : -1000);
            for (int c = 0; c < channels; c++) {
                b.putShort(v);
            }
        }
        Files.write(file, b.array());
    }
}
