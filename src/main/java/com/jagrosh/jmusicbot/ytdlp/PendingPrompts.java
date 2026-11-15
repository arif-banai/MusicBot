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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-guild pending-prompt state for the yt-dlp "download it?" prompt. Pure state: no JDA,
 * no timers, no I/O. {@code YtDlpPrompt} drives it: {@link #open} when a YouTube track is
 * blocked, {@link #withMessageId} once the Discord message is sent, {@link #press} from
 * button clicks (possibly two at once, from different JDA threads), and {@link #close} on
 * timeout, skip, stop, or the hold ending.
 * <p>
 * At most one prompt is open per guild at a time. {@link #press} accepts at most once per
 * opened prompt: the first matching press wins, every later press (including a second press
 * of the same button, a press for a different video ID, a press for a guild with no open
 * prompt, or a press after {@link #close}) gets {@link PressOutcome#EXPIRED}. An accepted
 * prompt is not removed by {@link #press} itself; it stays retrievable through {@link #get}
 * (and updatable through {@link #withMessageId}) until something explicitly {@link #close}s
 * it, because the caller still needs to edit the Discord message after acceptance.
 *
 * @author Arif Banai (arif-banai)
 */
public final class PendingPrompts {

    /** One tracked slot: the guild's {@link Pending} plus whether it has already been pressed. */
    private record Slot(Pending pending, boolean pressed) {
        private Slot(Pending pending) {
            this(pending, false);
        }
    }

    /**
     * @param hold the {@link LibraryAudioTrack} waiting on this prompt's gate, if any; this
     *             class never calls methods on it, so tests may pass {@code null} or a mock.
     * @param gate completed to release the waiting track once the prompt is resolved
     */
    public record Pending(long guildId, String videoId, long requesterId, long channelId,
                           LibraryAudioTrack hold, CompletableFuture<Void> gate, long messageId) {}

    public enum PressOutcome { ACCEPTED, EXPIRED }

    private final ConcurrentHashMap<Long, Slot> slots = new ConcurrentHashMap<>();

    /**
     * Opens a prompt for {@code p.guildId()}, if the guild doesn't already have one open.
     *
     * @return true if this prompt is now open; false if the guild already had one (unchanged)
     */
    public boolean open(Pending p) {
        return slots.putIfAbsent(p.guildId(), new Slot(p)) == null;
    }

    /** @return the guild's currently open prompt, if any (present whether or not it has been pressed) */
    public Optional<Pending> get(long guildId) {
        Slot slot = slots.get(guildId);
        return Optional.ofNullable(slot == null ? null : slot.pending());
    }

    /**
     * Records a button press for {@code videoId} against the guild's open prompt.
     * Accepts at most once per opened prompt: the decision and the state change happen
     * together inside a single {@code ConcurrentHashMap.computeIfPresent} call, so concurrent
     * presses from multiple threads are serialized by the map and exactly one sees ACCEPTED.
     *
     * @return ACCEPTED for the first press matching the open prompt's video ID; EXPIRED for
     *         every other case (no open prompt, wrong video ID, or already pressed)
     */
    public PressOutcome press(long guildId, String videoId) {
        PressOutcome[] outcome = { PressOutcome.EXPIRED };
        slots.computeIfPresent(guildId, (id, slot) -> {
            if (slot.pressed() || !slot.pending().videoId().equals(videoId)) {
                return slot; // unchanged: already pressed, or wrong video ID
            }
            outcome[0] = PressOutcome.ACCEPTED;
            return new Slot(slot.pending(), true);
        });
        return outcome[0];
    }

    /**
     * Closes the guild's open prompt (on timeout, skip, stop, or the hold ending), regardless
     * of whether it was ever pressed.
     *
     * @return the removed prompt, or empty if the guild had none open
     */
    public Optional<Pending> close(long guildId) {
        Slot slot = slots.remove(guildId);
        return Optional.ofNullable(slot == null ? null : slot.pending());
    }

    /**
     * Updates the open prompt's message ID, e.g. once the Discord message has been sent.
     * Does not resurrect a closed prompt.
     *
     * @return the updated prompt, or {@code null} if the guild has no open prompt (nothing is changed)
     */
    public Pending withMessageId(long guildId, long messageId) {
        Slot[] updated = new Slot[1];
        slots.computeIfPresent(guildId, (id, slot) -> {
            Pending withId = new Pending(slot.pending().guildId(), slot.pending().videoId(),
                    slot.pending().requesterId(), slot.pending().channelId(), slot.pending().hold(),
                    slot.pending().gate(), messageId);
            Slot next = new Slot(withId, slot.pressed());
            updated[0] = next;
            return next;
        });
        return updated[0] == null ? null : updated[0].pending();
    }
}
