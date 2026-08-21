package com.jagrosh.jmusicbot.unit.spotify;

import com.jagrosh.jmusicbot.spotify.SpotifyTrackMatcher;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SpotifyTrackMatcherTest
{

	private AudioTrack mockTrack(String title, String author, long durationMs)
	{
		AudioTrack track = mock(AudioTrack.class);
		AudioTrackInfo info = new AudioTrackInfo(title, author, durationMs, "identifier", false, "https://youtube.com");
		when(track.getInfo()).thenReturn(info);
		when(track.getDuration()).thenReturn(durationMs);
		return track;
	}

	@Test
	@DisplayName("Return null when candidate list is empty")
	void selectBestMatch_emptyCandidateList_returnsNull()
	{
		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(Collections.emptyList(), "Song Title", "Artist Name",
				180000L);
		assertNull(match);
	}

	@Test
	@DisplayName("Handle empty or null artist gracefully")
	void selectBestMatch_emptyArtist_handlesGracefully()
	{
		AudioTrack candidate = mockTrack("Bohemian Rhapsody", "Queen", 210000L);

		AudioTrack matchWithNullArtist = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "Bohemian Rhapsody",
				null, 210000L);
		assertNotNull(matchWithNullArtist);

		AudioTrack matchWithEmptyArtist = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "Bohemian Rhapsody",
				"", 210000L);
		assertNotNull(matchWithEmptyArtist);
	}

	@Test
	@DisplayName("Return null when Spotify title is null")
	void selectBestMatch_nullTitle_returnsNull()
	{
		AudioTrack candidate = mockTrack("Track 01", "Unknown Artist", 180000L);

		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), null, "Unknown Artist", 180000L);

		assertNull(match, "Matcher should return null when Spotify title is null");
	}

	@Test
	@DisplayName("Return null when Spotify title is empty or blank")
	void selectBestMatch_emptyTitle_returnsNull()
	{
		AudioTrack candidate = mockTrack("Track 01", "Unknown Artist", 180000L);

		AudioTrack emptyMatch = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "", "Unknown Artist", 180000L);
		AudioTrack blankMatch = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "   ", "Unknown Artist",
				180000L);

		assertNull(emptyMatch, "Matcher should return null when Spotify title is empty");
		assertNull(blankMatch, "Matcher should return null when Spotify title is blank");
	}

	@Test
	@DisplayName("Filter out short preview tracks (< 70% target duration)")
	void selectBestMatch_candidateUnderDurationFloor_filtersOut()
	{
		long targetDurationMs = 200000L; // 3m 20s
		long previewDurationMs = 30000L; // 30s preview (<70%)

		AudioTrack previewCandidate = mockTrack("Track Title", "Artist Name", previewDurationMs);

		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(List.of(previewCandidate), "Track Title", "Artist Name",
				targetDurationMs);

		assertNull(match, "Short preview tracks (<70% duration) must be filtered out");
	}

	@Test
	@DisplayName("Filter out candidate far over duration cap (> 130% target duration)")
	void selectBestMatch_candidateOverDurationCap_filtersOut()
	{
		long targetDurationMs = 200000L; // 3m 20s
		long extendedDurationMs = 600000L; // 10m hour-long mix/compilation

		AudioTrack extendedCandidate = mockTrack("Track Title", "Artist Name", extendedDurationMs);

		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(List.of(extendedCandidate), "Track Title", "Artist Name",
				targetDurationMs);

		assertNull(match, "Excessively long candidates (>130% duration) must be filtered out");
	}

	@Test
	@DisplayName("Select candidate with closest duration and highest title/author similarity")
	void selectBestMatch_multipleCandidates_selectsBestMatch()
	{
		long targetDurationMs = 180000L; // 3 minutes

		AudioTrack badMatch = mockTrack("Random Cover", "Other Band", 180000L);
		AudioTrack exactMatch = mockTrack("Blinding Lights", "The Weeknd", 182000L);

		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(List.of(badMatch, exactMatch), "Blinding Lights",
				"The Weeknd", targetDurationMs);

		assertNotNull(match);
		assertEquals("Blinding Lights", match.getInfo().title);
	}

	@Test
	@DisplayName("Prioritize official topic or VEVO channel over generic channels")
	void selectBestMatch_officialChannel_prioritized()
	{
		long duration = 180000L;
		AudioTrack genericTrack = mockTrack("Blinding Lights", "Random User", duration);
		AudioTrack topicTrack = mockTrack("Blinding Lights", "The Weeknd - Topic", duration);

		AudioTrack match = SpotifyTrackMatcher.selectBestMatch(List.of(genericTrack, topicTrack), "Blinding Lights",
				"The Weeknd", duration);

		assertNotNull(match);
		assertEquals("The Weeknd - Topic", match.getInfo().author);
	}

	@Test
	@DisplayName("Select official audio/remaster keyword match over generic fallback")
	void selectBestMatch_keywordMatch_prioritizedOverFallback()
	{
	    long duration = 180000L;

	    // "Fan Channel" ensures isOfficialChannel = false for both tracks
	    AudioTrack fallbackTrack = mockTrack("Blinding Lights - The Weeknd", "Fan Channel", duration);
	    AudioTrack keywordTrack = mockTrack("Blinding Lights (Official Audio) - The Weeknd", "Fan Channel", duration);

	    AudioTrack match = SpotifyTrackMatcher.selectBestMatch(
	        List.of(fallbackTrack, keywordTrack),
	        "Blinding Lights",
	        "The Weeknd",
	        duration
	    );

	    assertNotNull(match);
	    assertEquals("Blinding Lights (Official Audio) - The Weeknd", match.getInfo().title);
	}

	@Test
	@DisplayName("Allow any candidate when target duration is null or zero")
	void selectBestMatch_nullOrZeroDuration_allowsCandidates()
	{
		AudioTrack candidate = mockTrack("Some Track", "Artist", 500000L);

		AudioTrack matchWithNullDuration = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "Some Track",
				"Artist", null);
		AudioTrack matchWithZeroDuration = SpotifyTrackMatcher.selectBestMatch(List.of(candidate), "Some Track",
				"Artist", 0L);

		assertNotNull(matchWithNullDuration);
		assertNotNull(matchWithZeroDuration);
	}
}