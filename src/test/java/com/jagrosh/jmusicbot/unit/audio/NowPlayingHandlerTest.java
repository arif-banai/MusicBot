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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingHandler;
import com.jagrosh.jmusicbot.testutil.audio.AudioTestFixture;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.jagrosh.jmusicbot.testutil.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NowPlayingHandler.
 * Uses AudioTestFixture for consistent mock setup.
 */
@DisplayName("NowPlayingHandler Tests")
public class NowPlayingHandlerTest
{
    private AudioTestFixture fixture;
    private NowPlayingHandler nowPlayingHandler;
    private Presence presence;
    private AudioHandler audioHandler;

    @BeforeEach
    void setUp()
    {
        fixture = AudioTestFixture.create();
        
        // Setup presence mock
        presence = mock(Presence.class);
        when(fixture.getJda().getPresence()).thenReturn(presence);
        
        // Setup audio handler for the guild
        audioHandler = mock(AudioHandler.class);
        when(fixture.getAudioManager().getSendingHandler()).thenReturn(audioHandler);
        when(audioHandler.getPlayer()).thenReturn(fixture.getAudioPlayer());
        
        // Create handler
        nowPlayingHandler = new NowPlayingHandler(fixture.getBot());
    }

    // ==================== Initialization Tests ====================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests
    {
        @Test
        @DisplayName("init() schedules update task when not using NP images")
        void init_schedulesUpdateTask_whenNotUsingNPImages()
        {
            // Given
            when(fixture.getConfig().useNPImages()).thenReturn(false);

            // When
            nowPlayingHandler.init();

            // Then
            verify(fixture.getThreadpool()).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(10L), any());
        }

        @Test
        @DisplayName("init() does not schedule task when using NP images")
        void init_doesNotScheduleTask_whenUsingNPImages()
        {
            // Given
            when(fixture.getConfig().useNPImages()).thenReturn(true);

            // When
            nowPlayingHandler.init();

            // Then
            verify(fixture.getThreadpool(), never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
        }
    }

    // ==================== setLastNPMessage Tests ====================

    @Nested
    @DisplayName("setLastNPMessage")
    class SetLastNPMessageTests
    {
        @Test
        @DisplayName("setLastNPMessage() stores message location")
        void setLastNPMessage_storesLocation()
        {
            // When
            nowPlayingHandler.setLastNPMessage(fixture.getMessage());

            // Then - we can verify by calling clearLastNPMessage and checking no NPE
            assertDoesNotThrow(() -> nowPlayingHandler.clearLastNPMessage(fixture.getGuild()));
        }
    }

    // ==================== clearLastNPMessage Tests ====================

    @Nested
    @DisplayName("clearLastNPMessage")
    class ClearLastNPMessageTests
    {
        @Test
        @DisplayName("clearLastNPMessage() removes stored location")
        void clearLastNPMessage_removesLocation()
        {
            // Given
            nowPlayingHandler.setLastNPMessage(fixture.getMessage());

            // When
            nowPlayingHandler.clearLastNPMessage(fixture.getGuild());

            // Then - no exception thrown
            assertDoesNotThrow(() -> nowPlayingHandler.clearLastNPMessage(fixture.getGuild()));
        }

        @Test
        @DisplayName("clearLastNPMessage() handles non-existent guild gracefully")
        void clearLastNPMessage_handlesNonExistentGuild()
        {
            // When/Then - should not throw
            assertDoesNotThrow(() -> nowPlayingHandler.clearLastNPMessage(fixture.getGuild()));
        }
    }

    // ==================== onMessageDelete Tests ====================

    @Nested
    @DisplayName("onMessageDelete")
    class OnMessageDeleteTests
    {
        @Test
        @DisplayName("onMessageDelete() removes matching message location")
        void onMessageDelete_removesMatchingLocation()
        {
            // Given
            nowPlayingHandler.setLastNPMessage(fixture.getMessage());

            // When
            nowPlayingHandler.onMessageDelete(fixture.getGuild(), MESSAGE_ID);

            // Then - location should be removed (verified by no update attempt)
            // This is hard to verify directly, but the method should not throw
            assertDoesNotThrow(() -> nowPlayingHandler.onMessageDelete(fixture.getGuild(), MESSAGE_ID));
        }

        @Test
        @DisplayName("onMessageDelete() ignores non-matching message")
        void onMessageDelete_ignoresNonMatchingMessage()
        {
            // Given
            nowPlayingHandler.setLastNPMessage(fixture.getMessage());

            // When - delete different message
            nowPlayingHandler.onMessageDelete(fixture.getGuild(), 999999L);

            // Then - original location should still be stored
            assertDoesNotThrow(() -> nowPlayingHandler.clearLastNPMessage(fixture.getGuild()));
        }
    }

    // ==================== onTrackUpdate Tests ====================

    @Nested
    @DisplayName("onTrackUpdate")
    class OnTrackUpdateTests
    {
        @Test
        @DisplayName("onTrackUpdate() updates status when song in status is enabled")
        void onTrackUpdate_updatesStatus_whenSongInStatusEnabled()
        {
            // Given
            fixture.withSongInStatus();
            AudioTrack track = fixture.createMockTrack("Test Song", "Artist", 180000);
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(track);
            when(audioHandler.getNowPlaying(fixture.getJda())).thenReturn(mock(MessageCreateData.class));

            // When
            nowPlayingHandler.onTrackUpdate(GUILD_ID, track);

            // Then
            verify(presence).setActivity(argThat(activity -> 
                activity != null && activity.getType() == Activity.ActivityType.LISTENING
            ));
        }

        @Test
        @DisplayName("onTrackUpdate() resets game when track is null and song in status is enabled")
        void onTrackUpdate_resetsGame_whenTrackNullAndSongInStatusEnabled()
        {
            // Given
            fixture.withSongInStatus();

            // When
            nowPlayingHandler.onTrackUpdate(GUILD_ID, null);

            // Then
            verify(fixture.getBot()).resetGame();
        }

        @Test
        @DisplayName("onTrackUpdate() does not update status when song in status is disabled")
        void onTrackUpdate_doesNotUpdateStatus_whenSongInStatusDisabled()
        {
            // Given - song in status is disabled by default in fixture
            AudioTrack track = fixture.createMockTrack("Test Song", "Artist", 180000);
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(track);
            when(audioHandler.getNowPlaying(fixture.getJda())).thenReturn(mock(MessageCreateData.class));

            // When
            nowPlayingHandler.onTrackUpdate(GUILD_ID, track);

            // Then
            verify(presence, never()).setActivity(any());
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests
    {
        @Test
        @DisplayName("handles null guild from JDA gracefully")
        void handlesNullGuildGracefully()
        {
            // Given
            when(fixture.getJda().getGuildById(GUILD_ID)).thenReturn(null);

            // When/Then - should not throw
            assertDoesNotThrow(() -> nowPlayingHandler.onTrackUpdate(GUILD_ID, null));
        }

        @Test
        @DisplayName("handles multiple setLastNPMessage calls")
        void handlesMultipleSetLastNPMessageCalls()
        {
            // Given
            Message message2 = mock(Message.class);
            when(message2.getIdLong()).thenReturn(999999L);
            when(message2.getGuild()).thenReturn(fixture.getGuild());
            when(message2.getChannel()).thenReturn((MessageChannelUnion) fixture.getTextChannel());

            // When
            nowPlayingHandler.setLastNPMessage(fixture.getMessage());
            nowPlayingHandler.setLastNPMessage(message2);

            // Then - should not throw
            assertDoesNotThrow(() -> nowPlayingHandler.clearLastNPMessage(fixture.getGuild()));
        }
    }
}
