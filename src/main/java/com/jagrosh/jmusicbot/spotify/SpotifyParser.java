package com.jagrosh.jmusicbot.spotify; // Adjust package name as per your project structure

import java.net.URI;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing and extracting metadata identifiers from Spotify URLs and URI schemes.
 * <p>
 * Handles both native Spotify URI patterns (e.g., {@code spotify:track:<id>}) and HTTP/HTTPS web URLs
 * (e.g., {@code https://open.spotify.com/track/<id>}). Extracted entity identifiers are strictly validated
 * against standard 22-character Base62 pattern constraints.
 * </p>
 */
public class SpotifyParser
{
	private static final Logger LOG = LoggerFactory.getLogger(SpotifyParser.class);
    private static final Pattern BASE62_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{22}$");

    /**
     * Parses a raw input string to determine if it represents a valid Spotify URL or URI scheme.
     * <p>
     * Automatically strips leading/trailing whitespace and delegates to internal URI or URL parsing routines.
     * </p>
     *
     * @param args the raw input string containing a Spotify URL, URI, or command argument
     * @return a {@link SpotifyData} record holding the resolved {@link SpotifyType} and ID,
     *         or {@code null} if the input is blank, unparseable, or invalid
     */
    public static SpotifyData parse(String args)
    {
        if (args == null || args.isBlank())
            return null;

        String input = args.trim();

        if (input.startsWith("spotify:"))
        {
            return parseUri(input);
        }

        return parseUrl(input);
    }

    /**
     * Parses native Spotify URI schemes (e.g., {@code spotify:track:4uLU61m3OFy3A2Tf3L1A22}).
     * <p>
     * Splits the scheme by colon delimiters, maps the second segment to a {@link SpotifyType},
     * and verifies that the resource ID matches the expected 22-character Base62 format.
     * </p>
     *
     * @param input the sanitized Spotify URI string
     * @return a populated {@link SpotifyData} instance if valid; {@code null} otherwise
     */
    private static SpotifyData parseUri(String input)
    {
        try
        {
            String[] parts = input.split(":");
            if (parts.length >= 3)
            {
                SpotifyType type = SpotifyType.fromString(parts[1]);
                String id = parts[2];

                if (type != null && BASE62_ID_PATTERN.matcher(id).matches())
                {
                    return new SpotifyData(type, id);
                }
            }
        }
        catch (Exception e)
        {
            LOG.debug("Failed to parse Spotify URI input: \"{}\"", input, e);
        }
        return null;
    }
    
    /**
     * Parses HTTP and HTTPS Spotify web URLs (e.g., {@code https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M}).
     * <p>
     * Validates protocol schemes and enforces host verification against official domain bounds
     * ({@code spotify.com} or its subdomains). Scans URL path segments sequentially to find a valid
     * {@link SpotifyType} followed immediately by a 22-character Base62 resource ID.
     * </p>
     *
     * @param input the sanitized Spotify web URL string
     * @return a populated {@link SpotifyData} instance if valid; {@code null} otherwise
     */
    private static SpotifyData parseUrl(String input)
    {
        try
        {
            if (!input.startsWith("http://") && !input.startsWith("https://"))
            {
                input = "https://" + input;
            }

            URI uri = new URI(input);
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")))
            {
                LOG.debug("Rejected URL [{}]: Scheme is missing or unsupported ('{}')", input, scheme);
                return null;
            }

            String host = uri.getHost();
            if (host == null)
            {
                LOG.debug("Rejected URL [{}]: Host component is null", input);
                return null;
            }

            host = host.toLowerCase();

            if (!host.equals("spotify.com") && !host.endsWith(".spotify.com"))
            {
                LOG.debug("Rejected URL [{}]: Host '{}' is not a valid Spotify domain", input, host);
                return null;
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty())
            {
                LOG.debug("Rejected URL [{}]: Path component is null or empty", input);
                return null;
            }

            String[] segments = path.split("/");
            for (int i = 0; i < segments.length - 1; i++)
            {
                SpotifyType type = SpotifyType.fromString(segments[i]);
                if (type != null)
                {
                    String possibleId = segments[i + 1];
                    if (BASE62_ID_PATTERN.matcher(possibleId).matches())
                    {
                        return new SpotifyData(type, possibleId);
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOG.debug("Failed to parse Spotify URL input: \"{}\"", input, e);
        }

        return null;
    }

    /**
     * Immutable container representing an extracted Spotify entity target.
     *
     * @param type the resolved {@link SpotifyType} entity classification
     * @param id   the 22-character Base62 resource identifier
     */
    public record SpotifyData(SpotifyType type, String id) {}
}