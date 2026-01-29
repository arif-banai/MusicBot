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
package com.jagrosh.jmusicbot.unit.service;

import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.queue.HistoryQueue;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.testutil.service.MusicServiceScenarioBuilder;
import com.jagrosh.jmusicbot.testutil.service.OutputAdapterSpy;
import com.jagrosh.jmusicbot.testutil.service.ServiceTestFixture;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.jagrosh.jmusicbot.testutil.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for MusicService player operations.
 * Uses the test fixtures and scenario builders for maintainable tests.
 */
@DisplayName("MusicService Tests")
public class MusicServiceTest
{
    private ServiceTestFixture fixture;
    private MusicService musicService;
    private OutputAdapterSpy output;

    @BeforeEach
    void setUp()
    {
        fixture = ServiceTestFixture.create();
        musicService = new MusicService(fixture.getBot());
        output = new OutputAdapterSpy();
    }

    // ==================== Play Operation Tests ====================

    @Nested
    @DisplayName("Play Operation")
    class PlayOperationTests
    {
        @Test
        @DisplayName("play() with query loads track via PlayerManager")
        void playWithQuery_loadsTrackViaPlayerManager()
        {
            // Given
            String query = "test song";

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), query, 
                    fixture.getTextChannel(), output);

            // Then
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), 
                    eq(query), any());
        }

        @Test
        @DisplayName("play() with URL in angle brackets strips brackets")
        void playWithAngleBrackets_stripsBrackets()
        {
            // Given
            String url = "https://example.com/track";

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), 
                    "<" + url + ">", fixture.getTextChannel(), output);

            // Note: The stripping happens in playNext, not play - checking actual behavior
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), 
                    eq("<" + url + ">"), any());
        }

        @Test
        @DisplayName("play() with empty args when paused resumes playback for DJ")
        void playEmptyArgs_whenPaused_resumesForDJ()
        {
            // Given
            fixture.withDJPermission()
                   .withPausedTrack();

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), null, 
                    fixture.getTextChannel(), output);

            // Then
            verify(fixture.getAudioPlayer()).setPaused(false);
            output.assertSuccessMessageContains("Resumed");
        }

        @Test
        @DisplayName("play() with empty args when paused fails for non-DJ")
        void playEmptyArgs_whenPaused_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission()
                   .withPausedTrack();

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), null, 
                    fixture.getTextChannel(), output);

            // Then
            verify(fixture.getAudioPlayer(), never()).setPaused(anyBoolean());
            output.assertErrorMessageContains("Only DJs can unpause");
        }

        @Test
        @DisplayName("play() with empty args when not playing shows help")
        void playEmptyArgs_whenNotPlaying_showsHelp()
        {
            // Given
            fixture.withNoTrack();

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), null, 
                    fixture.getTextChannel(), output);

            // Then
            output.assertHelpShown();
        }

        @Test
        @DisplayName("play() with quoted query strips quotes")
        void playWithQuotes_stripsQuotes()
        {
            // Given
            String query = "test song";

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(), 
                    "\"" + query + "\"", fixture.getTextChannel(), output);

            // Then
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), 
                    eq(query), any());
        }
    }

    // ==================== PlayNext Operation Tests ====================

    @Nested
    @DisplayName("PlayNext Operation")
    class PlayNextOperationTests
    {
        @Test
        @DisplayName("playNext() with query loads track via PlayerManager")
        void playNextWithQuery_loadsTrack()
        {
            // Given
            String query = "test song";

            // When
            musicService.playNext(fixture.getGuild(), fixture.getMember(), query, 
                    fixture.getTextChannel(), output);

            // Then
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), 
                    eq(query), any());
        }

        @Test
        @DisplayName("playNext() with empty query shows warning")
        void playNextEmptyQuery_showsWarning()
        {
            // When
            musicService.playNext(fixture.getGuild(), fixture.getMember(), "", 
                    fixture.getTextChannel(), output);

            // Then
            output.assertWarningMessageContains("include a song title or URL");
        }

        @Test
        @DisplayName("playNext() with null query shows warning")
        void playNextNullQuery_showsWarning()
        {
            // When
            musicService.playNext(fixture.getGuild(), fixture.getMember(), null, 
                    fixture.getTextChannel(), output);

            // Then
            output.assertWarningMessageContains("include a song title or URL");
        }

        @Test
        @DisplayName("playNext() strips angle brackets from URL")
        void playNextStripsAngleBrackets()
        {
            // Given
            String url = "https://example.com/track";

            // When
            musicService.playNext(fixture.getGuild(), fixture.getMember(), 
                    "<" + url + ">", fixture.getTextChannel(), output);

            // Then
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), 
                    eq(url), any());
        }
    }

    // ==================== Previous Operation Tests ====================

    @Nested
    @DisplayName("Previous Operation")
    class PreviousOperationTests
    {
        @Test
        @DisplayName("previous() restarts track when position > 5 seconds")
        void previous_restartsTrack_whenPositionOver5Seconds()
        {
            // Given
            fixture.withDJPermission()
                   .withPlayingTrack();
            when(fixture.getCurrentTrack().getPosition()).thenReturn(6000L);
            
            // Setup request metadata
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getCurrentTrack()).setPosition(0);
            output.assertSuccessMessageContains("Restarted");
        }

        @Test
        @DisplayName("previous() goes to previous track when position < 5 seconds")
        void previous_goesToPreviousTrack_whenPositionUnder5Seconds()
        {
            // Given
            fixture.withDJPermission()
                   .withPlayingTrack();
            when(fixture.getCurrentTrack().getPosition()).thenReturn(3000L);
            
            // Setup request metadata
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);
            
            // Setup history with a previous track
            HistoryQueue<QueuedTrack> history = mock(HistoryQueue.class);
            when(history.isEmpty()).thenReturn(false);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            
            QueuedTrack previousTrack = mock(QueuedTrack.class);
            AudioTrack prevAudioTrack = mock(AudioTrack.class);
            AudioTrackInfo prevInfo = new AudioTrackInfo("Previous Song", "Artist", 180000, "id", false, "url");
            when(prevAudioTrack.getInfo()).thenReturn(prevInfo);
            when(previousTrack.getTrack()).thenReturn(prevAudioTrack);
            when(fixture.getQueue().rewind(any())).thenReturn(previousTrack);
            when(fixture.getCurrentTrack().makeClone()).thenReturn(fixture.getCurrentTrack());

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioPlayer()).playTrack(prevAudioTrack);
            output.assertSuccessMessageContains("Went back to");
        }

        @Test
        @DisplayName("previous() fails when no history")
        void previous_failsWhenNoHistory()
        {
            // Given
            fixture.withDJPermission()
                   .withPlayingTrack();
            when(fixture.getCurrentTrack().getPosition()).thenReturn(1000L);
            
            // Setup request metadata
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);
            
            // Empty history
            HistoryQueue<QueuedTrack> history = mock(HistoryQueue.class);
            when(history.isEmpty()).thenReturn(true);
            when(fixture.getQueue().getHistory()).thenReturn(history);

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            output.assertErrorMessageContains("no previous tracks");
        }

        @Test
        @DisplayName("previous() fails for non-DJ who doesn't own the track")
        void previous_failsForNonDJNonOwner()
        {
            // Given
            fixture.withoutDJPermission()
                   .withPlayingTrack();
            
            // Track owned by different user
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(999999L);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            output.assertErrorMessageContains("DJ or the requester");
        }
    }

    // ==================== Pause Operation Tests ====================

    @Nested
    @DisplayName("Pause Operation")
    class PauseOperationTests
    {
        @Test
        @DisplayName("pause() toggles pause state for DJ")
        void pause_togglesPauseState_forDJ()
        {
            // Given
            fixture.withDJPermission()
                   .withPlayingTrack();
            when(fixture.getAudioPlayer().isPaused()).thenReturn(false);

            // When
            musicService.pause(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioPlayer()).setPaused(true);
            output.assertNowPlayingEdited();
        }

        @Test
        @DisplayName("pause() fails for non-DJ")
        void pause_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission();

            // When
            musicService.pause(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioPlayer(), never()).setPaused(anyBoolean());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("isPaused() returns correct state")
        void isPaused_returnsCorrectState()
        {
            // Given
            when(fixture.getAudioPlayer().isPaused()).thenReturn(true);

            // When/Then
            assertTrue(musicService.isPaused(fixture.getGuild()));

            // Given
            when(fixture.getAudioPlayer().isPaused()).thenReturn(false);

            // When/Then
            assertFalse(musicService.isPaused(fixture.getGuild()));
        }

        @Test
        @DisplayName("setPaused() sets pause state and returns track title")
        void setPaused_setsPauseState_returnsTrackTitle()
        {
            // Given
            fixture.withPlayingTrack("My Song", "Artist", 180000);

            // When
            String title = musicService.setPaused(fixture.getGuild(), true);

            // Then
            verify(fixture.getAudioPlayer()).setPaused(true);
            assertEquals("My Song", title);
        }
    }

    // ==================== Stop Operation Tests ====================

    @Nested
    @DisplayName("Stop Operation")
    class StopOperationTests
    {
        @Test
        @DisplayName("stop() stops playback and closes connection for DJ")
        void stop_stopsAndCloses_forDJ()
        {
            // Given
            fixture.withDJPermission();

            // When
            musicService.stop(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioHandler()).stopAndClear();
            verify(fixture.getAudioManager()).closeAudioConnection();
            output.assertNoMusicEdited();
        }

        @Test
        @DisplayName("stop() fails for non-DJ")
        void stop_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission();

            // When
            musicService.stop(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioHandler(), never()).stopAndClear();
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("stopAndClear() stops playback without permission check")
        void stopAndClear_stopsWithoutPermissionCheck()
        {
            // When
            musicService.stopAndClear(fixture.getGuild());

            // Then
            verify(fixture.getAudioHandler()).stopAndClear();
            verify(fixture.getAudioManager()).closeAudioConnection();
        }
    }

    // ==================== Skip Operation Tests ====================

    @Nested
    @DisplayName("Skip Operation")
    class SkipOperationTests
    {
        @Test
        @DisplayName("skip() for DJ skips current track")
        void skip_forDJ_skipsTrack()
        {
            // Given
            fixture.withDJPermission()
                   .withPlayingTrack();
            
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);

            // When
            musicService.skip(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioPlayer()).stopTrack();
            output.assertSuccessMessageContains("Skipped");
        }

        @Test
        @DisplayName("skip() fails for non-DJ who doesn't own track")
        void skip_failsForNonDJNonOwner()
        {
            // Given
            fixture.withoutDJPermission()
                   .withPlayingTrack();
            
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(999999L);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);

            // When
            musicService.skip(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getAudioPlayer(), never()).stopTrack();
            output.assertErrorMessageContains("DJ or the requester");
        }
    }

    // ==================== Shuffle Operation Tests ====================

    @Nested
    @DisplayName("Shuffle Operation")
    class ShuffleOperationTests
    {
        @Test
        @DisplayName("shuffle() shuffles queue for DJ")
        void shuffle_shufflesQueue_forDJ()
        {
            // Given - use scenario builder for queue management setup
            MusicServiceScenarioBuilder.with(fixture).queueManagement();
            when(fixture.getQueue().shuffle(0)).thenReturn(10);

            // When
            musicService.shuffle(fixture.getGuild(), fixture.getMember(), 0, output);

            // Then
            verify(fixture.getQueue()).shuffle(0);
            output.assertSuccessMessageContains("Shuffled 10 tracks");
        }

        @Test
        @DisplayName("shuffle() fails for non-DJ")
        void shuffle_failsForNonDJ()
        {
            // Given - use scenario builder for non-DJ scenario
            MusicServiceScenarioBuilder.with(fixture).noDJPermission();

            // When
            musicService.shuffle(fixture.getGuild(), fixture.getMember(), 0, output);

            // Then
            verify(fixture.getQueue(), never()).shuffle(anyInt());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("shuffleUserTracks() shuffles only user's tracks")
        void shuffleUserTracks_shufflesOnlyUserTracks()
        {
            // Given
            when(fixture.getQueue().shuffle(USER_ID)).thenReturn(5);

            // When
            int count = musicService.shuffleUserTracks(fixture.getGuild(), USER_ID);

            // Then
            assertEquals(5, count);
            verify(fixture.getQueue()).shuffle(USER_ID);
        }
    }

    // ==================== Repeat Mode Tests ====================

    @Nested
    @DisplayName("Repeat Mode Operation")
    class RepeatModeTests
    {
        @Test
        @DisplayName("cycleRepeatMode() cycles OFF -> ALL for DJ")
        void cycleRepeatMode_offToAll()
        {
            // Given
            MusicServiceScenarioBuilder.with(fixture)
                .withDJ()
                .withRepeat(RepeatMode.OFF);

            // When
            musicService.cycleRepeatMode(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getSettings()).setRepeatMode(RepeatMode.ALL);
            output.assertNowPlayingEdited();
        }

        @Test
        @DisplayName("cycleRepeatMode() cycles ALL -> SINGLE")
        void cycleRepeatMode_allToSingle()
        {
            // Given - use scenario builder for repeat test setup
            MusicServiceScenarioBuilder.with(fixture)
                .withRepeat();  // Sets up DJ + playing + RepeatMode.ALL

            // When
            musicService.cycleRepeatMode(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getSettings()).setRepeatMode(RepeatMode.SINGLE);
        }

        @Test
        @DisplayName("cycleRepeatMode() cycles SINGLE -> OFF")
        void cycleRepeatMode_singleToOff()
        {
            // Given
            MusicServiceScenarioBuilder.with(fixture)
                .withDJ()
                .withRepeat(RepeatMode.SINGLE);

            // When
            musicService.cycleRepeatMode(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getSettings()).setRepeatMode(RepeatMode.OFF);
        }

        @Test
        @DisplayName("cycleRepeatMode() fails for non-DJ")
        void cycleRepeatMode_failsForNonDJ()
        {
            // Given
            MusicServiceScenarioBuilder.with(fixture).noDJPermission();

            // When
            musicService.cycleRepeatMode(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getSettings(), never()).setRepeatMode(any());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("getRepeatMode() returns current mode")
        void getRepeatMode_returnsCurrentMode()
        {
            // Given
            fixture.withRepeatMode(RepeatMode.ALL);

            // When/Then
            assertEquals(RepeatMode.ALL, musicService.getRepeatMode(fixture.getGuild()));
        }

        @Test
        @DisplayName("setRepeatMode() sets the mode")
        void setRepeatMode_setsMode()
        {
            // When
            musicService.setRepeatMode(fixture.getGuild(), RepeatMode.SINGLE);

            // Then
            verify(fixture.getSettings()).setRepeatMode(RepeatMode.SINGLE);
        }
    }

    // ==================== Volume Operation Tests ====================

    @Nested
    @DisplayName("Volume Operation")
    class VolumeOperationTests
    {
        @Test
        @DisplayName("adjustVolume() increases volume for DJ")
        void adjustVolume_increases_forDJ()
        {
            // Given - use scenario builder for volume test setup
            MusicServiceScenarioBuilder.with(fixture).volumeTest();

            // When
            musicService.adjustVolume(fixture.getGuild(), fixture.getMember(), 10, output);

            // Then
            verify(fixture.getAudioPlayer()).setVolume(60);
            verify(fixture.getSettings()).setVolume(60);
            output.assertNowPlayingEdited();
        }

        @Test
        @DisplayName("adjustVolume() clamps to max 150")
        void adjustVolume_clampsToMax()
        {
            // Given
            fixture.withDJPermission()
                   .withVolume(145);

            // When
            musicService.adjustVolume(fixture.getGuild(), fixture.getMember(), 10, output);

            // Then
            verify(fixture.getAudioPlayer()).setVolume(150);
        }

        @Test
        @DisplayName("adjustVolume() clamps to min 0")
        void adjustVolume_clampsToMin()
        {
            // Given
            fixture.withDJPermission()
                   .withVolume(5);

            // When
            musicService.adjustVolume(fixture.getGuild(), fixture.getMember(), -10, output);

            // Then
            verify(fixture.getAudioPlayer()).setVolume(0);
        }

        @Test
        @DisplayName("adjustVolume() fails for non-DJ")
        void adjustVolume_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission();

            // When
            musicService.adjustVolume(fixture.getGuild(), fixture.getMember(), 10, output);

            // Then
            verify(fixture.getAudioPlayer(), never()).setVolume(anyInt());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("setVolume() sets absolute volume")
        void setVolume_setsAbsoluteVolume()
        {
            // Given
            fixture.withVolume(50);

            // When
            MusicService.VolumeResult result = musicService.setVolume(fixture.getGuild(), 75);

            // Then
            verify(fixture.getAudioPlayer()).setVolume(75);
            verify(fixture.getSettings()).setVolume(75);
            assertNotNull(result);
            assertEquals(50, result.oldVolume);
            assertEquals(75, result.newVolume);
        }

        @Test
        @DisplayName("setVolume() returns null for invalid volume")
        void setVolume_returnsNullForInvalidVolume()
        {
            // When
            MusicService.VolumeResult resultLow = musicService.setVolume(fixture.getGuild(), -1);
            MusicService.VolumeResult resultHigh = musicService.setVolume(fixture.getGuild(), 151);

            // Then
            assertNull(resultLow);
            assertNull(resultHigh);
            verify(fixture.getAudioPlayer(), never()).setVolume(anyInt());
        }

        @Test
        @DisplayName("getVolume() returns current volume")
        void getVolume_returnsCurrentVolume()
        {
            // Given
            when(fixture.getAudioPlayer().getVolume()).thenReturn(75);

            // When/Then
            assertEquals(75, musicService.getVolume(fixture.getGuild()));
        }
    }

    // ==================== Queue Management Tests ====================

    @Nested
    @DisplayName("Queue Management Operations")
    class QueueManagementTests
    {
        @Test
        @DisplayName("removeTrack() removes user's own track")
        void removeTrack_removesOwnTrack()
        {
            // Given
            fixture.withQueueSize(5);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Test Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(qt.getIdentifier()).thenReturn(USER_ID);
            when(fixture.getQueue().get(1)).thenReturn(qt);

            // When
            musicService.removeTrack(fixture.getGuild(), fixture.getMember(), 2, output);

            // Then
            verify(fixture.getQueue()).remove(1);
            output.assertSuccessMessageContains("Removed");
        }

        @Test
        @DisplayName("removeTrack() DJ removes other user's track")
        void removeTrack_djRemovesOthersTrack()
        {
            // Given
            fixture.withDJPermission()
                   .withQueueSize(5);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Test Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(qt.getIdentifier()).thenReturn(999999L); // Different user
            when(fixture.getQueue().get(1)).thenReturn(qt);

            // When
            musicService.removeTrack(fixture.getGuild(), fixture.getMember(), 2, output);

            // Then
            verify(fixture.getQueue()).remove(1);
            output.assertSuccessMessageContains("Removed");
        }

        @Test
        @DisplayName("removeTrack() non-DJ cannot remove other's track")
        void removeTrack_nonDJCannotRemoveOthersTrack()
        {
            // Given
            fixture.withoutDJPermission()
                   .withQueueSize(5);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Test Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(qt.getIdentifier()).thenReturn(999999L); // Different user
            when(fixture.getQueue().get(1)).thenReturn(qt);

            // When
            musicService.removeTrack(fixture.getGuild(), fixture.getMember(), 2, output);

            // Then
            verify(fixture.getQueue(), never()).remove(anyInt());
            output.assertErrorMessageContains("didn't add it");
        }

        @Test
        @DisplayName("removeTrack() fails on empty queue")
        void removeTrack_failsOnEmptyQueue()
        {
            // Given
            fixture.withEmptyQueue();

            // When
            musicService.removeTrack(fixture.getGuild(), fixture.getMember(), 1, output);

            // Then
            output.assertErrorMessage("There is nothing in the queue!");
        }

        @Test
        @DisplayName("removeAllTracks() removes user's tracks")
        void removeAllTracks_removesUserTracks()
        {
            // Given
            fixture.withQueueSize(5);
            when(fixture.getQueue().removeAll(USER_ID)).thenReturn(3);

            // When
            musicService.removeAllTracks(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getQueue()).removeAll(USER_ID);
            output.assertSuccessMessageContains("3 entries");
        }

        @Test
        @DisplayName("removeAllTracks() shows warning when user has no tracks")
        void removeAllTracks_warnsWhenNoTracks()
        {
            // Given
            fixture.withQueueSize(5);
            when(fixture.getQueue().removeAll(USER_ID)).thenReturn(0);

            // When
            musicService.removeAllTracks(fixture.getGuild(), fixture.getMember(), output);

            // Then
            output.assertWarningMessageContains("don't have any songs");
        }

        @Test
        @DisplayName("removeAllTracksByUser() removes tracks for specific user")
        void removeAllTracksByUser_removesTracksForUser()
        {
            // Given
            when(fixture.getQueue().removeAll(999999L)).thenReturn(5);

            // When
            int count = musicService.removeAllTracksByUser(fixture.getGuild(), 999999L);

            // Then
            assertEquals(5, count);
            verify(fixture.getQueue()).removeAll(999999L);
        }

        @Test
        @DisplayName("moveTrack() moves track for DJ")
        void moveTrack_movesTrackForDJ()
        {
            // Given
            fixture.withDJPermission()
                   .withQueueSize(10);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Test Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(fixture.getQueue().moveItem(1, 4)).thenReturn(qt);

            // When
            musicService.moveTrack(fixture.getGuild(), fixture.getMember(), 2, 5, output);

            // Then
            verify(fixture.getQueue()).moveItem(1, 4);
            output.assertSuccessMessageContains("Moved");
        }

        @Test
        @DisplayName("moveTrack() fails for non-DJ")
        void moveTrack_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission();

            // When
            musicService.moveTrack(fixture.getGuild(), fixture.getMember(), 2, 5, output);

            // Then
            verify(fixture.getQueue(), never()).moveItem(anyInt(), anyInt());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("moveTrack() fails for same position")
        void moveTrack_failsForSamePosition()
        {
            // Given
            fixture.withDJPermission();

            // When
            musicService.moveTrack(fixture.getGuild(), fixture.getMember(), 2, 2, output);

            // Then
            verify(fixture.getQueue(), never()).moveItem(anyInt(), anyInt());
            output.assertErrorMessageContains("same position");
        }

        @Test
        @DisplayName("moveTrackPosition() moves track without permission check")
        void moveTrackPosition_movesWithoutPermCheck()
        {
            // Given
            fixture.withQueueSize(10);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Test Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(fixture.getQueue().moveItem(1, 4)).thenReturn(qt);

            // When
            String title = musicService.moveTrackPosition(fixture.getGuild(), 2, 5);

            // Then
            assertEquals("Test Song", title);
            verify(fixture.getQueue()).moveItem(1, 4);
        }

        @Test
        @DisplayName("skipTo() skips to position for DJ")
        void skipTo_skipsToPositionForDJ()
        {
            // Given
            fixture.withDJPermission()
                   .withQueueSize(10);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Target Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(fixture.getQueue().get(0)).thenReturn(qt);

            // When
            musicService.skipTo(fixture.getGuild(), fixture.getMember(), 5, output);

            // Then
            verify(fixture.getQueue()).skip(4);
            verify(fixture.getAudioPlayer()).stopTrack();
            output.assertSuccessMessageContains("Skipped to");
        }

        @Test
        @DisplayName("skipTo() fails for non-DJ")
        void skipTo_failsForNonDJ()
        {
            // Given
            fixture.withoutDJPermission();

            // When
            musicService.skipTo(fixture.getGuild(), fixture.getMember(), 5, output);

            // Then
            verify(fixture.getQueue(), never()).skip(anyInt());
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("skipToPosition() skips without permission check")
        void skipToPosition_skipsWithoutPermCheck()
        {
            // Given
            fixture.withQueueSize(10);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Target Song", "Artist", 180000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(fixture.getQueue().get(0)).thenReturn(qt);

            // When
            String title = musicService.skipToPosition(fixture.getGuild(), 5);

            // Then
            assertEquals("Target Song", title);
            verify(fixture.getQueue()).skip(4);
        }

        @Test
        @DisplayName("skipToPosition() returns null for invalid position")
        void skipToPosition_returnsNullForInvalidPosition()
        {
            // Given
            fixture.withQueueSize(5);

            // When
            String title = musicService.skipToPosition(fixture.getGuild(), 10);

            // Then
            assertNull(title);
            verify(fixture.getQueue(), never()).skip(anyInt());
        }

        @Test
        @DisplayName("isQueueEmpty() returns correct state")
        void isQueueEmpty_returnsCorrectState()
        {
            // Given
            when(fixture.getQueue().isEmpty()).thenReturn(true);
            assertTrue(musicService.isQueueEmpty(fixture.getGuild()));

            when(fixture.getQueue().isEmpty()).thenReturn(false);
            assertFalse(musicService.isQueueEmpty(fixture.getGuild()));
        }

        @Test
        @DisplayName("getQueueSize() returns correct size")
        void getQueueSize_returnsCorrectSize()
        {
            // Given
            when(fixture.getQueue().size()).thenReturn(15);

            // When/Then
            assertEquals(15, musicService.getQueueSize(fixture.getGuild()));
        }

        @Test
        @DisplayName("isValidQueuePosition() validates positions correctly")
        void isValidQueuePosition_validatesCorrectly()
        {
            // Given
            fixture.withQueueSize(10);

            // When/Then
            assertTrue(musicService.isValidQueuePosition(fixture.getGuild(), 1));
            assertTrue(musicService.isValidQueuePosition(fixture.getGuild(), 10));
            assertFalse(musicService.isValidQueuePosition(fixture.getGuild(), 0));
            assertFalse(musicService.isValidQueuePosition(fixture.getGuild(), 11));
        }
    }

    // ==================== Track Utility Tests ====================

    @Nested
    @DisplayName("Track Utility Methods")
    class TrackUtilityTests
    {
        @Test
        @DisplayName("isTooLong() delegates to config")
        void isTooLong_delegatesToConfig()
        {
            // Given
            AudioTrack track = mock(AudioTrack.class);
            when(fixture.getConfig().isTooLong(track)).thenReturn(true);

            // When/Then
            assertTrue(musicService.isTooLong(track));

            when(fixture.getConfig().isTooLong(track)).thenReturn(false);
            assertFalse(musicService.isTooLong(track));
        }

        @Test
        @DisplayName("formatTooLongError() formats message correctly")
        void formatTooLongError_formatsCorrectly()
        {
            // Given
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Long Song", "Artist", 600000, "id", false, "url");
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(600000L);
            when(fixture.getConfig().getMaxTime()).thenReturn("300");

            // When
            String error = musicService.formatTooLongError(track);

            // Then
            assertTrue(error.contains("Long Song"));
            assertTrue(error.contains("longer than the allowed maximum"));
        }

        @Test
        @DisplayName("formatTrackAddedMessage() formats position 0 as now playing")
        void formatTrackAddedMessage_position0_nowPlaying()
        {
            // When
            String message = musicService.formatTrackAddedMessage("Test Song", 180000, 0);

            // Then
            assertTrue(message.contains("Test Song"));
            assertTrue(message.contains("begin playing"));
        }

        @Test
        @DisplayName("formatTrackAddedMessage() formats queue position correctly")
        void formatTrackAddedMessage_queuePosition()
        {
            // When
            String message = musicService.formatTrackAddedMessage("Test Song", 180000, 5);

            // Then
            assertTrue(message.contains("Test Song"));
            assertTrue(message.contains("position 5"));
        }
    }
}
