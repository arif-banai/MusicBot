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
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
        @DisplayName("a blocked track that gets a prompt is replaced by its hold, and the queue waits")
        void offeredPrompt_loadFailed_playsHoldWithoutPullingQueue()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            FriendlyException ex = new FriendlyException("blocked", FriendlyException.Severity.SUSPICIOUS, null);
            when(prompt.shouldOffer(blocked, ex)).thenReturn(true);
            when(prompt.holdFor(guild, blocked)).thenReturn(Optional.of(hold));
            enqueue(track("next"));
            when(audioPlayer.getPlayingTrack()).thenReturn(blocked); // still current while the exception is reported

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);

            verify(audioPlayer).playTrack(hold);
            assertEquals(1, audioHandler.getQueue().size());
            verify(prompt, never()).onHoldEnded(anyLong());
        }

        @Test
        @DisplayName("a blocked track stopped before its hold starts closes the prompt and moves on")
        void blockedTrackStopped_closesPromptAndPullsNext()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            AudioTrack next = track("next");
            FriendlyException ex = new FriendlyException("blocked", FriendlyException.Severity.SUSPICIOUS, null);
            when(prompt.shouldOffer(blocked, ex)).thenReturn(true);
            when(prompt.holdFor(guild, blocked)).thenReturn(Optional.of(hold));
            enqueue(next);
            when(audioPlayer.getPlayingTrack()).thenReturn(blocked);

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.STOPPED);

            verify(prompt).onHoldEnded(GUILD_ID);
            verify(audioPlayer, never()).playTrack(hold);
            verify(audioPlayer).playTrack(next);
        }

        @Test
        @DisplayName("a load failure reported as FINISHED (lavaplayer race) still plays the hold")
        void offeredPrompt_finishedRace_playsHold()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            FriendlyException ex = new FriendlyException("blocked", FriendlyException.Severity.SUSPICIOUS, null);
            when(prompt.shouldOffer(blocked, ex)).thenReturn(true);
            when(prompt.holdFor(guild, blocked)).thenReturn(Optional.of(hold));
            enqueue(track("next"));
            when(audioPlayer.getPlayingTrack()).thenReturn(blocked);

            audioHandler.onTrackException(audioPlayer, blocked, ex);
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.FINISHED);

            verify(audioPlayer).playTrack(hold);
            assertEquals(1, audioHandler.getQueue().size());
        }

        @Test
        @DisplayName("if the blocked track's end ran before its hold was offered, the hold is withdrawn")
        void endBeforeException_withdrawsHoldAndClosesPrompt()
        {
            AudioTrack blocked = track("blocked");
            AudioTrack hold = track("hold");
            AudioTrack next = track("next");
            AudioTrack later = track("later");
            FriendlyException ex = new FriendlyException("blocked", FriendlyException.Severity.SUSPICIOUS, null);
            when(prompt.shouldOffer(blocked, ex)).thenReturn(true);
            when(prompt.holdFor(guild, blocked)).thenReturn(Optional.of(hold));
            enqueue(next);
            enqueue(later);

            // The audio thread ends the blocked track first and moves on to the next song...
            audioHandler.onTrackEnd(audioPlayer, blocked, AudioTrackEndReason.LOAD_FAILED);
            when(audioPlayer.getPlayingTrack()).thenReturn(next);
            // ...and only then does the playback thread report the exception.
            audioHandler.onTrackException(audioPlayer, blocked, ex);

            verify(prompt).onHoldEnded(GUILD_ID);
            // The withdrawn hold must not hijack the end of an unrelated track.
            audioHandler.onTrackEnd(audioPlayer, next, AudioTrackEndReason.FINISHED);
            verify(audioPlayer, never()).playTrack(hold);
            verify(audioPlayer).playTrack(later);
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
    }
}
