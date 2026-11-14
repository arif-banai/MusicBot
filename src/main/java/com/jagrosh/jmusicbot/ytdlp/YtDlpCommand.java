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

    private static final String YOUTUBE_TEMPLATE = "%(title)s [%(id)s].%(ext)s";

    private YtDlpCommand() {
    }

    /**
     * The output filename template for a library key: {@code %(title)s [%(id)s].%(ext)s} for
     * YouTube (unchanged from v1), and {@code %(title)s [<source>-%(id)s].%(ext)s} for other
     * sources, where {@code %(id)s} is yt-dlp's own ID. The index maps key to file, so the name
     * only needs to be unique.
     */
    public static String outputTemplate(String key) {
        if (LibraryKeys.isYoutubeKey(key)) {
            return YOUTUBE_TEMPLATE;
        }
        String source = LibraryKeys.sourceOf(key).orElse("file");
        return "%(title)s [" + source + "-%(id)s].%(ext)s";
    }

    /**
     * Builds the command line to download {@code url} into {@code settings}' library folder:
     * {@code <path> --encoding utf-8 -f bestaudio --no-playlist --no-progress --no-warnings
     * --write-info-json -o "<libraryFolder>/<outputTemplate>" --print after_move:filepath
     * <extraArgs...> <url>}. The sidecar lands next to the downloaded file under the same base
     * name; yt-dlp strips it of internal/private fields by default (its own {@code --clean-info-json}).
     *
     * <p>{@code --encoding utf-8} forces yt-dlp's own stdout/stderr encoding. Some
     * distributions (e.g. the winget/PyInstaller build on Windows) print in the
     * console's ANSI code page regardless of {@code PYTHONUTF8}/{@code PYTHONIOENCODING},
     * mangling non-ASCII titles in the {@code --print after_move:filepath} output.
     *
     * @param url            the page yt-dlp fetches (the watch URL for YouTube, the track URI otherwise)
     * @param outputTemplate the filename template, see {@link #outputTemplate(String)}
     */
    public static List<String> download(YtDlpSettings settings, String url, String outputTemplate) {
        List<String> command = new ArrayList<>();
        command.add(settings.path());
        command.add("--encoding");
        command.add("utf-8");
        command.add("-f");
        command.add("bestaudio");
        command.add("--no-playlist");
        command.add("--no-progress");
        command.add("--no-warnings");
        command.add("--write-info-json");
        command.add("-o");
        command.add(settings.libraryFolder().resolve(outputTemplate).toString());
        command.add("--print");
        command.add("after_move:filepath");
        command.addAll(settings.extraArgs());
        command.add(url);
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
}
