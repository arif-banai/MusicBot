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

import com.jagrosh.jmusicbot.Listener;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import net.dv8tion.jda.api.requests.CloseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Listener class.
 * Uses the ListenerTestFixture for consistent mock setup.
 */
@DisplayName("Listener Tests")
public class ListenerTest
{
    private ListenerTestFixture fixture;
    private Listener listener;

    @BeforeEach
    void setUp()
    {
        fixture = ListenerTestFixture.create();
        listener = new Listener(fixture.getBot());
    }

    // ==================== onMessageDelete Tests ====================

    @Nested
    @DisplayName("onMessageDelete")
    class OnMessageDeleteTests
    {
        @Test
        @DisplayName("onMessageDelete() delegates to NowPlayingHandler when from guild")
        void onMessageDelete_delegatesToNowPlayingHandler()
        {
            // When
            listener.onMessageDelete(fixture.getMessageDeleteEvent());

            // Then
            verify(fixture.getNowPlayingHandler()).onMessageDelete(
                    fixture.getGuild(), 
                    ListenerTestFixture.MESSAGE_ID
            );
        }

        @Test
        @DisplayName("onMessageDelete() does nothing when not from guild")
        void onMessageDelete_doesNothingWhenNotFromGuild()
        {
            // Given
            when(fixture.getMessageDeleteEvent().isFromGuild()).thenReturn(false);

            // When
            listener.onMessageDelete(fixture.getMessageDeleteEvent());

            // Then
            verify(fixture.getNowPlayingHandler(), never()).onMessageDelete(any(), anyLong());
        }
    }

    // ==================== onButtonInteraction Tests ====================

    @Nested
    @DisplayName("onButtonInteraction")
    class OnButtonInteractionTests
    {
        @Test
        @DisplayName("onButtonInteraction() ignores unknown button IDs")
        void onButtonInteraction_ignoresUnknownButtonId()
        {
            // Given - default fixture has "unknown" button ID

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
            verify(fixture.getMusicService(), never()).pause(any(), any(), any());
            verify(fixture.getMusicService(), never()).skip(any(), any(), any());
        }

        @Test
        @DisplayName("onButtonInteraction() handles stop button")
        void onButtonInteraction_handlesStopButton()
        {
            // Given
            fixture.withButtonId("stop")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).stop(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles pause button")
        void onButtonInteraction_handlesPauseButton()
        {
            // Given
            fixture.withButtonId("pause")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).pause(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles skip button")
        void onButtonInteraction_handlesSkipButton()
        {
            // Given
            fixture.withButtonId("skip")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).skip(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles previous button")
        void onButtonInteraction_handlesPreviousButton()
        {
            // Given
            fixture.withButtonId("previous")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).previous(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles shuffle button")
        void onButtonInteraction_handlesShuffleButton()
        {
            // Given
            fixture.withButtonId("shuffle")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).shuffle(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(0),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles repeat button")
        void onButtonInteraction_handlesRepeatButton()
        {
            // Given
            fixture.withButtonId("repeat")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).cycleRepeatMode(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles voldown button")
        void onButtonInteraction_handlesVoldownButton()
        {
            // Given
            fixture.withButtonId("voldown")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).adjustVolume(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(-10),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() handles volup button")
        void onButtonInteraction_handlesVolupButton()
        {
            // Given
            fixture.withButtonId("volup")
                   .withMemberInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getMusicService()).adjustVolume(
                    eq(fixture.getGuild()),
                    eq(fixture.getMember()),
                    eq(10),
                    any(MusicService.OutputAdapter.class)
            );
        }

        @Test
        @DisplayName("onButtonInteraction() replies error when no audio handler")
        void onButtonInteraction_repliesErrorWhenNoHandler()
        {
            // Given
            fixture.withButtonId("stop")
                   .withNoAudioHandler();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getButtonInteractionEvent()).reply("There is no music playing!");
            verify(fixture.getReplyAction()).setEphemeral(true);
        }

        @Test
        @DisplayName("onButtonInteraction() replies error when user not in voice")
        void onButtonInteraction_repliesErrorWhenUserNotInVoice()
        {
            // Given
            fixture.withButtonId("stop")
                   .withMemberNotInVoiceChannel()
                   .withAudioHandlerPlaying();

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then
            verify(fixture.getButtonInteractionEvent()).reply("You must be in the same voice channel to use this!");
            verify(fixture.getReplyAction()).setEphemeral(true);
        }

        @Test
        @DisplayName("onButtonInteraction() handles null guild gracefully")
        void onButtonInteraction_handlesNullGuild()
        {
            // Given
            fixture.withButtonId("stop");
            when(fixture.getButtonInteractionEvent().getGuild()).thenReturn(null);

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then - should not throw, and no service calls
            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
        }

        @Test
        @DisplayName("onButtonInteraction() handles null member gracefully")
        void onButtonInteraction_handlesNullMember()
        {
            // Given
            fixture.withButtonId("stop");
            when(fixture.getButtonInteractionEvent().getMember()).thenReturn(null);

            // When
            listener.onButtonInteraction(fixture.getButtonInteractionEvent());

            // Then - should not throw, and no service calls
            verify(fixture.getMusicService(), never()).stop(any(), any(), any());
        }
    }

    // ==================== onGuildVoiceUpdate Tests ====================

    @Nested
    @DisplayName("onGuildVoiceUpdate")
    class OnGuildVoiceUpdateTests
    {
        @Test
        @DisplayName("onGuildVoiceUpdate() delegates to AloneInVoiceHandler")
        void onGuildVoiceUpdate_delegatesToAloneInVoiceHandler()
        {
            // When
            listener.onGuildVoiceUpdate(fixture.getGuildVoiceUpdateEvent());

            // Then
            verify(fixture.getAloneInVoiceHandler()).onVoiceUpdate(fixture.getGuildVoiceUpdateEvent());
        }
    }

    // ==================== onSessionDisconnect Tests ====================

    @Nested
    @DisplayName("onSessionDisconnect")
    class OnSessionDisconnectTests
    {
        @Test
        @DisplayName("onSessionDisconnect() shows error alert when close code is DISALLOWED_INTENTS")
        void onSessionDisconnect_showsAlertForDisallowedIntents()
        {
            // Given
            fixture.withCloseCode(CloseCode.DISALLOWED_INTENTS);

            // When
            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            // Then
            verify(fixture.getUserInteraction()).alert(
                    eq(Level.ERROR),
                    eq("JMusicBot"),
                    contains("missing required Discord intents")
            );
        }

        @Test
        @DisplayName("onSessionDisconnect() does not show alert for null close code")
        void onSessionDisconnect_doesNothingForNullCloseCode()
        {
            // Given - default fixture has null close code

            // When
            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            // Then
            verify(fixture.getUserInteraction(), never()).alert(any(), any(), any());
        }

        @Test
        @DisplayName("onSessionDisconnect() does not show alert for other close codes")
        void onSessionDisconnect_doesNothingForOtherCloseCodes()
        {
            // Given
            fixture.withCloseCode(CloseCode.GRACEFUL_CLOSE);

            // When
            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            // Then
            verify(fixture.getUserInteraction(), never()).alert(any(), any(), any());
        }

        @Test
        @DisplayName("onSessionDisconnect() error message includes instructions for enabling intents")
        void onSessionDisconnect_messageIncludesInstructions()
        {
            // Given
            fixture.withCloseCode(CloseCode.DISALLOWED_INTENTS);

            // When
            listener.onSessionDisconnect(fixture.getSessionDisconnectEvent());

            // Then
            verify(fixture.getUserInteraction()).alert(
                    eq(Level.ERROR),
                    eq("JMusicBot"),
                    argThat(message -> 
                        message.contains("discord.com/developers/applications") &&
                        message.contains("MESSAGE CONTENT INTENT") &&
                        message.contains("Privileged Gateway Intents")
                    )
            );
        }
    }

    // ==================== onShutdown Tests ====================

    @Nested
    @DisplayName("onShutdown")
    class OnShutdownTests
    {
        @Test
        @DisplayName("onShutdown() calls bot.shutdown()")
        void onShutdown_callsBotShutdown()
        {
            // When
            listener.onShutdown(fixture.getShutdownEvent());

            // Then
            verify(fixture.getBot()).shutdown();
        }
    }
}
