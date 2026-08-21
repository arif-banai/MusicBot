package com.jagrosh.jmusicbot.spotify;

/**
 * Enumeration representing supported Spotify entity types recognized by the bridge and scraper subsystems.
 * <p>
 * Maps domain-level Spotify classifications (tracks, episodes, playlists, and albums) to their 
 * respective string values used in command-line scraper execution and API request payloads.
 * </p>
 */
public enum SpotifyType
{
    TRACK("track"),
    EPISODE("episode"),
    PLAYLIST("playlist"),
    ALBUM("album");

    private final String value;

    /**
     * Constructs a {@link SpotifyType} enum constant with its associated string identifier.
     *
     * @param value the raw string representation expected by scraper execution arguments
     */
    SpotifyType(String value)
    {
        this.value = value;
    }

    /**
     * Retrieves the raw string value associated with this Spotify entity type.
     *
     * @return the string identifier (e.g., {@code "track"}, {@code "playlist"})
     */
    public String getValue()
    {
        return value;
    }

    /**
     * Safely resolves a string value into its corresponding {@link SpotifyType} enum constant.
     * <p>
     * Performs a case-insensitive and trimmed lookup against all defined enum values.
     * </p>
     *
     * @param type the raw string representation to parse
     * @return the matching {@link SpotifyType} constant, or {@code null} if the input is null or unmapped
     */
    public static SpotifyType fromString(String type)
    {
        if (type == null)
            return null;

        for (SpotifyType t : values())
        {
            if (t.value.equalsIgnoreCase(type.trim()))
                return t;
        }
        return null;
    }
}