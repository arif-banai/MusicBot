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
        assertTrue(settings.downloadButton());
        assertTrue(settings.matchingEnabled());
        assertEquals(10, settings.durationToleranceSeconds());
        assertEquals(0.85, settings.minScore(), 1e-9);
        assertTrue(settings.alignSwitch());
    }

    @Test
    @DisplayName("alignSwitch defaults to true")
    void alignSwitch_defaultTrue() {
        assertTrue(YtDlpSettings.DEFAULT_ALIGN_SWITCH);

        Config config = ConfigFactory.parseResources("reference.conf");
        YtDlpSettings settings = YtDlpSettings.fromConfig(config);

        assertTrue(settings.alignSwitch());
    }

    @Test
    @DisplayName("alignSwitch is read from the matching block, and old configs without it default to true")
    void alignSwitch_readFromConfig() {
        Config off = ConfigFactory.parseString("ytdlp.matching.alignSwitch = false")
                .withFallback(ConfigFactory.parseResources("reference.conf"));
        Config missing = ConfigFactory.parseString("""
                ytdlp { path = "", mode = "prompt", libraryFolder = "lib", libraryPlaylist = "lib",
                        delaySeconds = 5, timeoutSeconds = 300, extraArgs = [], promptTimeoutSeconds = 120 }
                """);

        assertFalse(YtDlpSettings.fromConfig(off).alignSwitch());
        assertTrue(YtDlpSettings.fromConfig(missing).alignSwitch());
    }

    @Test
    @DisplayName("the default duration tolerance is 10 seconds")
    void defaultTolerance_is10() {
        assertEquals(10, YtDlpSettings.DEFAULT_DURATION_TOLERANCE_SECONDS);

        Config empty = ConfigFactory.parseString("""
                ytdlp { path = "", mode = "prompt", libraryFolder = "lib", libraryPlaylist = "lib",
                        delaySeconds = 5, timeoutSeconds = 300, extraArgs = [], promptTimeoutSeconds = 120 }
                """);
        YtDlpSettings settings = YtDlpSettings.fromConfig(empty);

        assertEquals(10, settings.durationToleranceSeconds());
    }

    @Test
    @DisplayName("downloadButton and matching.* are read from the ytdlp block")
    void newKeys_areRead() {
        Config config = ConfigFactory.parseString("""
                ytdlp {
                  downloadButton = false
                  matching { enabled = false, durationToleranceSeconds = 9, minScore = 0.9 }
                }
                """).withFallback(ConfigFactory.parseResources("reference.conf"));

        YtDlpSettings settings = YtDlpSettings.fromConfig(config);

        assertFalse(settings.downloadButton());
        assertFalse(settings.matchingEnabled());
        assertEquals(9, settings.durationToleranceSeconds());
        assertEquals(0.9, settings.minScore(), 1e-9);
    }

    @Test
    @DisplayName("out-of-range matching values are clamped")
    void matching_outOfRange_isClamped() {
        Config low = ConfigFactory.parseString("ytdlp.matching { durationToleranceSeconds = -3, minScore = -1.5 }")
                .withFallback(ConfigFactory.parseResources("reference.conf"));
        Config high = ConfigFactory.parseString("ytdlp.matching { durationToleranceSeconds = 100000, minScore = 7 }")
                .withFallback(ConfigFactory.parseResources("reference.conf"));

        YtDlpSettings lowSettings = YtDlpSettings.fromConfig(low);
        YtDlpSettings highSettings = YtDlpSettings.fromConfig(high);

        assertEquals(0, lowSettings.durationToleranceSeconds());
        assertEquals(0.0, lowSettings.minScore(), 1e-9);
        assertEquals(YtDlpSettings.MAX_DURATION_TOLERANCE_SECONDS, highSettings.durationToleranceSeconds());
        assertEquals(1.0, highSettings.minScore(), 1e-9);
    }

    @Test
    @DisplayName("settings without the new keys (e.g. an old ytdlp block) use the defaults")
    void missingNewKeys_useDefaults() {
        Config config = ConfigFactory.parseString("""
                ytdlp { path = "", mode = "prompt", libraryFolder = "lib", libraryPlaylist = "lib",
                        delaySeconds = 5, timeoutSeconds = 300, extraArgs = [], promptTimeoutSeconds = 120 }
                """);

        YtDlpSettings settings = YtDlpSettings.fromConfig(config);

        assertTrue(settings.downloadButton());
        assertTrue(settings.matchingEnabled());
        assertEquals(10, settings.durationToleranceSeconds());
        assertEquals(0.85, settings.minScore(), 1e-9);
        assertTrue(settings.alignSwitch());
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
