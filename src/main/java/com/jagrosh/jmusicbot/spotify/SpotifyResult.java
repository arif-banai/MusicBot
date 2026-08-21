package com.jagrosh.jmusicbot.spotify;

import java.util.List;

/**
 * Immutable record encapsulating the outcome of a Spotify metadata extraction or cache lookup operation.
 * <p>
 * Holds a list of parsed {@link SpotifyTrack} instances, an execution status flag, and an optional error message
 * describing failure reasons when applicable.
 * </p>
 *
 * @param tracks       the list of resolved {@link SpotifyTrack} records, or an empty list if execution failed
 * @param success      {@code true} if metadata extraction completed successfully; {@code false} otherwise
 * @param errorMessage a descriptive diagnostic message detailing the failure reason, or {@code null} if successful
 */
public record SpotifyResult(List<SpotifyTrack> tracks, boolean success, String errorMessage) {
	/**
	 * Factory method for constructing a successful {@link SpotifyResult} payload.
	 *
	 * @param tracks the list of parsed {@link SpotifyTrack} items (null-safe; defaults to an empty list if null)
	 * @return a successful {@link SpotifyResult} holding the track list
	 */
	public static SpotifyResult success(List<SpotifyTrack> tracks)
	{
		return new SpotifyResult(tracks != null ? tracks : List.of(), true, null);
	}

	/**
	 * Factory method for constructing a failed {@link SpotifyResult} payload.
	 *
	 * @param errorMessage a descriptive error or diagnostic string detailing why extraction failed
	 * @return a failed {@link SpotifyResult} with an empty track list and error reason
	 */
	public static SpotifyResult failure(String errorMessage)
	{
		return new SpotifyResult(List.of(), false, errorMessage);
	}
}