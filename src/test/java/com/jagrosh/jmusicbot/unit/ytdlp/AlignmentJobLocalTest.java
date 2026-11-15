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
package com.jagrosh.jmusicbot.unit.ytdlp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.jagrosh.jmusicbot.audio.RecentFrames;
import com.jagrosh.jmusicbot.ytdlp.AlignmentJob;
import com.jagrosh.jmusicbot.ytdlp.SwitchAligner.Alignment;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real decode path on a real pair. Skipped unless {@code -Dytdlp.alignTest.dir=<dir>}
 * names a folder holding {@code library.webm} and {@code stream.webm};
 * {@code -Dytdlp.alignTest.offsetMs} is the expected offset (default -3100, the Axel F pair).
 */
@DisplayName("AlignmentJob local pair")
class AlignmentJobLocalTest {

    private record Frame(byte[] data, long timecodeMs) {
    }

    @Test
    void realPair_alignsMidSong() throws Exception {
        String dir = System.getProperty("ytdlp.alignTest.dir");
        Assumptions.assumeTrue(dir != null);
        long expected = Long.getLong("ytdlp.alignTest.offsetMs", -3100L);
        Path library = Path.of(dir, "library.webm");
        Path stream = Path.of(dir, "stream.webm");

        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(new LocalAudioSourceManager());
        List<Frame> frames = new ArrayList<>();
        long streamLength;
        long libraryLength;
        try {
            AudioTrack streamTrack = (AudioTrack) manager.loadItemSync(stream.toString());
            libraryLength = ((AudioTrack) manager.loadItemSync(library.toString())).getDuration();
            streamLength = streamTrack.getDuration();
            AudioPlayer player = manager.createPlayer();
            player.playTrack(streamTrack);
            while (true) {
                AudioFrame f;
                try {
                    f = player.provide(3, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    f = null;
                }
                if (f == null) {
                    if (player.getPlayingTrack() == null) {
                        break;
                    }
                    continue;
                }
                frames.add(new Frame(f.getData(), f.getTimecode()));
            }
            player.destroy();
        } finally {
            manager.shutdown();
        }

        for (long posMs : new long[] {45_000, 90_000, 150_000}) {
            RecentFrames ring = new RecentFrames(1500);
            for (Frame f : frames) {
                if (f.timecodeMs() < posMs) {
                    ring.add(f.data(), f.timecodeMs());
                }
            }
            long t0 = System.nanoTime();
            Optional<Alignment> a = AlignmentJob.run(ring.snapshot(), library, streamLength, libraryLength);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("pos %d ms -> %s in %d ms%n", posMs, a, ms);
            assertTrue(a.isPresent(), "alignment expected at " + posMs);
            assertEquals(expected, a.get().offsetMs(), 20);
        }
    }
}
