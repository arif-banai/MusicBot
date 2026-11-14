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

    private static final String YT_URL = "https://www.youtube.com/watch?v=abc00000000";

    private static YtDlpSettings settings(Path libraryFolder, List<String> extraArgs) {
        return new YtDlpSettings("yt-dlp", YtDlpSettings.Mode.PROMPT, libraryFolder, "ytdlp-library",
                5, 300, extraArgs, 120);
    }

    @Test
    @DisplayName("download() builds the exact argument list")
    void download_buildsExactArgs() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());

        List<String> command = YtDlpCommand.download(settings, YT_URL, YtDlpCommand.outputTemplate("abc00000000"));

        assertEquals(List.of("yt-dlp", "--encoding", "utf-8", "-f", "bestaudio", "--no-playlist", "--no-progress",
                "--no-warnings", "--write-info-json", "-o", Path.of("/lib").resolve("%(title)s [%(id)s].%(ext)s").toString(),
                "--print", "after_move:filepath",
                YT_URL), command);
    }

    @Test
    @DisplayName("download() writes a sidecar .info.json before specifying the output template")
    void download_writesInfoJson() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());

        List<String> command = YtDlpCommand.download(settings, YT_URL, YtDlpCommand.outputTemplate("abc00000000"));

        int infoJsonIndex = command.indexOf("--write-info-json");
        int outputFlagIndex = command.indexOf("-o");
        assertTrue(infoJsonIndex >= 0, command.toString());
        assertTrue(infoJsonIndex < outputFlagIndex, command.toString());
    }

    @Test
    @DisplayName("download() forces UTF-8 output encoding right after the executable path")
    void download_forcesUtf8Encoding() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());

        List<String> command = YtDlpCommand.download(settings, YT_URL, YtDlpCommand.outputTemplate("abc00000000"));

        assertEquals(List.of("--encoding", "utf-8"), command.subList(1, 3));
    }

    @Test
    @DisplayName("download() places extra args before the URL")
    void download_extraArgsGoBeforeUrl() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of("--cookies", "c.txt"));

        List<String> command = YtDlpCommand.download(settings, YT_URL, YtDlpCommand.outputTemplate("abc00000000"));

        int size = command.size();
        assertEquals(List.of("--cookies", "c.txt", YT_URL),
                command.subList(size - 3, size));
    }

    @Test
    @DisplayName("download() fetches a non-YouTube page URL with a [source-id] output template")
    void download_nonYoutube_usesUrlAndSourceTemplate() {
        YtDlpSettings settings = settings(Path.of("/lib"), List.of());
        String url = "https://soundcloud.com/crazyfrog/axel-f";

        List<String> command = YtDlpCommand.download(settings, url,
                YtDlpCommand.outputTemplate("soundcloud:" + url));

        assertEquals(url, command.get(command.size() - 1));
        assertEquals(Path.of("/lib").resolve("%(title)s [soundcloud-%(id)s].%(ext)s").toString(),
                command.get(command.indexOf("-o") + 1));
        assertEquals(List.of("--encoding", "utf-8"), command.subList(1, 3));
    }

    @Test
    @DisplayName("outputTemplate() keeps YouTube's [id] form and prefixes other sources")
    void outputTemplate_perSource() {
        assertEquals("%(title)s [%(id)s].%(ext)s", YtDlpCommand.outputTemplate("k85mRPqvMbE"));
        assertEquals("%(title)s [vimeo-%(id)s].%(ext)s", YtDlpCommand.outputTemplate("vimeo:76979871"));
        assertEquals("%(title)s [bandcamp-%(id)s].%(ext)s",
                YtDlpCommand.outputTemplate("bandcamp:https://a.bandcamp.com/track/b"));
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
