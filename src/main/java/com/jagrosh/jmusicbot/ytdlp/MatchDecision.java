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

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Why a requested track was, or wasn't, offered a similar library song.
 *
 * @param candidateKey the library key of the closest candidate considered, or null when no
 *                      library entry shared any word with the requested title or artist
 * @param score        the candidate's score: the real match score when {@link #reason} is
 *                      {@link Reason#SUGGESTED} or {@link Reason#ALREADY_OFFERED}, otherwise the
 *                      raw title/artist score (ignoring duration and version words) used to pick the candidate for
 *                      this report
 * @param reason        why no suggestion went out, or {@link Reason#SUGGESTED} when one did
 * @param entry         the library entry to suggest, set only when {@link #reason} is
 *                      {@link Reason#SUGGESTED}
 * @author Arif Banai (arif-banai)
 */
public record MatchDecision(@Nullable String candidateKey, double score, Reason reason,
                            @Nullable LibraryEntry entry) {

    /** Why a candidate was or wasn't suggested. */
    public enum Reason {
        /** A suggestion should be posted. */
        SUGGESTED,
        /** No library entry shared any word with the requested title or artist. */
        NO_CANDIDATE,
        /** The candidate's length is too far from the requested track's, even after widening. */
        DURATION,
        /** The candidate and the requested track name different versions (live, remix, ...). */
        VERSION,
        /** The candidate doesn't look similar enough. */
        LOW_SCORE,
        /** This pair was already dismissed with "Not the same". */
        REJECTED,
        /** This pair was already offered earlier in the same run. */
        ALREADY_OFFERED,
        /** The candidate's file is gone. */
        MISSING_FILE
    }

    /** A one-line summary for the log: {@code "Match for <key>: best <cand> score <0.00> -> <reason>"}. */
    public String logLine(String requestedKey) {
        String candidate = candidateKey == null ? "none" : candidateKey;
        return String.format(Locale.ROOT, "Match for %s: best %s score %.2f -> %s", requestedKey, candidate, score,
                reason);
    }
}
