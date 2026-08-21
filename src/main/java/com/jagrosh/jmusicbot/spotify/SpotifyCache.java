package com.jagrosh.jmusicbot.spotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory JVM cache for storing resolved Spotify metadata using thread-safe data structures.
 * <p>
 * This class provides a localized caching layer backed by a {@link ConcurrentHashMap} to reduce
 * external Python scraper invocations. Cache entries are bounded by a maximum capacity limit
 * (2000 entries) and enforced with a 24-hour Time-To-Live (TTL).
 * </p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>TTL Eviction:</b> Automatically invalidates and purges expired entries upon lookup.</li>
 *   <li><b>Capacity Protection:</b> Cleans up expired items when the total cached entry count meets or exceeds {@code maxEntries}.</li>
 *   <li><b>Batch Pre-seeding:</b> Supports populating individual track caches from batch container lookups
 *       (playlists and albums) to optimize subsequent single-track requests.</li>
 * </ul>
 */
public class SpotifyCache
{
	private static final Logger LOG = LoggerFactory.getLogger(SpotifyCache.class);

    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxEntries = 2000;
    private final long ttlMillis = 24 * 60 * 60 * 1000L; // 24 Hours

    private record CacheKey(String type, String id) {}
    private record CacheEntry(SpotifyResult result, Instant expiresAt) {}

    /**
     * Retrieves a cached {@link SpotifyResult} for a specific entity if present and not expired.
     * <p>
     * If an entry is found but its TTL has expired, it is atomically removed from the map and treated as a cache miss.
     * </p>
     *
     * @param type the entity type classification string (e.g., {@code "track"}, {@code "playlist"})
     * @param id   the 22-character Spotify resource identifier
     * @return an {@link Optional} containing the cached {@link SpotifyResult} if valid; empty otherwise
     */
    public Optional<SpotifyResult> get(String type, String id)
    {
        CacheKey key = new CacheKey(type, id);
        CacheEntry entry = cache.get(key);

        if (entry == null)
        {
            LOG.debug("Spotify JVM cache MISS for [{}:{}]", key.type(), key.id());
            return Optional.empty();
        }

        if (Instant.now().isAfter(entry.expiresAt()))
        {
            cache.remove(key, entry);
            LOG.debug("Spotify JVM cache EXPIRED for [{}:{}]", key.type(), key.id());
            return Optional.empty();
        }

        LOG.info("Spotify JVM cache HIT for [{}:{}]", key.type(), key.id());
        return Optional.of(entry.result());
    }

    /**
     * Stores a successful {@link SpotifyResult} in the cache with a 24-hour expiration timestamp.
     * <p>
     * If the cache size exceeds {@code maxEntries}, a opportunistic sweep is performed to purge
     * expired entries before inserting the new key.
     * </p>
     *
     * @param type   the entity type classification string
     * @param id     the 22-character Spotify resource identifier
     * @param result the {@link SpotifyResult} payload to store
     */
    public void put(String type, String id, SpotifyResult result)
    {
    	if (result == null || !result.success() || result.tracks() == null || result.tracks().isEmpty())
        {
            return;
        }

        if (cache.size() >= maxEntries)
        {
            cache.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue().expiresAt()));
        }

        CacheKey key = new CacheKey(type, id);
        Instant expiresAt = Instant.now().plusMillis(ttlMillis);
        cache.put(key, new CacheEntry(result, expiresAt));
        LOG.debug("Cached Spotify metadata for [{}:{}]", key.type(), key.id());
    }

    /**
     * Pre-populates the in-memory cache with individual track records returned from a batch playlist or album resolution.
     * <p>
     * Each track within the provided list is cached under {@link SpotifyType#TRACK} using its unique track ID.
     * This eliminates future Python scraping overhead if a track from a previously loaded playlist is requested independently.
     * </p>
     *
     * @param tracks the list of {@link SpotifyTrack} records to cache
     */
    public void populateIndividualTrackCache(List<SpotifyTrack> tracks)
    {
        if (tracks == null || tracks.isEmpty())
            return;

        int cachedCount = 0;
        for (SpotifyTrack track : tracks)
        {
            if (track == null || track.id() == null || track.id().isBlank())
                continue;

            SpotifyResult singleResult = SpotifyResult.success(List.of(track));
            put(SpotifyType.TRACK.getValue(), track.id(), singleResult);
            cachedCount++;
        }

        LOG.info("Pre-populated JVM cache with {} individual tracks from playlist/album.", cachedCount);
    }
    
    /**
     * Clears all stored entries from the in-memory cache.
     */
    public void clear()
    {
        cache.clear();
        LOG.info("Spotify JVM cache cleared.");
    }

    /**
     * Returns the total number of entries currently held in the cache, including unexpired and pending-eviction items.
     *
     * @return the number of cached items
     */
    public long size()
    {
        return cache.size();
    }
}