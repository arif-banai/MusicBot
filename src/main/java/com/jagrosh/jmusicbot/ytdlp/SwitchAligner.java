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
package com.jagrosh.jmusicbot.ytdlp;

import java.util.Optional;

/**
 * Pure maths for lining up a stream with its library copy: multi-band log-energy envelopes
 * (512-point FFT, 20 ms hop, 12 log-spaced bands from 80 to 3800 Hz, at 8 kHz) matched by the
 * mean per-band Pearson correlation over every lag. A match is accepted only when it is both
 * strong ({@code r >= 0.80}) and unambiguous ({@code margin >= 0.20}, where the margin is
 * {@code r} minus the best score more than 1 s away). See docs/ytdlp-library.md,
 * "Switch alignment".
 *
 * @author Arif Banai (arif-banai)
 */
public final class SwitchAligner {

    public static final int SAMPLE_RATE = 8000;
    /** One feature row per 20 ms. */
    public static final int HOP_MS = 20;
    public static final double MIN_R = 0.80;
    public static final double MIN_MARGIN = 0.20;

    static final int BANDS = 12;
    private static final int HOP = SAMPLE_RATE * HOP_MS / 1000;
    private static final int WIN = 512;
    private static final int EXCLUDE_LAGS = 1000 / HOP_MS;
    /** Per-frame variance below this makes a band uninformative; it then scores 0. */
    private static final double MIN_VARIANCE = 1e-6;
    private static final int[] EDGES = bandEdges();
    private static final double[] HANN = hann();

    /**
     * A match between the stream and the library copy.
     *
     * @param offsetMs library time minus stream time (library position = stream position + offset)
     * @param r        the mean per-band correlation at the best lag
     * @param margin   {@code r} minus the best score more than 1 s away
     */
    public record Alignment(long offsetMs, double r, double margin) {
    }

    private SwitchAligner() {
    }

    /** Log band energies, {@code [frame][12]}; frame {@code i} starts at sample {@code i * 160}. */
    public static float[][] features(short[] mono8k) {
        int frames = Math.max(0, (mono8k.length - WIN) / HOP + 1);
        float[][] out = new float[frames][BANDS];
        double[] re = new double[WIN];
        double[] im = new double[WIN];
        for (int t = 0; t < frames; t++) {
            for (int i = 0; i < WIN; i++) {
                re[i] = mono8k[t * HOP + i] / 32768.0 * HANN[i];
                im[i] = 0;
            }
            fft(re, im);
            for (int b = 0; b < BANDS; b++) {
                double energy = 0;
                for (int k = EDGES[b]; k < Math.max(EDGES[b + 1], EDGES[b] + 1); k++) {
                    energy += re[k] * re[k] + im[k] * im[k];
                }
                out[t][b] = (float) Math.log(1e-9 + energy);
            }
        }
        return out;
    }

    /**
     * Finds where the query sits inside the window and applies the accept rule.
     *
     * @param windowStartMs library time of window row 0
     * @param queryStartMs  stream time of query row 0
     * @return the alignment, or empty when it is weak, ambiguous or impossible
     */
    public static Optional<Alignment> bestOffset(float[][] queryFeatures, float[][] windowFeatures,
                                                 long windowStartMs, long queryStartMs) {
        return match(queryFeatures, windowFeatures, windowStartMs, queryStartMs).filter(SwitchAligner::accepts);
    }

    /** The best lag with its score and margin, before the accept rule; empty when nothing can be scored. */
    public static Optional<Alignment> match(float[][] queryFeatures, float[][] windowFeatures,
                                            long windowStartMs, long queryStartMs) {
        double[] scores = scores(queryFeatures, windowFeatures);
        int best = -1;
        for (int i = 0; i < scores.length; i++) {
            if (best < 0 || scores[i] > scores[best]) {
                best = i;
            }
        }
        if (best < 0) {
            return Optional.empty();
        }
        double second = Double.NaN;
        for (int i = 0; i < scores.length; i++) {
            if (Math.abs(i - best) > EXCLUDE_LAGS && !(scores[i] <= second)) {
                second = scores[i];
            }
        }
        long offset = windowStartMs + (long) best * HOP_MS - queryStartMs;
        // No lag more than 1 s away means uniqueness can't be checked: margin NaN, rejected.
        return Optional.of(new Alignment(offset, scores[best], scores[best] - second));
    }

    /** The accept rule: {@code r >= 0.80} and {@code margin >= 0.20}; NaN never passes. */
    public static boolean accepts(Alignment a) {
        return a.r() >= MIN_R && a.margin() >= MIN_MARGIN;
    }

    /** Mean over bands of the Pearson correlation of the query with the window at every lag. */
    private static double[] scores(float[][] q, float[][] x) {
        int n = q.length;
        int lags = x.length - n + 1;
        if (n < 2 || lags <= 0) {
            return new double[0];
        }
        double[] score = new double[lags];
        double[] q0 = new double[n];
        double[] xc = new double[x.length];
        double[] p1 = new double[x.length + 1];
        double[] p2 = new double[x.length + 1];
        for (int b = 0; b < BANDS; b++) {
            double qMean = 0;
            for (float[] row : q) {
                qMean += row[b];
            }
            qMean /= n;
            double qNorm = 0;
            for (int i = 0; i < n; i++) {
                q0[i] = q[i][b] - qMean;
                qNorm += q0[i] * q0[i];
            }
            if (qNorm / n < MIN_VARIANCE) {
                continue;
            }
            qNorm = Math.sqrt(qNorm);
            double xMean = 0;
            for (float[] row : x) {
                xMean += row[b];
            }
            xMean /= x.length;
            for (int i = 0; i < x.length; i++) {
                xc[i] = x[i][b] - xMean;
                p1[i + 1] = p1[i] + xc[i];
                p2[i + 1] = p2[i] + xc[i] * xc[i];
            }
            for (int lag = 0; lag < lags; lag++) {
                double s1 = p1[lag + n] - p1[lag];
                double var = (p2[lag + n] - p2[lag]) - s1 * s1 / n;
                if (var / n < MIN_VARIANCE) {
                    continue;
                }
                double dot = 0;
                for (int i = 0; i < n; i++) {
                    dot += q0[i] * xc[lag + i];
                }
                score[lag] += dot / (qNorm * Math.sqrt(var)) / BANDS;
            }
        }
        return score;
    }

    private static int[] bandEdges() {
        int[] edges = new int[BANDS + 1];
        for (int b = 0; b <= BANDS; b++) {
            edges[b] = (int) Math.round(80 * Math.pow(3800 / 80.0, b / (double) BANDS) * WIN / SAMPLE_RATE);
        }
        return edges;
    }

    private static double[] hann() {
        double[] w = new double[WIN];
        for (int i = 0; i < WIN; i++) {
            w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / WIN);
        }
        return w;
    }

    /** In-place radix-2 FFT. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }
}
