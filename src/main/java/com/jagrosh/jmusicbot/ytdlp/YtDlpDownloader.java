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
import java.util.function.Predicate;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads songs into the {@link YtDlpLibrary} with yt-dlp, one at a time for the whole bot.
 * Each request names the library key (see {@link LibraryKeys}) the file is recorded under and the
 * page URL yt-dlp fetches.
 *
 * <p>Jobs wait in a priority queue: <em>next up</em> jobs run before others, and otherwise
 * jobs run in the order they were first requested. A single daemon worker thread runs
 * them, sleeping {@code delaySeconds} after every job that started a process, whether it
 * succeeded or failed. Jobs are keyed by library key, so a second request for a song that is
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
    /** Every queued or running job, by library key. Guarded by {@link #lock}. */
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
     * Returns a future for {@code key}'s library entry. A library hit completes immediately and
     * starts no process. A request for a key that is already queued or running joins that job,
     * adding {@code guildId} to it and upgrading it to {@code nextUp} if asked. After
     * {@link #shutdown()} the returned future is cancelled.
     *
     * <p>The returned future is shared by every request for the same key, so callers
     * shouldn't cancel it; use {@link #cancelGuild(long)} instead.
     *
     * @param key  the library key the file is recorded under
     * @param url  the page yt-dlp fetches: the watch URL for YouTube, the track's URI otherwise
     * @param info the track's metadata, recorded with the file
     */
    public CompletableFuture<LibraryEntry> request(String key, String url, AudioTrackInfo info, long guildId,
                                                   boolean nextUp) {
        synchronized (lock) {
            if (shutdown) {
                return cancelledFuture();
            }

            Job existing = jobs.get(key);
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
            Optional<LibraryEntry> hit = library.find(key);
            if (hit.isPresent()) {
                return CompletableFuture.completedFuture(hit.get());
            }

            Job job = new Job(key, url, info, nextUp, nextSequence++);
            job.guilds.add(guildId);
            jobs.put(key, job);
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
                if (shutdown || jobs.get(job.key) != job) {
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
                jobs.remove(job.key, job);
            }

            if (interrupted) {
                LOGGER.info("yt-dlp download of {} was interrupted", job.key);
                job.future.cancel(false);
                break;
            }
            if (failure != null) {
                YtDlpException ex = failure instanceof YtDlpException ytDlpEx ? ytDlpEx
                        : new YtDlpException("yt-dlp download failed: " + failure, failure);
                LOGGER.info("yt-dlp download of {} failed: {}", job.key, ex.getMessage());
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
        LOGGER.info("Downloading {} with yt-dlp", job.key);
        ProcessResult result = runner.run(
                YtDlpCommand.download(settings, job.url, YtDlpCommand.outputTemplate(job.key)),
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
            // would have produced for this song.
            file = (LibraryKeys.isYoutubeKey(job.key) ? findByIdSuffix(job.key) : findLoneNewSourceFile(job.key))
                    .orElse(null);
        }
        if (file == null) {
            if (printed.isEmpty()) {
                throw new YtDlpException("yt-dlp finished but its output path was missing");
            }
            throw new YtDlpException("yt-dlp finished but its output path was missing: " + printed.get());
        }

        LibraryEntry entry = library.record(job.key, file, job.info);
        LOGGER.info("Downloaded {} to {} ({} bytes)", job.key, entry.file(), sizeOf(entry.file()));
        return entry;
    }

    /**
     * YouTube fallback: the one regular file in the library folder (non-recursive) whose name
     * matches {@code [videoId].<ext>} per {@link YtDlpLibrary#videoIdFromFileName}, ignoring
     * yt-dlp's temp, sidecar and thumbnail files. Zero or several is treated as no usable fallback.
     */
    private Optional<Path> findByIdSuffix(String videoId) {
        return findLoneFile(fileName -> {
            Optional<String> id = YtDlpLibrary.videoIdFromFileName(fileName);
            return id.isPresent() && id.get().equals(videoId);
        });
    }

    /**
     * Fallback for other sources, whose yt-dlp ID isn't known in advance: the one non-temp file
     * named {@code ... [<source>-<id>].<ext>} that the library hasn't indexed yet (every file
     * present at startup is indexed by the import scan, and every earlier download by
     * {@link YtDlpLibrary#record}). Zero or several is treated as no usable fallback.
     */
    private Optional<Path> findLoneNewSourceFile(String key) {
        Optional<String> source = LibraryKeys.sourceOf(key);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        String marker = "[" + source.get() + "-";
        return findLoneFile(fileName -> {
            int bracket = fileName.lastIndexOf(marker);
            return bracket >= 0 && fileName.indexOf("].", bracket) > 0
                    && library.findByFile(settings.libraryFolder().resolve(fileName)).isEmpty();
        });
    }

    /**
     * The one regular file in the library folder whose name passes {@code matches}, if exactly one
     * does, skipping files that are never songs (see {@link YtDlpLibrary#isIgnoredFile}).
     */
    private Optional<Path> findLoneFile(Predicate<String> matches) {
        Path folder = settings.libraryFolder();
        if (!Files.isDirectory(folder)) {
            return Optional.empty();
        }

        Path match = null;
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (YtDlpLibrary.isIgnoredFile(fileName)) {
                    continue; // temp files, and sidecars/thumbnails yt-dlp may write beside the song
                }
                if (matches.test(fileName)) {
                    match = path;
                    count++;
                }
            }
        } catch (IOException ex) {
            // Treat a scan failure as no usable fallback; the caller's existing
            // "output path was missing" message still fires.
            return Optional.empty();
        }
        return count == 1 ? Optional.of(match) : Optional.empty();
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
     * One song to download. Ordered by {@link #nextUp} first, then {@link #sequence}.
     * {@link #nextUp} only changes while the job is out of the queue, so the queue's
     * ordering stays valid. Equality is identity, which {@code queue.remove} relies on.
     */
    private static final class Job implements Comparable<Job> {
        final String key;
        final String url;
        final AudioTrackInfo info;
        final long sequence;
        final CompletableFuture<LibraryEntry> future = new CompletableFuture<>();
        /** Guarded by the downloader's lock. */
        final Set<Long> guilds = new LinkedHashSet<>();
        /** Written under the downloader's lock, only while out of the queue. */
        volatile boolean nextUp;
        /** Guarded by the downloader's lock. */
        boolean running;

        Job(String key, String url, AudioTrackInfo info, boolean nextUp, long sequence) {
            this.key = key;
            this.url = url;
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
