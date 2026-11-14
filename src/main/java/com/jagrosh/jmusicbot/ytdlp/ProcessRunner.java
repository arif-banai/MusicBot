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
import java.time.Duration;
import java.util.List;

/**
 * Runs an external process and collects its result. Abstracted from
 * {@link SystemProcessRunner} so yt-dlp invocations can be unit tested without
 * ever starting a real process.
 *
 * @author Arif Banai (arif-banai)
 */
public interface ProcessRunner {

    /**
     * Runs {@code command}, waiting up to {@code timeout}. If the process doesn't
     * finish in time, implementations kill it (and any descendants) and return a
     * result with {@link ProcessResult#timedOut()} {@code true}.
     */
    ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException;

    /**
     * The outcome of running a process: its exit code, captured stdout/stderr, and
     * whether it was killed for exceeding its timeout. When {@link #timedOut()} is
     * {@code true}, {@link #exitCode()} has no meaningful value.
     */
    record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    }
}
