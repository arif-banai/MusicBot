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
package com.jagrosh.jmusicbot.unit.commands.v2;

import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import com.jagrosh.jmusicbot.testutil.commands.TestMusicSlashCommand;
import com.jagrosh.jmusicbot.testutil.commands.ValidationScenarioBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MusicSlashCommand} base class validation logic.
 * Uses reusable test fixtures for cleaner, more maintainable tests.
 */
public class MusicSlashCommandTest
{
    private SlashCommandTestFixture fixture;
    private TestMusicSlashCommand command;

    @BeforeEach
    void setUp()
    {
        fixture = SlashCommandTestFixture.create();
    }

    // ==================== Basic Validation Tests ====================

    @Test
    void testExecute_ValidCommand_CallsDoCommand()
    {
        // Given: Valid basic scenario
        ValidationScenarioBuilder.with(fixture).validBasic().build();
        command = TestMusicSlashCommand.createBasic(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        assertTrue(command.wasDoCommandCalled(), "doCommand should be called for valid command");
    }

    // ==================== Text Channel Restriction Tests ====================

    @Test
    void testExecute_WrongTextChannel_SendsErrorAndDoesNotCallDoCommand()
    {
        // Given: Wrong text channel scenario
        ValidationScenarioBuilder.with(fixture).wrongTextChannel().build();
        command = TestMusicSlashCommand.createBasic(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("❌ You can only use that command in #music!");
        verify(fixture.getReplyAction()).setEphemeral(true);
        assertFalse(command.wasDoCommandCalled(), "doCommand should not be called");
    }

    // ==================== bePlaying Validation Tests ====================

    @Test
    void testExecute_BePlayingButNotPlaying_SendsError()
    {
        // Given: Requires playing but music not playing
        ValidationScenarioBuilder.with(fixture)
                .noTextChannelRestriction()
                .musicNotPlaying()
                .build();
        command = TestMusicSlashCommand.createRequiresPlaying(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("❌ There must be music playing to use that!");
        verify(fixture.getReplyAction()).setEphemeral(true);
        assertFalse(command.wasDoCommandCalled(), "doCommand should not be called");
    }

    @Test
    void testExecute_BePlayingAndPlaying_CallsDoCommand()
    {
        // Given: Requires playing and music is playing
        ValidationScenarioBuilder.with(fixture).validWithMusicPlaying().build();
        command = TestMusicSlashCommand.createRequiresPlaying(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        assertTrue(command.wasDoCommandCalled(), "doCommand should be called when music is playing");
    }

    // ==================== beListening Validation Tests ====================

    @Test
    void testExecute_BeListeningButNotInVoice_SendsError()
    {
        // Given: Requires listening but user not in voice
        ValidationScenarioBuilder.with(fixture)
                .noTextChannelRestriction()
                .userNotInVoiceChannel()
                .build();
        command = TestMusicSlashCommand.createRequiresListening(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(argThat((String msg) -> msg.contains("You must be listening in")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        assertFalse(command.wasDoCommandCalled(), "doCommand should not be called");
    }

    @Test
    void testExecute_BeListeningInDifferentChannel_SendsError()
    {
        // Given: Requires listening but user in different channel
        ValidationScenarioBuilder.with(fixture)
                .noTextChannelRestriction()
                .userInDifferentVoiceChannel()
                .build();
        command = TestMusicSlashCommand.createRequiresListening(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(argThat((String msg) -> msg.contains("You must be listening in")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        assertFalse(command.wasDoCommandCalled(), "doCommand should not be called");
    }

    @Test
    void testExecute_BeListeningInAfkChannel_SendsError()
    {
        // Given: Requires listening but user in AFK channel
        ValidationScenarioBuilder.with(fixture)
                .noTextChannelRestriction()
                .userInAfkChannel()
                .build();
        command = TestMusicSlashCommand.createRequiresListening(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("❌ You cannot use that command in an AFK channel!");
        verify(fixture.getReplyAction()).setEphemeral(true);
        assertFalse(command.wasDoCommandCalled(), "doCommand should not be called");
    }

    @Test
    void testExecute_BeListeningValid_CallsDoCommand()
    {
        // Given: Requires listening and user is in correct channel
        ValidationScenarioBuilder.with(fixture).validWithUserListening().build();
        command = TestMusicSlashCommand.createRequiresListening(fixture.getBot());

        // When
        command.testExecute(fixture.getEvent());

        // Then
        assertTrue(command.wasDoCommandCalled(), "doCommand should be called when user is listening");
    }
}
