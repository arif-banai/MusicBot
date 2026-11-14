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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;

/**
 * Builds the yt-dlp command lines the bot runs, and parses their output.
 *
 * <p>Public (rather than package-private, as would otherwise fit a small internal
 * helper) because its unit tests live under {@code com.jagrosh.jmusicbot.unit.ytdlp},
 * a different package from {@code com.jagrosh.jmusicbot.ytdlp}, per the project's test
 * layout convention; that convention takes priority here.
 *
 * @author Arif Banai (arif-banai)
 */
public final class YtDlpCommand {

    private static final String OUTPUT_TEMPLATE = "%(title)s [%(id)s].%(ext)s";

    private YtDlpCommand() {
    }

    /**
     * Builds the command line to download {@code videoId} into {@code settings}'
     * library folder: {@code <path> -f bestaudio --no-playlist --no-progress
     * --no-warnings -o "<libraryFolder>/%(title)s [%(id)s].%(ext)s" --print
     * after_move:filepath <extraArgs...> https://www.youtube.com/watch?v=<id>}.
     */
    public static List<String> download(YtDlpSettings settings, String videoId) {
        List<String> command = new ArrayList<>();
        command.add(settings.path());
        command.add("-f");
        command.add("bestaudio");
        command.add("--no-playlist");
        command.add("--no-progress");
        command.add("--no-warnings");
        command.add("-o");
        command.add(settings.libraryFolder().resolve(OUTPUT_TEMPLATE).toString());
        command.add("--print");
        command.add("after_move:filepath");
        command.addAll(settings.extraArgs());
        command.add(videoUrl(videoId));
        return List.copyOf(command);
    }

    /** Builds {@code [path, "--version"]}, used to check that yt-dlp is usable. */
    public static List<String> version(String path) {
        return List.of(path, "--version");
    }

    /**
     * Returns the last non-blank line of {@code stdout}, which is the path yt-dlp
     * printed via {@code --print after_move:filepath}. Taken verbatim, never rebuilt
     * from the title, so yt-dlp's own filename sanitization (including for titles with
     * characters Windows forbids, or non-ASCII characters) survives unchanged.
     */
    public static Optional<Path> parsePrintedPath(String stdout) {
        if (stdout == null) {
            return Optional.empty();
        }
        List<String> lines = stdout.lines().toList();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!line.isBlank()) {
                try {
                    return Optional.of(Path.of(line));
                } catch (InvalidPathException ex) {
                    // Not a path this platform can parse; treat as if yt-dlp printed nothing
                    // usable rather than throwing out of a parse helper.
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Summarizes a failed run for logging/display: {@code "timed out after
     * <timeoutSeconds>s"} if the process was killed for exceeding its timeout,
     * otherwise the last three non-blank lines of stderr, joined with newlines.
     */
    public static String errorSummary(ProcessResult result, int timeoutSeconds) {
        if (result.timedOut()) {
            return "timed out after " + timeoutSeconds + "s";
        }

        List<String> nonBlank = new ArrayList<>();
        String stderr = result.stderr();
        if (stderr != null) {
            for (String line : stderr.lines().toList()) {
                if (!line.isBlank()) {
                    nonBlank.add(line);
                }
            }
        }
        int from = Math.max(0, nonBlank.size() - 3);
        return String.join("\n", nonBlank.subList(from, nonBlank.size()));
    }

    private static String videoUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
