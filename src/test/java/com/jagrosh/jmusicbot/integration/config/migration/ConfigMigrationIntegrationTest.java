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
package com.jagrosh.jmusicbot.integration.config.migration;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.testutil.config.LegacyConfigBuilder;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.parser.ConfigDocument;
import com.typesafe.config.parser.ConfigDocumentFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Config Migration Integration Tests")
class ConfigMigrationIntegrationTest extends BaseConfigTest {
    // Uses BaseConfigTest's system property management
    
    @Nested
    @DisplayName("Startup with Legacy Config")
    class LegacyConfigTests {
        
        @Test
        @DisplayName("loadMergedConfig migrates legacy config successfully")
        void testStartupWithLegacyConfig() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("integration_test_token")
                .withOwner(987654321L)
                .withPrefix("!!")
                .withAltPrefix("NONE")
                .withHelp("commands")
                .withGame("DEFAULT")
                .withStatus("ONLINE")
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            // Verify migration occurred
            assertTrue(merged.hasPath("meta.configVersion"));
            assertEquals(1, merged.getInt("meta.configVersion"));
            
            // Verify values migrated correctly
            assertEquals("integration_test_token", merged.getString("discord.token"));
            assertEquals(987654321L, merged.getLong("discord.owner"));
            assertEquals("!!", merged.getString("commands.prefix"));
            assertEquals("NONE", merged.getString("commands.altPrefix"));
            assertEquals("commands", merged.getString("commands.help"));
            assertEquals("DEFAULT", merged.getString("presence.game"));
            assertEquals("ONLINE", merged.getString("presence.status"));
        }
        
        @Test
        @DisplayName("loadMergedConfig preserves all user values after migration")
        void testMigrationPreservesAllValues() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .withPrefix("!!")
                .withAltPrefix("??")
                .withHelp("help")
                .withSuccess("✅")
                .withWarning("⚠️")
                .withError("❌")
                .withLoading("⏳")
                .withSearching("🔍")
                .withGame("Playing music")
                .withStatus("IDLE")
                .withSongInStatus(true)
                .withNPImages(true)
                .withStayInChannel(true)
                .withMaxTime(3600L)
                .withMaxYTPlaylistPages(20)
                .withUseYouTubeOAuth(true)
                .withAloneTimeUntilStop(300L)
                .withPlaylistsFolder("CustomPlaylists")
                .withUpdateAlerts(false)
                .withLogLevel("debug")
                .withEval(false)
                .withEvalEngine("Nashorn")
                .withSkipRatio(0.75)
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            // Verify all values preserved
            assertEquals("test_token", merged.getString("discord.token"));
            assertEquals(123456789L, merged.getLong("discord.owner"));
            assertEquals("!!", merged.getString("commands.prefix"));
            assertEquals("??", merged.getString("commands.altPrefix"));
            assertEquals("help", merged.getString("commands.help"));
            assertEquals("✅", merged.getString("ui.emojis.success"));
            assertEquals("⚠️", merged.getString("ui.emojis.warning"));
            assertEquals("❌", merged.getString("ui.emojis.error"));
            assertEquals("⏳", merged.getString("ui.emojis.loading"));
            assertEquals("🔍", merged.getString("ui.emojis.searching"));
            assertEquals("Playing music", merged.getString("presence.game"));
            assertEquals("IDLE", merged.getString("presence.status"));
            assertTrue(merged.getBoolean("presence.songInStatus"));
            assertTrue(merged.getBoolean("nowPlaying.images"));
            assertTrue(merged.getBoolean("voice.stayInChannel"));
            assertEquals(3600L, merged.getLong("playback.maxTrackSeconds"));
            assertEquals(20, merged.getInt("playback.maxYouTubePlaylistPages"));
            assertTrue(merged.getBoolean("playback.youtube.useOAuth"));
            assertEquals(300L, merged.getLong("voice.aloneTimeUntilStopSeconds"));
            assertEquals("CustomPlaylists", merged.getString("paths.playlistsFolder"));
            assertFalse(merged.getBoolean("updates.alerts"));
            assertEquals("debug", merged.getString("logging.level"));
            assertFalse(merged.getBoolean("dangerous.eval"));
            assertEquals("Nashorn", merged.getString("dangerous.evalEngine"));
            assertEquals(0.75, merged.getDouble("playback.skipRatio"));
        }
    }
    
    @Nested
    @DisplayName("Startup with New Format Config")
    class NewFormatConfigTests {
        
        @Test
        @DisplayName("loadMergedConfig handles new-format config without migration")
        void testStartupWithNewFormatConfig() throws IOException {
            String newConfig = V1ConfigBuilder.create()
                .withMetaVersion(1)
                .withDiscordToken("integration_test_token")
                .withDiscordOwner(987654321L)
                .withCommandsPrefix("!!")
                .withCommandsAltPrefix("NONE")
                .withCommandsHelp("help")
                .buildAsString();
            
            Path configFile = createTempConfigFile(newConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            // Verify no migration occurred (already version 1)
            assertEquals(1, ConfigMigration.detectVersion(ConfigLoader.loadRawUserConfig(configFile)));
            
            // Verify values preserved
            assertEquals("integration_test_token", merged.getString("discord.token"));
            assertEquals(987654321L, merged.getLong("discord.owner"));
            assertEquals("!!", merged.getString("commands.prefix"));
        }
    }
    
    @Nested
    @DisplayName("Custom Config Path")
    class CustomConfigPathTests {
        
        @Test
        @DisplayName("loadMergedConfig works with -Dconfig.file custom path")
        void testCustomConfigPath() throws IOException {
            String config = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .buildAsString();
            Path configFile = createTempConfigFile(config);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertTrue(merged.hasPath("discord.token"));
            assertEquals("test_token", merged.getString("discord.token"));
        }
    }
    
    @Nested
    @DisplayName("Generated Config File")
    class GeneratedConfigFileTests {
        
        @Test
        @DisplayName("generateUpdatedConfig creates parseable config.updated.conf")
        void testGeneratedConfigIsParseable() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .withPrefix("!!")
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            Config migratedUser = ConfigLoader.loadMigratedUserConfig(configFile);
            Config defaults = ConfigFactory.load();
            
            ConfigDiagnostics.Report diagnostics = 
                ConfigDiagnostics.analyze(migratedUser, merged, defaults);
            
            Path updatedPath = ConfigUpdater.generateUpdatedConfig(configFile, migratedUser, diagnostics, ConfigUpdateType.MIGRATION);
            
            assertNotNull(updatedPath);
            assertFileExists(updatedPath);
            
            // Verify the generated file is parseable as ConfigDocument (primary method)
            String content = readFileContent(updatedPath);
            ConfigDocument doc = ConfigDocumentFactory.parseString(content);
            assertNotNull(doc);
            
            // Also verify it's parseable as Config (fallback)
            Config generated = ConfigFactory.parseFile(updatedPath.toFile());
            assertNotNull(generated);
            assertTrue(generated.hasPath("meta.configVersion"));
        }
    }
    
    @Nested
    @DisplayName("Audio Sources Migration")
    class AudioSourcesMigrationTests {
        
        @Test
        @DisplayName("migrates audiosources list to nested booleans")
        void testAudioSourcesMigration() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .withAudioSources("youtube", "soundcloud", "local")
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertTrue(merged.hasPath("playback.audioSources"));
            Config audioSources = merged.getConfig("playback.audioSources");
            assertTrue(audioSources.getBoolean("youtube"));
            assertTrue(audioSources.getBoolean("soundcloud"));
            assertTrue(audioSources.getBoolean("local"));
            assertFalse(audioSources.getBoolean("bandcamp"));
        }
        
        @Test
        @DisplayName("defaults to all enabled when audiosources missing")
        void testAudioSourcesDefault() throws IOException {
            String legacyConfig = LegacyConfigBuilder.create()
                .withToken("test_token")
                .withOwner(123456789L)
                .buildAsString();
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            Config merged = ConfigLoader.loadMergedConfig(configFile);
            
            assertTrue(merged.hasPath("playback.audioSources"));
            Config audioSources = merged.getConfig("playback.audioSources");
            // All should be enabled by default
            assertTrue(audioSources.getBoolean("youtube"));
            assertTrue(audioSources.getBoolean("soundcloud"));
        }
    }
}
