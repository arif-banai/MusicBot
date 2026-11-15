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
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBuffer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioTrackExecutor;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

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

        @Test
        @DisplayName("stopAndClearQueuePreserveHistory() does not add current track and clears queue only")
        public void testStopAndClearQueuePreserveHistoryDoesNotAddCurrentTrack() {
            AudioTrack playingTrack = mock(AudioTrack.class);
            AudioTrackInfo playingInfo = new AudioTrackInfo("Playing", "Author", 1000, "playing-id", false, "uri");
            when(playingTrack.getInfo()).thenReturn(playingInfo);
            when(audioPlayer.getPlayingTrack()).thenReturn(playingTrack);

            // Put one item in the queue to verify it gets cleared.
            QueuedTrack queued = mock(QueuedTrack.class);
            AudioTrack queuedTrack = mock(AudioTrack.class);
            AudioTrackInfo queuedInfo = new AudioTrackInfo("Queued", "Author", 1000, "queued-id", false, "uri2");
            when(queuedTrack.getInfo()).thenReturn(queuedInfo);
            when(queued.getTrack()).thenReturn(queuedTrack);
            audioHandler.addTrack(queued);

            audioHandler.stopAndClearQueuePreserveHistory();

            verify(audioPlayer).stopTrack();
            assertTrue(audioHandler.getQueue().isEmpty());
            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("stopAndClearQueuePreserveHistory() leaves history unchanged when nothing is playing")
        public void testStopAndClearQueuePreserveHistoryNoCurrentTrack() {
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            audioHandler.stopAndClearQueuePreserveHistory();

            verify(audioPlayer).stopTrack();
            assertTrue(audioHandler.getQueue().isEmpty());
            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("beginQueueReplacement() clears the queue without stopping playback")
        public void testBeginQueueReplacementDoesNotStop() {
            AudioTrack playingTrack = mock(AudioTrack.class);
            AudioTrackInfo playingInfo = new AudioTrackInfo("Playing", "Author", 1000, "playing-id", false, "uri");
            when(playingTrack.getInfo()).thenReturn(playingInfo);
            when(audioPlayer.getPlayingTrack()).thenReturn(playingTrack);

            QueuedTrack queued = mock(QueuedTrack.class);
            AudioTrack queuedTrack = mock(AudioTrack.class);
            AudioTrackInfo queuedInfo = new AudioTrackInfo("Queued", "Author", 1000, "queued-id", false, "uri2");
            when(queuedTrack.getInfo()).thenReturn(queuedInfo);
            when(queued.getTrack()).thenReturn(queuedTrack);
            audioHandler.addTrack(queued);

            audioHandler.beginQueueReplacement();

            verify(audioPlayer, never()).stopTrack();
            verify(audioPlayer, never()).playTrack(any());
            verify(audioPlayer, never()).setPaused(anyBoolean());
            assertTrue(audioHandler.getQueue().isEmpty());
        }
    }

    // ==================== Queue Replacement ("Play Now") Tests ====================

    @Nested
    @DisplayName("Queue Replacement While Playing")
    class QueueReplacementTests
    {
        private AudioTrack track(String identifier)
        {
            AudioTrack track = mock(AudioTrack.class);
            when(track.getInfo()).thenReturn(new AudioTrackInfo("Title " + identifier, "Author", 1000, identifier, false, identifier + "-uri"));
            return track;
        }

        private QueuedTrack queued(AudioTrack track)
        {
            QueuedTrack qt = mock(QueuedTrack.class);
            when(qt.getTrack()).thenReturn(track);
            return qt;
        }

        @BeforeEach
        void stubNowPlayingHandler() {
            when(bot.getNowplayingHandler()).thenReturn(mock(com.jagrosh.jmusicbot.audio.NowPlayingHandler.class));
        }

        @Test
        @DisplayName("replacing the current track while something plays never disconnects: the replacement plays directly")
        public void testQueueReplacementWhilePlayingDoesNotDisconnect() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            // Simulate MusicService.playPlaylistNow's fixed order: begin the replacement, queue
            // the first loaded track, then let AudioHandler replace the current track directly.
            Object token = audioHandler.beginQueueReplacement();
            AudioTrack newTrack = track("new");
            audioHandler.addTrack(queued(newTrack));
            assertFalse(audioHandler.getQueue().isEmpty());

            boolean replaced = audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack);

            assertTrue(replaced);
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
            verify(audioPlayer).playTrack(newTrack);
        }

        @Test
        @DisplayName("the old track's late REPLACED end does not skip or re-play the already-started replacement")
        public void testLateReplacedEndDoesNotSkipReplacement() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object token = audioHandler.beginQueueReplacement();
            AudioTrack newTrack = track("new");
            audioHandler.addTrack(queued(newTrack));
            audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack);
            verify(audioPlayer, times(1)).playTrack(newTrack);

            // The real player fires this once lavaplayer processes the playTrack() call above.
            audioHandler.onTrackEnd(audioPlayer, oldTrack, AudioTrackEndReason.REPLACED);

            verify(audioPlayer, times(1)).playTrack(any());
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
        }

        @Test
        @DisplayName("a track ending naturally before the first replacement track loads does not disconnect; the loaded track plays")
        public void testTrackEndsBeforeFirstItemLoads_noDisconnect_firstItemPlays() {
            AudioTrack oldTrack = track("old");
            AudioTrack oldClone = track("old-clone");
            when(oldTrack.makeClone()).thenReturn(oldClone);
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object token = audioHandler.beginQueueReplacement();

            // The old track finishes naturally before anything has loaded; the queue is empty,
            // but the replacement is still pending, so this must not disconnect.
            audioHandler.onTrackEnd(audioPlayer, oldTrack, AudioTrackEndReason.FINISHED);
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
            assertTrue(audioHandler.getQueue().isEmpty(), "Nothing should have been re-queued (e.g. by repeat mode)");

            // Nothing is playing now (the real player would report this).
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            AudioTrack firstLoaded = track("first-loaded");
            audioHandler.addTrack(queued(firstLoaded)); // idle -> plays immediately
            boolean replaced = audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack); // no-op: oldTrack already gone

            assertFalse(replaced);
            verify(audioPlayer).playTrack(firstLoaded);
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
        }

        @Test
        @DisplayName("a track ending naturally right after the first replacement track loads does not skip it")
        public void testTrackEndsAfterFirstItemLoadedButBeforeDone_firstItemNotSkipped() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object token = audioHandler.beginQueueReplacement();
            AudioTrack firstLoaded = track("first-loaded");
            audioHandler.addTrack(queued(firstLoaded));
            boolean replaced = audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack);
            assertTrue(replaced);
            verify(audioPlayer, times(1)).playTrack(firstLoaded);

            // endQueueReplacement() runs once loading is "done"; it must not disturb the track
            // that's already playing.
            audioHandler.endQueueReplacement(token);

            verify(audioPlayer, times(1)).playTrack(any());
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
        }

        @Test
        @DisplayName("a late call from a superseded ('Play now' clicked twice) replacement can't disarm the newer one")
        public void testStaleTokenFromSupersededReplacementCannotAffectNewerOne() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object firstToken = audioHandler.beginQueueReplacement();
            // A second "Play now" begins before the first one's loaded track arrives.
            Object secondToken = audioHandler.beginQueueReplacement();
            AudioTrack secondTrack = track("second");
            audioHandler.addTrack(queued(secondTrack));

            // The first (now-superseded) replacement's loaded track arrives late.
            AudioTrack firstLateTrack = track("first-late");
            boolean firstReplaced = audioHandler.replaceCurrentTrackIfStillPlaying(firstToken, oldTrack);
            assertFalse(firstReplaced, "A stale token must not be able to act");
            verify(audioPlayer, never()).playTrack(firstLateTrack);

            // The second (active) replacement's loaded track still replaces correctly.
            boolean secondReplaced = audioHandler.replaceCurrentTrackIfStillPlaying(secondToken, oldTrack);
            assertTrue(secondReplaced);
            verify(audioPlayer).playTrack(secondTrack);
        }

        /**
         * N1 regression: lavaplayer dispatches every track event (onTrackEnd included) while
         * holding its own internal track-switch lock, and also takes that same lock inside
         * playTrack()/stopTrack(). If AudioHandler ever called into the player while holding its
         * own monitor, two threads can deadlock: one thread holds the player's lock and blocks
         * entering AudioHandler (via onTrackEnd), while the other holds AudioHandler's lock and
         * blocks entering the player (via playTrack()). This test stands a {@link ReentrantLock}
         * in for the player's lock and drives both sides with real threads and a timeout: under
         * the pre-fix design (a {@code synchronized} method wrapping the whole decide-and-play
         * sequence) this reliably deadlocks; under the fixed design (decide under a small
         * monitor guarding only pure state, act after releasing it) it always completes.
         */
        @Test
        @DisplayName("replacing the current track never deadlocks against a concurrent track end under the player's own lock")
        public void testReplaceDoesNotDeadlockAgainstConcurrentTrackEnd() throws InterruptedException {
            when(bot.getNowplayingHandler()).thenReturn(mock(com.jagrosh.jmusicbot.audio.NowPlayingHandler.class));

            // Stands in for lavaplayer's internal track-switch lock (DefaultAudioPlayer:
            // dispatchEvent/startTrack/stopWithReason/handleTerminator all take it).
            ReentrantLock trackSwitchLock = new ReentrantLock();
            CountDownLatch playerThreadHasLock = new CountDownLatch(1);
            CountDownLatch loaderThreadCallingPlayTrack = new CountDownLatch(1);

            AudioTrack oldTrack = track("old");
            AudioTrack newTrack = track("new");
            AudioTrack unrelatedTrack = track("unrelated");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            // playTrack() simulates startTrack(): it takes the player's lock, the one call into
            // the player that AudioHandler's replace path must make after releasing its own.
            doAnswer(inv -> {
                loaderThreadCallingPlayTrack.countDown();
                trackSwitchLock.lock();
                trackSwitchLock.unlock();
                return null;
            }).when(audioPlayer).playTrack(any());

            Object token = audioHandler.beginQueueReplacement();
            // Queued directly (not via addTrack()) so the only getPlayingTrack() check left is
            // the one inside replaceCurrentTrackIfStillPlaying itself.
            audioHandler.getQueue().add(queued(newTrack));

            java.util.concurrent.atomic.AtomicReference<Throwable> playerThreadFailure = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> loaderThreadFailure = new java.util.concurrent.atomic.AtomicReference<>();

            Thread playerThread = new Thread(() -> {
                trackSwitchLock.lock();
                try
                {
                    playerThreadHasLock.countDown();
                    // Wait until the loader thread is blocked trying to enter the player (i.e.
                    // it has already made its "replace" decision), the same way lavaplayer
                    // dispatches onTrackEnd for a track ending while trackSwitchLock is held.
                    if (!loaderThreadCallingPlayTrack.await(2, TimeUnit.SECONDS))
                        throw new AssertionError("loader thread never reached playTrack()");
                    audioHandler.onTrackEnd(audioPlayer, unrelatedTrack, AudioTrackEndReason.FINISHED);
                }
                catch (Throwable ex)
                {
                    playerThreadFailure.set(ex);
                }
                finally
                {
                    trackSwitchLock.unlock();
                }
            }, "fake-player-thread");

            Thread loaderThread = new Thread(() -> {
                try
                {
                    if (!playerThreadHasLock.await(2, TimeUnit.SECONDS))
                        throw new AssertionError("player thread never took the lock");
                    audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack);
                }
                catch (Throwable ex)
                {
                    loaderThreadFailure.set(ex);
                }
            }, "loader-thread");

            playerThread.start();
            loaderThread.start();

            loaderThread.join(2000);
            playerThread.join(2000);

            assertFalse(loaderThread.isAlive(), "replaceCurrentTrackIfStillPlaying deadlocked");
            assertFalse(playerThread.isAlive(), "the simulated player thread deadlocked");
            assertNull(loaderThreadFailure.get(), () -> "loader thread failed: " + loaderThreadFailure.get());
            assertNull(playerThreadFailure.get(), () -> "player thread failed: " + playerThreadFailure.get());
        }

        @Test
        @DisplayName("replacing the current track unpauses the player: Play now means play")
        public void testReplaceUnpausesPlayer() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);
            when(audioPlayer.isPaused()).thenReturn(true);

            Object token = audioHandler.beginQueueReplacement();
            audioHandler.addTrack(queued(track("new")));
            audioHandler.replaceCurrentTrackIfStillPlaying(token, oldTrack);

            verify(audioPlayer).setPaused(false);
        }

        @Test
        @DisplayName("an idle start during a pending replacement also unpauses the player")
        public void testIdleStartDuringReplacementUnpausesPlayer() {
            when(audioPlayer.getPlayingTrack()).thenReturn(null);
            when(audioPlayer.isPaused()).thenReturn(true);

            Object token = audioHandler.beginQueueReplacement();
            audioHandler.addTrack(queued(track("first-loaded"))); // idle -> plays immediately
            audioHandler.replaceCurrentTrackIfStillPlaying(token, null);

            verify(audioPlayer).setPaused(false);
        }

        @Test
        @DisplayName("repeat mode does not re-queue a track that finishes while a replacement is pending")
        public void testRepeatModeDoesNotRequeueDuringPendingReplacement() {
            when(settings.getRepeatMode()).thenReturn(RepeatMode.ALL);
            AudioTrack oldTrack = track("old");
            AudioTrack oldClone = track("old-clone");
            when(oldTrack.makeClone()).thenReturn(oldClone);
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            audioHandler.beginQueueReplacement();
            audioHandler.onTrackEnd(audioPlayer, oldTrack, AudioTrackEndReason.FINISHED);

            assertTrue(audioHandler.getQueue().isEmpty(), "Repeat mode must not re-queue the replaced song while a replacement is pending");
            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
        }

        @Test
        @DisplayName("endQueueReplacement() applies the normal empty-queue behaviour when nothing ended up playing (e.g. zero tracks loaded, idle)")
        public void testEndQueueReplacement_zeroTracksIdle_appliesEmptyQueueBehaviour() {
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            Object token = audioHandler.beginQueueReplacement();
            // Nothing ever loaded (e.g. an empty playlist); loading is now "done".
            audioHandler.endQueueReplacement(token);

            verify(playerManager.getBot()).closeAudioConnection(GUILD_ID);
        }

        @Test
        @DisplayName("endQueueReplacement() leaves a still-playing track alone when zero tracks were loaded")
        public void testEndQueueReplacement_zeroTracksButSomethingStillPlaying_doesNothing() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object token = audioHandler.beginQueueReplacement();
            // Nothing ever loaded, but the original track never ended either.
            audioHandler.endQueueReplacement(token);

            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
            verify(audioPlayer, never()).stopTrack();
        }

        @Test
        @DisplayName("endQueueReplacement() is a no-op for a token a newer replacement has superseded")
        public void testEndQueueReplacement_staleToken_isNoOp() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            Object firstToken = audioHandler.beginQueueReplacement();
            audioHandler.beginQueueReplacement(); // a second "Play now" supersedes the first

            // The first (stale) request's loadTracks DONE callback fires late.
            audioHandler.endQueueReplacement(firstToken);

            verify(playerManager.getBot(), never()).closeAudioConnection(anyLong());
            verify(audioPlayer, never()).stopTrack();
        }

        @Test
        @DisplayName("stopping before the replacement queue is loaded (the old bug) leaves the queue empty and disconnects")
        public void testStopBeforeQueueLoad_demonstratesDisconnectBug() {
            AudioTrack oldTrack = track("old");
            when(audioPlayer.getPlayingTrack()).thenReturn(oldTrack);

            // The pre-fix order: a full stop (and clear) before anything new is queued. This is
            // what /stop still does on purpose (see StopOperationTests) — guarded here too, to
            // document exactly which path the fix moved away from for "Play now".
            audioHandler.stopAndClearQueuePreserveHistory();
            // The real player calls onTrackEnd for the stopped track; the queue is still empty.
            audioHandler.onTrackEnd(audioPlayer, oldTrack, AudioTrackEndReason.STOPPED);

            verify(playerManager.getBot()).closeAudioConnection(GUILD_ID);
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

    @Nested
    @DisplayName("Track Start History")
    class TrackStartHistoryTests
    {
        @Test
        @DisplayName("onTrackStart() adds started track to history")
        void onTrackStart_addsToHistory()
        {
            AudioTrack startedTrack = createTrack("started-track", "Started Track", "https://example.com/started-track");
            when(bot.getNowplayingHandler()).thenReturn(mock(com.jagrosh.jmusicbot.audio.NowPlayingHandler.class));

            audioHandler.onTrackStart(audioPlayer, startedTrack);

            assertEquals(1, audioHandler.getPreviousTracks().size());
        }

        @Test
        @DisplayName("onTrackEnd(FINISHED) does not add track to history")
        void onTrackEnd_finished_doesNotAddToHistory()
        {
            AudioTrack playedTrack = createTrack("played-finished", "Played Finished", "https://example.com/played-finished");
            enqueueNextTrack("queued-finished", "Queued Finished", "https://example.com/queued-finished");

            audioHandler.onTrackEnd(audioPlayer, playedTrack, AudioTrackEndReason.FINISHED);

            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("onTrackEnd(STOPPED) does not add track to history")
        void onTrackEnd_stopped_doesNotAddToHistory()
        {
            AudioTrack playedTrack = createTrack("played-stopped", "Played Stopped", "https://example.com/played-stopped");
            enqueueNextTrack("queued-stopped", "Queued Stopped", "https://example.com/queued-stopped");

            audioHandler.onTrackEnd(audioPlayer, playedTrack, AudioTrackEndReason.STOPPED);

            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("onTrackEnd(STOPPED) does not add history across repeated calls")
        void onTrackEnd_stopped_repeatedCalls_doNotAddHistory()
        {
            AudioTrack firstStoppedTrack = createTrack("played-guard-1", "Played Guard 1", "https://example.com/played-guard-1");
            enqueueNextTrack("queued-guard-1", "Queued Guard 1", "https://example.com/queued-guard-1");
            audioHandler.onTrackEnd(audioPlayer, firstStoppedTrack, AudioTrackEndReason.STOPPED);
            assertTrue(audioHandler.getPreviousTracks().isEmpty());

            AudioTrack secondStoppedTrack = createTrack("played-guard-2", "Played Guard 2", "https://example.com/played-guard-2");
            enqueueNextTrack("queued-guard-2", "Queued Guard 2", "https://example.com/queued-guard-2");
            audioHandler.onTrackEnd(audioPlayer, secondStoppedTrack, AudioTrackEndReason.STOPPED);
            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("onTrackEnd(LOAD_FAILED) does not add track to history")
        void onTrackEnd_loadFailed_doesNotAddToHistory()
        {
            AudioTrack playedTrack = createTrack("played-load-failed", "Played Load Failed", "https://example.com/played-load-failed");
            enqueueNextTrack("queued-load-failed", "Queued Load Failed", "https://example.com/queued-load-failed");

            audioHandler.onTrackEnd(audioPlayer, playedTrack, AudioTrackEndReason.LOAD_FAILED);

            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("stopAndClearQueuePreserveHistory() does not duplicate a track already added on start")
        void stopAndClearQueuePreserveHistory_doesNotDuplicateTrackAlreadyInHistory()
        {
            AudioTrack startedTrack = createTrack("started-dedup", "Started Dedup", "https://example.com/started-dedup");
            when(audioPlayer.getPlayingTrack()).thenReturn(startedTrack);
            when(bot.getNowplayingHandler()).thenReturn(mock(com.jagrosh.jmusicbot.audio.NowPlayingHandler.class));

            audioHandler.onTrackStart(audioPlayer, startedTrack);
            audioHandler.stopAndClearQueuePreserveHistory();

            assertEquals(1, audioHandler.getPreviousTracks().size());
        }

        private AudioTrack createTrack(String identifier, String title, String uri)
        {
            AudioTrack track = mock(AudioTrack.class);
            AudioTrack clone = mock(AudioTrack.class);
            AudioTrackInfo info = new AudioTrackInfo(title, "Author", 1000, identifier, false, uri);
            when(track.getInfo()).thenReturn(info);
            when(track.getIdentifier()).thenReturn(identifier);
            when(track.makeClone()).thenReturn(clone);
            return track;
        }

        private void enqueueNextTrack(String identifier, String title, String uri)
        {
            AudioTrack track = createTrack(identifier, title, uri);
            QueuedTrack queuedTrack = mock(QueuedTrack.class);
            when(queuedTrack.getTrack()).thenReturn(track);
            when(queuedTrack.getIdentifier()).thenReturn(0L);
            audioHandler.getQueue().add(queuedTrack);
        }
    }

    // ==================== yt-dlp wiring ====================

    @Nested
    @DisplayName("yt-dlp wiring")
    class YtDlpWiringTests
    {
        private YtDlpService ytDlp;
        private YtDlpPrompt prompt;

        @BeforeEach
        void setUpYtDlp()
        {
            ytDlp = mock(YtDlpService.class);
            prompt = mock(YtDlpPrompt.class);
            when(bot.getYtDlp()).thenReturn(ytDlp);
            when(ytDlp.getPrompt()).thenReturn(prompt);
            when(ytDlp.prepareForPlayback(any(), anyLong())).thenAnswer(inv -> inv.getArgument(0));
            when(ytDlp.wrapLibraryFile(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
            when(bot.getNowplayingHandler()).thenReturn(mock(com.jagrosh.jmusicbot.audio.NowPlayingHandler.class));
        }

        private AudioTrack track(String identifier)
        {
            AudioTrack track = mock(AudioTrack.class);
            when(track.getInfo()).thenReturn(new AudioTrackInfo("Title " + identifier, "Author", 1000, identifier, false, "uri"));
            when(track.getIdentifier()).thenReturn(identifier);
            return track;
        }

        /** A real-looking lavaplayer track whose frame buffer has, or hasn't, received audio. */
        private AudioTrack trackWithFrames(String identifier, boolean receivedFrames)
        {
            InternalAudioTrack track = mock(InternalAudioTrack.class);
            when(track.getInfo()).thenReturn(new AudioTrackInfo("Title " + identifier, "Author", 1000, identifier, false, "uri"));
            when(track.getIdentifier()).thenReturn(identifier);
            AudioTrackExecutor executor = mock(AudioTrackExecutor.class);
            AudioFrameBuffer buffer = mock(AudioFrameBuffer.class);
            when(track.getActiveExecutor()).thenReturn(executor);
            when(executor.getAudioBuffer()).thenReturn(buffer);
            when(buffer.hasReceivedFrames()).thenReturn(receivedFrames);
            return track;
        }

        /** The one task scheduled on the prompt scheduler (the zero-audio FINISHED fallback). */
        private Runnable scheduledFallback()
        {
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(prompt).schedule(task.capture(), anyLong(), any(TimeUnit.class));
            return task.getValue();
        }

        private LibraryAudioTrack libraryTrack(CompletableFuture<Void> gate)
        {
            return new LibraryAudioTrack(new AudioTrackInfo("Lib", "Author", 1000, "abcdefghijk", false, "uri"),
                    "abcdefghijk", () -> new CompletableFuture<LibraryEntry>(), gate, null);
        }

        private void enqueue(AudioTrack track)
        {
            QueuedTrack queued = mock(QueuedTrack.class);
            when(queued.getTrack()).thenReturn(track);
            audioHandler.getQueue().add(queued);
        }

        @Test
        @DisplayName("beginQueueReplacement() cancels an open yt-dlp prompt for a gated hold that's currently playing")
        void beginQueueReplacement_cancelsOpenPromptForGatedHold()
        {
            LibraryAudioTrack hold = libraryTrack(new CompletableFuture<>());
            when(audioPlayer.getPlayingTrack()).thenReturn(hold);

            audioHandler.beginQueueReplacement();

            verify(prompt).cancel(GUILD_ID);
        }

        @Test
        @DisplayName("cancelCurrentTrackYtDlpPrompt() cancels an open prompt without stopping or clearing the queue")
        void cancelCurrentTrackYtDlpPrompt_cancelsWithoutStoppingOrClearing()
        {
            LibraryAudioTrack hold = libraryTrack(new CompletableFuture<>());
            when(audioPlayer.getPlayingTrack()).thenReturn(hold);
            enqueue(track("still-queued"));

            audioHandler.cancelCurrentTrackYtDlpPrompt();

            verify(prompt).cancel(GUILD_ID);
            verify(audioPlayer, never()).stopTrack();
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("addTrack() plays the track prepared by the yt-dlp library")
        void addTrack_playsPreparedTrack()
        {
            AudioTrack original = track("orig");
            AudioTrack prepared = track("prepared");
            when(ytDlp.prepareForPlayback(original, GUILD_ID)).thenReturn(prepared);
            QueuedTrack queued = mock(QueuedTrack.class);
            when(queued.getTrack()).thenReturn(original);
            when(audioPlayer.getPlayingTrack()).thenReturn(null);

            audioHandler.addTrack(queued);

            verify(audioPlayer).playTrack(prepared);
        }

        @Test
        @DisplayName("addTrack() queues a local library file wrapped with its YouTube title, not the filename")
        void addTrack_localLibraryFile_queuedWithYoutubeTitle()
        {
            AudioTrack localFile = track("local-file");
            LibraryAudioTrack wrapped = libraryTrack(null);
            when(ytDlp.wrapLibraryFile(localFile)).thenReturn(wrapped);
            QueuedTrack queued = mock(QueuedTrack.class);
            when(queued.getTrack()).thenReturn(localFile);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            audioHandler.addTrack(queued);

            assertEquals(1, audioHandler.getQueue().size());
            assertSame(wrapped, audioHandler.getQueue().get(0).getTrack());
            assertEquals("Lib", audioHandler.getQueue().get(0).getTrack().getInfo().title);
        }

        @Test
        @DisplayName("addTrack() leaves a local file that isn't in the library index unchanged")
        void addTrack_localNonLibraryFile_unchanged()
        {
            AudioTrack localFile = track("plain-file");
            // Default stub from setUpYtDlp: wrapLibraryFile returns the same instance (not a library file).
            QueuedTrack queued = mock(QueuedTrack.class);
            when(queued.getTrack()).thenReturn(localFile);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            audioHandler.addTrack(queued);

            assertEquals(1, audioHandler.getQueue().size());
            assertSame(localFile, audioHandler.getQueue().get(0).getTrack());
        }

        @Test
        @DisplayName("addTrack() leaves a YouTube track unchanged at queue time; misses are still decided at play time")
        void addTrack_youtubeTrack_unchanged()
        {
            AudioTrack youtubeTrack = track("yt-video-id");
            QueuedTrack queued = mock(QueuedTrack.class);
            when(queued.getTrack()).thenReturn(youtubeTrack);
            when(audioPlayer.getPlayingTrack()).thenReturn(mock(AudioTrack.class));

            audioHandler.addTrack(queued);

            assertEquals(1, audioHandler.getQueue().size());
            assertSame(youtubeTrack, audioHandler.getQueue().get(0).getTrack());
            verify(ytDlp, never()).prepareForPlayback(any(), anyLong());
        }

        private FriendlyException blockedBy(AudioTrack blocked, Optional<AudioTrack> hold, boolean offer)
        {
            FriendlyException ex = new FriendlyException("blocked", FriendlyException.Severity.SUSPICIOUS, null);
            when(prompt.isCandidate(blocked)).thenReturn(true);
            when(prompt.shouldOffer(blocked, ex)).thenReturn(offer);
            when(prompt.holdFor(guild, blocked)).thenReturn(hold);
            return ex;
        }

        @Test
        @DisplayName("exception first: a blocked track that gets a prompt is replaced by its hold, and the queue waits")
        void offeredPrompt_loadFailed_playsHoldWithoutPullingQueue()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);

            verify(audioPlayer).playTrack(hold);
            verify(audioPlayer, times(1)).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());
            verify(prompt, never()).onHoldEnded(anyLong());
        }

        @Test
        @DisplayName("exception first, then stopped: the prompt closes and the queue moves on")
        void blockedTrackStopped_closesPromptAndPullsNext()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            AudioTrack next = track("next");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(next);

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.STOPPED);

            verify(prompt).onHoldEnded(GUILD_ID);
            verify(audioPlayer, never()).playTrack(hold);
            verify(audioPlayer).playTrack(next);
        }

        @Test
        @DisplayName("exception first, then FINISHED (the track played some audio before failing): the hold plays")
        void offeredPrompt_finishedAfterAudio_playsHold()
        {
            AudioTrack blocked = trackWithFrames("blocked", true);
            AudioTrack hold = track("hold");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);

            verify(audioPlayer).playTrack(hold);
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("end first (LOAD_FAILED), prompt offered: the queue waits and the hold plays")
        void endFirst_promptOffered_playsHoldWithoutPulling()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            verify(audioPlayer, never()).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());

            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer).playTrack(hold);
            verify(audioPlayer, times(1)).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());
            verify(prompt, never()).onHoldEnded(anyLong());
        }

        @Test
        @DisplayName("end first (LOAD_FAILED), no prompt could be opened: the next song plays exactly once")
        void endFirst_holdForEmpty_pullsNextOnce()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack next = track("next");
            FriendlyException ex = blockedBy(blocked, Optional.empty(), true);
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            verify(audioPlayer, never()).playTrack(any());
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer, times(1)).playTrack(next);
            verify(audioPlayer, times(1)).playTrack(any());
            assertTrue(audioHandler.getQueue().isEmpty());
        }

        @Test
        @DisplayName("end first (LOAD_FAILED), not a blocked-by-YouTube failure: the next song plays exactly once")
        void endFirst_notOffered_pullsNextOnce()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack next = track("next");
            FriendlyException ex = blockedBy(blocked, Optional.empty(), false);
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            verify(audioPlayer, never()).playTrack(any());
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer, times(1)).playTrack(next);
            verify(audioPlayer, times(1)).playTrack(any());
            verify(prompt, never()).holdFor(any(), any());
        }

        @Test
        @DisplayName("end first as FINISHED with no audio (lavaplayer still logging the failure): the queue waits and the hold plays")
        void finishedEndFirst_zeroFrames_waitsAndPlaysHold()
        {
            AudioTrack blocked = trackWithFrames("blocked", false);
            AudioTrack hold = track("hold");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));
            enqueue(track("later"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);
            verify(audioPlayer, never()).playTrack(any());
            assertEquals(2, audioHandler.getQueue().size());
            Runnable fallback = scheduledFallback();

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            verify(audioPlayer).playTrack(hold);
            verify(prompt, never()).onHoldEnded(anyLong());

            fallback.run(); // the exception already settled it: nothing more happens
            verify(audioPlayer, times(1)).playTrack(any());
            assertEquals(2, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("end first as FINISHED with no audio and no exception after it: the queue moves on once, after the fallback")
        void finishedEndFirst_zeroFrames_noException_movesOnAfterFallback()
        {
            AudioTrack blocked = trackWithFrames("blocked", false);
            AudioTrack next = track("next");
            when(prompt.isCandidate(blocked)).thenReturn(true);
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);
            verify(audioPlayer, never()).playTrack(any());
            Runnable fallback = scheduledFallback();

            fallback.run();
            fallback.run();
            verify(audioPlayer, times(1)).playTrack(next);
            verify(audioPlayer, times(1)).playTrack(any());
        }

        @Test
        @DisplayName("end first as FINISHED with no audio, and the fallback can't be scheduled: the queue moves on at once")
        void finishedEndFirst_zeroFrames_schedulerDown_pullsImmediately()
        {
            AudioTrack blocked = trackWithFrames("blocked", false);
            AudioTrack next = track("next");
            when(prompt.isCandidate(blocked)).thenReturn(true);
            doThrow(new java.util.concurrent.RejectedExecutionException("shut down"))
                    .when(prompt).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);

            verify(audioPlayer, times(1)).playTrack(next);
        }

        @Test
        @DisplayName("feature on: a normal song end (FINISHED, audio played) pulls the next song at once")
        void candidate_finishedWithFrames_pullsImmediately()
        {
            AudioTrack song = trackWithFrames("song", true);
            AudioTrack next = track("next");
            when(prompt.isCandidate(song)).thenReturn(true);
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, song, AudioTrackEndReason.FINISHED);

            verify(audioPlayer, times(1)).playTrack(next);
            verify(prompt, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("end first as FINISHED after some audio: the queue moves on and the late hold is withdrawn")
        void finishedWithFramesEndFirst_withdrawsLateHold()
        {
            AudioTrack blocked = trackWithFrames("blocked", true);
            AudioTrack hold = track("hold");
            AudioTrack next = track("next");
            AudioTrack later = track("later");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(next);
            enqueue(later);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);
            verify(audioPlayer).playTrack(next);
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(prompt).onHoldEnded(GUILD_ID);
            // The withdrawn hold must not hijack the end of an unrelated track.
            audioHandler.onTrackEnd(audioPlayer, next, AudioTrackEndReason.FINISHED);
            verify(audioPlayer, never()).playTrack(hold);
            verify(audioPlayer).playTrack(later);
        }

        @Test
        @DisplayName("end first, a user's song starts in the gap: the late hold is withdrawn and nothing else is pulled")
        void endFirst_userPlaysInGap_withdrawsHold()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            AudioTrack user = track("user");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            audioHandler.playPrepared(user); // e.g. a play command finding the player idle
            when(audioPlayer.getPlayingTrack()).thenReturn(user);
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer, never()).playTrack(hold);
            verify(audioPlayer, times(1)).playTrack(any());
            verify(prompt).onHoldEnded(GUILD_ID);
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("end first, a user's song starts in the gap, no prompt: the queue isn't pulled over it")
        void endFirst_userPlaysInGap_noPrompt_doesNotPull()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack user = track("user");
            FriendlyException ex = blockedBy(blocked, Optional.empty(), true);
            enqueue(track("next"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            audioHandler.playPrepared(user);
            when(audioPlayer.getPlayingTrack()).thenReturn(user);
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer, times(1)).playTrack(any());
            verify(prompt, never()).onHoldEnded(anyLong());
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("end first as FINISHED with no audio, a user's song starts before the fallback: the fallback pulls nothing")
        void finishedEndFirst_userPlaysBeforeFallback_doesNotPull()
        {
            AudioTrack blocked = trackWithFrames("blocked", false);
            AudioTrack user = track("user");
            when(prompt.isCandidate(blocked)).thenReturn(true);
            enqueue(track("next"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);
            audioHandler.playPrepared(user);
            when(audioPlayer.getPlayingTrack()).thenReturn(user);
            scheduledFallback().run();

            verify(audioPlayer, times(1)).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("end first, then stop before the exception: the late hold is withdrawn and nothing plays")
        void endFirst_stopInGap_withdrawsHoldAndPlaysNothing()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            FriendlyException ex = blockedBy(blocked, Optional.of(hold), true);
            enqueue(track("next"));

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            audioHandler.stopAndClear();
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(audioPlayer, never()).playTrack(any());
            verify(prompt).onHoldEnded(GUILD_ID);
        }

        @Test
        @DisplayName("end first as FINISHED with no audio, then stop: neither the exception nor the fallback plays anything")
        void finishedEndFirst_stopInGap_fallbackDoesNothing()
        {
            AudioTrack blocked = trackWithFrames("blocked", false);
            when(prompt.isCandidate(blocked)).thenReturn(true);

            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);
            Runnable fallback = scheduledFallback();
            audioHandler.stopAndClearQueuePreserveHistory();
            enqueue(track("added after the stop"));
            fallback.run();

            verify(audioPlayer, never()).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("not a prompt candidate (e.g. feature off): LOAD_FAILED pulls the next song at once")
        void nonCandidate_loadFailed_pullsImmediately()
        {
            AudioTrack failed = track("failed");
            AudioTrack next = track("next");
            enqueue(next);

            audioHandler.onTrackEnd(audioPlayer, failed, AudioTrackEndReason.LOAD_FAILED);

            verify(audioPlayer).playTrack(next);
            audioHandler.onTrackException(audioPlayer, failed,
                    new FriendlyException("broken", FriendlyException.Severity.COMMON, null));
            verify(audioPlayer, times(1)).playTrack(any());
            verify(prompt, never()).holdFor(any(), any());
        }

        @Test
        @DisplayName("a prompt's hold is not added to history; the blocked track already is")
        void gatedHoldStart_notAddedToHistory()
        {
            audioHandler.onTrackStart(audioPlayer, libraryTrack(new CompletableFuture<>()));

            assertTrue(audioHandler.getPreviousTracks().isEmpty());
        }

        @Test
        @DisplayName("a failure without a prompt moves on to the next song as before")
        void notOffered_loadFailed_pullsNext()
        {
            AudioTrack failed = track("failed");
            AudioTrack next = track("next");
            FriendlyException ex = new FriendlyException("broken", FriendlyException.Severity.COMMON, null);
            enqueue(next);

            audioHandler.onTrackException(audioPlayer, failed, ex);
            audioHandler.onTrackEnd(audioPlayer, failed, AudioTrackEndReason.LOAD_FAILED);

            verify(prompt, never()).holdFor(any(), any());
            verify(audioPlayer).playTrack(next);
        }

        @Test
        @DisplayName("stop cancels an open prompt before the player stops its hold")
        void stop_cancelsPromptBeforeStopping()
        {
            audioHandler.stopAndClear();
            audioHandler.stopAndClearQueuePreserveHistory();

            org.mockito.InOrder order = inOrder(prompt, audioPlayer);
            order.verify(prompt).cancel(GUILD_ID);
            order.verify(audioPlayer).stopTrack();
            order.verify(prompt).cancel(GUILD_ID);
            order.verify(audioPlayer).stopTrack();
        }

        @Test
        @DisplayName("a hold track ending (skip, stop, finish) reports back to the prompt")
        void gatedLibraryTrackEnds_endsPrompt()
        {
            LibraryAudioTrack hold = libraryTrack(new CompletableFuture<>());

            audioHandler.onTrackEnd(audioPlayer, hold, AudioTrackEndReason.STOPPED);

            verify(prompt).onHoldEnded(GUILD_ID);
        }

        @Test
        @DisplayName("a library track failing is reported, not offered a prompt")
        void libraryTrackException_notifiesDownloadFailed()
        {
            LibraryAudioTrack library = libraryTrack(null);
            FriendlyException ex = new FriendlyException("yt-dlp couldn't download this track: x",
                    FriendlyException.Severity.COMMON, null);

            audioHandler.onTrackException(audioPlayer, library, ex);

            verify(prompt).notifyDownloadFailed(guild, library, ex);
            verify(prompt, never()).holdFor(any(), any());
        }

        @Test
        @DisplayName("status emoji shows paused while a hold waits for its prompt")
        void statusEmoji_pausedWhileHoldAwaitsGate()
        {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            when(audioPlayer.getPlayingTrack()).thenReturn(libraryTrack(gate));
            when(audioPlayer.isPaused()).thenReturn(false);

            assertEquals(AudioHandler.PAUSE_EMOJI, audioHandler.getStatusEmoji());
            gate.complete(null);
            assertEquals(AudioHandler.PLAY_EMOJI, audioHandler.getStatusEmoji());
        }

        @Test
        @DisplayName("stopping cancels the guild's queued downloads")
        void stop_cancelsGuildDownloads()
        {
            audioHandler.stopAndClear();
            audioHandler.stopAndClearQueuePreserveHistory();

            verify(ytDlp, times(2)).cancelGuild(GUILD_ID);
        }

        // ---- similar-song matching ----

        /** A track whose clone (for history) is another track with the same info. */
        private AudioTrack startable(String identifier)
        {
            AudioTrack track = track(identifier);
            AudioTrack clone = track(identifier);
            when(track.makeClone()).thenReturn(clone);
            return track;
        }

        /** Makes playTrack behave like lavaplayer: the old track ends (REPLACED), then the new one starts, inline. */
        private void playTrackDispatchesEvents(AudioTrack old)
        {
            doAnswer(inv -> {
                AudioTrack started = inv.getArgument(0);
                audioHandler.onTrackEnd(audioPlayer, old, AudioTrackEndReason.REPLACED);
                when(audioPlayer.getPlayingTrack()).thenReturn(started);
                audioHandler.onTrackStart(audioPlayer, started);
                return null;
            }).when(audioPlayer).playTrack(any());
        }

        @Test
        @DisplayName("onTrackStart asks for a similar-song suggestion; onTrackEnd expires it")
        void trackStartAndEnd_notifyMatchSuggestions()
        {
            com.jagrosh.jmusicbot.ytdlp.MatchSuggestions suggestions = mock(com.jagrosh.jmusicbot.ytdlp.MatchSuggestions.class);
            when(ytDlp.getMatchSuggestions()).thenReturn(suggestions);
            AudioTrack requested = startable("requested");

            audioHandler.onTrackStart(audioPlayer, requested);
            audioHandler.onTrackEnd(audioPlayer, requested, AudioTrackEndReason.FINISHED);

            verify(suggestions).consider(GUILD_ID, requested);
            verify(suggestions).onTrackEnded(GUILD_ID, requested);
        }

        @Test
        @DisplayName("a queued track handed off to its library copy at play time closes its suggestion")
        void handedOffTrack_closesItsSuggestion()
        {
            com.jagrosh.jmusicbot.ytdlp.MatchSuggestions suggestions = mock(com.jagrosh.jmusicbot.ytdlp.MatchSuggestions.class);
            when(ytDlp.getMatchSuggestions()).thenReturn(suggestions);
            AudioTrack original = track("orig");
            AudioTrack copy = track("copy");
            AudioTrack unchanged = track("unchanged");
            when(ytDlp.prepareForPlayback(original, GUILD_ID)).thenReturn(copy);

            audioHandler.playPrepared(original);
            audioHandler.playPrepared(unchanged);

            verify(suggestions).onTrackEnded(GUILD_ID, original);
            verify(suggestions, never()).onTrackEnded(GUILD_ID, unchanged);
        }

        @Test
        @DisplayName("no suggestion service (feature off): track start and end work as before")
        void noMatchSuggestions_noop()
        {
            when(ytDlp.getMatchSuggestions()).thenReturn(null);
            AudioTrack requested = startable("requested");

            assertDoesNotThrow(() -> {
                audioHandler.onTrackStart(audioPlayer, requested);
                audioHandler.onTrackEnd(audioPlayer, requested, AudioTrackEndReason.FINISHED);
            });
            assertEquals(1, audioHandler.getPreviousTracks().size());
        }

        @Test
        @DisplayName("Switch: the library copy replaces the stream through the player, with an empty queue, and the bot stays")
        void replaceWithLibraryCopy_emptyQueue_doesNotDisconnect()
        {
            // Pins that the requested track's REPLACED end (delivered inline by playTrack below) runs
            // no end-of-queue logic: continueAfterEnd ignores every REPLACED end, with no Switch flag.
            AudioTrack requested = startable("requested");
            LibraryAudioTrack copy = libraryTrack(null);
            when(audioPlayer.getPlayingTrack()).thenReturn(requested);
            audioHandler.onTrackStart(audioPlayer, requested);
            playTrackDispatchesEvents(requested);

            assertTrue(audioHandler.replaceWithLibraryCopy(requested, copy));

            verify(audioPlayer).playTrack(copy);
            verify(audioPlayer, never()).stopTrack();
            verify(bot, never()).closeAudioConnection(anyLong());
            // History keeps the requested song once; the library copy isn't added again.
            assertEquals(1, audioHandler.getPreviousTracks().size());
        }

        @Test
        @DisplayName("Switch with songs queued: the queue is not pulled")
        void replaceWithLibraryCopy_queued_doesNotPull()
        {
            AudioTrack requested = startable("requested");
            LibraryAudioTrack copy = libraryTrack(null);
            enqueue(track("next"));
            when(audioPlayer.getPlayingTrack()).thenReturn(requested);
            playTrackDispatchesEvents(requested);

            assertTrue(audioHandler.replaceWithLibraryCopy(requested, copy));

            verify(audioPlayer, times(1)).playTrack(any());
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("Switch is refused when the requested song is no longer playing")
        void replaceWithLibraryCopy_notPlaying_refused()
        {
            AudioTrack requested = startable("requested");
            AudioTrack other = track("other");
            when(audioPlayer.getPlayingTrack()).thenReturn(other);

            assertFalse(audioHandler.replaceWithLibraryCopy(requested, libraryTrack(null)));

            verify(audioPlayer, never()).playTrack(any());
        }

        @Test
        @DisplayName("a REPLACED end that isn't a switch follows the normal REPLACED rule (another track took over: no pull, no disconnect)")
        void replacedEnd_notASwitch_unchanged()
        {
            AudioTrack requested = startable("requested");
            LibraryAudioTrack copy = libraryTrack(null);
            when(audioPlayer.getPlayingTrack()).thenReturn(requested);
            playTrackDispatchesEvents(requested);
            audioHandler.replaceWithLibraryCopy(requested, copy);

            audioHandler.onTrackEnd(audioPlayer, copy, AudioTrackEndReason.REPLACED);

            verify(bot, never()).closeAudioConnection(GUILD_ID);
        }
    }
}
