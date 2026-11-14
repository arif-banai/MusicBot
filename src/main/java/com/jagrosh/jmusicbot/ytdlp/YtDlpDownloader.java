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
package com.jagrosh.jmusicbot.ytdlp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads YouTube videos into the {@link YtDlpLibrary} with yt-dlp, one at a time for
 * the whole bot.
 *
 * <p>Jobs wait in a priority queue: <em>next up</em> jobs run before others, and otherwise
 * jobs run in the order they were first requested. A single daemon worker thread runs
 * them, sleeping {@code delaySeconds} after every job that started a process, whether it
 * succeeded or failed. Jobs are keyed by video ID, so a second request for a video that is
 * already queued or running joins the existing job and gets the same future.
 *
 * <p>All mutations of the job map and the queue happen under one lock, so de-duplication
 * holds when requests arrive from several threads at once.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpDownloader.class);
    private static final String THREAD_NAME = "ytdlp-downloader";

    /** Pauses the worker between downloads. Abstracted so tests don't really sleep. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private final YtDlpSettings settings;
    private final YtDlpLibrary library;
    private final ProcessRunner runner;
    private final Sleeper sleeper;

    private final Object lock = new Object();
    /** Every queued or running job, by video ID. Guarded by {@link #lock}. */
    private final Map<String, Job> jobs = new HashMap<>();
    /** Queued jobs only. Mutated under {@link #lock}, except the worker's {@code take()}. */
    private final PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();
    /** Guarded by {@link #lock}. */
    private long nextSequence;
    /** Guarded by {@link #lock}. */
    private Thread worker;
    private volatile boolean shutdown;

    public YtDlpDownloader(YtDlpSettings settings, YtDlpLibrary library, ProcessRunner runner, Sleeper sleeper) {
        this.settings = settings;
        this.library = library;
        this.runner = runner;
        this.sleeper = sleeper;
    }

    /**
     * Returns a future for {@code videoId}'s library entry. A library hit completes
     * immediately and starts no process. A request for a video that is already queued or
     * running joins that job, adding {@code guildId} to it and upgrading it to
     * {@code nextUp} if asked. After {@link #shutdown()} the returned future is cancelled.
     *
     * <p>The returned future is shared by every request for the same video, so callers
     * shouldn't cancel it; use {@link #cancelGuild(long)} instead.
     */
    public CompletableFuture<LibraryEntry> request(String videoId, AudioTrackInfo info, long guildId,
                                                   boolean nextUp) {
        synchronized (lock) {
            if (shutdown) {
                return cancelledFuture();
            }

            Job existing = jobs.get(videoId);
            if (existing != null) {
                existing.guilds.add(guildId);
                if (nextUp && !existing.nextUp) {
                    // The queue never re-sorts an element changed in place, so take it out,
                    // change it, and put it back. If the worker already took it, it isn't
                    // queued any more and must not be re-added, or it would run twice.
                    boolean wasQueued = queue.remove(existing);
                    existing.nextUp = true;
                    if (wasQueued) {
                        queue.add(existing);
                    }
                }
                return existing.future;
            }

            // Checked under the lock: the worker removes a finished job from the map only
            // after recording it, so a request can never miss both the job and the entry.
            Optional<LibraryEntry> hit = library.findById(videoId);
            if (hit.isPresent()) {
                return CompletableFuture.completedFuture(hit.get());
            }

            Job job = new Job(videoId, info, nextUp, nextSequence++);
            job.guilds.add(guildId);
            jobs.put(videoId, job);
            queue.add(job);
            return job.future;
        }
    }

    /**
     * Removes {@code guildId} from every queued job. A job no guild wants any more is
     * dropped and its future cancelled. A running job is never stopped; it finishes into
     * the library.
     */
    public void cancelGuild(long guildId) {
        List<Job> dropped = new ArrayList<>();
        synchronized (lock) {
            Iterator<Job> it = jobs.values().iterator();
            while (it.hasNext()) {
                Job job = it.next();
                if (job.running) {
                    continue;
                }
                job.guilds.remove(guildId);
                if (job.guilds.isEmpty()) {
                    it.remove();
                    queue.remove(job);
                    dropped.add(job);
                }
            }
        }
        for (Job job : dropped) {
            job.future.cancel(false);
        }
    }

    /** Starts the single daemon worker thread, {@value #THREAD_NAME}. Does nothing if already started. */
    public void start() {
        synchronized (lock) {
            if (worker != null || shutdown) {
                return;
            }
            worker = new Thread(this::runWorker, THREAD_NAME);
            worker.setDaemon(true);
            worker.start();
        }
    }

    /**
     * Stops the worker: cancels every queued job's future and interrupts the worker, which
     * cancels the running job's future if its process is interrupted. Later requests get a
     * cancelled future. Safe to call more than once.
     */
    public void shutdown() {
        List<Job> dropped = new ArrayList<>();
        Thread toInterrupt;
        synchronized (lock) {
            shutdown = true;
            Iterator<Job> it = jobs.values().iterator();
            while (it.hasNext()) {
                Job job = it.next();
                if (!job.running) {
                    it.remove();
                    dropped.add(job);
                }
            }
            queue.clear();
            toInterrupt = worker;
        }
        for (Job job : dropped) {
            job.future.cancel(false);
        }
        if (toInterrupt != null) {
            toInterrupt.interrupt();
        }
    }

    private void runWorker() {
        while (!shutdown) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException ex) {
                // Only shutdown() stops the worker; the loop condition decides.
                continue;
            }

            // Future callbacks run on this thread and may leave a stray interrupt behind;
            // clear it so it can't fail the next process. Cleared before reading the
            // shutdown flag, which shutdown() sets before interrupting, so a real shutdown
            // interrupt arriving after this point is kept.
            Thread.interrupted();
            synchronized (lock) {
                // take() runs outside the lock, so cancelGuild or shutdown may have dropped
                // this job between take() and here. Then no process runs and no pause follows.
                if (shutdown || jobs.get(job.videoId) != job) {
                    continue;
                }
                job.running = true;
            }

            LibraryEntry entry = null;
            Throwable failure = null;
            boolean interrupted = false;
            try {
                entry = download(job);
            } catch (InterruptedException ex) {
                if (shutdown) {
                    interrupted = true;
                } else {
                    failure = new YtDlpException("yt-dlp download was interrupted", ex);
                }
            } catch (Throwable t) {
                failure = t;
            }

            synchronized (lock) {
                jobs.remove(job.videoId, job);
            }

            if (interrupted) {
                LOGGER.info("yt-dlp download of {} was interrupted", job.videoId);
                job.future.cancel(false);
                break;
            }
            if (failure != null) {
                YtDlpException ex = failure instanceof YtDlpException ytDlpEx ? ytDlpEx
                        : new YtDlpException("yt-dlp download failed: " + failure, failure);
                LOGGER.info("yt-dlp download of {} failed: {}", job.videoId, ex.getMessage());
                job.future.completeExceptionally(ex);
            } else {
                job.future.complete(entry);
            }

            try {
                sleeper.sleep(Duration.ofSeconds(settings.delaySeconds()));
            } catch (InterruptedException ex) {
                // Shutdown, or a stray interrupt from a future callback; the loop condition
                // decides which.
                continue;
            } catch (Throwable t) {
                LOGGER.warn("yt-dlp downloader pause failed: {}", t.toString());
            }
        }
    }

    private LibraryEntry download(Job job) throws IOException, InterruptedException {
        LOGGER.info("Downloading {} with yt-dlp", job.videoId);
        ProcessResult result = runner.run(YtDlpCommand.download(settings, job.videoId),
                Duration.ofSeconds(settings.timeoutSeconds()));

        if (result.timedOut() || result.exitCode() != 0) {
            String summary = YtDlpCommand.errorSummary(result, settings.timeoutSeconds());
            if (summary.isBlank()) {
                summary = "yt-dlp exited with code " + result.exitCode();
            }
            throw new YtDlpException(summary);
        }

        Optional<Path> printed = YtDlpCommand.parsePrintedPath(result.stdout());
        Path file = printed.filter(Files::isRegularFile).orElse(null);
        if (file == null) {
            // Some yt-dlp builds (e.g. winget/PyInstaller on Windows) mangle non-ASCII
            // characters in the --print output despite --encoding utf-8, so the printed
            // path may not refer to the file that was actually written. Fall back to
            // scanning the library folder for the one file yt-dlp's own naming scheme
            // would have produced for this video.
            file = findByIdSuffix(job.videoId).orElse(null);
        }
        if (file == null) {
            if (printed.isEmpty()) {
                throw new YtDlpException("yt-dlp finished but its output path was missing");
            }
            throw new YtDlpException("yt-dlp finished but its output path was missing: " + printed.get());
        }

        LibraryEntry entry = library.record(job.videoId, file, job.info);
        LOGGER.info("Downloaded {} to {} ({} bytes)", job.videoId, entry.file(), sizeOf(entry.file()));
        return entry;
    }

    /**
     * Scans the library folder (non-recursive) for a regular file whose name matches
     * {@code [videoId].<ext>} per {@link YtDlpLibrary#videoIdFromFileName}, ignoring
     * yt-dlp's own in-progress temp names ({@code .part}, {@code .ytdl}, {@code .temp},
     * and {@code .part-Frag*} fragment files). Returns the match only if there is
     * exactly one; zero or several is treated as no usable fallback, same as today.
     */
    private Optional<Path> findByIdSuffix(String videoId) {
        Path folder = settings.libraryFolder();
        if (!Files.isDirectory(folder)) {
            return Optional.empty();
        }

        Path match = null;
        int matches = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (isTempFileName(fileName)) {
                    continue;
                }
                Optional<String> id = YtDlpLibrary.videoIdFromFileName(fileName);
                if (id.isPresent() && id.get().equals(videoId)) {
                    match = path;
                    matches++;
                }
            }
        } catch (IOException ex) {
            // Treat a scan failure as no usable fallback; the caller's existing
            // "output path was missing" message still fires.
            return Optional.empty();
        }
        return matches == 1 ? Optional.of(match) : Optional.empty();
    }

    /** True for yt-dlp's own in-progress temp names: {@code .part}, {@code .ytdl}, {@code .temp}, or a {@code .part-Frag*} fragment. */
    private static boolean isTempFileName(String fileName) {
        return fileName.endsWith(".part") || fileName.endsWith(".ytdl") || fileName.endsWith(".temp")
                || fileName.contains(".part-Frag");
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return -1L;
        }
    }

    private static CompletableFuture<LibraryEntry> cancelledFuture() {
        CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        future.cancel(false);
        return future;
    }

    /**
     * One video to download. Ordered by {@link #nextUp} first, then {@link #sequence}.
     * {@link #nextUp} only changes while the job is out of the queue, so the queue's
     * ordering stays valid. Equality is identity, which {@code queue.remove} relies on.
     */
    private static final class Job implements Comparable<Job> {
        final String videoId;
        final AudioTrackInfo info;
        final long sequence;
        final CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        /** Guarded by the downloader's lock. */
        final Set<Long> guilds = new LinkedHashSet<>();
        /** Written under the downloader's lock, only while out of the queue. */
        volatile boolean nextUp;
        /** Guarded by the downloader's lock. */
        boolean running;

        Job(String videoId, AudioTrackInfo info, boolean nextUp, long sequence) {
            this.videoId = videoId;
            this.info = info;
            this.nextUp = nextUp;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(Job other) {
            if (nextUp != other.nextUp) {
                return nextUp ? -1 : 1;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
