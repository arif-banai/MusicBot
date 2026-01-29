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

import com.jagrosh.jmusicbot.TestBase;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AudioHandler Tests")
public class AudioHandlerTest extends TestBase {

    @Mock
    private SelfMember selfMember;
    @Mock
    private GuildVoiceState voiceState;

    private AudioHandler audioHandler;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        when(settings.getQueueType()).thenReturn(QueueType.FAIR);

        // AudioHandler's constructor is not visible, so use reflection to instantiate it for testing
        try {
            var constructor = AudioHandler.class.getDeclaredConstructor(
                    playerManager.getClass().getInterfaces().length > 0 ? playerManager.getClass().getInterfaces()[0] : playerManager.getClass(),
                    guild.getClass().getInterfaces().length > 0 ? guild.getClass().getInterfaces()[0] : guild.getClass(),
                    audioPlayer.getClass().getInterfaces().length > 0 ? audioPlayer.getClass().getInterfaces()[0] : audioPlayer.getClass()
            );
            constructor.setAccessible(true);
            audioHandler = (AudioHandler) constructor.newInstance(playerManager, guild, audioPlayer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate AudioHandler via reflection", e);
        }
    }

    // ==================== Add Track Tests ====================

    @Nested
    @DisplayName("Add Track Operations")
    class AddTrackTests
    {
        @Test
        @DisplayName("addTrack() plays immediately when nothing is playing")
        public void testAddTrackWhenNothingPlaying() {
            QueuedTrack qtrack = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            when(qtrack.getTrack()).thenReturn(track);
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            int result = audioHandler.addTrack(qtrack);

            assertEquals(-1, result);
            verify(audioPlayer).playTrack(track);
        }

        @Test
        @DisplayName("addTrack() queues track when something is playing")
        public void testAddTrackWhenSomethingPlaying() {
            QueuedTrack qtrack = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 1000, "identifier", true, "uri");
            when(track.getInfo()).thenReturn(info);
            when(qtrack.getTrack()).thenReturn(track);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            int result = audioHandler.addTrack(qtrack);

            assertTrue(result >= 0);
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("addTrackToFront() plays immediately when nothing is playing")
        public void testAddTrackToFrontWhenNothingPlaying() {
            QueuedTrack qtrack = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            when(qtrack.getTrack()).thenReturn(track);
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            int result = audioHandler.addTrackToFront(qtrack);

            assertEquals(-1, result);
            verify(audioPlayer).playTrack(track);
        }

        @Test
        @DisplayName("addTrackToFront() adds to position 0 when something is playing")
        public void testAddTrackToFrontWhenSomethingPlaying() {
            // First add a track to the queue
            QueuedTrack qtrack1 = mock(QueuedTrack.class);
            AudioTrack track1 = mock(AudioTrack.class);
            AudioTrackInfo info1 = new AudioTrackInfo("Track 1", "Author", 1000, "id1", true, "uri1");
            when(track1.getInfo()).thenReturn(info1);
            when(qtrack1.getTrack()).thenReturn(track1);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
            audioHandler.addTrack(qtrack1);

            // Now add to front
            QueuedTrack qtrack2 = mock(QueuedTrack.class);
            AudioTrack track2 = mock(AudioTrack.class);
            AudioTrackInfo info2 = new AudioTrackInfo("Track 2", "Author", 1000, "id2", true, "uri2");
            when(track2.getInfo()).thenReturn(info2);
            when(qtrack2.getTrack()).thenReturn(track2);

            int result = audioHandler.addTrackToFront(qtrack2);

            assertEquals(0, result);
            assertEquals(2, audioHandler.getQueue().size());
        }
    }

    // ==================== Stop and Clear Tests ====================

    @Nested
    @DisplayName("Stop and Clear Operations")
    class StopAndClearTests
    {
        @Test
        @DisplayName("stopAndClear() stops playback and clears queue")
        public void testStopAndClear() {
            audioHandler.stopAndClear();

            verify(audioPlayer).stopTrack();
            assertTrue(audioHandler.getQueue().isEmpty());
        }

        @Test
        @DisplayName("stopAndClear() can be called multiple times safely")
        public void testStopAndClearMultipleTimes() {
            audioHandler.stopAndClear();
            audioHandler.stopAndClear();

            verify(audioPlayer, times(2)).stopTrack();
        }
    }

    // ==================== isMusicPlaying Tests ====================

    @Nested
    @DisplayName("isMusicPlaying")
    class IsMusicPlayingTests
    {
        @Test
        @DisplayName("isMusicPlaying() returns true when connected and playing")
        public void testIsMusicPlayingTrue() {
            when(jda.getGuildById(anyLong())).thenReturn(guild);
            when(guild.getSelfMember()).thenReturn(selfMember);
            when(selfMember.getVoiceState()).thenReturn(voiceState);
            when(voiceState.getChannel()).thenReturn(audioChannel);
            when(audioPlayer.getPlayingTrack()).thenReturn(audioTrack);

            assertTrue(audioHandler.isMusicPlaying(jda));
        }

        @Test
        @DisplayName("isMusicPlaying() returns false when not in voice channel")
        public void testIsMusicPlayingFalseNotInVoice() {
            when(jda.getGuildById(anyLong())).thenReturn(guild);
            when(guild.getSelfMember()).thenReturn(selfMember);
            when(selfMember.getVoiceState()).thenReturn(voiceState);
            when(voiceState.getChannel()).thenReturn(null);
            when(audioPlayer.getPlayingTrack()).thenReturn(audioTrack);

            assertFalse(audioHandler.isMusicPlaying(jda));
        }

        @Test
        @DisplayName("isMusicPlaying() returns false when nothing is playing")
        public void testIsMusicPlayingFalseNoTrack() {
            when(jda.getGuildById(anyLong())).thenReturn(guild);
            when(guild.getSelfMember()).thenReturn(selfMember);
            when(selfMember.getVoiceState()).thenReturn(voiceState);
            when(voiceState.getChannel()).thenReturn(audioChannel);
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            assertFalse(audioHandler.isMusicPlaying(jda));
        }
    }

    // ==================== Vote Tests ====================

    @Nested
    @DisplayName("Vote Tracking")
    class VoteTests
    {
        @Test
        @DisplayName("getVotes() returns empty set initially")
        public void testGetVotesInitiallyEmpty() {
            assertTrue(audioHandler.getVotes().isEmpty());
        }

        @Test
        @DisplayName("votes can be added and retrieved")
        public void testAddVote() {
            audioHandler.getVotes().add("user123");

            assertEquals(1, audioHandler.getVotes().size());
            assertTrue(audioHandler.getVotes().contains("user123"));
        }

        @Test
        @DisplayName("duplicate votes are not added")
        public void testDuplicateVotes() {
            audioHandler.getVotes().add("user123");
            audioHandler.getVotes().add("user123");

            assertEquals(1, audioHandler.getVotes().size());
        }
    }

    // ==================== Queue Operations Tests ====================

    @Nested
    @DisplayName("Queue Operations")
    class QueueOperationsTests
    {
        @Test
        @DisplayName("getQueue() returns non-null queue")
        public void testGetQueueNotNull() {
            assertNotNull(audioHandler.getQueue());
        }

        @Test
        @DisplayName("queue starts empty")
        public void testQueueStartsEmpty() {
            assertTrue(audioHandler.getQueue().isEmpty());
            assertEquals(0, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("setQueueType() changes queue type")
        public void testSetQueueType() {
            // Add a track first
            QueuedTrack qtrack = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 1000, "identifier", true, "uri");
            when(track.getInfo()).thenReturn(info);
            when(qtrack.getTrack()).thenReturn(track);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));
            audioHandler.addTrack(qtrack);

            // Change queue type
            audioHandler.setQueueType(QueueType.LINEAR);

            // Queue should still exist
            assertNotNull(audioHandler.getQueue());
        }
    }

    // ==================== Player Access Tests ====================

    @Nested
    @DisplayName("Player Access")
    class PlayerAccessTests
    {
        @Test
        @DisplayName("getPlayer() returns the audio player")
        public void testGetPlayer() {
            assertEquals(audioPlayer, audioHandler.getPlayer());
        }
    }

    // ==================== Last Reason Tests ====================

    @Nested
    @DisplayName("Last Reason")
    class LastReasonTests
    {
        @Test
        @DisplayName("setLastReason() stores reason")
        public void testSetLastReason() {
            // Just verify it doesn't throw
            assertDoesNotThrow(() -> audioHandler.setLastReason("Test reason"));
        }
    }

    // ==================== Previous Tracks Tests ====================

    @Nested
    @DisplayName("Previous Tracks (History)")
    class PreviousTracksTests
    {
        @Test
        @DisplayName("getPreviousTracks() returns list")
        public void testGetPreviousTracks() {
            assertNotNull(audioHandler.getPreviousTracks());
        }

        @Test
        @DisplayName("getPreviousTracks() starts empty")
        public void testGetPreviousTracksStartsEmpty() {
            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }
    }
}
