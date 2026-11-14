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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.jagrosh.jmusicbot.audio.RecentFrames;
import com.jagrosh.jmusicbot.ytdlp.SwitchAligner.Alignment;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.format.transcoder.AudioChunkDecoder;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One alignment attempt for "Switch to library copy": decodes the ring of sent Opus frames and
 * the matching window of the library file, then tries a 30 s query and a 10 s query (see
 * {@link SwitchAligner}). Blocking and CPU-bound (about 0.3-0.7 s), so run it off the event
 * thread with a timeout; it stops early when interrupted and never throws.
 *
 * @author Arif Banai (arif-banai)
 */
public final class AlignmentJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlignmentJob.class);

    /** Fewer contiguous frames than 10 s and the job is skipped. */
    public static final int MIN_FRAMES = 500;
    private static final long FRAME_MS = 20;
    private static final int[] QUERY_SECONDS = {30, 10};
    private static final long MAX_WINDOW_MS = 60_000;
    private static final long END_GUARD_MS = 2_000;
    /** Hard cap on the library decode, in case the caller never cancels. */
    private static final long DECODE_DEADLINE_MS = 5_000;
    private static final int SAMPLES_PER_FRAME = (int) (SwitchAligner.SAMPLE_RATE * FRAME_MS / 1000);

    private record Window(short[] samples, long startMs) {
    }

    private AlignmentJob() {
    }

    /**
     * Lines up the stream that produced {@code snap} with {@code libraryFile}.
     *
     * @return the accepted alignment, whose {@code offsetMs} is the library position minus
     *         {@code snap.lastTimecodeMs()}; empty on rejection, failure or interruption
     */
    public static Optional<Alignment> run(RecentFrames.Snapshot snap, Path libraryFile,
                                          long streamLengthMs, long libraryLengthMs) {
        try {
            if (snap == null || libraryFile == null || snap.frames().size() < MIN_FRAMES
                    || !Files.isRegularFile(libraryFile)) {
                return Optional.empty();
            }
            List<byte[]> frames = snap.frames();
            float[][] ring = SwitchAligner.features(decodeOpus(frames));
            long ringStartMs = snap.lastTimecodeMs() + FRAME_MS - frames.size() * FRAME_MS;
            long w = windowMs(streamLengthMs, libraryLengthMs);
            long from = Math.max(0, ringStartMs - w);
            long to = snap.lastTimecodeMs() + FRAME_MS + w;
            if (libraryLengthMs > 0) {
                to = Math.min(to, libraryLengthMs);
            }
            if (to <= from) {
                return Optional.empty();
            }
            Window lib = decodeLibrary(libraryFile, from, to);
            if (lib == null) {
                return Optional.empty();
            }
            float[][] libFeatures = SwitchAligner.features(lib.samples());
            int previousRows = -1;
            for (int seconds : QUERY_SECONDS) {
                int rows = Math.min(ring.length, featureRows(seconds * SwitchAligner.SAMPLE_RATE));
                if (rows == previousRows || Thread.currentThread().isInterrupted()) {
                    continue;
                }
                previousRows = rows;
                int first = ring.length - rows;
                Optional<Alignment> m = SwitchAligner.match(Arrays.copyOfRange(ring, first, ring.length),
                        libFeatures, lib.startMs(), ringStartMs + first * FRAME_MS);
                LOGGER.debug("Switch alignment {} s query: r={} margin={} offset={} ms", seconds,
                        m.map(a -> String.format(Locale.ROOT, "%.2f", a.r())).orElse("-"),
                        m.map(a -> String.format(Locale.ROOT, "%.2f", a.margin())).orElse("-"),
                        m.map(a -> String.valueOf(a.offsetMs())).orElse("-"));
                if (m.isPresent() && SwitchAligner.accepts(m.get())) {
                    return m;
                }
            }
            return Optional.empty();
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else {
                LOGGER.warn("Switch alignment failed for {}: {}", libraryFile, t.toString());
            }
            return Optional.empty();
        }
    }

    /**
     * Where the library copy should start: {@code currentStreamPosMs + a.offsetMs()}, or empty
     * when that lies outside {@code [0, libraryLengthMs - 2 s]}.
     */
    public static OptionalLong targetPosition(long currentStreamPosMs, Alignment a, long libraryLengthMs) {
        long target = currentStreamPosMs + a.offsetMs();
        if (target < 0 || target > libraryLengthMs - END_GUARD_MS) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(target);
    }

    /** The search half-width: min(|stream length - library length| + 10 s, 60 s). */
    static long windowMs(long streamLengthMs, long libraryLengthMs) {
        boolean known = streamLengthMs > 0 && libraryLengthMs > 0
                && streamLengthMs < Long.MAX_VALUE / 2 && libraryLengthMs < Long.MAX_VALUE / 2;
        return known ? Math.min(Math.abs(streamLengthMs - libraryLengthMs) + 10_000, MAX_WINDOW_MS) : MAX_WINDOW_MS;
    }

    private static int featureRows(int samples) {
        return Math.max(0, (samples - 512) / (SwitchAligner.SAMPLE_RATE * SwitchAligner.HOP_MS / 1000) + 1);
    }

    /** Discord Opus (48 kHz stereo) to 8 kHz mono: downmix, then average every 6 samples. */
    private static short[] decodeOpus(List<byte[]> frames) {
        short[] out = new short[frames.size() * SAMPLES_PER_FRAME];
        ShortBuffer buf = ByteBuffer.allocateDirect(960 * 2 * 2 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        AudioChunkDecoder decoder = StandardAudioDataFormats.DISCORD_OPUS.createDecoder();
        try {
            for (int f = 0; f < frames.size(); f++) {
                decoder.decode(frames.get(f), buf);
                int k = f * SAMPLES_PER_FRAME;
                int end = k + SAMPLES_PER_FRAME;
                while (k < end && buf.remaining() >= 12) {
                    int acc = 0;
                    for (int i = 0; i < 12; i++) {
                        acc += buf.get();
                    }
                    out[k++] = (short) (acc / 12);
                }
            }
        } finally {
            decoder.close();
        }
        return out;
    }

    /** Decodes library audio from {@code fromMs} to {@code toMs} as 8 kHz mono, or null on failure. */
    private static Window decodeLibrary(Path file, long fromMs, long toMs) throws InterruptedException {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        AudioPlayer player = null;
        try {
            // Mono output is broken in lavaplayer 2.2.7 (frames double); decode stereo and downmix.
            manager.getConfiguration().setOutputFormat(
                    new Pcm16AudioDataFormat(2, SwitchAligner.SAMPLE_RATE, SAMPLES_PER_FRAME, false));
            manager.registerSourceManager(new LocalAudioSourceManager());
            AudioItem item = manager.loadItemSync(file.toAbsolutePath().toString());
            if (!(item instanceof AudioTrack track)) {
                return null;
            }
            if (fromMs > 0) {
                track.setPosition(fromMs);
            }
            player = manager.createPlayer();
            player.playTrack(track);
            short[] out = new short[(int) ((toMs - fromMs) / FRAME_MS + 1) * SAMPLES_PER_FRAME];
            int k = 0;
            long startMs = -1;
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DECODE_DEADLINE_MS);
            while (k < out.length) {
                if (System.nanoTime() > deadline) {
                    return null;
                }
                AudioFrame frame;
                try {
                    frame = player.provide(250, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    frame = null;
                }
                if (frame == null || frame.isTerminator()) {
                    if (player.getPlayingTrack() == null || (frame != null && frame.isTerminator())) {
                        break;
                    }
                    continue;
                }
                if (startMs < 0) {
                    startMs = frame.getTimecode();
                }
                if (frame.getTimecode() >= toMs) {
                    break;
                }
                byte[] d = frame.getData();
                for (int i = 0; i + 3 < d.length && k < out.length; i += 4) {
                    int left = (short) ((d[i] & 0xff) | (d[i + 1] << 8));
                    int right = (short) ((d[i + 2] & 0xff) | (d[i + 3] << 8));
                    out[k++] = (short) ((left + right) / 2);
                }
            }
            return startMs < 0 ? null : new Window(Arrays.copyOf(out, k), startMs);
        } finally {
            if (player != null) {
                player.destroy();
            }
            manager.shutdown();
        }
    }
}
