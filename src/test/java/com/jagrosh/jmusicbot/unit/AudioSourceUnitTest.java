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

import com.jagrosh.jmusicbot.audio.AudioSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AudioSource Unit Tests")
class AudioSourceUnitTest {

    @Nested
    @DisplayName("Registration Priority Tests")
    class RegistrationPriorityTests {

        @Test
        @DisplayName("valuesSortedByPriority() returns sources in ascending priority order")
        void valuesSortedByPriorityReturnsCorrectOrder() {
            List<AudioSource> sorted = AudioSource.valuesSortedByPriority();
            
            // Verify list is sorted by priority
            for (int i = 0; i < sorted.size() - 1; i++) {
                assertTrue(sorted.get(i).getRegistrationPriority() <= sorted.get(i + 1).getRegistrationPriority(),
                    String.format("Source %s (priority %d) should come before %s (priority %d)",
                        sorted.get(i).getConfigName(), sorted.get(i).getRegistrationPriority(),
                        sorted.get(i + 1).getConfigName(), sorted.get(i + 1).getRegistrationPriority()));
            }
        }

        @Test
        @DisplayName("Platform-specific sources have lower priority than catch-all sources")
        void platformSpecificSourcesHaveLowerPriorityThanCatchAll() {
            // Platform-specific sources that should be registered first
            AudioSource[] platformSources = {
                AudioSource.YOUTUBE, AudioSource.SOUNDCLOUD, AudioSource.BANDCAMP,
                AudioSource.VIMEO, AudioSource.TWITCH, AudioSource.BEAM,
                AudioSource.GETYARN, AudioSource.NICO, AudioSource.BILIBILI
            };
            
            // Catch-all sources that should be registered last
            AudioSource[] catchAllSources = { AudioSource.HTTP, AudioSource.LOCAL };
            
            // Get the maximum priority of platform sources
            int maxPlatformPriority = 0;
            for (AudioSource source : platformSources) {
                maxPlatformPriority = Math.max(maxPlatformPriority, source.getRegistrationPriority());
            }
            
            // Get the minimum priority of catch-all sources
            int minCatchAllPriority = Integer.MAX_VALUE;
            for (AudioSource source : catchAllSources) {
                minCatchAllPriority = Math.min(minCatchAllPriority, source.getRegistrationPriority());
            }
            
            assertTrue(maxPlatformPriority < minCatchAllPriority,
                String.format("Platform sources (max priority %d) should all have lower priority than catch-all sources (min priority %d)",
                    maxPlatformPriority, minCatchAllPriority));
        }

        @Test
        @DisplayName("HTTP source is registered after SoundCloud")
        void httpSourceRegisteredAfterSoundCloud() {
            // This is the specific regression that was fixed - HTTP was claiming SoundCloud URLs
            assertTrue(AudioSource.SOUNDCLOUD.getRegistrationPriority() < AudioSource.HTTP.getRegistrationPriority(),
                "SoundCloud should have lower priority (registered first) than HTTP");
            
            List<AudioSource> sorted = AudioSource.valuesSortedByPriority();
            int soundcloudIndex = sorted.indexOf(AudioSource.SOUNDCLOUD);
            int httpIndex = sorted.indexOf(AudioSource.HTTP);
            
            assertTrue(soundcloudIndex < httpIndex,
                "SoundCloud should appear before HTTP in the sorted list");
        }

        @Test
        @DisplayName("BILIBILI is registered as a platform-specific source before HTTP")
        void bilibiliIsPlatformSpecific() {
            assertEquals("bilibili", AudioSource.BILIBILI.getConfigName());
            assertTrue(AudioSource.BILIBILI.getRegistrationPriority()
                            < AudioSource.HTTP.getRegistrationPriority(),
                "Bilibili must be registered before the HTTP catch-all, or HTTP claims its URLs");
        }

        @Test
        @DisplayName("BILIBILI resolves from its config name case-insensitively")
        void bilibiliResolvesFromConfigName() {
            assertEquals(Optional.of(AudioSource.BILIBILI), AudioSource.fromConfigName("bilibili"));
            assertEquals(Optional.of(AudioSource.BILIBILI), AudioSource.fromConfigName("BiliBili"));
        }

        @Test
        @DisplayName("HTTP source is registered after all platform-specific sources")
        void httpSourceRegisteredLast() {
            List<AudioSource> sorted = AudioSource.valuesSortedByPriority();
            int httpIndex = sorted.indexOf(AudioSource.HTTP);
            
            // HTTP should be near the end (only LOCAL should be after or equal)
            assertTrue(httpIndex >= sorted.size() - 2,
                "HTTP should be one of the last two sources registered");
        }

        @Test
        @DisplayName("All sources have unique priorities")
        void allSourcesHaveUniquePriorities() {
            AudioSource[] sources = AudioSource.values();
            List<Integer> priorities = new ArrayList<>();
            
            for (AudioSource source : sources) {
                int priority = source.getRegistrationPriority();
                assertFalse(priorities.contains(priority),
                    String.format("Priority %d is duplicated (found in %s)", priority, source.getConfigName()));
                priorities.add(priority);
            }
        }
    }

    @Nested
    @DisplayName("Config Name Tests")
    class ConfigNameTests {

        @Test
        @DisplayName("fromConfigName() returns correct source for valid names")
        void fromConfigNameReturnsCorrectSource() {
            assertEquals(Optional.of(AudioSource.YOUTUBE), AudioSource.fromConfigName("youtube"));
            assertEquals(Optional.of(AudioSource.SOUNDCLOUD), AudioSource.fromConfigName("soundcloud"));
            assertEquals(Optional.of(AudioSource.HTTP), AudioSource.fromConfigName("http"));
            assertEquals(Optional.of(AudioSource.LOCAL), AudioSource.fromConfigName("local"));
        }

        @Test
        @DisplayName("fromConfigName() is case-insensitive")
        void fromConfigNameIsCaseInsensitive() {
            assertEquals(Optional.of(AudioSource.YOUTUBE), AudioSource.fromConfigName("YouTube"));
            assertEquals(Optional.of(AudioSource.YOUTUBE), AudioSource.fromConfigName("YOUTUBE"));
            assertEquals(Optional.of(AudioSource.SOUNDCLOUD), AudioSource.fromConfigName("SoundCloud"));
        }

        @Test
        @DisplayName("fromConfigName() returns empty for invalid names")
        void fromConfigNameReturnsEmptyForInvalidNames() {
            assertEquals(Optional.empty(), AudioSource.fromConfigName("invalid"));
            assertEquals(Optional.empty(), AudioSource.fromConfigName("spotify"));
            assertEquals(Optional.empty(), AudioSource.fromConfigName(""));
        }

        @Test
        @DisplayName("fromConfigName() returns empty for null")
        void fromConfigNameReturnsEmptyForNull() {
            assertEquals(Optional.empty(), AudioSource.fromConfigName(null));
        }

        @Test
        @DisplayName("All sources have non-empty config names")
        void allSourcesHaveNonEmptyConfigNames() {
            for (AudioSource source : AudioSource.values()) {
                assertNotNull(source.getConfigName(), source.name() + " should have a config name");
                assertFalse(source.getConfigName().isEmpty(), source.name() + " config name should not be empty");
            }
        }

        @Test
        @DisplayName("All sources have unique config names")
        void allSourcesHaveUniqueConfigNames() {
            AudioSource[] sources = AudioSource.values();
            List<String> configNames = new ArrayList<>();
            
            for (AudioSource source : sources) {
                String name = source.getConfigName();
                assertFalse(configNames.contains(name),
                    String.format("Config name '%s' is duplicated", name));
                configNames.add(name);
            }
        }
    }

    @Nested
    @DisplayName("Description Tests")
    class DescriptionTests {

        @Test
        @DisplayName("All sources have non-empty descriptions")
        void allSourcesHaveNonEmptyDescriptions() {
            for (AudioSource source : AudioSource.values()) {
                assertNotNull(source.getDescription(), source.name() + " should have a description");
                assertFalse(source.getDescription().isEmpty(), source.name() + " description should not be empty");
            }
        }
    }

    @Nested
    @DisplayName("Completeness Tests")
    class CompletenessTests {

        @Test
        @DisplayName("valuesSortedByPriority() contains all sources")
        void valuesSortedByPriorityContainsAllSources() {
            List<AudioSource> sorted = AudioSource.valuesSortedByPriority();
            AudioSource[] all = AudioSource.values();
            
            assertEquals(all.length, sorted.size(), "Sorted list should contain all sources");
            
            for (AudioSource source : all) {
                assertTrue(sorted.contains(source),
                    source.getConfigName() + " should be in the sorted list");
            }
        }
    }
}
