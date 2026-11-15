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
package com.jagrosh.jmusicbot.unit.ytdlp;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.MatchDecision;
import com.jagrosh.jmusicbot.ytdlp.SongMatcher;
import com.jagrosh.jmusicbot.ytdlp.SongMatcher.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SongMatcher Tests")
class SongMatcherTest {

    private static final long TOLERANCE_MS = 5_000L;
    private static final double MIN_SCORE = 0.85;
    private static final long LEN = 168_000L;

    private static Song song(String title, String author) {
        return SongMatcher.song(title, author, LEN, null);
    }

    private static boolean matches(Song a, Song b) {
        return SongMatcher.matches(a, b, TOLERANCE_MS, MIN_SCORE);
    }

    static Stream<Arguments> realWorldPairs() {
        return Stream.of(
                Arguments.of("official video vs lyric video",
                        "Crazy Frog - Axel F (Official Video)", "Crazy Frog",
                        "Crazy Frog - Axel F (Lyrics)", "Lyrics Hub", true),
                Arguments.of("official video vs lyric video, lyric video label in square brackets",
                        "Daft Punk - Get Lucky (Official Audio) ft. Pharrell Williams, Nile Rodgers", "Daft Punk",
                        "Daft Punk - Get Lucky [Lyric Video]", "Some Uploader", true),
                Arguments.of("\"Artist - Title\" vs a \"- Topic\" channel",
                        "Crazy Frog - Axel F", "CrazyFrogVEVO",
                        "Axel F", "Crazy Frog - Topic", true),
                Arguments.of("\"Title - Artist\" title vs a \"- Topic\" channel (author tells the order)",
                        "Axel F - Crazy Frog", "Crazy Frog",
                        "Axel F", "Crazy Frog - Topic", true),
                Arguments.of("VEVO channel glued together vs spaced artist",
                        "Axel F (Official Music Video) [HD]", "CrazyFrogVEVO",
                        "Axel F", "Crazy Frog", true),
                Arguments.of("remaster suffix in brackets",
                        "Queen - Bohemian Rhapsody (Remastered 2011)", "Queen Official",
                        "Bohemian Rhapsody", "Queen - Topic", true),
                Arguments.of("remaster suffix after a dash",
                        "Bohemian Rhapsody - Remastered 2011", "Queen - Topic",
                        "Queen – Bohemian Rhapsody [Official Video]", "Queen Official", true),
                Arguments.of("featured artist present on one side only",
                        "Mark Ronson - Uptown Funk (feat. Bruno Mars)", "Mark Ronson",
                        "Uptown Funk ft. Bruno Mars", "Mark Ronson - Topic", true),
                Arguments.of("accents",
                        "Beyoncé - Halo", "BeyoncéVEVO",
                        "Halo", "Beyonce - Topic", true),
                Arguments.of("different songs by the same artist",
                        "Queen - Bohemian Rhapsody (Official Video)", "Queen Official",
                        "Queen - Radio Ga Ga (Official Video)", "Queen Official", false),
                Arguments.of("different songs by the same artist sharing a word",
                        "Coldplay - Fix You", "Coldplay",
                        "Coldplay - Fix You Live", "Coldplay", false),
                Arguments.of("same title, different artist",
                        "Hello", "Adele",
                        "Hello", "Lionel Richie", false),
                Arguments.of("same title, different artist, both in \"Artist - Title\" form",
                        "Adele - Hello (Official Music Video)", "AdeleVEVO",
                        "Lionel Richie - Hello", "LionelRichieVEVO", false),
                Arguments.of("one-word title with the same artist on both sides",
                        "Hello", "Adele",
                        "Adele - Hello (Official Music Video)", "AdeleVEVO", true),
                Arguments.of("a word that is also noise stays when it is the whole title",
                        "Clean", "Taylor Swift",
                        "Taylor Swift - Clean (Official Audio)", "TaylorSwiftVEVO", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("realWorldPairs")
    void realWorldPairs(String name, String titleA, String authorA, String titleB, String authorB, boolean expected) {
        Song a = song(titleA, authorA);
        Song b = song(titleB, authorB);

        assertEquals(expected, matches(a, b), () -> name + ": score " + SongMatcher.score(a, b, TOLERANCE_MS));
        assertEquals(expected, matches(b, a), () -> name + " (reversed)");
    }

    @Test
    @DisplayName("normalize strips noise, splits \"Artist - Title\" and tokenizes")
    void normalize_stripsNoiseAndSplits() {
        Song s = SongMatcher.normalize("Crazy Frog - Axel F (Official Music Video) [HD]", "CrazyFrogVEVO");

        assertEquals(Set.of("axel", "f"), s.title());
        assertEquals(Set.of("crazy", "frog"), s.artist());
    }

    @Test
    @DisplayName("normalize takes the artist from the author, dropping \"- Topic\", VEVO and Official")
    void normalize_artistFromAuthor() {
        assertEquals(Set.of("crazy", "frog"), SongMatcher.normalize("Axel F", "Crazy Frog - Topic").artist());
        assertEquals(Set.of("crazyfrog"), SongMatcher.normalize("Axel F", "CrazyFrogVEVO").artist());
        assertEquals(Set.of("queen"), SongMatcher.normalize("Bohemian Rhapsody", "Queen Official").artist());
    }

    @Test
    @DisplayName("a duration outside the tolerance scores 0; the boundary still counts")
    void durationGate() {
        // Short enough that the floor applies rather than the 10% relative widening (tested separately below).
        long len = 40_000L;
        Song a = SongMatcher.song("Axel F", "Crazy Frog", len, null);

        assertEquals(0.0, SongMatcher.score(a, SongMatcher.song("Axel F", "Crazy Frog", len + 6_000L, null), TOLERANCE_MS));
        assertTrue(matches(a, SongMatcher.song("Axel F", "Crazy Frog", len + 5_000L, null)));
        assertTrue(matches(a, SongMatcher.song("Axel F", "Crazy Frog", len - 5_000L, null)));
    }

    // ---- relative duration tolerance ----

    @Test
    @DisplayName("toleranceMs widens to 10% of the shorter song when that is more than the floor")
    void toleranceMs_widensToTenPercentOfShorter() {
        assertEquals(17_200L, SongMatcher.toleranceMs(10_000L, 187_000L, 172_000L));
    }

    @Test
    @DisplayName("toleranceMs stays at the floor when 10% of the shorter song is smaller")
    void toleranceMs_staysAtFloor() {
        assertEquals(10_000L, SongMatcher.toleranceMs(10_000L, 60_000L, 65_000L));
    }

    @Test
    @DisplayName("a live test pair (lyrics video vs official video) passes with a 10s floor")
    void relativeTolerance_livePairPasses() {
        Song a = SongMatcher.song("Crazy Frog - Axel F (Lyrics)", "Lyrics Hub", 187_000L, null);
        Song b = SongMatcher.song("Crazy Frog - Axel F (Official Video)", "Crazy Frog", 172_000L, null);

        assertTrue(SongMatcher.matches(a, b, 10_000L, 0.85));
    }

    @Test
    @DisplayName("the same titles too far apart even after widening don't match")
    void relativeTolerance_tooFarApart() {
        Song a = SongMatcher.song("Crazy Frog - Axel F (Lyrics)", "Lyrics Hub", 172_000L, null);
        Song b = SongMatcher.song("Crazy Frog - Axel F (Official Video)", "Crazy Frog", 200_000L, null);

        assertFalse(SongMatcher.matches(a, b, 10_000L, 0.85));
    }

    @Test
    @DisplayName("whyNot explains a pair that is too far apart in duration")
    void whyNot_duration() {
        Song a = SongMatcher.song("Crazy Frog - Axel F (Lyrics)", "Lyrics Hub", 172_000L, null);
        Song b = SongMatcher.song("Crazy Frog - Axel F (Official Video)", "Crazy Frog", 200_000L, null);

        assertEquals(MatchDecision.Reason.DURATION, SongMatcher.whyNot(a, b, 10_000L, 0.85));
    }

    @Test
    @DisplayName("whyNot explains a version mismatch")
    void whyNot_version() {
        Song original = song("Some Great Song", "Some Band");
        Song live = song("Some Great Song (Live)", "Some Band");

        assertEquals(MatchDecision.Reason.VERSION, SongMatcher.whyNot(original, live, 10_000L, 0.85));
    }

    @Test
    @DisplayName("whyNot explains an otherwise-eligible pair that just doesn't look alike")
    void whyNot_lowScore() {
        Song a = song("Totally Different Song Title", "Some Band");
        Song b = song("Nothing Alike Whatsoever", "Another Band");

        assertEquals(MatchDecision.Reason.LOW_SCORE, SongMatcher.whyNot(a, b, 10_000L, 0.85));
    }

    @Test
    @DisplayName("an unknown length on either side never passes the duration gate")
    void unknownLength_neverMatches() {
        Song known = SongMatcher.song("Axel F", "Crazy Frog", 200_000L, null);
        Song unknown = SongMatcher.song("Axel F", "Crazy Frog", 0L, null);

        assertFalse(matches(known, unknown));
        assertFalse(matches(unknown, known));
    }

    @Test
    @DisplayName("an ISRC match scores 1.0 even with different titles; the duration gate still applies")
    void isrc() {
        Song a = SongMatcher.song("Axel F", "Crazy Frog", LEN, "GBAHT0500123");
        Song b = SongMatcher.song("Ring Ding Ding", "Someone Else", LEN + 1_000L, "gbaht0500123");

        assertEquals(1.0, SongMatcher.score(a, b, TOLERANCE_MS));
        assertEquals(0.0, SongMatcher.score(a,
                SongMatcher.song("Ring Ding Ding", "Someone Else", LEN + 60_000L, "GBAHT0500123"), TOLERANCE_MS));
        // Different ISRCs fall back to the normal scoring.
        assertTrue(matches(a, SongMatcher.song("Axel F", "Crazy Frog", LEN, "USXXX9900001")));
    }

    @Test
    @DisplayName("weighted score is 0.7 x title Jaccard + 0.3 x artist Jaccard")
    void weightedScore() {
        Song a = song("Fix You", "Coldplay");
        Song b = song("Fix You Again", "Coldplay");

        assertEquals(0.7 * (2.0 / 3.0) + 0.3, SongMatcher.score(a, b, TOLERANCE_MS), 1e-9);
    }

    // ---- version words ----

    /** Eight words, so one or two extra words alone would still pass the 0.85 bar. */
    private static final String LONG = "Somewhere Over The Rainbow What A Wonderful World";

    static Stream<String> versionSuffixes() {
        return Stream.of("(Instrumental)", "(Acoustic)", "(Live)", "(Karaoke)", "(Karaoke Version)",
                "(A Cappella)", "(Acapella)", "(Remix)", "(Radio Edit)", "(Cover)", "(Slowed)", "(Sped Up)",
                "(Nightcore)", "(Slowed + Reverb)", "(Reverb)", "(Sped)", "(8D Audio)", "[8D]", "(Demo)", "- Live", "Live");
    }

    @ParameterizedTest(name = "{0} vs the original")
    @MethodSource("versionSuffixes")
    @DisplayName("a version (instrumental, live, remix, ...) never matches the original")
    void versionWord_onOneSideOnly_scoresZero(String suffix) {
        Song original = song("Israel Kamakawiwo'ole - " + LONG, "Mountain Apple Company");
        Song version = song("Israel Kamakawiwo'ole - " + LONG + " " + suffix, "Mountain Apple Company");

        assertEquals(0.0, SongMatcher.score(original, version, TOLERANCE_MS), suffix);
        assertEquals(0.0, SongMatcher.score(version, original, TOLERANCE_MS), suffix + " (reversed)");
    }

    @Test
    @DisplayName("two different versions never match; the same version on both sides still does")
    void versionWords_mustAgree() {
        String base = "Israel Kamakawiwo'ole - " + LONG;
        String artist = "Mountain Apple Company";

        assertFalse(matches(song(base + " (Live)", artist), song(base + " (Acoustic)", artist)));
        assertTrue(matches(song(base + " (Live)", artist), song(base + " (Live Version) [Official Video]", artist)));
        assertTrue(matches(song(base + " (Acoustic)", artist),
                song(LONG + " (Acoustic)", "Israel Kamakawiwo'ole - Topic")));
    }

    @Test
    @DisplayName("a version word after a featured artist still counts")
    void versionWord_afterFeaturing() {
        assertFalse(matches(song("Mark Ronson - Uptown Funk ft. Bruno Mars (Live)", "Mark Ronson"),
                song("Uptown Funk", "Mark Ronson - Topic")));
    }

    @Test
    @DisplayName("upload noise (Official Video, Lyrics) is not a version: those uploads still match")
    void noise_isNotAVersion() {
        String base = "Israel Kamakawiwo'ole - " + LONG;
        assertTrue(matches(song(base + " (Official Video)", "IZ"), song(base + " (Lyrics)", "Lyrics Hub")));
        assertTrue(matches(song(base + " [Official Music Video] [HD]", "IZ"), song(LONG, "Israel Kamakawiwo'ole - Topic")));
    }

    @Test
    @DisplayName("an imported file named as a version doesn't match the original, by either reading of its name")
    void versionWord_inImportedFile() {
        LibraryEntry live = fileEntry("Crazy Frog - Axel F (Live)", List.of("Crazy Frog", "Axel F (Live)"), LEN);
        LibraryEntry plain = fileEntry("Axel F (Live)", List.of(), LEN);

        assertTrue(SongMatcher.matchScore(song("Axel F", "Crazy Frog - Topic"), live, TOLERANCE_MS, MIN_SCORE).isEmpty());
        assertTrue(SongMatcher.matchScore(song("Axel F (Live)", "Crazy Frog - Topic"), live, TOLERANCE_MS, MIN_SCORE)
                .isPresent());
        assertTrue(SongMatcher.matchScore(song("Crazy Frog - Axel F (Live)", "x"), plain, TOLERANCE_MS, MIN_SCORE)
                .isPresent());
    }

    // ---- library entries ----

    private static LibraryEntry fileEntry(String title, List<String> nameParts, long lengthMs) {
        return new LibraryEntry("file:" + title + ".mp3", Path.of(title + ".mp3"), title, "", lengthMs, null,
                null, null, nameParts, true);
    }

    @Test
    @DisplayName("matchScore: the same ISRC on both sides matches at 1.0 despite different titles")
    void isrc_matchesExactly() {
        Song requested = SongMatcher.song("Ring Ding Ding", "Someone Else", LEN, "GBAHT0500123");
        LibraryEntry entry = new LibraryEntry("file:Axel F.mp3", Path.of("Axel F.mp3"), "Axel F", "Crazy Frog", LEN,
                null, null, null, List.of(), true, null, null, null, null, "GBAHT0500123");

        assertEquals(1.0, SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("interpretations(): an entry with sidecar artist+track puts \"artist track\" first")
    void artistTrack_interpretationFirst() {
        LibraryEntry entry = new LibraryEntry("abc123def45", Path.of("X.webm"),
                "Crazy Frog - Axel F (Official Video)", "", LEN, null, null, null, List.of(), false,
                "Crazy Frog", "Axel F", null, null, null);

        List<Song> interpretations = SongMatcher.interpretations(entry);

        assertEquals(Set.of("axel", "f"), interpretations.get(0).title());
    }

    @Test
    @DisplayName("interpretations(): an entry titled \"X (Live)\" whose sidecar track is just \"X\" never matches a studio \"X\"")
    void artistTrack_keepsVersionWordsFromTitle() {
        LibraryEntry live = new LibraryEntry("abc123def45", Path.of("X.webm"),
                "Crazy Frog - Axel F (Live)", "", LEN, null, null, null, List.of(), false,
                "Crazy Frog", "Axel F", null, null, null);

        assertTrue(SongMatcher.matchScore(song("Crazy Frog - Axel F", "Crazy Frog"), live, TOLERANCE_MS, MIN_SCORE)
                .isEmpty());
        assertTrue(SongMatcher.matchScore(song("Crazy Frog - Axel F (Live)", "Crazy Frog"), live, TOLERANCE_MS,
                MIN_SCORE).isPresent(), "the live upload still matches its live library copy");
    }

    @Test
    @DisplayName("interpretations(): a version-like word in the artist's name isn't read as a version")
    void artistTrack_artistNameIsNotAVersion() {
        LibraryEntry entry = new LibraryEntry("abc123def46", Path.of("Y.webm"),
                "Live - Lightning Crashes", "", LEN, null, null, null, List.of(), false,
                "Live", "Lightning Crashes", null, null, null);

        assertTrue(SongMatcher.matchScore(song("Live - Lightning Crashes", "Live"), entry, TOLERANCE_MS, MIN_SCORE)
                .isPresent());
    }

    @Test
    @DisplayName("filename pairs: \"Artist - Title\" and \"Title - Artist\" both match the same upload")
    void nameParts_scoredBothWays() {
        Song requested = song("Crazy Frog - Axel F (Official Video)", "Crazy Frog");

        LibraryEntry artistFirst = fileEntry("Crazy Frog - Axel F", List.of("Crazy Frog", "Axel F"), LEN);
        LibraryEntry titleFirst = fileEntry("Axel F - Crazy Frog", List.of("Axel F", "Crazy Frog"), LEN);

        assertTrue(SongMatcher.matchScore(requested, artistFirst, TOLERANCE_MS, MIN_SCORE).isPresent());
        assertTrue(SongMatcher.matchScore(requested, titleFirst, TOLERANCE_MS, MIN_SCORE).isPresent());
        assertEquals(1.0, SongMatcher.matchScore(requested, titleFirst, TOLERANCE_MS, MIN_SCORE).getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("filename pairs still only match the right song")
    void nameParts_differentSong_noMatch() {
        Song requested = song("Crazy Frog - Popcorn (Official Video)", "Crazy Frog");
        LibraryEntry entry = fileEntry("Axel F - Crazy Frog", List.of("Axel F", "Crazy Frog"), LEN);

        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isEmpty());
    }

    @Test
    @DisplayName("filename pairs are still used after an author was filled in")
    void nameParts_usedEvenWithAuthor() {
        LibraryEntry entry = new LibraryEntry("file:Axel F - Crazy Frog.mp3", Path.of("Axel F - Crazy Frog.mp3"),
                "Axel F - Crazy Frog", "Some Uploader", LEN, null, null, null, List.of("Axel F", "Crazy Frog"), true);

        assertTrue(SongMatcher.matchScore(song("Axel F", "Crazy Frog - Topic"), entry, TOLERANCE_MS, MIN_SCORE)
                .isPresent());
    }

    @Test
    @DisplayName("artist-less: a title-only match needs at least 0.9, whatever minScore says")
    void artistless_needsPointNine() {
        // 8 of 9 words shared: Jaccard 0.889, under 0.9.
        String eight = "One Two Three Four Five Six Seven Eight";
        Song requested = song(eight + " Nine", "Some Band");
        LibraryEntry entry = fileEntry(eight, List.of(), LEN);

        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isEmpty());
        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, 0.5).isEmpty());
        // With an artist on both sides the same titles would pass (0.7 x 0.889 + 0.3 = 0.92).
        assertTrue(matches(requested, song(eight, "Some Band")));
        // An exact artist-less title passes.
        assertTrue(SongMatcher.matchScore(song(eight, "Some Band"), entry, TOLERANCE_MS, MIN_SCORE).isPresent());
    }

    @Test
    @DisplayName("artist-less: the stricter bar also applies when it is above minScore only by the 0.9 floor")
    void artistless_minScoreAboveFloorWins() {
        Song requested = song("Axel F (Official Video)", "Crazy Frog");
        LibraryEntry entry = fileEntry("Axel F", List.of(), LEN);

        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isPresent());
        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, 1.0).isPresent());
    }

    @Test
    @DisplayName("generic or one-word titles are never suggested from a title alone")
    void genericTitles_neverFromTitleAlone() {
        for (String title : List.of("Intro", "Outro", "Interlude", "Untitled", "Track 01", "Track 7", "Hello")) {
            Song requested = song(title, "Some Band");
            LibraryEntry entry = fileEntry(title, List.of(), LEN);
            assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isEmpty(), title);
        }
    }

    @Test
    @DisplayName("a trailing catalogue code in a filename title is ignored")
    void catalogueCode_ignored() {
        Song requested = song("Some Long Title (Original Mix)", "Some Producer");
        LibraryEntry entry = fileEntry("Some Long Title (Original Mix) [UTR035]", List.of(), LEN);

        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isPresent());
    }

    @Test
    @DisplayName("a filename with several dashes and no pair is artist-less (its title is not split)")
    void severalDashes_artistless() {
        Song requested = song("Axel F", "Crazy Frog");
        LibraryEntry entry = fileEntry("Crazy Frog - Axel F - Live", List.of(), LEN);

        assertTrue(SongMatcher.matchScore(requested, entry, TOLERANCE_MS, MIN_SCORE).isEmpty());
    }

    @Test
    @DisplayName("a recorded entry is scored by its title and author")
    void recordedEntry_titleAndAuthor() {
        LibraryEntry entry = new LibraryEntry("dQw4w9WgXcQ", Path.of("Axel F [dQw4w9WgXcQ].webm"),
                "Crazy Frog - Axel F (Official Video)", "Crazy Frog", LEN, null, null);

        assertTrue(SongMatcher.matchScore(song("Axel F", "Crazy Frog - Topic"), entry, TOLERANCE_MS, MIN_SCORE)
                .isPresent());
        assertTrue(SongMatcher.matchScore(song("Popcorn", "Crazy Frog - Topic"), entry, TOLERANCE_MS, MIN_SCORE)
                .isEmpty());
    }
}
