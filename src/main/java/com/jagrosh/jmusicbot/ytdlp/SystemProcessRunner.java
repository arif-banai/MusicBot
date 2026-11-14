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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a real OS process with {@link ProcessBuilder}. Stdout and stderr are each
 * drained on their own thread so neither pipe can block the process (a process that
 * fills one pipe's buffer while nobody is reading it will hang forever otherwise).
 * On timeout, the process and any descendants it spawned are killed.
 *
 * @author Arif Banai (arif-banai)
 */
public final class SystemProcessRunner implements ProcessRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemProcessRunner.class);

    /** Bound on how long we wait for the killed process/threads to actually stop. */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    @Override
    public ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();

        StreamGobbler stdout = new StreamGobbler(process.getInputStream());
        StreamGobbler stderr = new StreamGobbler(process.getErrorStream());
        Thread stdoutThread = new Thread(stdout, "ytdlp-stdout");
        Thread stderrThread = new Thread(stderr, "ytdlp-stderr");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            // Being interrupted (e.g. a shutdown, or Task 4 cancelling this job) must not
            // leak the child process; kill it, then let the caller see the interrupt.
            killTree(process);
            stdoutThread.join(SHUTDOWN_GRACE.toMillis());
            stderrThread.join(SHUTDOWN_GRACE.toMillis());
            throw ex;
        }

        if (!finished) {
            LOGGER.warn("yt-dlp process exceeded its {} timeout; killing it", timeout);
            killTree(process);
            // Bounded waits here: if a descendant outside the one-shot descendants()
            // snapshot survives and keeps a pipe open, we must still return rather than
            // hang forever and stall the single-download queue.
            process.waitFor(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            stdoutThread.join(SHUTDOWN_GRACE.toMillis());
            stderrThread.join(SHUTDOWN_GRACE.toMillis());
            return new ProcessResult(-1, stdout.text(), stderr.text(), true);
        }

        stdoutThread.join();
        stderrThread.join();
        return new ProcessResult(process.exitValue(), stdout.text(), stderr.text(), false);
    }

    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** Reads a stream to completion on its own thread, collecting it as UTF-8 text. */
    private static final class StreamGobbler implements Runnable {

        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StreamGobbler(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try {
                input.transferTo(buffer);
            } catch (IOException ex) {
                // The pipe closes when the process (or its forcible kill) ends; nothing more
                // to read at that point, and whatever was captured before that is kept.
                LOGGER.debug("Stream closed while reading yt-dlp output: {}", ex.toString());
            }
        }

        String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
