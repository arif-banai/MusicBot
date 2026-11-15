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

import com.jagrosh.jmusicbot.audio.RecentFrames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RecentFrames Tests")
class RecentFramesTest {

    @Test
    @DisplayName("holds at most 1500 frames, keeping the newest")
    void holdsAtMost1500() {
        RecentFrames ring = new RecentFrames(1500);
        for (int i = 0; i < 2000; i++) {
            ring.add(new byte[] {(byte) i}, i * 20L);
        }
        RecentFrames.Snapshot s = ring.snapshot();
        assertEquals(1500, s.frames().size());
        assertEquals(1999 * 20L, s.lastTimecodeMs());
        assertEquals((byte) 500, s.frames().get(0)[0]);
    }

    @Test
    @DisplayName("a timecode jump (forward or backward) resets the run")
    void jumpResetsRun() {
        RecentFrames ring = new RecentFrames(1500);
        for (int i = 0; i < 100; i++) {
            ring.add(new byte[] {1}, i * 20L);
        }
        ring.add(new byte[] {2}, 60_000);
        ring.add(new byte[] {3}, 60_020);
        RecentFrames.Snapshot s = ring.snapshot();
        assertEquals(2, s.frames().size());
        assertEquals(60_020, s.lastTimecodeMs());

        ring.add(new byte[] {4}, 10_000);
        assertEquals(1, ring.snapshot().frames().size());

        ring.clear();
        RecentFrames.Snapshot empty = ring.snapshot();
        assertTrue(empty.frames().isEmpty());
        assertEquals(-1, empty.lastTimecodeMs());
    }

    @Test
    @DisplayName("add and snapshot copy the bytes")
    void snapshotReturnsCopies() {
        RecentFrames ring = new RecentFrames(1500);
        byte[] data = {7, 8};
        ring.add(data, 0);
        data[0] = 99;
        RecentFrames.Snapshot s = ring.snapshot();
        assertEquals(7, s.frames().get(0)[0]);

        s.frames().get(0)[0] = 55;
        ring.add(new byte[] {9}, 20);
        assertEquals(7, ring.snapshot().frames().get(0)[0]);
        assertEquals(1, s.frames().size());
        assertThrows(UnsupportedOperationException.class, () -> s.frames().add(new byte[0]));
    }
}
