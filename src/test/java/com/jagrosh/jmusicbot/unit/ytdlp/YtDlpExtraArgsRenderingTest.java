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

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards that {@code ytdlp.extraArgs}, a STRING_LIST option, survives a
 * ConfigRenderer regeneration (the path ConfigUpdater uses to rewrite a
 * user's config.txt when optional keys are missing) even when the list
 * contains values with spaces, colons, and equals signs.
 */
@DisplayName("ytdlp.extraArgs ConfigRenderer round-trip")
class YtDlpExtraArgsRenderingTest {

    @Test
    @DisplayName("extraArgs with spaces/colons/equals survives a ConfigRenderer regenerate")
    void extraArgs_survivesConfigRendererRoundTrip() {
        List<String> expected = List.of("--extractor-args", "youtube:player_client=web", "c x.txt");

        Config migratedUserConfig = ConfigFactory.parseString("""
                meta { configVersion = 2 }
                discord { token = "test_token", owner = 123456789 }
                ytdlp {
                  extraArgs = ["--extractor-args", "youtube:player_client=web", "c x.txt"]
                }
                """);

        ConfigDiagnostics.Report diagnostics = new ConfigDiagnostics.Report(
                new HashSet<>(), new HashSet<>(), new HashSet<>());

        String regenerated = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, ConfigUpdateType.UPDATE);

        // The regenerated file must itself be valid HOCON that reloads to the same list.
        Config reloaded = ConfigFactory.parseString(regenerated);
        assertEquals(expected, reloaded.getStringList("ytdlp.extraArgs"));

        // And YtDlpSettings reading straight from it (merged with defaults) sees the same list.
        Config merged = reloaded.withFallback(ConfigFactory.parseResources("reference.conf")).resolve();
        assertEquals(expected, YtDlpSettings.fromConfig(merged).extraArgs());
    }
}
