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
package com.jagrosh.jmusicbot.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A ring of the newest Opus frames sent to Discord, used to line up "Switch to library copy"
 * with the stream. Only the newest contiguous run is kept: a timecode jump of more than
 * 100 ms (a seek, in either direction) starts a new run. Thread-safe; {@link #add} runs on the
 * audio send thread and only copies bytes.
 *
 * @author Arif Banai (arif-banai)
 */
public final class RecentFrames {

    /** Largest timecode step, in ms, that still counts as contiguous. */
    private static final long MAX_STEP_MS = 100;

    /**
     * The newest contiguous run of frames, oldest first.
     *
     * @param frames         copies of the Opus frames; unmodifiable
     * @param lastTimecodeMs the newest frame's timecode, or -1 when empty
     */
    public record Snapshot(List<byte[]> frames, long lastTimecodeMs) {
    }

    private final int maxFrames;
    private final ArrayDeque<byte[]> frames;
    private long lastTimecodeMs = -1;

    public RecentFrames(int maxFrames) {
        if (maxFrames <= 0) {
            throw new IllegalArgumentException("maxFrames must be positive");
        }
        this.maxFrames = maxFrames;
        this.frames = new ArrayDeque<>(maxFrames);
    }

    /** Appends a copy of {@code opus}; a jump of more than 100 ms resets the run first. */
    public synchronized void add(byte[] opus, long timecodeMs) {
        if (!frames.isEmpty() && Math.abs(timecodeMs - lastTimecodeMs) > MAX_STEP_MS) {
            frames.clear();
        }
        if (frames.size() == maxFrames) {
            frames.removeFirst();
        }
        frames.addLast(opus.clone());
        lastTimecodeMs = timecodeMs;
    }

    public synchronized void clear() {
        frames.clear();
        lastTimecodeMs = -1;
    }

    /** Copies of the newest contiguous run, independent of later changes to the ring. */
    public synchronized Snapshot snapshot() {
        List<byte[]> copy = new ArrayList<>(frames.size());
        for (byte[] frame : frames) {
            copy.add(frame.clone());
        }
        return new Snapshot(Collections.unmodifiableList(copy), frames.isEmpty() ? -1 : lastTimecodeMs);
    }
}
