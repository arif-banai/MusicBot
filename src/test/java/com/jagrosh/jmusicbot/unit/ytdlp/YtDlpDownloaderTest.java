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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpException;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YtDlpDownloader Tests")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class YtDlpDownloaderTest {

    private static final long WAIT_SECONDS = 4;

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private YtDlpSettings settings;
    private YtDlpLibrary library;
    private FakeRunner runner;
    private FakeSleeper sleeper;
    private YtDlpDownloader downloader;

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        Files.createDirectories(libraryFolder);
        settings = new YtDlpSettings("yt-dlp", YtDlpSettings.Mode.PROMPT, libraryFolder, "ytdlp-library",
                5, 300, List.of(), 120);
        library = new YtDlpLibrary(libraryFolder, tempDir.resolve("playlists").resolve("ytdlp-library.txt"));
        runner = new FakeRunner(libraryFolder);
        sleeper = new FakeSleeper();
        downloader = new YtDlpDownloader(settings, library, runner, sleeper);
    }

    @AfterEach
    void tearDown() {
        runner.releaseAll();
        downloader.shutdown();
    }

    @Test
    @DisplayName("a library hit completes immediately and starts no process")
    void libraryHit_noProcess() throws Exception {
        Path file = Files.createFile(libraryFolder.resolve("Song [v1].webm"));
        LibraryEntry recorded = library.record("v1", file, info("v1"));
        downloader.start();

        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);

        assertTrue(future.isDone());
        assertEquals(recorded, future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, runner.calls().size());
    }

    @Test
    @DisplayName("the same video requested twice runs one process")
    void sameVideoTwice_oneProcess() throws Exception {
        CompletableFuture<LibraryEntry> first = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        CompletableFuture<LibraryEntry> second = downloader.request("v1", url("v1"), info("v1"), 2L, false);
        downloader.start();

        LibraryEntry a = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
        LibraryEntry b = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, runner.calls().size());
        assertEquals(a, b);
    }

    @Test
    @DisplayName("a request for a video that is already running joins that job")
    void sameVideoWhileRunning_joinsRunningJob() throws Exception {
        runner.gate("v1");
        CompletableFuture<LibraryEntry> first = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();
        runner.awaitStarted("v1");

        CompletableFuture<LibraryEntry> second = downloader.request("v1", url("v1"), info("v1"), 2L, true);
        runner.release("v1");

        assertEquals(first.get(WAIT_SECONDS, TimeUnit.SECONDS), second.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, runner.calls().size());
    }

    @Test
    @DisplayName("many threads requesting one video at once run one process")
    void concurrentRequests_oneProcess() throws Exception {
        downloader.start();
        runner.gate("v1");
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<LibraryEntry>> futures = new CopyOnWriteArrayList<>();
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long guild = i;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    futures.add(downloader.request("v1", url("v1"), info("v1"), guild, guild % 2 == 0));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();
            started.add(t);
        }
        go.countDown();
        for (Thread t : started) {
            t.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        }
        runner.release("v1");

        assertEquals(threads, futures.size());
        LibraryEntry expected = futures.get(0).get(WAIT_SECONDS, TimeUnit.SECONDS);
        for (CompletableFuture<LibraryEntry> f : futures) {
            assertEquals(expected, f.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }
        assertEquals(1, runner.calls().size());
    }

    @Test
    @DisplayName("next-up jobs run before normal jobs")
    void nextUpRunsFirst() throws Exception {
        CompletableFuture<LibraryEntry> a = downloader.request("a", url("a"), info("a"), 1L, false);
        CompletableFuture<LibraryEntry> b = downloader.request("b", url("b"), info("b"), 1L, false);
        CompletableFuture<LibraryEntry> c = downloader.request("c", url("c"), info("c"), 1L, true);
        downloader.start();

        CompletableFuture.allOf(a, b, c).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(List.of("c", "a", "b"), runner.videoIds());
    }

    @Test
    @DisplayName("re-requesting a queued job as next up moves it ahead")
    void nextUpUpgradesQueuedJob() throws Exception {
        CompletableFuture<LibraryEntry> a = downloader.request("a", url("a"), info("a"), 1L, false);
        CompletableFuture<LibraryEntry> b = downloader.request("b", url("b"), info("b"), 1L, false);
        CompletableFuture<LibraryEntry> b2 = downloader.request("b", url("b"), info("b"), 2L, true);
        downloader.start();

        CompletableFuture.allOf(a, b, b2).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(List.of("b", "a"), runner.videoIds());
    }

    @Test
    @DisplayName("the worker sleeps delaySeconds after every job")
    void pacesBetweenDownloads() throws Exception {
        sleeper.expect(2);
        CompletableFuture<LibraryEntry> a = downloader.request("a", url("a"), info("a"), 1L, false);
        CompletableFuture<LibraryEntry> b = downloader.request("b", url("b"), info("b"), 1L, false);
        downloader.start();

        CompletableFuture.allOf(a, b).get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertTrue(sleeper.await(WAIT_SECONDS));

        assertEquals(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5)), sleeper.durations());
    }

    @Test
    @DisplayName("a failed download completes the future with the stderr summary")
    void failure_completesExceptionallyWithSummary() {
        runner.script("v1", new ProcessResult(1, "", "ERROR: HTTP Error 403", false));
        sleeper.expect(1);
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("403"), cause.getMessage());
    }

    @Test
    @DisplayName("exit 0 without a printed path is a failure")
    void missingPrintedPath_isFailure() {
        runner.script("v1", new ProcessResult(0, "", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("output path"), cause.getMessage());
    }

    @Test
    @DisplayName("exit 0 with a printed path that doesn't exist is a failure")
    void printedPathMissingOnDisk_isFailure() {
        runner.script("v1", new ProcessResult(0, libraryFolder.resolve("gone [v1].webm") + "\n", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("output path"), cause.getMessage());
    }

    @Test
    @DisplayName("a mangled printed path falls back to the lone [id] file in the library folder")
    void mangledPrintedPath_fallsBackToIdFile() throws Exception {
        String videoId = "vid00000001";
        Path realFile = Files.writeString(libraryFolder.resolve("X Æ [" + videoId + "].webm"), "audio");
        String mangledPrinted = libraryFolder.resolve("X �DIDIAS [" + videoId + "].webm").toString();
        runner.script(videoId, new ProcessResult(0, mangledPrinted + "\n", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request(videoId, url(videoId), info(videoId), 1L, false);
        downloader.start();

        LibraryEntry entry = future.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(realFile.toAbsolutePath().normalize(), entry.file());
    }

    @Test
    @DisplayName("a mangled printed path with no matching [id] file in the library still fails")
    void noMatchingFile_stillFails() throws Exception {
        String videoId = "vid00000001";
        Files.writeString(libraryFolder.resolve("Other [vid00000002].webm"), "audio");
        String mangledPrinted = libraryFolder.resolve("X � [" + videoId + "].webm").toString();
        runner.script(videoId, new ProcessResult(0, mangledPrinted + "\n", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request(videoId, url(videoId), info(videoId), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("output path"), cause.getMessage());
    }

    @Test
    @DisplayName("a .part sibling of the [id] file is ignored by the fallback scan")
    void partFile_ignored() throws Exception {
        String videoId = "vid00000001";
        Files.writeString(libraryFolder.resolve("X [" + videoId + "].part"), "partial");
        String mangledPrinted = libraryFolder.resolve("X � [" + videoId + "].webm").toString();
        runner.script(videoId, new ProcessResult(0, mangledPrinted + "\n", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request(videoId, url(videoId), info(videoId), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("output path"), cause.getMessage());
    }

    @Test
    @DisplayName("several matching [id] files in the library still fails")
    void multipleMatches_stillFails() throws Exception {
        String videoId = "vid00000001";
        Files.writeString(libraryFolder.resolve("X [" + videoId + "].webm"), "audio");
        Files.writeString(libraryFolder.resolve("X [" + videoId + "].m4a"), "audio");
        String mangledPrinted = libraryFolder.resolve("X � [" + videoId + "].webm").toString();
        runner.script(videoId, new ProcessResult(0, mangledPrinted + "\n", "", false));
        CompletableFuture<LibraryEntry> future = downloader.request(videoId, url(videoId), info(videoId), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));

        YtDlpException cause = assertInstanceOf(YtDlpException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("output path"), cause.getMessage());
    }

    @Test
    @DisplayName("the worker survives a job whose runner throws, and paces after it")
    void runnerThrows_workerContinues() throws Exception {
        runner.throwFor("bad", new IOException("boom"));
        sleeper.expect(2);
        CompletableFuture<LibraryEntry> bad = downloader.request("bad", url("bad"), info("bad"), 1L, false);
        CompletableFuture<LibraryEntry> good = downloader.request("good", url("good"), info("good"), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> bad.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(YtDlpException.class, ex.getCause());
        assertEquals("good", good.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
        assertTrue(sleeper.await(WAIT_SECONDS));
    }

    @Test
    @DisplayName("a runner interrupted outside shutdown fails that job, and the worker keeps going")
    void runnerInterrupted_workerContinues() throws Exception {
        runner.throwFor("bad", new InterruptedException("stray"));
        CompletableFuture<LibraryEntry> bad = downloader.request("bad", url("bad"), info("bad"), 1L, false);
        CompletableFuture<LibraryEntry> good = downloader.request("good", url("good"), info("good"), 1L, false);
        downloader.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> bad.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(YtDlpException.class, ex.getCause());
        assertEquals("good", good.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
    }

    @Test
    @DisplayName("a future callback that interrupts the worker doesn't kill it")
    void callbackInterrupt_workerSurvives() throws Exception {
        CompletableFuture<LibraryEntry> a = downloader.request("a", url("a"), info("a"), 1L, false);
        // Registered before start(), so it runs on the worker thread when "a" completes. The test
        // thread never waits on "a" itself (a waiting get() can run pending callbacks on the
        // waiting thread), and the callback only interrupts the worker.
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> ranOn = new AtomicReference<>();
        a.thenRun(() -> {
            ranOn.set(Thread.currentThread().getName());
            if ("ytdlp-downloader".equals(Thread.currentThread().getName())) {
                Thread.currentThread().interrupt();
            }
            ran.countDown();
        });
        CompletableFuture<LibraryEntry> b = downloader.request("b", url("b"), info("b"), 1L, false);
        downloader.start();

        assertTrue(ran.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("ytdlp-downloader", ranOn.get());
        assertEquals("b", b.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
        assertEquals("a", a.getNow(null).key());

        CompletableFuture<LibraryEntry> c = downloader.request("c", url("c"), info("c"), 1L, false);
        assertEquals("c", c.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
        assertEquals(List.of("a", "b", "c"), runner.videoIds());
    }

    @Test
    @DisplayName("cancelGuild drops a queued job only that guild wanted")
    void cancelGuild_dropsQueuedJob() throws Exception {
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);

        downloader.cancelGuild(1L);

        assertThrows(CancellationException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<LibraryEntry> other = downloader.request("v2", url("v2"), info("v2"), 2L, false);
        downloader.start();
        other.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(List.of("v2"), runner.videoIds());
    }

    @Test
    @DisplayName("cancelGuild keeps a job another guild still waits on")
    void cancelGuild_keepsJobOtherGuildWaitsOn() throws Exception {
        CompletableFuture<LibraryEntry> first = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        CompletableFuture<LibraryEntry> second = downloader.request("v1", url("v1"), info("v1"), 2L, false);

        downloader.cancelGuild(1L);
        downloader.start();

        assertEquals("v1", second.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
        assertEquals(List.of("v1"), runner.videoIds());
        assertSame(first, second);
    }

    @Test
    @DisplayName("cancelGuild never stops a running job")
    void cancelGuild_doesNotStopRunningJob() throws Exception {
        runner.gate("v1");
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();
        runner.awaitStarted("v1");

        downloader.cancelGuild(1L);
        runner.release("v1");

        assertEquals("v1", future.get(WAIT_SECONDS, TimeUnit.SECONDS).key());
        assertTrue(library.find("v1").isPresent());
    }

    @Test
    @DisplayName("a successful download is recorded in the library")
    void success_recordsInLibrary() throws Exception {
        CompletableFuture<LibraryEntry> future = downloader.request("v1", url("v1"), info("v1"), 1L, false);
        downloader.start();

        LibraryEntry entry = future.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertTrue(library.find("v1").isPresent());
        assertEquals("Title v1", library.find("v1").get().title());
        assertEquals(entry, library.find("v1").get());
    }

    @Test
    @DisplayName("shutdown cancels queued jobs, and later requests are cancelled")
    void shutdown_cancelsQueuedJobs() {
        runner.gate("a");
        CompletableFuture<LibraryEntry> a = downloader.request("a", url("a"), info("a"), 1L, false);
        CompletableFuture<LibraryEntry> b = downloader.request("b", url("b"), info("b"), 1L, false);
        downloader.start();
        runner.awaitStarted("a");

        downloader.shutdown();

        assertThrows(CancellationException.class, () -> b.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertThrows(CancellationException.class, () -> a.get(WAIT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<LibraryEntry> late = downloader.request("c", url("c"), info("c"), 1L, false);
        assertTrue(late.isCancelled());
        assertEquals(List.of("a"), runner.videoIds());
    }

    @Test
    @DisplayName("a non-YouTube request fetches the track URI with a [source-id] template and records under the key")
    void nonYoutube_usesUriAndSourceTemplate_recordsUnderKey() throws Exception {
        String permalink = "https://soundcloud.com/crazyfrog/axel-f";
        String key = "soundcloud:" + permalink;
        Path written = libraryFolder.resolve("Axel F [soundcloud-123456789].mp3");
        runner.scriptUrl(permalink, () -> {
            Files.writeString(written, "audio");
            return new ProcessResult(0, written + "\n", "", false);
        });
        AudioTrackInfo info = new AudioTrackInfo("Axel F", "Crazy Frog", 170_000L,
                "O:https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/x/stream/hls", false, permalink,
                "https://i1.sndcdn.com/art.jpg", null);
        downloader.start();

        LibraryEntry entry = downloader.request(key, permalink, info, 1L, false).get(WAIT_SECONDS, TimeUnit.SECONDS);

        List<String> command = runner.calls().get(0);
        assertEquals(permalink, command.get(command.size() - 1));
        int o = command.indexOf("-o");
        assertEquals(libraryFolder.resolve("%(title)s [soundcloud-%(id)s].%(ext)s").toString(), command.get(o + 1));
        assertEquals(key, entry.key());
        assertEquals(written.toAbsolutePath().normalize(), entry.file());
        assertEquals("soundcloud", entry.source());
        assertEquals(entry, library.find(key).orElseThrow());
        assertEquals(entry, downloader.request(key, permalink, info, 2L, false).getNow(null),
                "a later request for the same key is a library hit");
    }

    @Test
    @DisplayName("a non-YouTube mangled printed path falls back to the lone new [source-...] file")
    void nonYoutube_mangledPath_fallsBackToLoneNewSourceFile() throws Exception {
        String permalink = "https://soundcloud.com/x/y";
        String key = "soundcloud:" + permalink;
        Path indexedSibling = Files.writeString(libraryFolder.resolve("Old [soundcloud-111].mp3"), "audio");
        library.record("soundcloud:https://soundcloud.com/x/old", indexedSibling, info("old"));
        Path realFile = libraryFolder.resolve("Æ Song [soundcloud-222].mp3");
        Files.writeString(libraryFolder.resolve("Æ Song [soundcloud-222].mp3.part"), "partial");
        runner.scriptUrl(permalink, () -> {
            Files.writeString(realFile, "audio");
            return new ProcessResult(0, libraryFolder.resolve("� Song [soundcloud-222].mp3") + "\n", "", false);
        });
        downloader.start();

        LibraryEntry entry = downloader.request(key, permalink, info("y"), 1L, false).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(realFile.toAbsolutePath().normalize(), entry.file());
    }

    @Test
    @DisplayName("the fallback ignores yt-dlp's sidecar and thumbnail files beside the song")
    void mangledPath_fallbackIgnoresSidecarAndThumbnail() throws Exception {
        String permalink = "https://soundcloud.com/x/y";
        Path realFile = libraryFolder.resolve("Æ Song [soundcloud-222].mp3");
        runner.scriptUrl(permalink, () -> {
            Files.writeString(realFile, "audio");
            Files.writeString(libraryFolder.resolve("Æ Song [soundcloud-222].info.json"), "{}");
            Files.writeString(libraryFolder.resolve("Æ Song [soundcloud-222].jpg"), "image");
            return new ProcessResult(0, libraryFolder.resolve("� Song [soundcloud-222].mp3") + "\n", "", false);
        });
        String videoId = "vid00000001";
        Path ytFile = libraryFolder.resolve("X Æ [" + videoId + "].webm");
        runner.script(videoId, new ProcessResult(0, libraryFolder.resolve("X � [" + videoId + "].webm") + "\n", "",
                false));
        Files.writeString(ytFile, "audio");
        Files.writeString(libraryFolder.resolve("X Æ [" + videoId + "].webp"), "thumbnail");
        downloader.start();

        LibraryEntry sc = downloader.request("soundcloud:" + permalink, permalink, info("y"), 1L, false)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        LibraryEntry yt = downloader.request(videoId, url(videoId), info(videoId), 1L, false)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(realFile.toAbsolutePath().normalize(), sc.file());
        assertEquals(ytFile.toAbsolutePath().normalize(), yt.file());
    }

    @Test
    @DisplayName("a non-YouTube mangled printed path with two new [source-...] files still fails")
    void nonYoutube_mangledPath_twoNewFiles_fails() throws Exception {
        String permalink = "https://soundcloud.com/x/y";
        Files.writeString(libraryFolder.resolve("A [soundcloud-333].mp3"), "audio");
        runner.scriptUrl(permalink, () -> {
            Files.writeString(libraryFolder.resolve("Æ Song [soundcloud-222].mp3"), "audio");
            return new ProcessResult(0, libraryFolder.resolve("� Song [soundcloud-222].mp3") + "\n", "", false);
        });
        downloader.start();

        CompletableFuture<LibraryEntry> future = downloader.request("soundcloud:" + permalink, permalink, info("y"),
                1L, false);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("output path"), ex.getCause().getMessage());
    }

    private static String url(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static AudioTrackInfo info(String videoId) {
        return new AudioTrackInfo("Title " + videoId, "Author", 1000L, videoId, false,
                "https://www.youtube.com/watch?v=" + videoId, null, null);
    }

    /**
     * Records each command it's asked to run. By default it creates
     * {@code <libraryFolder>/Title [<id>].webm} and prints its path, like yt-dlp would.
     * Individual videos can be scripted with a fixed result, made to throw, or gated
     * so the test controls when the "process" finishes.
     */
    private static final class FakeRunner implements ProcessRunner {
        private final Path libraryFolder;
        private final List<List<String>> calls = new CopyOnWriteArrayList<>();
        private final Map<String, ProcessResult> scripted = new ConcurrentHashMap<>();
        private final Map<String, Exception> throwing = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> started = new ConcurrentHashMap<>();
        private final Map<String, ScriptedRun> byUrl = new ConcurrentHashMap<>();

        interface ScriptedRun {
            ProcessResult run() throws IOException;
        }

        FakeRunner(Path libraryFolder) {
            this.libraryFolder = libraryFolder;
        }

        void script(String videoId, ProcessResult result) {
            scripted.put(videoId, result);
        }

        void throwFor(String videoId, Exception ex) {
            throwing.put(videoId, ex);
        }

        void gate(String videoId) {
            gates.put(videoId, new CountDownLatch(1));
            started.put(videoId, new CountDownLatch(1));
        }

        void awaitStarted(String videoId) {
            try {
                assertTrue(started.get(videoId).await(WAIT_SECONDS, TimeUnit.SECONDS), "runner never started");
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
        }

        void release(String videoId) {
            gates.get(videoId).countDown();
        }

        void releaseAll() {
            gates.values().forEach(CountDownLatch::countDown);
        }

        List<List<String>> calls() {
            return List.copyOf(calls);
        }

        List<String> videoIds() {
            List<String> ids = new ArrayList<>();
            for (List<String> command : calls) {
                ids.add(videoIdOf(command));
            }
            return Collections.unmodifiableList(ids);
        }

        void scriptUrl(String url, ScriptedRun run) {
            byUrl.put(url, run);
        }

        private static String videoIdOf(List<String> command) {
            String url = command.get(command.size() - 1);
            return url.substring(url.indexOf("v=") + 2);
        }

        @Override
        public ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
            calls.add(command);
            ScriptedRun byUrlRun = byUrl.get(command.get(command.size() - 1));
            if (byUrlRun != null) {
                return byUrlRun.run();
            }
            String videoId = videoIdOf(command);
            CountDownLatch startedLatch = started.get(videoId);
            if (startedLatch != null) {
                startedLatch.countDown();
            }
            CountDownLatch gate = gates.get(videoId);
            if (gate != null) {
                gate.await();
            }
            Exception ex = throwing.get(videoId);
            if (ex instanceof IOException io) {
                throw io;
            } else if (ex instanceof InterruptedException ie) {
                throw ie;
            } else if (ex != null) {
                throw new IllegalStateException(ex);
            }
            ProcessResult result = scripted.get(videoId);
            if (result != null) {
                return result;
            }
            Path file = libraryFolder.resolve("Title [" + videoId + "].webm");
            Files.writeString(file, "audio");
            return new ProcessResult(0, "[download] something\n" + file + "\n", "", false);
        }
    }

    private static final class FakeSleeper implements YtDlpDownloader.Sleeper {
        private final List<Duration> durations = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch latch = new CountDownLatch(0);

        void expect(int count) {
            latch = new CountDownLatch(count);
        }

        boolean await(long seconds) throws InterruptedException {
            return latch.await(seconds, TimeUnit.SECONDS);
        }

        List<Duration> durations() {
            return List.copyOf(durations);
        }

        /** Doesn't really sleep, but like {@link Thread#sleep} throws if the thread is interrupted. */
        @Override
        public void sleep(Duration d) throws InterruptedException {
            durations.add(d);
            latch.countDown();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
    }
}
