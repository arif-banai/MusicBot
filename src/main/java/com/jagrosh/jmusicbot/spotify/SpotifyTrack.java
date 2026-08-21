package com.jagrosh.jmusicbot.spotify;

/**
 * Immutable representation of a single Spotify track's metadata.
 * <p>
 * Encapsulates essential track properties extracted from Spotify API payloads or local cache hits,
 * including resource identifiers, track titles, primary artist names, and track durations in milliseconds.
 * </p>
 *
 * @param id         the 22-character Base62 Spotify track identifier, or an empty string if omitted
 * @param title      the track title or episode name
 * @param artist     the primary artist, show, or podcast name associated with the track
 * @param durationMs the total duration of the track in milliseconds
 */
public record SpotifyTrack(
    String id,
    String title,
    String artist,
    long durationMs
) {}