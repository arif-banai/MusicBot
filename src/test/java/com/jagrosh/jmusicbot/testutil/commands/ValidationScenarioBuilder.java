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
package com.jagrosh.jmusicbot.testutil.commands;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;

import static org.mockito.Mockito.*;

/**
 * Builder for creating validation test scenarios for MusicSlashCommand tests.
 * Provides a fluent API for setting up common validation states.
 */
public class ValidationScenarioBuilder
{
    private final SlashCommandTestFixture fixture;

    private ValidationScenarioBuilder(SlashCommandTestFixture fixture)
    {
        this.fixture = fixture;
    }

    /**
     * Creates a new scenario builder with a fresh fixture.
     */
    public static ValidationScenarioBuilder create()
    {
        return new ValidationScenarioBuilder(SlashCommandTestFixture.create());
    }

    /**
     * Creates a scenario builder using an existing fixture.
     */
    public static ValidationScenarioBuilder with(SlashCommandTestFixture fixture)
    {
        return new ValidationScenarioBuilder(fixture);
    }

    // ==================== Text Channel Scenarios ====================

    /**
     * Scenario: No text channel restriction (valid for any channel).
     */
    public ValidationScenarioBuilder noTextChannelRestriction()
    {
        when(fixture.getSettings().getTextChannel(fixture.getGuild())).thenReturn(null);
        return this;
    }

    /**
     * Scenario: Command used in wrong text channel.
     */
    public ValidationScenarioBuilder wrongTextChannel()
    {
        TextChannel requiredChannel = mock(TextChannel.class);
        when(requiredChannel.getAsMention()).thenReturn("#music");
        when(fixture.getSettings().getTextChannel(fixture.getGuild())).thenReturn(requiredChannel);
        return this;
    }

    /**
     * Scenario: Command used in correct text channel.
     */
    public ValidationScenarioBuilder correctTextChannel()
    {
        when(fixture.getSettings().getTextChannel(fixture.getGuild())).thenReturn(fixture.getTextChannel());
        return this;
    }

    // ==================== Playing State Scenarios ====================

    /**
     * Scenario: Music is currently playing.
     */
    public ValidationScenarioBuilder musicPlaying()
    {
        fixture.withMusicPlaying();
        return this;
    }

    /**
     * Scenario: Music is not playing.
     */
    public ValidationScenarioBuilder musicNotPlaying()
    {
        fixture.withMusicNotPlaying();
        return this;
    }

    // ==================== Voice Channel Scenarios ====================

    /**
     * Scenario: User is in the correct voice channel (same as bot or required channel).
     * This uses the shared voiceChannel mock to ensure equality checks pass.
     */
    public ValidationScenarioBuilder userInCorrectVoiceChannel()
    {
        fixture.withRequiredVoiceChannel();
        fixture.withUserInVoiceChannel();
        return this;
    }

    /**
     * Scenario: User is in a voice channel but bot is in a different one.
     */
    public ValidationScenarioBuilder userInDifferentVoiceChannel()
    {
        // Bot is in the shared voice channel
        fixture.withBotInVoiceChannel();
        // User is in a different channel - create with extra interface for proper casting
        VoiceChannel differentChannel = mock(VoiceChannel.class, withSettings().extraInterfaces(AudioChannelUnion.class));
        fixture.withUserInVoiceChannel((AudioChannelUnion) differentChannel);
        return this;
    }

    /**
     * Scenario: User is not in any voice channel.
     */
    public ValidationScenarioBuilder userNotInVoiceChannel()
    {
        when(fixture.getMemberVoiceState().getChannel()).thenReturn(null);
        return this;
    }

    /**
     * Scenario: User is in the AFK channel.
     * Uses the same channel for both user location and AFK channel to ensure equality.
     */
    public ValidationScenarioBuilder userInAfkChannel()
    {
        // Use same mock for both required channel, user channel, and AFK channel
        fixture.withRequiredVoiceChannel();
        fixture.withUserInVoiceChannel();
        fixture.withAfkChannel();
        return this;
    }

    /**
     * Scenario: User is deafened.
     */
    public ValidationScenarioBuilder userDeafened()
    {
        fixture.withUserDeafened();
        return this;
    }

    /**
     * Scenario: Bot is not in any voice channel.
     */
    public ValidationScenarioBuilder botNotInVoiceChannel()
    {
        when(fixture.getSelfVoiceState().getChannel()).thenReturn(null);
        return this;
    }

    /**
     * Scenario: Bot is in the same voice channel as user.
     */
    public ValidationScenarioBuilder botInSameVoiceChannelAsUser()
    {
        fixture.withBotInVoiceChannel();
        fixture.withUserInVoiceChannel();
        return this;
    }

    // ==================== Combined Scenarios ====================

    /**
     * Scenario: Valid for commands that require no special conditions.
     */
    public ValidationScenarioBuilder validBasic()
    {
        return noTextChannelRestriction();
    }

    /**
     * Scenario: Valid for commands that require music to be playing.
     */
    public ValidationScenarioBuilder validWithMusicPlaying()
    {
        return noTextChannelRestriction().musicPlaying();
    }

    /**
     * Scenario: Valid for commands that require user to be listening.
     */
    public ValidationScenarioBuilder validWithUserListening()
    {
        return noTextChannelRestriction().userInCorrectVoiceChannel();
    }

    /**
     * Scenario: Valid for commands that require both music playing and user listening.
     */
    public ValidationScenarioBuilder validWithMusicPlayingAndUserListening()
    {
        return noTextChannelRestriction().musicPlaying().userInCorrectVoiceChannel();
    }

    // ==================== Build ====================

    /**
     * Returns the configured fixture.
     */
    public SlashCommandTestFixture build()
    {
        return fixture;
    }

    /**
     * Returns the fixture (alias for build()).
     */
    public SlashCommandTestFixture getFixture()
    {
        return fixture;
    }
}
