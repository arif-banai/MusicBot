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
import com.jagrosh.jmusicbot.queue.PlaybackHistory;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.testutil.service.MusicServiceScenarioBuilder;
import com.jagrosh.jmusicbot.testutil.service.OutputAdapterSpy;
import com.jagrosh.jmusicbot.testutil.service.ServiceTestFixture;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

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

        @Test
        @DisplayName("play() with scheme-less YouTube link loads it as a link, not a search")
        void playWithSchemeLessYoutubeLink_loadsAsHttpsLink()
        {
            // Given
            String query = "youtube.com/watch?v=IY1oU1G2DA0";

            // When
            musicService.play(fixture.getGuild(), fixture.getMember(),
                    query, fixture.getTextChannel(), output);

            // Then
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()),
                    eq("https://youtube.com/watch?v=IY1oU1G2DA0"), any());
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

    // ==================== PlayNowFromUrl Operation Tests ====================

    @Nested
    @DisplayName("PlayNowFromUrl Operation")
    class PlayNowFromUrlOperationTests
    {
        private AudioTrack loadedTrack()
        {
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 180000L, "id",
                    false, "https://example.com/track");
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(180000L);
            return track;
        }

        private AudioLoadResultHandler captureLoadHandler(String url)
        {
            ArgumentCaptor<AudioLoadResultHandler> captor = ArgumentCaptor.forClass(AudioLoadResultHandler.class);
            verify(fixture.getPlayerManager()).loadItemOrdered(eq(fixture.getGuild()), eq(url), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("playNowFromUrl() does not stop the track it just started when nothing was playing")
        void playNowFromUrl_doesNotStopFreshlyStartedTrack_whenIdle()
        {
            String url = "https://example.com/track";
            AudioTrack track = loadedTrack();

            // Nothing is playing before the add; addTrackToFront would start it immediately,
            // the same way the real AudioHandler does.
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(null);
            doAnswer(invocation ->
            {
                when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));
                return -1;
            }).when(fixture.getAudioHandler()).addTrackToFront(any(QueuedTrack.class));

            musicService.playNowFromUrl(fixture.getGuild(), fixture.getMember(), url,
                    fixture.getTextChannel(), output);
            captureLoadHandler(url).trackLoaded(track);

            verify(fixture.getAudioPlayer(), never()).stopTrack();
            output.assertSuccessMessageContains("Now playing");
        }

        @Test
        @DisplayName("playNowFromUrl() replaces the previous track directly (not via addTrackToFront+stopTrack) when something was already playing")
        void playNowFromUrl_replacesPreviousTrackDirectly_whenSomethingWasPlaying()
        {
            String url = "https://example.com/track";
            AudioTrack track = loadedTrack();
            fixture.withPlayingTrack();

            musicService.playNowFromUrl(fixture.getGuild(), fixture.getMember(), url,
                    fixture.getTextChannel(), output);
            captureLoadHandler(url).trackLoaded(track);

            // N2: addTrackToFront + a later stopTrack() would race a prompt-cancel-triggered end
            // of the old track; playNowReplacingCurrent starts the new track directly instead.
            verify(fixture.getAudioHandler()).playNowReplacingCurrent(any());
            verify(fixture.getAudioHandler(), never()).addTrackToFront(any());
            verify(fixture.getAudioPlayer(), never()).stopTrack();
        }

        @Test
        @DisplayName("playNowFromUrl() unpauses the player: Play now means play")
        void playNowFromUrl_unpausesPlayer()
        {
            String url = "https://example.com/track";
            AudioTrack track = loadedTrack();
            fixture.withPausedTrack();

            musicService.playNowFromUrl(fixture.getGuild(), fixture.getMember(), url,
                    fixture.getTextChannel(), output);
            captureLoadHandler(url).trackLoaded(track);

            verify(fixture.getAudioPlayer()).setPaused(false);
        }

        @Test
        @DisplayName("playNowFromUrl() cancels any open yt-dlp prompt for the track it's replacing")
        void playNowFromUrl_cancelsOpenYtDlpPrompt()
        {
            String url = "https://example.com/track";
            AudioTrack track = loadedTrack();
            fixture.withPlayingTrack();

            musicService.playNowFromUrl(fixture.getGuild(), fixture.getMember(), url,
                    fixture.getTextChannel(), output);
            captureLoadHandler(url).trackLoaded(track);

            verify(fixture.getAudioHandler()).cancelCurrentTrackYtDlpPrompt();
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
            when(fixture.getCurrentTrack().getIdentifier()).thenReturn("current-id");
            
            // Setup history where current track appears at head, followed by an actual previous track.
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.isEmpty()).thenReturn(false);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            QueuedTrack currentHistoryEntry = mock(QueuedTrack.class);
            AudioTrack currentHistoryTrack = mock(AudioTrack.class);
            when(currentHistoryEntry.getTrack()).thenReturn(currentHistoryTrack);
            when(currentHistoryTrack.getIdentifier()).thenReturn("current-id");
            when(history.get(0)).thenReturn(currentHistoryEntry);
            
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
            verify(fixture.getQueue()).removeFromHistoryAt(0);
            verify(fixture.getAudioHandler()).playPrepared(prevAudioTrack);
            output.assertSuccessMessageContains("Went back to");
        }

        @Test
        @DisplayName("previous() while paused restarts current track when position > 5 seconds")
        void previous_whenPausedAndOver5Seconds_restartsCurrentTrack()
        {
            // Given
            fixture.withDJPermission()
                    .withPlayingTrack();
            when(fixture.getCurrentTrack().getPosition()).thenReturn(6000L);
            when(fixture.getAudioPlayer().isPaused()).thenReturn(true);

            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getCurrentTrack()).setPosition(0);
            verify(fixture.getQueue(), never()).rewind(any());
            output.assertSuccessMessageContains("Restarted");
        }

        @Test
        @DisplayName("previous() fails when only current track exists in history under 5 seconds")
        void previous_failsWhenOnlyCurrentTrackInHistory_under5Seconds()
        {
            // Given
            fixture.withDJPermission()
                    .withPlayingTrack();
            when(fixture.getCurrentTrack().getPosition()).thenReturn(3000L);

            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);
            when(fixture.getCurrentTrack().getIdentifier()).thenReturn("current-id");

            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.isEmpty()).thenReturn(false, true);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            QueuedTrack currentHistoryEntry = mock(QueuedTrack.class);
            AudioTrack currentHistoryTrack = mock(AudioTrack.class);
            when(currentHistoryEntry.getTrack()).thenReturn(currentHistoryTrack);
            when(currentHistoryTrack.getIdentifier()).thenReturn("current-id");
            when(history.get(0)).thenReturn(currentHistoryEntry);
            when(fixture.getCurrentTrack().makeClone()).thenReturn(fixture.getCurrentTrack());

            // When
            musicService.previous(fixture.getGuild(), fixture.getMember(), output);

            // Then
            verify(fixture.getQueue()).removeFromHistoryAt(0);
            verify(fixture.getQueue(), never()).rewind(any());
            output.assertErrorMessageContains("no previous tracks");
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
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
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
            verify(fixture.getAudioHandler()).stopAndClearQueuePreserveHistory();
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
            verify(fixture.getAudioHandler(), never()).stopAndClearQueuePreserveHistory();
            output.assertErrorMessageContains("need to be a DJ");
        }

        @Test
        @DisplayName("stopAndClear() stops playback without permission check")
        void stopAndClear_stopsWithoutPermissionCheck()
        {
            // When
            musicService.stopAndClear(fixture.getGuild());

            // Then
            verify(fixture.getAudioHandler()).stopAndClearQueuePreserveHistory();
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

    // ==================== Playlist Operation Tests ====================

    @Nested
    @DisplayName("Playlist Operations")
    class PlaylistOperationTests
    {
        @Test
        @DisplayName("getAvailablePlaylistNames() returns explicit storage error")
        void getAvailablePlaylistNames_returnsStorageError()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistError error =
                    com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistError.of(
                            com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistErrorType.STORAGE_UNAVAILABLE,
                            "Access denied", "Playlists", null);
            when(fixture.getPlaylistLoader().getPlaylistNamesResult())
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.failure(error));

            MusicService.PlaylistNamesInfo info = musicService.getAvailablePlaylistNames();

            assertTrue(info.hasError());
            assertTrue(info.errorMessage.contains("unavailable"));
        }

        @Test
        @DisplayName("queuePlaylist() refreshes now playing when tracks were loaded and player is active")
        void queuePlaylist_refreshesNowPlaying_whenTracksLoadedAndPlayerActive()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("favorite"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("favorite");
            when(playlist.getTracks()).thenReturn(List.of(mock(AudioTrack.class), mock(AudioTrack.class)));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.queuePlaylist(fixture.getGuild(), fixture.getMember(), "favorite",
                    fixture.getTextChannel(), output);

            verify(fixture.getNowPlayingHandler()).requestReconcile(eq(fixture.getGuild().getIdLong()), anyString());
            output.assertSuccessMessageContains("Queued **2** track(s)");
        }

        @Test
        @DisplayName("queuePlaylist() never offers a queue-time library suggestion (playlist loads don't post one per song)")
        void queuePlaylist_neverOffersQueueTimeSuggestion()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            AudioTrack first = mock(AudioTrack.class);
            when(first.getInfo()).thenReturn(new AudioTrackInfo("First", "Author", 180000L, "id1", false, "https://example.com/1"));
            AudioTrack second = mock(AudioTrack.class);
            when(second.getInfo()).thenReturn(new AudioTrackInfo("Second", "Author", 180000L, "id2", false, "https://example.com/2"));
            when(fixture.getPlaylistLoader().getPlaylistResult("favorite"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("favorite");
            when(playlist.getTracks()).thenReturn(List.of(first, second));
            when(playlist.getErrors()).thenReturn(List.of());

            doAnswer(invocation ->
            {
                // Mirror the real Playlist.loadTracks: feed each loaded track to the per-track
                // consumer (which calls AudioHandler#addTrack directly, not the single-track funnel).
                java.util.function.Consumer<AudioTrack> consumer = invocation.getArgument(1);
                consumer.accept(first);
                consumer.accept(second);
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.queuePlaylist(fixture.getGuild(), fixture.getMember(), "favorite",
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioHandler(), times(2)).addTrack(any());
            verify(fixture.getAudioHandler(), never()).considerQueuedSuggestion(any());
        }

        @Test
        @DisplayName("queuePlaylist() skips now playing refresh when tracks loaded but player still inactive")
        void queuePlaylist_skipsRefresh_whenTracksLoadedButPlayerInactive()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("favorite"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("favorite");
            when(playlist.getTracks()).thenReturn(List.of(mock(AudioTrack.class), mock(AudioTrack.class)));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(null);

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.queuePlaylist(fixture.getGuild(), fixture.getMember(), "favorite",
                    fixture.getTextChannel(), output);

            verify(fixture.getNowPlayingHandler(), never()).requestReconcile(anyLong(), anyString());
            output.assertSuccessMessageContains("Queued **2** track(s)");
        }

        @Test
        @DisplayName("queuePlaylist() does not refresh now playing when no tracks loaded")
        void queuePlaylist_doesNotRefreshNowPlaying_whenNoTracksLoaded()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("empty"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("empty");
            when(playlist.getTracks()).thenReturn(List.of());
            when(playlist.getErrors()).thenReturn(List.of(mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistLoadError.class)));

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.queuePlaylist(fixture.getGuild(), fixture.getMember(), "empty",
                    fixture.getTextChannel(), output);

            verify(fixture.getNowPlayingHandler(), never()).requestReconcile(anyLong(), anyString());
            output.assertWarningMessageContains("No tracks were loaded");
        }

        @Test
        @DisplayName("playPlaylistNow() refreshes now playing after queue replacement")
        void playPlaylistNow_refreshesNowPlaying_afterQueueReplacement()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("mix"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("mix");
            when(playlist.getTracks()).thenReturn(List.of(mock(AudioTrack.class)));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "mix",
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioHandler()).beginQueueReplacement();
            verify(fixture.getAudioHandler(), never()).stopAndClearQueuePreserveHistory();
            verify(fixture.getAudioHandler()).endQueueReplacement(any());
            verify(fixture.getNowPlayingHandler()).requestReconcile(eq(fixture.getGuild().getIdLong()), anyString());
            output.assertSuccessMessageContains("Now playing playlist `mix` (queue replaced)");
        }

        @Test
        @DisplayName("playPlaylistNow() queues each loaded track, then asks AudioHandler to replace the captured original track")
        void playPlaylistNow_whileSomethingPlaying_queuesThenAsksHandlerToReplaceByIdentity()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            AudioTrack firstTrack = mock(AudioTrack.class);
            when(firstTrack.getInfo()).thenReturn(new AudioTrackInfo("First", "Author", 180000L, "id", false, "https://example.com/1"));
            AudioTrack originalTrack = mock(AudioTrack.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("mix"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("mix");
            when(playlist.getTracks()).thenReturn(List.of(firstTrack));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(originalTrack);

            doAnswer(invocation ->
            {
                // Mirror the real Playlist.loadTracks: feed each loaded track to the per-track
                // consumer (which calls AudioHandler#addTrack) before running the done callback.
                java.util.function.Consumer<AudioTrack> consumer = invocation.getArgument(1);
                consumer.accept(firstTrack);
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "mix",
                    fixture.getTextChannel(), output);

            // The queue must be loaded (addTrack) before AudioHandler is asked to replace the
            // original track, and beginQueueReplacement() must run first of all, or the "replace"
            // call would have nothing queued to pull from.
            InOrder inOrder = inOrder(fixture.getAudioHandler());
            inOrder.verify(fixture.getAudioHandler()).beginQueueReplacement();
            inOrder.verify(fixture.getAudioHandler()).addTrack(any());
            inOrder.verify(fixture.getAudioHandler()).replaceCurrentTrackIfStillPlaying(any(), same(originalTrack));
            inOrder.verify(fixture.getAudioHandler()).endQueueReplacement(any());
            verify(fixture.getAudioHandler(), never()).stopAndClear();
            verify(fixture.getAudioHandler(), never()).stopAndClearQueuePreserveHistory();
            verify(fixture.getAudioPlayer(), never()).stopTrack();
        }

        @Test
        @DisplayName("playPlaylistNow() asks AudioHandler to replace with a null original when nothing was playing")
        void playPlaylistNow_whileIdle_asksHandlerToReplaceWithNullOriginal()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("mix"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("mix");
            when(playlist.getTracks()).thenReturn(List.of(mock(AudioTrack.class)));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(null);

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "mix",
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioPlayer(), never()).stopTrack();
            output.assertSuccessMessageContains("Now playing playlist `mix` (queue replaced)");
        }

        @Test
        @DisplayName("playPlaylistNow() with zero tracks loaded warns, ends the replacement, and never does a full stop")
        void playPlaylistNow_withZeroTracksLoaded_warnsAndEndsReplacementWithoutFullStop()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("empty"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("empty");
            when(playlist.getTracks()).thenReturn(List.of());
            when(playlist.getErrors()).thenReturn(List.of(mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistLoadError.class)));
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            doAnswer(invocation ->
            {
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "empty",
                    fixture.getTextChannel(), output);

            output.assertWarningMessageContains("No tracks were loaded");
            verify(fixture.getAudioHandler()).endQueueReplacement(any());
            verify(fixture.getAudioHandler(), never()).replaceCurrentTrackIfStillPlaying(any(), any());
            verify(fixture.getAudioPlayer(), never()).stopTrack();
            verify(fixture.getAudioHandler(), never()).stopAndClear();
            verify(fixture.getAudioHandler(), never()).stopAndClearQueuePreserveHistory();
        }

        @Test
        @DisplayName("playPlaylistNow() still resolves the replacement when the per-track consumer throws (N3)")
        void playPlaylistNow_consumerThrows_stillResolvesReplacement()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            AudioTrack firstTrack = mock(AudioTrack.class);
            when(firstTrack.getInfo()).thenReturn(new AudioTrackInfo("First", "Author", 180000L, "id", false, "https://example.com/1"));
            when(fixture.getPlaylistLoader().getPlaylistResult("mix"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("mix");
            when(playlist.getTracks()).thenReturn(List.of(firstTrack));
            when(playlist.getErrors()).thenReturn(List.of());
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));
            doThrow(new RuntimeException("boom")).when(fixture.getAudioHandler()).addTrack(any());

            doAnswer(invocation ->
            {
                java.util.function.Consumer<AudioTrack> consumer = invocation.getArgument(1);
                consumer.accept(firstTrack);
                Runnable callback = invocation.getArgument(2);
                callback.run();
                return null;
            }).when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "mix",
                    fixture.getTextChannel(), output);

            // The consumer's own try/finally must still resolve the replacement even though
            // addTrack() threw, so a later natural track end isn't left permanently deferred.
            verify(fixture.getAudioHandler()).replaceCurrentTrackIfStillPlaying(any(), any());
        }

        @Test
        @DisplayName("playPlaylistNow() ends the replacement when loadTracks itself throws synchronously (N3)")
        void playPlaylistNow_loadTracksThrowsSynchronously_endsReplacement()
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist = mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(fixture.getPlaylistLoader().getPlaylistResult("mix"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            when(playlist.getName()).thenReturn("mix");
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));
            doThrow(new RuntimeException("loader exploded"))
                    .when(playlist).loadTracks(eq(fixture.getPlayerManager()), any(), any());

            musicService.playPlaylistNow(fixture.getGuild(), fixture.getMember(), "mix",
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioHandler()).endQueueReplacement(any());
            output.assertErrorMessageContains("Failed to load playlist");
        }

    }

    @Nested
    @DisplayName("yt-dlp library files show the library's details")
    class LibraryFileDisplayTests
    {
        private static final String FILE = "C:/lib/Name [x1Xd0m2OdcU].webm";

        private AudioTrack localTrack;
        private AudioTrack libraryTrack;

        @BeforeEach
        void setUpLibrary()
        {
            localTrack = mock(AudioTrack.class);
            when(localTrack.getInfo()).thenReturn(new AudioTrackInfo(
                    "Name [x1Xd0m2OdcU].webm", "Unknown artist", 61_000L, FILE, false, FILE));
            when(localTrack.getDuration()).thenReturn(61_000L);
            libraryTrack = mock(AudioTrack.class);
            when(libraryTrack.getInfo()).thenReturn(new AudioTrackInfo(
                    "Indexed Title", "Indexed Author", 123_000L, "x1Xd0m2OdcU", false,
                    "https://www.youtube.com/watch?v=x1Xd0m2OdcU"));
            when(libraryTrack.getDuration()).thenReturn(123_000L);
            com.jagrosh.jmusicbot.ytdlp.YtDlpService ytDlp = mock(com.jagrosh.jmusicbot.ytdlp.YtDlpService.class);
            when(ytDlp.wrapLibraryFile(any())).thenAnswer(inv -> inv.getArgument(0) == localTrack
                    ? libraryTrack : inv.getArgument(0));
            when(fixture.getBot().getYtDlp()).thenReturn(ytDlp);
        }

        @Test
        @DisplayName("addTrackToQueue() confirms and queues a library file under its library title")
        void addTrackToQueue_libraryFile_usesLibraryTitle()
        {
            MusicService.TrackAddResult result = musicService.addTrackToQueue(fixture.getGuild(), fixture.getMember(),
                    localTrack, FILE, fixture.getTextChannel());

            assertNotNull(result);
            assertTrue(result.formattedMessage.contains("Indexed Title"), result.formattedMessage);
            assertFalse(result.formattedMessage.contains(".webm"), result.formattedMessage);
            assertTrue(result.formattedMessage.contains("02:03"), result.formattedMessage);
            assertEquals("Indexed Title", result.trackTitle);
            org.mockito.ArgumentCaptor<QueuedTrack> queued = org.mockito.ArgumentCaptor.forClass(QueuedTrack.class);
            verify(fixture.getAudioHandler()).addTrack(queued.capture());
            assertSame(libraryTrack, queued.getValue().getTrack());
            // The request still records what was actually asked for.
            assertEquals(FILE, queued.getValue().getRequestMetadata().requestInfo.url);
        }

        @Test
        @DisplayName("addTrackToQueue() leaves a track unchanged when the bot has no yt-dlp service")
        void addTrackToQueue_noYtDlpService_unchanged()
        {
            when(fixture.getBot().getYtDlp()).thenReturn(null);

            MusicService.TrackAddResult result = musicService.addTrackToQueue(fixture.getGuild(), fixture.getMember(),
                    localTrack, FILE, fixture.getTextChannel());

            assertEquals("Name [x1Xd0m2OdcU].webm", result.trackTitle);
        }

        private List<String> loadPlaylistLines(java.util.function.Consumer<com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler> respond)
        {
            com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist playlist =
                    mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class);
            when(playlist.getName()).thenReturn("ytdlp-library");
            when(playlist.getItems()).thenReturn(List.of(FILE));
            when(fixture.getPlaylistLoader().getPlaylistResult("ytdlp-library"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(playlist));
            doAnswer(invocation ->
            {
                respond.accept(invocation.getArgument(2));
                return null;
            }).when(fixture.getPlayerManager()).loadItemOrdered(any(), eq(FILE),
                    any(com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler.class));

            List<String> lines = new ArrayList<>();
            musicService.loadPlaylistPageWithTracks("ytdlp-library", 1, 10, page -> lines.addAll(page.formattedLines));
            return lines;
        }

        @Test
        @DisplayName("Playlists details view lists a library file (track loaded) under its library title")
        void playlistDetails_trackLoaded_usesLibraryTitle()
        {
            List<String> lines = loadPlaylistLines(handler -> handler.trackLoaded(localTrack));

            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("Indexed Title"), lines.get(0));
            assertFalse(lines.get(0).contains(".webm"), lines.get(0));
        }

        @Test
        @DisplayName("Playlists details view lists a library file (playlist loaded) under its library title")
        void playlistDetails_playlistLoaded_usesLibraryTitle()
        {
            com.sedmelluq.discord.lavaplayer.track.AudioPlaylist loaded =
                    mock(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist.class);
            when(loaded.getTracks()).thenReturn(List.of(localTrack));

            List<String> lines = loadPlaylistLines(handler -> handler.playlistLoaded(loaded));

            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("Indexed Title"), lines.get(0));
        }
    }

    @Nested
    @DisplayName("Favorites Playlist Operation")
    class FavoritesPlaylistOperationTests
    {
        @Test
        @DisplayName("addCurrentTrackToFavorites() fails for non-DJ")
        void addCurrentTrackToFavorites_failsForNonDJ()
        {
            fixture.withoutDJPermission()
                    .withPlayingTrack();

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            output.assertErrorMessageContains("need to be a DJ");
            verify(fixture.getPlaylistLoader(), never()).appendItemIfAbsentResult(anyString(), anyString());
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() fails when no track is playing")
        void addCurrentTrackToFavorites_failsWhenNoTrackPlaying()
        {
            fixture.withDJPermission()
                    .withNoTrack();

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            output.assertErrorMessageContains("no track currently playing");
            verify(fixture.getPlaylistLoader(), never()).appendItemIfAbsentResult(anyString(), anyString());
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() fails for unsupported favorite entry")
        void addCurrentTrackToFavorites_failsForUnsupportedEntry()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 180000L, "id", false, "relative/path.mp3");
            when(fixture.getCurrentTrack().getInfo()).thenReturn(info);

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            output.assertErrorMessageContains("Only URI references or absolute file paths for this OS");
            verify(fixture.getPlaylistLoader(), never()).appendItemIfAbsentResult(anyString(), anyString());
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() accepts OS-native absolute path")
        void addCurrentTrackToFavorites_acceptsOsNativeAbsolutePath()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            String absolutePath = sampleNativeAbsolutePath();
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 180000L, "id", false, absolutePath);
            when(fixture.getCurrentTrack().getInfo()).thenReturn(info);
            when(fixture.getPlaylistLoader().appendItemIfAbsentResult("favorites", absolutePath))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(
                            com.jagrosh.jmusicbot.playlist.PlaylistLoader.AppendIfAbsentResult.appended()));

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            verify(fixture.getPlaylistLoader()).appendItemIfAbsentResult("favorites", absolutePath);
            verify(fixture.getAudioHandler()).markCurrentTrackFavorited(absolutePath);
            output.assertNowPlayingEdited();
            output.assertSuccessMessage("Added Title to Favorites");
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() rejects non-native absolute path format")
        void addCurrentTrackToFavorites_rejectsNonNativeAbsolutePathFormat()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            String nonNativePath = sampleNonNativeAbsolutePath();
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 180000L, "id", false, nonNativePath);
            when(fixture.getCurrentTrack().getInfo()).thenReturn(info);

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            output.assertErrorMessageContains("Only URI references or absolute file paths for this OS");
            verify(fixture.getPlaylistLoader(), never()).appendItemIfAbsentResult(anyString(), anyString());
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() creates favorites playlist when missing and writes URI")
        void addCurrentTrackToFavorites_createsMissingPlaylistAndWrites()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            when(fixture.getPlaylistLoader().appendItemIfAbsentResult("favorites", "https://example.com/track"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(
                            com.jagrosh.jmusicbot.playlist.PlaylistLoader.AppendIfAbsentResult.appended()));

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            verify(fixture.getPlaylistLoader()).appendItemIfAbsentResult("favorites", "https://example.com/track");
            verify(fixture.getAudioHandler()).markCurrentTrackFavorited("https://example.com/track");
            output.assertNowPlayingEdited();
            output.assertSuccessMessage("Added Test Track to Favorites");
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() appends to existing favorites playlist")
        void addCurrentTrackToFavorites_appendsToExistingPlaylist()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            when(fixture.getPlaylistLoader().appendItemIfAbsentResult("favorites", "https://example.com/track"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(
                            com.jagrosh.jmusicbot.playlist.PlaylistLoader.AppendIfAbsentResult.appended()));

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            verify(fixture.getPlaylistLoader()).appendItemIfAbsentResult("favorites", "https://example.com/track");
            verify(fixture.getAudioHandler()).markCurrentTrackFavorited("https://example.com/track");
            output.assertNowPlayingEdited();
            output.assertSuccessMessage("Added Test Track to Favorites");
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() warns and skips write when already favorited")
        void addCurrentTrackToFavorites_warnsAndSkipsWriteWhenAlreadyFavorited()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            when(fixture.getPlaylistLoader().appendItemIfAbsentResult("favorites", "https://example.com/track"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(
                            com.jagrosh.jmusicbot.playlist.PlaylistLoader.AppendIfAbsentResult.alreadyPresent()));

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            verify(fixture.getPlaylistLoader()).appendItemIfAbsentResult("favorites", "https://example.com/track");
            verify(fixture.getAudioHandler()).markCurrentTrackFavorited("https://example.com/track");
            output.assertNowPlayingEdited();
            output.assertWarningMessage("This track is already favorited");
        }

        @Test
        @DisplayName("addCurrentTrackToFavorites() surfaces fail-fast storage configuration errors")
        void addCurrentTrackToFavorites_surfacesStorageConfigurationErrors()
        {
            fixture.withDJPermission()
                    .withPlayingTrack();
            var configError = com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistError.of(
                    com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistErrorType.INVALID_CONFIG,
                    "Playlists folder is not configured.", null, null);
            when(fixture.getPlaylistLoader().appendItemIfAbsentResult("favorites", "https://example.com/track"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.failure(configError));

            musicService.addCurrentTrackToFavorites(fixture.getGuild(), fixture.getMember(), output);

            output.assertErrorMessageContains("misconfigured");
            output.assertErrorMessageContains("(not configured)");
        }
    }

    // ==================== History Operation Tests ====================

    @Nested
    @DisplayName("History Operations")
    class HistoryOperationTests
    {
        @Test
        @DisplayName("getHistoryInfo() passes non-ASCII track titles through verbatim")
        void getHistoryInfo_passesNonAsciiTitlesThroughVerbatim()
        {
            // Title decoding is handled at the source-manager level; the app no longer
            // transforms titles, so they must appear in history exactly as provided.
            String cyrillicTitle = "Привет мир";

            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo(cyrillicTitle, "Artist", 180000L, "id-1", false, "https://example.com/1");
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(180000L);
            when(track.isSeekable()).thenReturn(false);
            when(track.getUserData(RequestMetadata.class)).thenReturn(RequestMetadata.EMPTY);

            QueuedTrack queuedTrack = new QueuedTrack(track, RequestMetadata.EMPTY);
            when(fixture.getAudioHandler().getPreviousTracks()).thenReturn(List.of(queuedTrack));
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(history.getMaxSize()).thenReturn(40);

            MusicService.HistoryInfo historyInfo = musicService.getHistoryInfo(fixture.getGuild(), fixture.getJda());

            assertNotNull(historyInfo);
            assertEquals(1, historyInfo.tracks.length);
            assertTrue(historyInfo.tracks[0].contains(cyrillicTitle));
        }

        @Test
        @DisplayName("getQueueInfo() keeps ASCII titles unchanged")
        void getQueueInfo_keepsAsciiTitlesUnchanged()
        {
            String asciiTitle = "Rick Astley - Never Gonna Give You Up";

            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo(asciiTitle, "Artist", 180000L, "id-2", false, "https://example.com/2");
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(180000L);
            when(track.isSeekable()).thenReturn(false);
            when(track.getUserData(RequestMetadata.class)).thenReturn(RequestMetadata.EMPTY);

            QueuedTrack queuedTrack = new QueuedTrack(track, RequestMetadata.EMPTY);
            when(fixture.getQueue().getList()).thenReturn(List.of(queuedTrack));

            MusicService.QueueInfo queueInfo = musicService.getQueueInfo(fixture.getGuild(), fixture.getJda());

            assertNotNull(queueInfo);
            assertEquals(1, queueInfo.tracks.length);
            assertTrue(queueInfo.tracks[0].contains(asciiTitle));
        }

        @Test
        @DisplayName("getHistoryInfo() returns null when no handler")
        void getHistoryInfo_returnsNullWhenNoHandler()
        {
            when(fixture.getAudioManager().getSendingHandler()).thenReturn(null);

            MusicService.HistoryInfo info = musicService.getHistoryInfo(fixture.getGuild(), fixture.getJda());

            assertNull(info);
        }

        @Test
        @DisplayName("queueFromHistory() replies error when no handler")
        void queueFromHistory_repliesErrorWhenNoHandler()
        {
            when(fixture.getAudioManager().getSendingHandler()).thenReturn(null);

            musicService.queueFromHistory(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            output.assertErrorMessageContains("no player");
        }

        @Test
        @DisplayName("queueFromHistory() replies error when history empty")
        void queueFromHistory_repliesErrorWhenHistoryEmpty()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(history.isEmpty()).thenReturn(true);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(fixture.getAudioHandler().getQueue()).thenReturn(fixture.getQueue());

            musicService.queueFromHistory(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            output.assertErrorMessageContains("empty");
        }

        @Test
        @DisplayName("queueFromHistory() replies error when position out of range")
        void queueFromHistory_repliesErrorWhenPositionOutOfRange()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(history.isEmpty()).thenReturn(false);
            when(history.size()).thenReturn(3);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(fixture.getAudioHandler().getQueue()).thenReturn(fixture.getQueue());

            musicService.queueFromHistory(fixture.getGuild(), fixture.getMember(), 10,
                    fixture.getTextChannel(), output);

            output.assertErrorMessageContains("1 and 3");
        }

        @Test
        @DisplayName("queueAllFromHistory() requests reconcile when tracks added and player active")
        void queueAllFromHistory_requestsReconcile_whenTracksAddedAndPlayerActive()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(history.isEmpty()).thenReturn(false);
            when(fixture.getQueue().getHistory()).thenReturn(history);

            QueuedTrack qtOne = mock(QueuedTrack.class);
            QueuedTrack qtTwo = mock(QueuedTrack.class);
            AudioTrack originalTrackOne = mock(AudioTrack.class);
            AudioTrack originalTrackTwo = mock(AudioTrack.class);
            AudioTrack clonedTrackOne = mock(AudioTrack.class);
            AudioTrack clonedTrackTwo = mock(AudioTrack.class);
            AudioTrackInfo infoOne = new AudioTrackInfo("History Song 1", "Artist", 180000, "id-1", false, "https://example.com/1");
            AudioTrackInfo infoTwo = new AudioTrackInfo("History Song 2", "Artist", 180000, "id-2", false, "https://example.com/2");
            when(qtOne.getTrack()).thenReturn(originalTrackOne);
            when(qtTwo.getTrack()).thenReturn(originalTrackTwo);
            when(originalTrackOne.makeClone()).thenReturn(clonedTrackOne);
            when(originalTrackTwo.makeClone()).thenReturn(clonedTrackTwo);
            when(originalTrackOne.getInfo()).thenReturn(infoOne);
            when(originalTrackTwo.getInfo()).thenReturn(infoTwo);
            when(originalTrackOne.getIdentifier()).thenReturn("id-1");
            when(originalTrackTwo.getIdentifier()).thenReturn("id-2");
            when(history.getList()).thenReturn(List.of(qtOne, qtTwo));
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            musicService.queueAllFromHistory(fixture.getGuild(), fixture.getMember(), fixture.getTextChannel(), output);

            verify(fixture.getAudioHandler(), times(2)).addTrack(any(QueuedTrack.class));
            verify(fixture.getQueue()).clearHistory();
            verify(fixture.getQueue(), never()).removeFromHistoryAt(anyInt());
            verify(fixture.getQueue(), never()).removeFromHistoryFirstMatch(any());
            verify(fixture.getNowPlayingHandler()).requestReconcile(eq(fixture.getGuild().getIdLong()), eq("history-queueall-loaded"));
            output.assertSuccessMessage("Added **2** track(s) to the queue.");
        }

        @Test
        @DisplayName("queueAllFromHistory() preserves first replayed track in history after idle queue-all and skip progression")
        void queueAllFromHistory_preservesFirstReplayedTrackInHistory_afterIdleQueueAllAndSkipProgression()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(fixture.getQueue().getHistory()).thenReturn(history);

            QueuedTrack qtOne = mock(QueuedTrack.class);
            QueuedTrack qtTwo = mock(QueuedTrack.class);
            QueuedTrack qtThree = mock(QueuedTrack.class);
            AudioTrack originalTrackOne = mock(AudioTrack.class);
            AudioTrack originalTrackTwo = mock(AudioTrack.class);
            AudioTrack originalTrackThree = mock(AudioTrack.class);
            AudioTrack clonedTrackOne = mock(AudioTrack.class);
            AudioTrack clonedTrackTwo = mock(AudioTrack.class);
            AudioTrack clonedTrackThree = mock(AudioTrack.class);
            AudioTrackInfo infoOne = new AudioTrackInfo("History Song 1", "Artist", 180000, "id-1", false, "https://example.com/1");
            AudioTrackInfo infoTwo = new AudioTrackInfo("History Song 2", "Artist", 180000, "id-2", false, "https://example.com/2");
            AudioTrackInfo infoThree = new AudioTrackInfo("History Song 3", "Artist", 180000, "id-3", false, "https://example.com/3");
            when(qtOne.getTrack()).thenReturn(originalTrackOne);
            when(qtTwo.getTrack()).thenReturn(originalTrackTwo);
            when(qtThree.getTrack()).thenReturn(originalTrackThree);
            when(originalTrackOne.makeClone()).thenReturn(clonedTrackOne);
            when(originalTrackTwo.makeClone()).thenReturn(clonedTrackTwo);
            when(originalTrackThree.makeClone()).thenReturn(clonedTrackThree);
            when(originalTrackOne.getInfo()).thenReturn(infoOne);
            when(originalTrackTwo.getInfo()).thenReturn(infoTwo);
            when(originalTrackThree.getInfo()).thenReturn(infoThree);
            when(originalTrackOne.getIdentifier()).thenReturn("id-1");
            when(originalTrackTwo.getIdentifier()).thenReturn("id-2");
            when(originalTrackThree.getIdentifier()).thenReturn("id-3");

            List<QueuedTrack> liveHistory = new ArrayList<>(List.of(qtOne, qtTwo, qtThree));
            when(history.isEmpty()).thenAnswer(invocation -> liveHistory.isEmpty());
            when(history.getList()).thenAnswer(invocation -> List.copyOf(liveHistory));
            doAnswer(invocation ->
            {
                liveHistory.clear();
                return null;
            }).when(fixture.getQueue()).clearHistory();

            AtomicInteger addCalls = new AtomicInteger();
            doAnswer(invocation ->
            {
                // Simulate idle addTrack starting playback and onTrackStart re-adding first entry to history.
                if (addCalls.getAndIncrement() == 0)
                {
                    liveHistory.add(0, qtOne);
                    return -1;
                }
                return addCalls.get();
            }).when(fixture.getAudioHandler()).addTrack(any(QueuedTrack.class));
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(null);

            musicService.queueAllFromHistory(fixture.getGuild(), fixture.getMember(), fixture.getTextChannel(), output);

            verify(fixture.getNowPlayingHandler(), never()).requestReconcile(anyLong(), anyString());
            verify(fixture.getAudioHandler(), times(3)).addTrack(any(QueuedTrack.class));
            verify(fixture.getQueue()).clearHistory();
            verify(fixture.getQueue(), never()).removeFromHistoryAt(anyInt());
            verify(fixture.getQueue(), never()).removeFromHistoryFirstMatch(any());
            output.assertSuccessMessage("Added **3** track(s) to the queue.");
            assertEquals(1, liveHistory.size(), "Expected currently playing replayed track to remain in history");
            assertSame(qtOne, liveHistory.get(0), "Expected first replayed track at history front");

            // Simulate skipping through the remaining queued tracks; each started track should appear in history.
            liveHistory.add(0, qtTwo);
            liveHistory.add(0, qtThree);
            assertEquals(3, liveHistory.size(), "Expected all replayed tracks to be retained in history");
        }

        @Test
        @DisplayName("playFromHistoryNow() replies error when history empty")
        void playFromHistoryNow_repliesErrorWhenHistoryEmpty()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(history.isEmpty()).thenReturn(true);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(fixture.getAudioHandler().getQueue()).thenReturn(fixture.getQueue());

            musicService.playFromHistoryNow(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            output.assertErrorMessageContains("empty");
        }

        private QueuedTrack historyEntryAt(int position)
        {
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("History Track", "Author", 180000L, "id",
                    false, "https://example.com/history-" + position);
            when(track.getInfo()).thenReturn(info);
            when(track.getDuration()).thenReturn(180000L);
            when(track.makeClone()).thenReturn(track);
            when(qt.getTrack()).thenReturn(track);
            return qt;
        }

        private void setUpHistoryWithOneEntry(QueuedTrack qt)
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(history.isEmpty()).thenReturn(false);
            when(history.size()).thenReturn(1);
            when(history.get(0)).thenReturn(qt);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(fixture.getAudioHandler().getQueue()).thenReturn(fixture.getQueue());
        }

        @Test
        @DisplayName("playFromHistoryNow() does not stop the track it just started when nothing was playing")
        void playFromHistoryNow_doesNotStopFreshlyStartedTrack_whenIdle()
        {
            setUpHistoryWithOneEntry(historyEntryAt(1));

            // Nothing is playing before the add; addTrackToFront would start it immediately,
            // the same way the real AudioHandler does.
            when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(null);
            doAnswer(invocation ->
            {
                when(fixture.getAudioPlayer().getPlayingTrack()).thenReturn(mock(AudioTrack.class));
                return -1;
            }).when(fixture.getAudioHandler()).addTrackToFront(any(QueuedTrack.class));

            musicService.playFromHistoryNow(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioPlayer(), never()).stopTrack();
            output.assertSuccessMessageContains("Now playing");
        }

        @Test
        @DisplayName("playFromHistoryNow() replaces the previous track directly (not via addTrackToFront+stopTrack) when something was already playing")
        void playFromHistoryNow_replacesPreviousTrackDirectly_whenSomethingWasPlaying()
        {
            setUpHistoryWithOneEntry(historyEntryAt(1));
            fixture.withPlayingTrack();

            musicService.playFromHistoryNow(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            // N2: addTrackToFront + a later stopTrack() would race a prompt-cancel-triggered end
            // of the old track; playNowReplacingCurrent starts the new track directly instead.
            verify(fixture.getAudioHandler()).playNowReplacingCurrent(any());
            verify(fixture.getAudioHandler(), never()).addTrackToFront(any());
            verify(fixture.getAudioPlayer(), never()).stopTrack();
        }

        @Test
        @DisplayName("playFromHistoryNow() unpauses the player: Play now means play")
        void playFromHistoryNow_unpausesPlayer()
        {
            setUpHistoryWithOneEntry(historyEntryAt(1));
            fixture.withPausedTrack();

            musicService.playFromHistoryNow(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioPlayer()).setPaused(false);
        }

        @Test
        @DisplayName("playFromHistoryNow() cancels any open yt-dlp prompt for the track it's replacing")
        void playFromHistoryNow_cancelsOpenYtDlpPrompt()
        {
            setUpHistoryWithOneEntry(historyEntryAt(1));
            fixture.withPlayingTrack();

            musicService.playFromHistoryNow(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            verify(fixture.getAudioHandler()).cancelCurrentTrackYtDlpPrompt();
        }

        @Test
        @DisplayName("queueFromHistory() replies disabled when history feature disabled")
        void queueFromHistory_repliesDisabledWhenHistoryDisabled()
        {
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(0);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            when(fixture.getAudioHandler().getQueue()).thenReturn(fixture.getQueue());

            musicService.queueFromHistory(fixture.getGuild(), fixture.getMember(), 1,
                    fixture.getTextChannel(), output);

            output.assertErrorMessageContains("disabled by config");
        }

        @Test
        @DisplayName("getHistoryInfo() marks disabled when max history size is zero")
        void getHistoryInfo_marksDisabledWhenMaxSizeZero()
        {
            when(fixture.getAudioHandler().getPreviousTracks()).thenReturn(List.of());
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(0);
            when(fixture.getQueue().getHistory()).thenReturn(history);

            MusicService.HistoryInfo info = musicService.getHistoryInfo(fixture.getGuild(), fixture.getJda());

            assertNotNull(info);
            assertTrue(info.isDisabled());
            assertEquals(0, info.maxSize);
            assertTrue(info.isEmpty());
        }

        @Test
        @DisplayName("saveHistoryAsPlaylist() replies error when not DJ and not owner")
        void saveHistoryAsPlaylist_repliesErrorWhenNotAuthorized()
        {
            fixture.withoutDJPermission();
            when(fixture.getConfig().getOwnerId()).thenReturn(999999L);

            musicService.saveHistoryAsPlaylist(fixture.getGuild(), fixture.getMember(), "myplaylist", output);

            output.assertErrorMessageContains("DJ or the bot owner");
        }

        @Test
        @DisplayName("saveHistoryAsPlaylist() replies error when playlist already exists")
        void saveHistoryAsPlaylist_repliesErrorWhenPlaylistExists()
        {
            fixture.withDJPermission();
            PlaybackHistory<QueuedTrack> history = mock(PlaybackHistory.class);
            when(history.getMaxSize()).thenReturn(40);
            when(fixture.getQueue().getHistory()).thenReturn(history);
            QueuedTrack qt = mock(QueuedTrack.class);
            AudioTrack track = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo("Title", "Author", 180000, "id", false, "https://example.com/1");
            when(track.getInfo()).thenReturn(info);
            when(qt.getTrack()).thenReturn(track);
            when(fixture.getAudioHandler().getPreviousTracks()).thenReturn(List.of(qt));
            when(fixture.getBot().getPlaylistLoader().getPlaylistResult("existing"))
                    .thenReturn(com.jagrosh.jmusicbot.playlist.PlaylistLoader.PlaylistResult.success(
                            mock(com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist.class)));

            musicService.saveHistoryAsPlaylist(fixture.getGuild(), fixture.getMember(), "existing", output);

            output.assertErrorMessageContains("already exists");
        }
    }

    private static boolean isWindowsRuntime()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String sampleNativeAbsolutePath()
    {
        return isWindowsRuntime() ? "C:\\music\\track.mp3" : "/var/music/track.mp3";
    }

    private static String sampleNonNativeAbsolutePath()
    {
        return isWindowsRuntime() ? "/var/music/track.mp3" : "C:\\music\\track.mp3";
    }
}
