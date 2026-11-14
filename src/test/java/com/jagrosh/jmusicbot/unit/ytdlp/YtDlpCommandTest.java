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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.jagrosh.jmusicbot.ytdlp.YtDlpCommand;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YtDlpCommand Tests")
class YtDlpCommandTest {

    private static YtDlpSettings settings(Path libraryFolder, List<String> extraArgs) {
        return new YtDlpSettings("yt-dlp", YtDlpSettings.Mode.PROMPT, libraryFolder, "ytdlp-library",
                5, 300, extraArgs, 120);
    }

    @Test
    @DisplayName("download() builds the exact argument list")
    void download_buildsExactArgs() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());

        List<String> command = YtDlpCommand.download(settings, "abc");

        assertEquals(List.of("yt-dlp", "--encoding", "utf-8", "-f", "bestaudio", "--no-playlist", "--no-progress",
                "--no-warnings", "-o", Path.of("/lib").resolve("%(title)s [%(id)s].%(ext)s").toString(),
                "--print", "after_move:filepath",
                "https://www.youtube.com/watch?v=abc"), command);
    }

    @Test
    @DisplayName("download() forces UTF-8 output encoding right after the executable path")
    void download_forcesUtf8Encoding() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());

        List<String> command = YtDlpCommand.download(settings, "abc");

        assertEquals(List.of("--encoding", "utf-8"), command.subList(1, 3));
    }

    @Test
    @DisplayName("download() places extra args before the URL")
    void download_extraArgsGoBeforeUrl() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of("--cookies", "c.txt"));

        List<String> command = YtDlpCommand.download(settings, "abc");

        int size = command.size();
        assertEquals(List.of("--cookies", "c.txt", "https://www.youtube.com/watch?v=abc"),
                command.subList(size - 3, size));
    }

    @Test
    @DisplayName("version() returns [path, --version]")
    void version_buildsArgs() {
        List<String> command = YtDlpCommand.version("yt-dlp");

        assertEquals(List.of("yt-dlp", "--version"), command);
    }

    @Test
    @DisplayName("parsePrintedPath() returns the last non-blank line")
    void parsePrintedPath_lastNonBlankLine() {
        Optional<Path> path = YtDlpCommand.parsePrintedPath("noise\n/lib/A [x].webm\n\n");

        assertEquals(Optional.of(Path.of("/lib/A [x].webm")), path);
    }

    @Test
    @DisplayName("parsePrintedPath() round-trips unicode and sanitized titles unchanged")
    void parsePrintedPath_unicodeAndSanitizedTitle() {
        String printed = "/lib/Café ＂Live＂ ｜ 1 [a_b-c].opus";

        Optional<Path> path = YtDlpCommand.parsePrintedPath(printed);

        assertEquals(Optional.of(Path.of(printed)), path);
    }

    @Test
    @DisplayName("errorSummary() reports the timeout when the process timed out")
    void errorSummary_timeout() {
        String summary = YtDlpCommand.errorSummary(new ProcessResult(-1, "", "", true), 300);

        assertEquals("timed out after 300s", summary);
    }

    @Test
    @DisplayName("errorSummary() returns the last three non-blank stderr lines")
    void errorSummary_lastThreeStderrLines() {
        String summary = YtDlpCommand.errorSummary(new ProcessResult(1, "", "a\nb\n\nc\nd\n", false), 300);

        assertEquals("b\nc\nd", summary);
    }
}
