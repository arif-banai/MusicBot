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
package com.jagrosh.jmusicbot.unit.bilibili;

import com.jagrosh.jmusicbot.audio.bilibili.BilibiliRequestThrottle;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BilibiliRequestThrottle Tests")
class BilibiliRequestThrottleTest
{
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<Long> sleeps = new ArrayList<>();

    /** A fake clock: sleeping just advances time, so no test waits for real. */
    private BilibiliRequestThrottle throttle(long intervalMs, long cooldownMs)
    {
        return new BilibiliRequestThrottle(intervalMs, cooldownMs, now::get, ms ->
        {
            sleeps.add(ms);
            now.addAndGet(ms);
        });
    }

    @AfterEach
    void clearInterrupt()
    {
        Thread.interrupted();
    }

    @Test
    @DisplayName("lets the first request through without waiting")
    void firstRequestIsImmediate() throws Exception
    {
        throttle(1000, 60_000).acquire();
        assertEquals(List.of(), sleeps);
    }

    @Test
    @DisplayName("spaces back-to-back requests by the interval")
    void spacesRequests() throws Exception
    {
        BilibiliRequestThrottle throttle = throttle(1000, 60_000);
        throttle.acquire();
        throttle.acquire();
        throttle.acquire();
        assertEquals(List.of(1000L, 1000L), sleeps);
    }

    @Test
    @DisplayName("does not wait when the interval has already passed")
    void noWaitAfterIdle() throws Exception
    {
        BilibiliRequestThrottle throttle = throttle(1000, 60_000);
        throttle.acquire();
        now.addAndGet(5000);
        throttle.acquire();
        assertEquals(List.of(), sleeps);
    }

    @Test
    @DisplayName("waits only for the rest of the interval")
    void waitsForRemainder() throws Exception
    {
        BilibiliRequestThrottle throttle = throttle(1000, 60_000);
        throttle.acquire();
        now.addAndGet(300);
        throttle.acquire();
        assertEquals(List.of(700L), sleeps);
    }

    @Test
    @DisplayName("refuses requests during a cooldown instead of adding to the block")
    void refusesDuringCooldown() throws Exception
    {
        BilibiliRequestThrottle throttle = throttle(1000, 60_000);
        throttle.startCooldown();

        FriendlyException thrown = assertThrows(FriendlyException.class, throttle::acquire);
        assertEquals(FriendlyException.Severity.COMMON, thrown.severity);
        assertTrue(thrown.getMessage().contains("try again"), thrown.getMessage());
        assertEquals(List.of(), sleeps);
    }

    @Test
    @DisplayName("allows requests again once the cooldown ends")
    void resumesAfterCooldown() throws Exception
    {
        BilibiliRequestThrottle throttle = throttle(1000, 60_000);
        throttle.startCooldown();
        now.addAndGet(60_000);
        assertDoesNotThrow(throttle::acquire);
    }

    @Test
    @DisplayName("keeps the interrupt flag when interrupted, so skip and stop still work")
    void preservesInterrupt()
    {
        BilibiliRequestThrottle throttle = new BilibiliRequestThrottle(1000, 60_000, now::get, ms ->
        {
            throw new InterruptedException();
        });

        assertDoesNotThrow(throttle::acquire);
        assertThrows(InterruptedIOException.class, throttle::acquire);
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
