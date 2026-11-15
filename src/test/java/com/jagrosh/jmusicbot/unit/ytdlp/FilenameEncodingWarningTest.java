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

import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("yt-dlp file name encoding warning")
class FilenameEncodingWarningTest {

    @Test
    @DisplayName("a Linux JVM without a UTF-8 locale gets a warning naming the fix")
    void posixLocale_warns() {
        String warning = YtDlpService.filenameEncodingWarning("Linux", "ANSI_X3.4-1968").orElseThrow();

        assertTrue(warning.contains("ANSI_X3.4-1968"));
        assertTrue(warning.contains("LANG=C.UTF-8"));
    }

    @Test
    @DisplayName("a UTF-8 locale gets no warning")
    void utf8_noWarning() {
        assertTrue(YtDlpService.filenameEncodingWarning("Linux", "UTF-8").isEmpty());
        assertTrue(YtDlpService.filenameEncodingWarning("Mac OS X", "utf8").isEmpty());
    }

    @Test
    @DisplayName("Windows never warns: its file names don't depend on the locale")
    void windows_noWarning() {
        assertTrue(YtDlpService.filenameEncodingWarning("Windows 11", "Cp1252").isEmpty());
    }

    @Test
    @DisplayName("an unknown encoding outside Windows warns")
    void unknownEncoding_warns() {
        assertTrue(YtDlpService.filenameEncodingWarning("Linux", null).isPresent());
    }
}
