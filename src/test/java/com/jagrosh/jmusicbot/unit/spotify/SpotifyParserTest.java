package com.jagrosh.jmusicbot.unit.spotify;

import com.jagrosh.jmusicbot.spotify.SpotifyParser;
import com.jagrosh.jmusicbot.spotify.SpotifyParser.SpotifyData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SpotifyParserTest
{

	@Test
	@DisplayName("Parse valid track URL")
	void parse_validTrackUrl_returnsCorrectData()
	{
		String url = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("track", data.type().getValue());
		assertEquals("4uLU6hMCjMI75M1A2tKUQC", data.id());
	}

	@Test
	@DisplayName("Parse valid playlist URL")
	void parse_validPlaylistUrl_returnsCorrectData()
	{
		String url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("playlist", data.type().getValue());
		assertEquals("37i9dQZF1DXcBWIGoYBM5M", data.id());
	}

	@Test
	@DisplayName("Parse valid album URL")
	void parse_validAlbumUrl_returnsCorrectData()
	{
		String url = "https://open.spotify.com/album/4aawyAB9vmqN3uR72i224B";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("album", data.type().getValue());
		assertEquals("4aawyAB9vmqN3uR72i224B", data.id());
	}

	@Test
	@DisplayName("Parse valid episode URL")
	void parse_validEpisodeUrl_returnsCorrectData()
	{
		String url = "https://open.spotify.com/episode/7makk4oTQel546B0PZlDM5";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("episode", data.type().getValue());
		assertEquals("7makk4oTQel546B0PZlDM5", data.id());
	}

	@Test
	@DisplayName("Reject unsupported entity types (e.g., artist)")
	void parse_artistUrl_returnsNull()
	{
		String url = "https://open.spotify.com/artist/0TnOYISbd1XYRBk9myaseg";
		SpotifyData data = SpotifyParser.parse(url);

		assertNull(data, "Artist URLs should return null as artist is not a supported SpotifyType");
	}

	@Test
	@DisplayName("Parse Spotify URI format (spotify:track:id)")
	void parse_validSpotifyUri_returnsCorrectData()
	{
		String uri = "spotify:track:4uLU6hMCjMI75M1A2tKUQC";
		SpotifyData data = SpotifyParser.parse(uri);

		assertNotNull(data);
		assertEquals("track", data.type().getValue());
		assertEquals("4uLU6hMCjMI75M1A2tKUQC", data.id());
	}

	@Test
	@DisplayName("Parse URL with ?si= tracking parameter and strip parameters cleanly")
	void parse_urlWithTrackingParam_stripsParam()
	{
		String url = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1234567890abcdef&context=spotify%3Auser";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("track", data.type().getValue());
		assertEquals("4uLU6hMCjMI75M1A2tKUQC", data.id());
	}

	@Test
	@DisplayName("Parse URL without explicit scheme (e.g., open.spotify.com/track/id)")
	void parse_urlWithoutScheme_returnsCorrectData()
	{
		String url = "open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("track", data.type().getValue());
		assertEquals("4uLU6hMCjMI75M1A2tKUQC", data.id());
	}

	@Test
	@DisplayName("Parse localized Spotify URL with path prefix (e.g., /intl-en/track/id)")
	void parse_localizedUrl_returnsCorrectData()
	{
		String url = "https://open.spotify.com/intl-en/track/4uLU6hMCjMI75M1A2tKUQC";
		SpotifyData data = SpotifyParser.parse(url);

		assertNotNull(data);
		assertEquals("track", data.type().getValue());
		assertEquals("4uLU6hMCjMI75M1A2tKUQC", data.id());
	}

	@Test
	@DisplayName("Reject host-spoofing URL attempt")
	void parse_hostSpoofingUrl_returnsNull()
	{
		String url = "https://open.spotify.com.attacker.com/track/4uLU6hMCjMI75M1A2tKUQC";
		SpotifyData data = SpotifyParser.parse(url);

		assertNull(data, "Host spoofing attempt must be rejected");
	}

	@Test
	@DisplayName("Handle null input gracefully")
	void parse_nullInput_returnsNull()
	{
		assertNull(SpotifyParser.parse(null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://youtube.com/watch?v=dQw4w9WgXcQ", "https://soundcloud.com/artist/track",
			"4uLU6hMCjMI75M1A2tKUQC", "", "   " })
	@DisplayName("Reject non-Spotify input formats")
	void parse_invalidInputs_returnsNull(String input)
	{
		assertNull(SpotifyParser.parse(input));
	}

	@Test
	@DisplayName("Reject non-Spotify host with Spotify path embedded in query parameters")
	void parse_spotifyInQueryParamOfOtherHost_returnsNull()
	{
		String url = "https://youtube.com/watch?v=x&ref=spotify.com/track/aaaaaaaaaaaaaaaaaaaaaa";
		SpotifyData data = SpotifyParser.parse(url);

		assertNull(data, "Query parameter containing Spotify path on non-Spotify host must be rejected");
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://open.spotify.com/track/123456789012345678901", // 21 chars (too short)
			"https://open.spotify.com/track/12345678901234567890123", // 23 chars (too long)
			"https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQ!", // Contains symbol (!)
			"https://open.spotify.com/track/4uLU6hMCjMI75M1A2_KUQC", // Contains underscore (_)
			"https://open.spotify.com/track/4uLU6hMCjMI75M1A2 tKUQC", // Contains space
			"spotify:track:invalidShortId", // Short URI ID
			"spotify:track:4uLU6hMCjMI75M1A2tKUQ@#" // Special chars in URI
	})
	@DisplayName("Reject invalid Spotify IDs that fail Base62 22-character validation")
	void parse_invalidIds_returnsNull(String input)
	{
		assertNull(SpotifyParser.parse(input));
	}
}