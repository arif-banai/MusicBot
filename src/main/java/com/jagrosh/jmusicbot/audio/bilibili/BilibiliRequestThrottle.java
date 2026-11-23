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
package com.jagrosh.jmusicbot.audio.bilibili;

import java.io.InterruptedIOException;
import java.util.function.LongSupplier;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

/**
 * Keeps the bot's calls to Bilibili's web API slow enough not to trip its risk control.
 *
 * <p>Bilibili answers bursts of anonymous requests by withholding streams (a
 * {@code v_voucher} challenge instead of audio) or with HTTP 412, for a few minutes at a
 * time. Two guards keep the bot clear of that:
 * <ul>
 *   <li>API requests are spaced at least {@code intervalMs} apart, across all guilds.</li>
 *   <li>Once Bilibili does push back, requests are refused for {@code cooldownMs} rather
 *       than sent, since retrying into the block only extends it.</li>
 * </ul>
 *
 * <p>Applies to API calls only. Audio downloads from the CDN are not throttled, so
 * playback and seeking are unaffected.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliRequestThrottle
{
    /** Gap between API requests. A play costs two requests, so this adds about a second. */
    public static final long DEFAULT_INTERVAL_MS = 1000L;

    /** How long to stay away after Bilibili pushes back. */
    public static final long DEFAULT_COOLDOWN_MS = 2L * 60 * 1000;

    /** Sleeps for the given number of milliseconds; replaced by a fake clock in tests. */
    @FunctionalInterface
    public interface Sleeper
    {
        void sleep(long ms) throws InterruptedException;
    }

    private final long intervalMs;
    private final long cooldownMs;
    private final LongSupplier clock;
    private final Sleeper sleeper;

    private long nextSlotAt;
    private volatile long cooldownUntil;

    public BilibiliRequestThrottle()
    {
        this(DEFAULT_INTERVAL_MS, DEFAULT_COOLDOWN_MS, System::currentTimeMillis, Thread::sleep);
    }

    public BilibiliRequestThrottle(long intervalMs, long cooldownMs, LongSupplier clock, Sleeper sleeper)
    {
        this.intervalMs = intervalMs;
        this.cooldownMs = cooldownMs;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Waits for this request's turn.
     *
     * @throws FriendlyException if Bilibili is in a cooldown
     * @throws InterruptedIOException if the wait is interrupted; the interrupt flag is kept,
     *         because lavaplayer interrupts the playback thread to stop or skip a track
     */
    public void acquire() throws InterruptedIOException
    {
        long now = clock.getAsLong();
        if(now < cooldownUntil)
            throw rateLimited();

        long wait;
        synchronized(this)
        {
            long slot = Math.max(now, nextSlotAt);
            nextSlotAt = slot + intervalMs;
            wait = slot - now;
        }

        if(wait <= 0)
            return;

        try
        {
            sleeper.sleep(wait);
        }
        catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to call Bilibili");
        }
    }

    /**
     * Stops all API requests for the cooldown period. Called when Bilibili's risk control
     * answers instead of the API.
     */
    public void startCooldown()
    {
        cooldownUntil = clock.getAsLong() + cooldownMs;
    }

    /**
     * The error users see while Bilibili is limiting the bot.
     */
    public static FriendlyException rateLimited()
    {
        return new FriendlyException(
                "Bilibili is limiting requests from this bot right now. Please try again in a couple of minutes.",
                FriendlyException.Severity.COMMON, null);
    }
}
