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
package com.jagrosh.jmusicbot.testutil.service;

import com.jagrosh.jmusicbot.settings.RepeatMode;

/**
 * Builder for common MusicService test scenarios.
 * Provides pre-configured scenarios for testing various MusicService operations.
 * 
 * Usage:
 * <pre>
 * ServiceTestFixture fixture = MusicServiceScenarioBuilder.with(ServiceTestFixture.create())
 *     .standardPlayback()
 *     .build();
 * </pre>
 */
public class MusicServiceScenarioBuilder
{
    private final ServiceTestFixture fixture;

    private MusicServiceScenarioBuilder(ServiceTestFixture fixture)
    {
        this.fixture = fixture;
    }

    /**
     * Creates a new scenario builder with the given fixture.
     */
    public static MusicServiceScenarioBuilder with(ServiceTestFixture fixture)
    {
        return new MusicServiceScenarioBuilder(fixture);
    }

    /**
     * Returns the configured fixture.
     */
    public ServiceTestFixture build()
    {
        return fixture;
    }

    // ==================== Pre-configured Scenarios ====================

    /**
     * Configures a standard playback scenario:
     * - User has DJ permission
     * - A track is playing
     * - Queue has some tracks
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder standardPlayback()
    {
        fixture.withDJPermission()
               .withPlayingTrack()
               .withQueueSize(5)
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a scenario where no music is playing:
     * - User has DJ permission
     * - No track playing
     * - Empty queue
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder noMusicPlaying()
    {
        fixture.withDJPermission()
               .withNoTrack()
               .withEmptyQueue()
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a paused playback scenario:
     * - User has DJ permission
     * - A track is paused
     * - Queue has some tracks
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder pausedPlayback()
    {
        fixture.withDJPermission()
               .withPausedTrack()
               .withQueueSize(3)
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a scenario where user lacks DJ permission:
     * - User does NOT have DJ permission
     * - A track is playing
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder noDJPermission()
    {
        fixture.withoutDJPermission()
               .withPlayingTrack()
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a scenario for queue management:
     * - User has DJ permission
     * - A track is playing
     * - Queue has 10 tracks
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder queueManagement()
    {
        fixture.withDJPermission()
               .withPlayingTrack()
               .withQueueSize(10)
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a scenario with repeat mode on:
     * - User has DJ permission
     * - A track is playing
     * - Repeat mode is set to ALL
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder withRepeat()
    {
        fixture.withDJPermission()
               .withPlayingTrack()
               .withRepeatMode(RepeatMode.ALL)
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a scenario for volume testing:
     * - User has DJ permission
     * - A track is playing
     * - Volume is at 50%
     * - User and bot are in voice channel
     */
    public MusicServiceScenarioBuilder volumeTest()
    {
        fixture.withDJPermission()
               .withPlayingTrack()
               .withVolume(50)
               .withBothInVoiceChannel();
        return this;
    }

    /**
     * Configures a solo listening scenario:
     * - User has DJ permission (as owner of the track)
     * - A track is playing
     * - Only user in voice channel
     */
    public MusicServiceScenarioBuilder soloListening()
    {
        fixture.withDJPermission()
               .withPlayingTrack()
               .withUserInVoiceChannel();
        return this;
    }

    // ==================== Chainable Modifiers ====================

    /**
     * Adds DJ permission to the current scenario.
     */
    public MusicServiceScenarioBuilder withDJ()
    {
        fixture.withDJPermission();
        return this;
    }

    /**
     * Removes DJ permission from the current scenario.
     */
    public MusicServiceScenarioBuilder withoutDJ()
    {
        fixture.withoutDJPermission();
        return this;
    }

    /**
     * Adds a playing track to the current scenario.
     */
    public MusicServiceScenarioBuilder playing()
    {
        fixture.withPlayingTrack();
        return this;
    }

    /**
     * Adds a paused track to the current scenario.
     */
    public MusicServiceScenarioBuilder paused()
    {
        fixture.withPausedTrack();
        return this;
    }

    /**
     * Clears the current track.
     */
    public MusicServiceScenarioBuilder notPlaying()
    {
        fixture.withNoTrack();
        return this;
    }

    /**
     * Sets the queue size.
     */
    public MusicServiceScenarioBuilder withQueue(int size)
    {
        fixture.withQueueSize(size);
        return this;
    }

    /**
     * Sets the repeat mode.
     */
    public MusicServiceScenarioBuilder withRepeat(RepeatMode mode)
    {
        fixture.withRepeatMode(mode);
        return this;
    }

    /**
     * Sets the volume.
     */
    public MusicServiceScenarioBuilder withVolume(int volume)
    {
        fixture.withVolume(volume);
        return this;
    }

    /**
     * Puts both user and bot in voice channel.
     */
    public MusicServiceScenarioBuilder inVoiceChannel()
    {
        fixture.withBothInVoiceChannel();
        return this;
    }
}
