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

import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.Mode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YtDlpSettings Unit Tests")
class YtDlpSettingsTest {

    @Test
    @DisplayName("Defaults from reference.conf are disabled, prompt mode")
    void defaults_areDisabledPrompt() {
        Config config = ConfigFactory.parseResources("reference.conf");
        YtDlpSettings settings = YtDlpSettings.fromConfig(config);

        assertFalse(settings.enabled());
        assertEquals(Mode.PROMPT, settings.mode());
        assertEquals(5, settings.delaySeconds());
        assertEquals(300, settings.timeoutSeconds());
        assertEquals(120, settings.promptTimeoutSeconds());
        assertTrue(settings.extraArgs().isEmpty());
        assertEquals("ytdlp-library", settings.libraryPlaylist());
    }

    @Test
    @DisplayName("Mode.parse is case-insensitive")
    void modeParse_isCaseInsensitive() {
        assertEquals(Mode.ALWAYS, Mode.parse("ALWAYS"));
        assertEquals(Mode.ALWAYS, Mode.parse("always"));
    }

    @Test
    @DisplayName("Mode.parse falls back to PROMPT for an unknown value")
    void modeParse_unknownFallsBackToPrompt() {
        assertEquals(Mode.PROMPT, Mode.parse("bogus"));
    }

    @Test
    @DisplayName("extraArgs is read as a list")
    void extraArgs_readAsList() {
        Config config = ConfigFactory.parseString("""
                ytdlp {
                  path = ""
                  mode = "prompt"
                  libraryFolder = "ytdlp-library"
                  libraryPlaylist = "ytdlp-library"
                  delaySeconds = 5
                  timeoutSeconds = 300
                  extraArgs = ["--cookies", "c.txt"]
                  promptTimeoutSeconds = 120
                }
                """);

        YtDlpSettings settings = YtDlpSettings.fromConfig(config);

        assertEquals(List.of("--cookies", "c.txt"), settings.extraArgs());
    }
}
