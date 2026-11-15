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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.jagrosh.jmusicbot.ytdlp.PendingPrompts;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts.Pending;
import com.jagrosh.jmusicbot.ytdlp.PendingPrompts.PressOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class PendingPromptsTest {

    private static final long GUILD = 111L;
    private static final String VIDEO_ID = "a_b-c_d1234";
    private static final long REQUESTER = 222L;
    private static final long CHANNEL = 333L;

    private static Pending pending(long guildId, String videoId) {
        return new Pending(guildId, videoId, REQUESTER, CHANNEL, null, new CompletableFuture<>(), 0L);
    }

    @Test
    @DisplayName("open: a second open for the same guild returns false")
    void open_onePerGuild() {
        PendingPrompts prompts = new PendingPrompts();
        assertTrue(prompts.open(pending(GUILD, VIDEO_ID)));
        assertFalse(prompts.open(pending(GUILD, "other12345")));

        // The original stays in place; the rejected open didn't overwrite it.
        Optional<Pending> stored = prompts.get(GUILD);
        assertTrue(stored.isPresent());
        assertEquals(VIDEO_ID, stored.get().videoId());
    }

    @Test
    @DisplayName("press: matching video ID is accepted")
    void press_matchingVideo_accepted() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("press: wrong video ID is expired")
    void press_wrongVideo_expired() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        assertEquals(PressOutcome.EXPIRED, prompts.press(GUILD, "wrongvideo1"));
    }

    @Test
    @DisplayName("press: no open prompt for guild is expired")
    void press_noPromptForGuild_expired() {
        PendingPrompts prompts = new PendingPrompts();

        assertEquals(PressOutcome.EXPIRED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("press: a second press on the same prompt is expired")
    void secondPress_isExpired() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
        assertEquals(PressOutcome.EXPIRED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("press: after close (timeout/skip/stop) is expired")
    void pressAfterExpire_isExpired() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        prompts.close(GUILD);

        assertEquals(PressOutcome.EXPIRED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("close: returns the removed Pending, empty when none was open")
    void close_returnsRemovedPending_emptyWhenNone() {
        PendingPrompts prompts = new PendingPrompts();
        assertTrue(prompts.close(GUILD).isEmpty());

        prompts.open(pending(GUILD, VIDEO_ID));
        Optional<Pending> closed = prompts.close(GUILD);
        assertTrue(closed.isPresent());
        assertEquals(VIDEO_ID, closed.get().videoId());

        // The prompt is gone now.
        assertTrue(prompts.get(GUILD).isEmpty());
        assertTrue(prompts.close(GUILD).isEmpty());
    }

    @Test
    @DisplayName("get: stays retrievable after an ACCEPTED press, until close")
    void get_staysRetrievableAfterAccept_untilClose() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));

        // The prompt stays so the listener can still update its message after acceptance.
        Optional<Pending> stillThere = prompts.get(GUILD);
        assertTrue(stillThere.isPresent());
        assertEquals(VIDEO_ID, stillThere.get().videoId());

        prompts.close(GUILD);
        assertTrue(prompts.get(GUILD).isEmpty());
    }

    @Test
    @DisplayName("withMessageId: returns the updated Pending with the message ID set")
    void withMessageId_updatesPending() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        Pending updated = prompts.withMessageId(GUILD, 999L);
        assertEquals(999L, updated.messageId());
        assertEquals(VIDEO_ID, updated.videoId());

        Optional<Pending> stored = prompts.get(GUILD);
        assertTrue(stored.isPresent());
        assertEquals(999L, stored.get().messageId());
    }

    @Test
    @DisplayName("withMessageId: does not resurrect a closed prompt; returns null, changes nothing")
    void withMessageId_closedPrompt_returnsNullChangesNothing() {
        PendingPrompts prompts = new PendingPrompts();

        assertNull(prompts.withMessageId(GUILD, 999L));
        assertTrue(prompts.get(GUILD).isEmpty());

        prompts.open(pending(GUILD, VIDEO_ID));
        prompts.close(GUILD);

        assertNull(prompts.withMessageId(GUILD, 999L));
        assertTrue(prompts.get(GUILD).isEmpty());
    }

    @Test
    @DisplayName("press: an accepted press survives withMessageId; the next press is still expired")
    void pressedFlag_survivesWithMessageId() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
        prompts.withMessageId(GUILD, 999L);

        assertEquals(PressOutcome.EXPIRED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("press: reopening after close gives a fresh prompt that accepts again")
    void reopenAfterClose_isFreshPrompt() {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));
        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
        prompts.close(GUILD);

        assertTrue(prompts.open(pending(GUILD, VIDEO_ID)));
        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
    }

    @Test
    @DisplayName("concurrency: 8 threads pressing at once yield exactly one ACCEPTED, seven EXPIRED")
    void concurrentPresses_exactlyOneAccepted() throws InterruptedException {
        PendingPrompts prompts = new PendingPrompts();
        prompts.open(pending(GUILD, VIDEO_ID));

        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger acceptedCount = new AtomicInteger(0);
        AtomicInteger expiredCount = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (prompts.press(GUILD, VIDEO_ID) == PressOutcome.ACCEPTED) {
                        acceptedCount.incrementAndGet();
                    } else {
                        expiredCount.incrementAndGet();
                    }
                });
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS), "threads failed to start in time");
            go.countDown();

            pool.shutdown();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS), "threads failed to finish in time");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, acceptedCount.get());
        assertEquals(threadCount - 1, expiredCount.get());
    }

    @Test
    @DisplayName("Pending: a different guild's prompt is independent")
    void differentGuilds_areIndependent() {
        PendingPrompts prompts = new PendingPrompts();
        long otherGuild = 444L;

        assertTrue(prompts.open(pending(GUILD, VIDEO_ID)));
        assertTrue(prompts.open(pending(otherGuild, "other99999")));

        assertEquals(PressOutcome.ACCEPTED, prompts.press(otherGuild, "other99999"));
        prompts.close(otherGuild);

        // Closing the other guild's prompt doesn't touch this guild's.
        Optional<Pending> stillOpen = prompts.get(GUILD);
        assertTrue(stillOpen.isPresent());
        assertEquals(VIDEO_ID, stillOpen.get().videoId());
        assertEquals(PressOutcome.ACCEPTED, prompts.press(GUILD, VIDEO_ID));
    }
}
