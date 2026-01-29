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
package com.jagrosh.jmusicbot.integration;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.MockUserInteraction;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.testutil.config.V1ConfigBuilder;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BotConfig Migration Integration Tests")
class BotConfigMigrationIntegrationTest extends BaseConfigTest {
    // Uses BaseConfigTest's system property management
    
    @Nested
    @DisplayName("BotConfig Loads Migrated Config")
    class BotConfigLoadTests {
        
        @Test
        @DisplayName("BotConfig.load() works with migrated legacy config")
        void testBotConfigLoadsMigratedConfig() throws IOException {
            String legacyConfig = """
                token = integration_test_token
                owner = 987654321
                prefix = "!!"
                altprefix = "??"
                help = commands
                game = DEFAULT
                status = ONLINE
                """;
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("integration_test_token", config.getToken());
            assertEquals(987654321L, config.getOwnerId());
            assertEquals("!!", config.getPrefix());
            assertEquals("??", config.getAltPrefix());
            assertEquals("commands", config.getHelp());
        }
        
        @Test
        @DisplayName("BotConfig getters return correct migrated values")
        void testBotConfigValuesCorrectAfterMigration() throws IOException {
            String legacyConfig = """
                token = test_token
                owner = 123456789
                prefix = "!!"
                altprefix = "NONE"
                help = help
                success = ✅
                warning = ⚠️
                error = ❌
                loading = ⏳
                searching = 🔍
                game = Playing music
                status = IDLE
                songinstatus = true
                npimages = true
                stayinchannel = true
                maxtime = 3600
                maxytplaylistpages = 20
                useyoutubeoauth = true
                alonetimeuntilstop = 300
                playlistsfolder = CustomPlaylists
                updatealerts = false
                loglevel = debug
                eval = false
                evalengine = Nashorn
                skipratio = 0.75
                """;
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("test_token", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertEquals("!!", config.getPrefix());
            assertNull(config.getAltPrefix()); // "NONE" converted to null
            assertEquals("help", config.getHelp());
            assertEquals("✅", config.getSuccess());
            assertEquals("⚠️", config.getWarning());
            assertEquals("❌", config.getError());
            assertEquals("⏳", config.getLoading());
            assertEquals("🔍", config.getSearching());
            assertTrue(config.getSongInStatus());
            assertTrue(config.useNPImages());
            assertTrue(config.getStay());
            assertEquals(3600L, config.getMaxSeconds());
            assertEquals(20, config.getMaxYTPlaylistPages());
            assertTrue(config.useYouTubeOauth());
            assertEquals(300L, config.getAloneTimeUntilStop());
            assertEquals("CustomPlaylists", config.getPlaylistsFolder());
            assertFalse(config.useUpdateAlerts());
            assertEquals("debug", config.getLogLevel());
            assertFalse(config.useEval());
            assertEquals("Nashorn", config.getEvalEngine());
            assertEquals(0.75, config.getSkipRatio());
        }
        
        @Test
        @DisplayName("BotConfig audio sources work correctly after migration")
        void testAudioSourcesAfterMigration() throws IOException {
            String legacyConfig = """
                token = test_token
                owner = 123456789
                audiosources = [ youtube, soundcloud, local ]
                """;
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            Set<AudioSource> enabled = config.getEnabledAudioSources();
            
            assertTrue(enabled.contains(AudioSource.YOUTUBE));
            assertTrue(enabled.contains(AudioSource.SOUNDCLOUD));
            assertTrue(enabled.contains(AudioSource.LOCAL));
            assertFalse(enabled.contains(AudioSource.BANDCAMP));
            
            assertTrue(config.isAudioSourceEnabled(AudioSource.YOUTUBE));
            assertTrue(config.isAudioSourceEnabled(AudioSource.SOUNDCLOUD));
            assertTrue(config.isAudioSourceEnabled(AudioSource.LOCAL));
            assertFalse(config.isAudioSourceEnabled(AudioSource.BANDCAMP));
        }
        
        @Test
        @DisplayName("BotConfig audio sources default to all enabled when missing")
        void testAudioSourcesDefaultAfterMigration() throws IOException {
            String legacyConfig = """
                token = test_token
                owner = 123456789
                """;
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            Set<AudioSource> enabled = config.getEnabledAudioSources();
            
            // All sources should be enabled by default
            assertTrue(enabled.contains(AudioSource.YOUTUBE));
            assertTrue(enabled.contains(AudioSource.SOUNDCLOUD));
            assertTrue(enabled.contains(AudioSource.LOCAL));
            assertTrue(enabled.contains(AudioSource.BANDCAMP));
        }
    }
    
    @Nested
    @DisplayName("BotConfig Migration Failure Tests")
    class BotConfigMigrationFailureTests {
        
        @Test
        @DisplayName("BotConfig.load() shows error alert when migration fails")
        void testBotConfigShowsErrorOnMigrationFailure() throws IOException {
            // Create a legacy config (version 0)
            String legacyConfig = """
                token = test_token
                owner = 123456789
                """;
            
            Path configFile = createTempConfigFile(legacyConfig);
            setConfigFileProperty(configFile);
            
            // Create a defaults config that claims version 2 (no migration path from 1->2)
            Config fakeDefaults = V1ConfigBuilder.create()
                .withMetaVersion(2)
                .withDiscordToken("BOT_TOKEN_HERE")
                .withDiscordOwner(0L)
                .build();
            
            // Mock ConfigIO.loadDefaults() to return our fake defaults
            try (MockedStatic<ConfigIO> mockedConfigIO = Mockito.mockStatic(ConfigIO.class)) {
                mockedConfigIO.when(ConfigIO::loadDefaults).thenReturn(fakeDefaults);
                // Let getConfigPath() work normally
                mockedConfigIO.when(ConfigIO::getConfigPath).thenCallRealMethod();
                
                BotConfig config = new BotConfig(mockUserInteraction);
                config.load();
                
                // Config should be invalid due to migration failure
                assertFalse(config.isValid());
                
                // User should have been alerted about the migration failure
                MockUserInteraction.AlertCall lastAlert = mockUserInteraction.getLastAlert();
                assertNotNull(lastAlert, "Expected an alert to be shown");
                assertEquals(Level.ERROR, lastAlert.getLevel());
                assertEquals("Config Migration", lastAlert.getContext());
                assertTrue(lastAlert.getMessage().contains("migration"),
                    "Alert message should mention migration: " + lastAlert.getMessage());
            }
        }
    }
}
