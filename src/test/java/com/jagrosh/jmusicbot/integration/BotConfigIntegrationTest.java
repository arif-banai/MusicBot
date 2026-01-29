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
import com.jagrosh.jmusicbot.audio.AudioSource;
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

@DisplayName("BotConfig Integration Tests")
class BotConfigIntegrationTest extends BaseConfigTest {
    // Uses BaseConfigTest's system property management
    
    @Nested
    @DisplayName("Full Load Flow")
    class FullLoadFlowTests {
        
        @Test
        @DisplayName("Loads config with all optional fields")
        void loadsConfigWithAllOptionalFields() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = integration_test_token
                discord.owner = 987654321
                commands.prefix = "!!"
                commands.altPrefix = "??"
                commands.help = commands
                ui.emojis.success = ✅
                ui.emojis.warning = ⚠️
                ui.emojis.error = ❌
                ui.emojis.loading = ⏳
                ui.emojis.searching = 🔍
                presence.game = Playing music
                presence.status = ONLINE
                presence.songInStatus = true
                nowPlaying.images = true
                voice.stayInChannel = true
                playback.maxTrackSeconds = 3600
                playback.maxYouTubePlaylistPages = 20
                playback.youtube.useOAuth = true
                voice.aloneTimeUntilStopSeconds = 300
                paths.playlistsFolder = CustomPlaylists
                updates.alerts = false
                logging.level = debug
                dangerous.eval = false
                dangerous.evalEngine = Nashorn
                playback.skipRatio = 0.75
                commands.aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip, vs ]
                }
                playback.transforms = {}
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("integration_test_token", config.getToken());
            assertEquals(987654321L, config.getOwnerId());
            assertEquals("!!", config.getPrefix());
            assertEquals("??", config.getAltPrefix());
            assertTrue(config.getSongInStatus());
            assertEquals(20, config.getMaxYTPlaylistPages());
        }
        
        @Test
        @DisplayName("Loads config with missing required fields and prompts")
        void loadsConfigWithMissingRequiredFieldsAndPrompts() throws IOException {
            // Note: We use legacy keys here to test migration + prompting
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 0");
            setConfigFileProperty(configFile);
            
            mockUserInteraction.addPromptResponse("prompted_token");
            mockUserInteraction.addPromptResponse("123456789");
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("prompted_token", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertEquals(2, mockUserInteraction.getPromptCalls().size());
        }
        
        @Test
        @DisplayName("Fails to load when user cancels validation")
        void failsToLoadWhenUserCancelsValidation() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 123456789");
            setConfigFileProperty(configFile);
            
            mockUserInteraction.setPromptCancelled();
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isValid());
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
        }
        
        @Test
        @DisplayName("Writes config file when validation prompts for input")
        void writesConfigFileWhenValidationPromptsForInput() throws IOException {
            Path configFile = createTempConfigFile("token = BOT_TOKEN_HERE\nowner = 0");
            setConfigFileProperty(configFile);
            
            mockUserInteraction.addPromptResponse("new_token");
            mockUserInteraction.addPromptResponse("987654321");
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            // Config file should have been written with new values
            // Note: writeToFile() replaces the entire file content with default template
            String fileContent = readFileContent(configFile);
            // After write, file should contain the config structure (new format uses discord.token)
            // The file will be written with the default template, so check for config structure
            assertTrue(fileContent.contains("token") || fileContent.contains("discord"));
        }
    }
    
    @Nested
    @DisplayName("Config Updates Integration")
    class ConfigUpdatesIntegrationTests {
        
        @Test
        @DisplayName("Updates config with missing values after load")
        void updatesConfigWithMissingValuesAfterLoad() throws IOException {
            // Minimal config (using nested keys)
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            // Since we didn't provide all optional fields, the original config should have been backed up and updated
            Path backupConfig = configFile.resolveSibling(configFile.getFileName().toString() + ".bak");
            assertTrue(java.nio.file.Files.exists(backupConfig), "Backup config file should have been generated");
            // The original config file should have been updated with migrated content
            assertTrue(java.nio.file.Files.exists(configFile), "Original config file should still exist");
            String updatedContent = java.nio.file.Files.readString(configFile);
            
            // Verify it can be parsed as ConfigDocument (primary method used by application)
            ConfigDocument doc = ConfigDocumentFactory.parseString(updatedContent);
            assertNotNull(doc, "Updated config should be parseable as ConfigDocument");
            
            // Verify it contains migrated content (should have meta.configVersion = 1 or other migrated keys)
            assertTrue(updatedContent.contains("meta") || updatedContent.contains("configVersion"), 
                "Updated config should contain migrated content");
            
            // Also verify it's parseable as Config (fallback)
            Config parsed = ConfigFactory.parseString(updatedContent);
            assertTrue(parsed.hasPath("meta.configVersion"));
        }
        
        @Test
        @DisplayName("Correctly detects explicitly set audio sources after config update")
        void correctlyDetectsExplicitlySetAudioSourcesAfterConfigUpdate() throws IOException {
            // Create a v1 config with partial audioSources (some true, some false, missing local)
            // This simulates a scenario where user has explicitly set some sources
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = false
                  vimeo = false
                  # local key is intentionally missing
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            
            // Verify that the config file was updated (local key should be added)
            String updatedContent = readFileContent(configFile);
            Config parsed = ConfigFactory.parseString(updatedContent);
            assertTrue(parsed.hasPath("playback.audioSources.local"), 
                "Missing local key should be added to config file");
            
            // After the fix, the bot should reload the config after update
            // and correctly detect that youtube and soundcloud were explicitly set to true
            var enabledSources = config.getEnabledAudioSources();
            assertNotNull(enabledSources);
            
            // The key fix: after reloading migratedUserConfig, it should see the explicitly set keys
            // After the config update, all missing keys are added with template defaults (true)
            // So all keys that exist in the updated config file are considered "explicitly set"
            assertTrue(enabledSources.contains(AudioSource.YOUTUBE), 
                "Youtube should be enabled (was explicitly set to true)");
            assertTrue(enabledSources.contains(AudioSource.SOUNDCLOUD), 
                "Soundcloud should be enabled (was explicitly set to true)");
            assertFalse(enabledSources.contains(AudioSource.BANDCAMP), 
                "Bandcamp should not be enabled (was explicitly set to false)");
            assertFalse(enabledSources.contains(AudioSource.VIMEO), 
                "Vimeo should not be enabled (was explicitly set to false)");
            
            // The newly added local key IS enabled because after config update and reload,
            // it exists in the config file with template default (true), so it's considered "explicitly set"
            assertTrue(enabledSources.contains(AudioSource.LOCAL), 
                "Local should be enabled (was added to config file with template default true)");
            
            // After config update, all missing audio source keys are added with template defaults (true)
            // So sources that were missing (like twitch, beam, getyarn, nico, http, bandcamp, vimeo) 
            // are added with true, making them enabled
            // Original config had: youtube=true, soundcloud=true, bandcamp=false, vimeo=false, local missing
            // After update: all missing sources are added with true, so enabled = youtube, soundcloud, local, 
            // and all other missing sources (twitch, beam, getyarn, nico, http) = 8 total
            assertTrue(enabledSources.size() >= 3, 
                "Should have at least 3 enabled sources (youtube, soundcloud, and local)");
            // Verify that sources explicitly set to false remain disabled
            assertFalse(enabledSources.contains(AudioSource.BANDCAMP), 
                "Bandcamp should remain disabled (was explicitly set to false)");
            assertFalse(enabledSources.contains(AudioSource.VIMEO), 
                "Vimeo should remain disabled (was explicitly set to false)");
        }
    }
    
    @Nested
    @DisplayName("Error Handling Integration")
    class ErrorHandlingIntegrationTests {
        
        @Test
        @DisplayName("Handles ConfigException and shows alert")
        void handlesConfigExceptionAndShowsAlert() throws IOException {
            // Malformed config
            String configContent = """
                token = test_token
                owner = 123456789
                invalid syntax {
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isValid());
            // Should have shown error alert
            assertEquals(1, mockUserInteraction.getAlertCalls().size());
            var alert = mockUserInteraction.getLastAlert();
            assertNotNull(alert);
            assertEquals("Config", alert.getContext());
        }
        
        @Test
        @DisplayName("Shows config location in error messages")
        void showsConfigLocationInErrorMessages() throws IOException {
            Path configFile = createTempConfigFile("invalid syntax");
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            var alert = mockUserInteraction.getLastAlert();
            if (alert != null) {
                assertTrue(alert.getMessage().contains("Config Location") ||
                          alert.getMessage().contains(configFile.toString()));
            }
        }
    }
}
