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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;

import com.jagrosh.jmusicbot.audio.RecentFrames;
import com.jagrosh.jmusicbot.ytdlp.AlignmentJob;
import com.jagrosh.jmusicbot.ytdlp.SwitchAligner;
import com.jagrosh.jmusicbot.ytdlp.SwitchAligner.Alignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SwitchAligner Tests")
class SwitchAlignerTest {

    private static final int SR = 8000;

    /** Seeded "music": tones spread over 80-3800 Hz, each with its own non-periodic note envelope. */
    private static short[] music(long seed, int seconds) {
        Random rnd = new Random(seed);
        int n = seconds * SR;
        double[] mix = new double[n];
        int tones = 30;
        for (int k = 0; k < tones; k++) {
            double freq = 90 * Math.pow(3600 / 90.0, (k + rnd.nextDouble()) / tones);
            double phase = rnd.nextDouble() * 2 * Math.PI;
            int i = 0;
            while (i < n) {
                int len = (int) (SR * (0.1 + 0.2 * rnd.nextDouble()));
                double amp = rnd.nextDouble() < 0.4 ? 0 : rnd.nextDouble();
                for (int j = i; j < Math.min(n, i + len); j++) {
                    mix[j] += amp * Math.sin(2 * Math.PI * freq * j / SR + phase);
                }
                i += len;
            }
        }
        double peak = 1e-9;
        for (double v : mix) {
            peak = Math.max(peak, Math.abs(v));
        }
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) Math.round(mix[i] / peak * 20000);
        }
        return out;
    }

    private static short[] slice(short[] x, double fromS, double toS) {
        return Arrays.copyOfRange(x, (int) Math.round(fromS * SR), (int) Math.round(toS * SR));
    }

    private static short[] noise(long seed, int seconds) {
        Random rnd = new Random(seed);
        short[] out = new short[seconds * SR];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (rnd.nextGaussian() * 5000);
        }
        return out;
    }

    @Test
    @DisplayName("a gain-changed copy with a 3.1 s lead-in is found at -3100 ms")
    void shiftedCopy_isFound() {
        short[] library = music(42, 120);
        // The stream has a 3.1 s lead-in at half the gain: stream(t) = 0.5 * library(t - 3.1 s).
        short[] stream = new short[library.length];
        int shift = (int) (3.1 * SR);
        for (int i = shift; i < stream.length; i++) {
            stream[i] = (short) (library[i - shift] / 2);
        }
        float[][] query = SwitchAligner.features(slice(stream, 60, 90));
        float[][] window = SwitchAligner.features(slice(library, 40, 110));

        Optional<Alignment> a = SwitchAligner.bestOffset(query, window, 40_000, 60_000);

        assertTrue(a.isPresent(), "alignment expected");
        assertEquals(-3100, a.get().offsetMs(), 20);
        assertTrue(a.get().r() >= 0.80, "r=" + a.get().r());
        assertTrue(a.get().margin() >= 0.20, "margin=" + a.get().margin());
    }

    @Test
    @DisplayName("independent noise is rejected")
    void noise_isRejected() {
        float[][] query = SwitchAligner.features(noise(1, 30));
        float[][] window = SwitchAligner.features(music(2, 70));

        assertTrue(SwitchAligner.bestOffset(query, window, 0, 30_000).isEmpty());
    }

    @Test
    @DisplayName("an all-zero query is rejected and produces no NaN")
    void silence_isRejected() {
        float[][] query = SwitchAligner.features(new short[30 * SR]);
        float[][] window = SwitchAligner.features(music(3, 70));
        for (float[] row : query) {
            for (float v : row) {
                assertFalse(Float.isNaN(v));
            }
        }

        assertTrue(SwitchAligner.bestOffset(query, window, 0, 30_000).isEmpty());
        // A silent window against real music must be rejected too.
        assertTrue(SwitchAligner.bestOffset(SwitchAligner.features(music(3, 30)),
                SwitchAligner.features(new short[70 * SR]), 0, 30_000).isEmpty());
    }

    @Test
    @DisplayName("a repeating 4 s loop is rejected because the margin fails")
    void repeatingLoop_isRejected() {
        short[] loop = music(4, 4);
        short[] song = new short[80 * SR];
        for (int i = 0; i < song.length; i++) {
            song[i] = loop[i % loop.length];
        }
        float[][] query = SwitchAligner.features(slice(song, 40, 70));
        float[][] window = SwitchAligner.features(slice(song, 0, 80));

        // The match itself is strong; only the margin rule rejects it.
        Alignment raw = SwitchAligner.match(query, window, 0, 40_000).orElseThrow();
        assertTrue(raw.r() >= 0.80, "r=" + raw.r());
        assertTrue(raw.margin() < 0.20, "margin=" + raw.margin());
        assertTrue(SwitchAligner.bestOffset(query, window, 0, 40_000).isEmpty());
    }

    @Test
    @DisplayName("features gives 12 bands per 20 ms frame")
    void features_shape() {
        float[][] f = SwitchAligner.features(new short[10 * SR]);
        assertEquals((10 * SR - 512) / 160 + 1, f.length);
        assertEquals(12, f[0].length);
        assertEquals(0, SwitchAligner.features(new short[100]).length);
    }

    @Test
    @DisplayName("targetPosition applies the [0, length - 2 s] bound")
    void targetPosition_bounds() {
        Alignment a = new Alignment(-3100, 0.9, 0.4);
        assertEquals(OptionalLong.of(46_900), AlignmentJob.targetPosition(50_000, a, 170_000));
        assertTrue(AlignmentJob.targetPosition(3_000, a, 170_000).isEmpty());
        assertTrue(AlignmentJob.targetPosition(171_200, a, 170_000).isEmpty());
        assertEquals(OptionalLong.of(168_000), AlignmentJob.targetPosition(171_100, a, 170_000));
    }

    @Test
    @DisplayName("the job returns empty without throwing for a short ring or a missing file")
    void job_neverThrows() {
        RecentFrames ring = new RecentFrames(1500);
        for (int i = 0; i < 400; i++) {
            ring.add(new byte[] {1, 2, 3}, i * 20L);
        }
        Path missing = Path.of("does-not-exist-" + System.nanoTime() + ".webm");
        assertTrue(AlignmentJob.run(ring.snapshot(), missing, 180_000, 170_000).isEmpty());
        assertTrue(AlignmentJob.run(new RecentFrames.Snapshot(List.of(), -1), missing, 0, 0).isEmpty());
        assertTrue(AlignmentJob.run(null, null, 0, 0).isEmpty());
    }
}
