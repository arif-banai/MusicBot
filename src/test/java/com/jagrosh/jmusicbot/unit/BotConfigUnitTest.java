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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioSource;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.OnlineStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("BotConfig Unit Tests")
class BotConfigUnitTest extends BaseConfigTest {
    
    @Mock
    private AudioTrack mockAudioTrack;
    
    @Override
    @org.junit.jupiter.api.BeforeEach
    protected void setUpBase() {
        super.setUpBase();
        MockitoAnnotations.openMocks(this);
    }
    
    @Nested
    @DisplayName("Getters Tests")
    class GettersTests {
        
        @Test
        @DisplayName("All getters return correct values after load")
        void allGettersReturnCorrectValuesAfterLoad() throws IOException {
            String configContent = """
                token = test_token_12345
                owner = 123456789
                prefix = "@mention"
                altprefix = "NONE"
                help = help
                game = DEFAULT
                status = ONLINE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals("test_token_12345", config.getToken());
            assertEquals(123456789L, config.getOwnerId());
            assertNotNull(config.getPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns null for NONE")
        void getAltPrefixReturnsNullForNone() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.altPrefix = "NONE"
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            // getAltPrefix() returns null when internal value is "NONE" (for API compatibility)
            assertNull(config.getAltPrefix());
        }
        
        @Test
        @DisplayName("getAltPrefix() returns value when not NONE")
        void getAltPrefixReturnsValueWhenNotNone() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.altPrefix = "!!"
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertEquals("!!", config.getAltPrefix());
        }
        
        @Test
        @DisplayName("isGameNone() returns true for NONE game")
        void isGameNoneReturnsTrueForNoneGame() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                presence.game = NONE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isGameNone());
        }
        
        @Test
        @DisplayName("isGameNone() returns false for other games")
        void isGameNoneReturnsFalseForOtherGames() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                presence.game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.isGameNone());
        }
        
        @Test
        @DisplayName("getDBots() returns true for specific owner ID")
        void getDBotsReturnsTrueForSpecificOwnerId() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 113156185389092864
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.getDBots());
        }
        
        @Test
        @DisplayName("getDBots() returns false for other owner IDs")
        void getDBotsReturnsFalseForOtherOwnerIds() throws IOException {
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
            
            assertFalse(config.getDBots());
        }
    }
    
    @Nested
    @DisplayName("isTooLong() Tests")
    class IsTooLongTests {
        
        @Test
        @DisplayName("isTooLong() returns false when maxSeconds is 0")
        void isTooLongReturnsFalseWhenMaxSecondsIsZero() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 0
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(3600000L); // 1 hour
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns false when track is shorter than max")
        void isTooLongReturnsFalseWhenTrackIsShorter() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(120000L); // 2 minutes
            
            assertFalse(config.isTooLong(mockAudioTrack));
        }
        
        @Test
        @DisplayName("isTooLong() returns true when track is longer than max")
        void isTooLongReturnsTrueWhenTrackIsLonger() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.maxTrackSeconds = 300
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            when(mockAudioTrack.getDuration()).thenReturn(600000L); // 10 minutes
            
            assertTrue(config.isTooLong(mockAudioTrack));
        }
    }
    
    @Nested
    @DisplayName("getAliases() Tests")
    class GetAliasesTests {
        
        @Test
        @DisplayName("getAliases() returns aliases for existing command")
        void getAliasesReturnsAliasesForExistingCommand() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.aliases {
                  play = [ p, playmusic ]
                  skip = [ voteskip ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            String[] playAliases = config.getAliases("play");
            assertEquals(2, playAliases.length);
            assertTrue(java.util.Arrays.asList(playAliases).contains("p"));
            assertTrue(java.util.Arrays.asList(playAliases).contains("playmusic"));
        }
        
        @Test
        @DisplayName("getAliases() returns empty array for non-existent command")
        void getAliasesReturnsEmptyArrayForNonExistentCommand() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                commands.aliases {
                  play = [ p ]
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            String[] aliases = config.getAliases("nonexistent");
            assertEquals(0, aliases.length);
        }
        
        @Test
        @DisplayName("getAliases() returns empty array when aliases config is missing")
        void getAliasesReturnsEmptyArrayWhenAliasesConfigMissing() throws IOException {
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
            
            String[] aliases = config.getAliases("play");
            assertEquals(0, aliases.length);
        }
    }
    
    @Nested
    @DisplayName("Audio Sources Tests")
    class AudioSourcesTests {
        
        @Test
        @DisplayName("All sources enabled when audioSources key is missing")
        void allSourcesEnabledWhenAudioSourcesKeyMissing() throws IOException {
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
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertNotNull(sources);
            assertEquals(AudioSource.values().length, sources.size(),
                "All sources should be enabled when audioSources key is missing");
            for (AudioSource source : AudioSource.values()) {
                assertTrue(config.isAudioSourceEnabled(source),
                    source.getConfigName() + " should be enabled");
            }
        }
        
        @Test
        @DisplayName("All sources enabled when all set to true")
        void allSourcesEnabledWhenAllSetToTrue() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = true
                  vimeo = true
                  twitch = true
                  beam = true
                  getyarn = true
                  nico = true
                  http = true
                  local = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertEquals(AudioSource.values().length, sources.size(),
                "All sources should be enabled when all set to true");
            for (AudioSource source : AudioSource.values()) {
                assertTrue(config.isAudioSourceEnabled(source),
                    source.getConfigName() + " should be enabled");
            }
        }
        
        @Test
        @DisplayName("Some sources disabled when set to false")
        void someSourcesDisabledWhenSetToFalse() throws IOException {
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
                  vimeo = true
                  twitch = true
                  beam = false
                  getyarn = true
                  nico = true
                  bilibili = true
                  http = true
                  local = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            
            // Should have 9 sources enabled (11 total - 2 disabled)
            assertEquals(9, sources.size(), "Should have 9 sources enabled");
            
            // Enabled sources
            assertTrue(config.isAudioSourceEnabled(AudioSource.YOUTUBE));
            assertTrue(config.isAudioSourceEnabled(AudioSource.SOUNDCLOUD));
            assertTrue(config.isAudioSourceEnabled(AudioSource.VIMEO));
            assertTrue(config.isAudioSourceEnabled(AudioSource.TWITCH));
            assertTrue(config.isAudioSourceEnabled(AudioSource.GETYARN));
            assertTrue(config.isAudioSourceEnabled(AudioSource.NICO));
            assertTrue(config.isAudioSourceEnabled(AudioSource.BILIBILI));
            assertTrue(config.isAudioSourceEnabled(AudioSource.HTTP));
            assertTrue(config.isAudioSourceEnabled(AudioSource.LOCAL));
            
            // Disabled sources
            assertFalse(config.isAudioSourceEnabled(AudioSource.BANDCAMP),
                "Bandcamp should be disabled when set to false");
            assertFalse(config.isAudioSourceEnabled(AudioSource.BEAM),
                "Beam should be disabled when set to false");
        }
        
        @Test
        @DisplayName("All sources enabled when all set to false (fallback behavior)")
        void allSourcesEnabledWhenAllSetToFalse() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = false
                  soundcloud = false
                  bandcamp = false
                  vimeo = false
                  twitch = false
                  beam = false
                  getyarn = false
                  nico = false
                  bilibili = false
                  http = false
                  local = false
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertEquals(AudioSource.values().length, sources.size(),
                "All sources should be enabled when all are set to false (fallback behavior)");
            for (AudioSource source : AudioSource.values()) {
                assertTrue(config.isAudioSourceEnabled(source),
                    source.getConfigName() + " should be enabled (fallback)");
            }
        }
        
        @Test
        @DisplayName("isAudioSourceEnabled() returns correct values")
        void isAudioSourceEnabledReturnsCorrectValues() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = false
                  bandcamp = true
                  vimeo = false
                  twitch = true
                  beam = true
                  getyarn = true
                  nico = true
                  http = true
                  local = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isAudioSourceEnabled(AudioSource.YOUTUBE));
            assertFalse(config.isAudioSourceEnabled(AudioSource.SOUNDCLOUD));
            assertTrue(config.isAudioSourceEnabled(AudioSource.BANDCAMP));
            assertFalse(config.isAudioSourceEnabled(AudioSource.VIMEO));
        }
        
        @Test
        @DisplayName("Invalid source names in config are ignored")
        void invalidSourceNamesAreIgnored() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  invalid_source = true
                  another_invalid = false
                  bandcamp = true
                  vimeo = true
                  twitch = true
                  beam = true
                  getyarn = true
                  nico = true
                  http = true
                  local = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            // Invalid source names should be ignored, all valid sources enabled
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertEquals(AudioSource.values().length, sources.size(),
                "All valid sources should be enabled, invalid names ignored");
            assertTrue(sources.contains(AudioSource.YOUTUBE));
            assertTrue(sources.contains(AudioSource.SOUNDCLOUD));
        }
        
        @Test
        @DisplayName("getEnabledAudioSources() returns sources in priority order")
        void getEnabledAudioSourcesReturnsPriorityOrder() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = true
                  soundcloud = true
                  bandcamp = true
                  vimeo = true
                  twitch = true
                  beam = true
                  getyarn = true
                  nico = true
                  http = true
                  local = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            
            // Convert to list to check ordering
            java.util.List<AudioSource> sourceList = new java.util.ArrayList<>(sources);
            
            // Verify the list is in priority order
            for (int i = 0; i < sourceList.size() - 1; i++) {
                assertTrue(sourceList.get(i).getRegistrationPriority() <= sourceList.get(i + 1).getRegistrationPriority(),
                    String.format("Source %s (priority %d) should come before %s (priority %d)",
                        sourceList.get(i).getConfigName(), sourceList.get(i).getRegistrationPriority(),
                        sourceList.get(i + 1).getConfigName(), sourceList.get(i + 1).getRegistrationPriority()));
            }
        }
        
        @Test
        @DisplayName("SoundCloud is registered before HTTP (regression test)")
        void soundCloudRegisteredBeforeHttp() throws IOException {
            // This test specifically guards against the regression where HTTP claimed SoundCloud URLs
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  soundcloud = true
                  http = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            java.util.List<AudioSource> sourceList = new java.util.ArrayList<>(sources);
            
            int soundcloudIndex = sourceList.indexOf(AudioSource.SOUNDCLOUD);
            int httpIndex = sourceList.indexOf(AudioSource.HTTP);
            
            assertTrue(soundcloudIndex >= 0, "SoundCloud should be in the enabled sources");
            assertTrue(httpIndex >= 0, "HTTP should be in the enabled sources");
            assertTrue(soundcloudIndex < httpIndex,
                "SoundCloud must be registered before HTTP to avoid HTTP claiming SoundCloud URLs");
        }
        
        @Test
        @DisplayName("Partial source selection maintains priority order")
        void partialSourceSelectionMaintainsPriorityOrder() throws IOException {
            // Enable only a subset of sources, including both platform-specific and catch-all
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                playback.audioSources {
                  youtube = false
                  soundcloud = true
                  bandcamp = false
                  vimeo = true
                  twitch = false
                  beam = false
                  getyarn = false
                  nico = false
                  bilibili = false
                  http = true
                  local = false
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            Set<AudioSource> sources = config.getEnabledAudioSources();
            assertEquals(3, sources.size(), "Should have 3 sources enabled");
            
            java.util.List<AudioSource> sourceList = new java.util.ArrayList<>(sources);
            
            // Verify order: soundcloud, vimeo, http (ascending priority)
            assertEquals(AudioSource.SOUNDCLOUD, sourceList.get(0), "SoundCloud should be first");
            assertEquals(AudioSource.VIMEO, sourceList.get(1), "Vimeo should be second");
            assertEquals(AudioSource.HTTP, sourceList.get(2), "HTTP should be last");
        }
    }
    
    @Nested
    @DisplayName("Proxy Configuration Tests")
    class ProxyConfigurationTests {
        
        @Test
        @DisplayName("hasProxy() returns false when host is empty")
        void hasProxyReturnsFalseWhenHostEmpty() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = ""
                  port = 8080
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.hasProxy());
        }
        
        @Test
        @DisplayName("hasProxy() returns false when port is 0")
        void hasProxyReturnsFalseWhenPortIsZero() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = "127.0.0.1"
                  port = 0
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertFalse(config.hasProxy());
        }
        
        @Test
        @DisplayName("hasProxy() returns true when host and port are configured")
        void hasProxyReturnsTrueWhenConfigured() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = "127.0.0.1"
                  port = 8080
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.hasProxy());
            assertEquals("127.0.0.1", config.getProxyHost());
            assertEquals(8080, config.getProxyPort());
        }
        
        @Test
        @DisplayName("Proxy component flags default to false")
        void proxyComponentFlagsDefaultToFalse() throws IOException {
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
            
            assertFalse(config.proxyLavaplayer());
            assertFalse(config.proxyJda());
            assertFalse(config.proxyGithub());
        }
        
        @Test
        @DisplayName("Proxy component flags are loaded correctly")
        void proxyComponentFlagsLoadedCorrectly() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = "127.0.0.1"
                  port = 8080
                  lavaplayer = true
                  jda = false
                  github = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.proxyLavaplayer());
            assertFalse(config.proxyJda());
            assertTrue(config.proxyGithub());
        }
        
        @Test
        @DisplayName("All proxy component flags can be enabled")
        void allProxyComponentFlagsCanBeEnabled() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = "proxy.example.com"
                  port = 18080
                  lavaplayer = true
                  jda = true
                  github = true
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.hasProxy());
            assertEquals("proxy.example.com", config.getProxyHost());
            assertEquals(18080, config.getProxyPort());
            assertTrue(config.proxyLavaplayer());
            assertTrue(config.proxyJda());
            assertTrue(config.proxyGithub());
        }
        
        @Test
        @DisplayName("Practical use case: only Lavaplayer through proxy")
        void practicalUseCaseOnlyLavaplayerThroughProxy() throws IOException {
            String configContent = """
                meta {
                  configVersion = 1
                }
                discord.token = test_token
                discord.owner = 123456789
                proxy {
                  host = "127.0.0.1"
                  port = 18080
                  lavaplayer = true
                  jda = false
                  github = false
                }
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.hasProxy());
            assertTrue(config.proxyLavaplayer());
            assertFalse(config.proxyJda());
            assertFalse(config.proxyGithub());
        }
    }
    
    @Nested
    @DisplayName("Status and Game Tests")
    class StatusAndGameTests {
        
        @Test
        @DisplayName("getStatus() returns correct OnlineStatus")
        void getStatusReturnsCorrectOnlineStatus() throws IOException {
            // Use legacy format - will be migrated to nested
            String configContent = """
                token = test_token
                owner = 123456789
                status = IDLE
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertEquals(OnlineStatus.IDLE, config.getStatus());
        }
        
        @Test
        @DisplayName("getGame() returns correct Activity")
        void getGameReturnsCorrectActivity() throws IOException {
            // Use legacy format - will be migrated to nested
            String configContent = """
                token = test_token
                owner = 123456789
                game = Playing music
                """;
            Path configFile = createTempConfigFile(configContent);
            setConfigFileProperty(configFile);
            
            BotConfig config = new BotConfig(mockUserInteraction);
            config.load();
            
            assertTrue(config.isValid());
            assertNotNull(config.getGame());
            assertTrue(config.getGame().getName().contains("music"));
        }
    }
}
