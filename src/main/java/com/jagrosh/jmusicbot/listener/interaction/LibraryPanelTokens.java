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
package com.jagrosh.jmusicbot.listener.interaction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Server-side state behind a {@code /library panel} search-results render, keyed by a short
 * random token instead of being packed into the component ID. A Discord custom ID is capped at
 * 100 characters; a search query or a library key (some {@code file:} keys and SoundCloud keys
 * easily exceed that on their own) can't safely ride along in one, so the component ID carries
 * only this token (plus a small index/delta), and the token looks the real values up here.
 * <p>
 * Entries expire after {@link #ttlMillis} (matching roughly how long a Discord interaction's
 * follow-ups stay editable) so this map can't grow without bound; a lookup or a new entry both
 * opportunistically sweep expired ones.
 *
 * @author Arif Banai (arif-banai)
 */
public final class LibraryPanelTokens {

    private static final long DEFAULT_TTL_MILLIS = 15 * 60 * 1000L;

    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    /** One search-results render: the query and page it came from, and the keys on that page, in order. */
    public record SearchState(long ownerUserId, String query, int page, List<String> keys) {
    }

    private record Entry(SearchState state, long expiresAtMillis) {
    }

    public LibraryPanelTokens() {
        this(DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    /** For tests: a controllable clock and TTL, so expiry doesn't need a real 15-minute wait. */
    public LibraryPanelTokens(long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /** Stores {@code state} under a new token, sweeping expired entries first, and returns the token. */
    public String create(long ownerUserId, String query, int page, List<String> keys) {
        sweep();
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Entry(new SearchState(ownerUserId, query, page, List.copyOf(keys)), clock.getAsLong() + ttlMillis));
        return token;
    }

    /** The state for {@code token}, or empty if it's missing or has expired (expired ones are dropped here too). */
    public Optional<SearchState> get(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() <= clock.getAsLong()) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.state());
    }

    private void sweep() {
        long now = clock.getAsLong();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
    }
}
