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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether two songs are probably the same track, from their cleaned-up title and
 * artist words plus their lengths. Pure and stateless; see docs/ytdlp-library.md, "Matching".
 * <ul>
 *   <li>Titles are lower-cased, accents are stripped, and upload noise ("Official Video",
 *       "Lyrics", "Remastered 2011", "[HD]", featured artists, catalogue codes) is removed.</li>
 *   <li>"Artist - Title" titles are split; otherwise the artist comes from the uploader, minus
 *       "- Topic", "VEVO" and "Official" suffixes.</li>
 *   <li>The score is 0.7 x title Jaccard + 0.3 x artist Jaccard. An ISRC match scores 1.0. A pair
 *       whose lengths differ by more than the tolerance, or with an unknown length, scores 0.</li>
 *   <li>When either side has no artist, only the title counts, it must reach
 *       {@code max(minScore, 0.9)}, and generic or one-word titles never match.</li>
 *   <li>Version words in the title (instrumental, acoustic, live, karaoke, a cappella, remix, edit,
 *       cover, slowed, sped up, nightcore, reverb, 8D, demo) must agree: a pair where only one side
 *       has them, or the two have different ones, scores 0.</li>
 * </ul>
 *
 * @author Arif Banai (arif-banai)
 */
public final class SongMatcher {

    /** The title-only bar when either side has no artist. */
    public static final double ARTISTLESS_MIN_SCORE = 0.9;
    private static final double TITLE_WEIGHT = 0.7;
    private static final double ARTIST_WEIGHT = 0.3;

    private static final String NOISE_WORDS = "official (?:music |lyric )?video|official audio|audio"
            + "|lyrics?(?: video)?|visuali[sz]er|hd|hq|4k|remaster(?:ed)?(?: \\d{4})?|\\d{4} remaster(?:ed)?"
            + "|explicit|clean|mv|m/v|full song|topic|official";
    private static final Pattern NOISE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + NOISE_WORDS + ")(?![\\p{L}\\p{N}])");
    private static final Pattern TRAILING_NOISE = Pattern.compile("(?:[\\s\\p{Punct}]*(?<![\\p{L}\\p{N}])(?:"
            + NOISE_WORDS + "))+[\\s\\p{Punct}]*$");
    private static final Pattern BRACKETS = Pattern.compile("[(\\[{]([^)\\]}]*)[)\\]}]");
    private static final Pattern FEATURING = Pattern.compile("(?<![\\p{L}\\p{N}])(?:feat|ft|featuring)\\.?(?:\\s|$).*$");
    private static final Pattern CODE = Pattern.compile("(?=.*\\d)\\S+|[A-Za-z0-9_-]{11}");
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[-\u2013\u2014|]\\s+|\\s+//\\s+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern AUTHOR_SUFFIX = Pattern.compile(
            "(?:\\s*-\\s*topic|\\s*vevo|\\s+official(?:\\s+channel)?|(?<=\\p{L})official)$");
    private static final Pattern GENERIC = Pattern.compile("(?:intro|outro|interlude|untitled)(?: \\d+)?|track \\d+");

    /**
     * Title words that mark a different version of a song, each mapped to its class ("a cappella"
     * and "acapella" are one class; "sped up" is matched by "sped").
     */
    private static final Map<String, String> VERSION_WORDS = Map.ofEntries(
            Map.entry("instrumental", "instrumental"), Map.entry("acoustic", "acoustic"),
            Map.entry("live", "live"), Map.entry("karaoke", "karaoke"),
            Map.entry("cappella", "acappella"), Map.entry("capella", "acappella"),
            Map.entry("acappella", "acappella"), Map.entry("acapella", "acappella"),
            Map.entry("remix", "remix"), Map.entry("edit", "edit"),
            Map.entry("cover", "cover"), Map.entry("slowed", "slowed"),
            Map.entry("sped", "sped"), Map.entry("nightcore", "nightcore"),
            Map.entry("reverb", "reverb"), Map.entry("8d", "8d"),
            Map.entry("demo", "demo"));

    private SongMatcher() {
    }

    /**
     * A song cleaned up for matching.
     *
     * @param title         the title's words
     * @param artist        the artist's words; empty when unknown
     * @param titleText     the title's words in order, space-separated
     * @param artistCompact the artist's letters and digits run together (so "CrazyFrogVEVO" and
     *                      "Crazy Frog" agree); empty when unknown
     * @param lengthMs      0 or less when unknown
     * @param isrc          the recording's ISRC, if known
     * @param versions      the version classes named in the title ("live", "remix", ...); empty for
     *                      the original
     */
    public record Song(Set<String> title, Set<String> artist, String titleText, String artistCompact,
                       long lengthMs, @Nullable String isrc, Set<String> versions) {

        public Song {
            title = Set.copyOf(title);
            artist = Set.copyOf(artist);
            versions = Set.copyOf(versions);
        }

        public boolean hasArtist() {
            return !artist.isEmpty();
        }
    }

    /** Cleans up a title and its uploader, with no length or ISRC. */
    public static Song normalize(@Nullable String title, @Nullable String author) {
        return song(title, author, 0L, null);
    }

    /** Cleans up a title and its uploader, splitting "Artist - Title" titles. */
    public static Song song(@Nullable String title, @Nullable String author, long lengthMs, @Nullable String isrc) {
        String cleanAuthor = cleanAuthor(author);
        List<String> segments = segments(stripBrackets(fold(title)), cleanAuthor);
        String titlePart;
        String artistPart;
        if (segments.isEmpty()) {
            titlePart = "";
            artistPart = cleanAuthor;
        } else if (segments.size() == 1) {
            titlePart = segments.get(0);
            artistPart = cleanAuthor;
        } else {
            String first = segments.get(0);
            String rest = String.join(" ", segments.subList(1, segments.size()));
            // "Title - Artist" is common too: trust the uploader when it names the second half.
            boolean swapped = segments.size() == 2 && !cleanAuthor.isEmpty()
                    && similarity(cleanAuthor, rest) > similarity(cleanAuthor, first);
            titlePart = swapped ? first : rest;
            artistPart = swapped ? rest : first;
        }
        return build(titlePart, artistPart, lengthMs, isrc);
    }

    /** The requested track as a song. */
    public static Song of(AudioTrackInfo info) {
        return song(info.title, info.author, info.isStream ? 0L : info.length, info.isrc);
    }

    /**
     * Every way a library entry can be read, each carrying the entry's ISRC (if known):
     * <ul>
     *   <li>when the sidecar named both an artist and a track, "artist track" comes first, with
     *       the version words of both the track and the entry's title;</li>
     *   <li>a filename pair {@code {P, Q}} gives both "P is the artist, Q the title" and the reverse;</li>
     *   <li>an entry known only from its filename or tags, with no artist, is artist-less, and its
     *       title isn't split (a name with several dashes stays one title);</li>
     *   <li>anything else is read like a requested track, from its title and author.</li>
     * </ul>
     */
    public static List<Song> interpretations(LibraryEntry entry) {
        String isrc = entry.isrc();
        List<Song> interpretations = new ArrayList<>();
        if (entry.artist() != null && !entry.artist().isBlank() && entry.track() != null && !entry.track().isBlank()) {
            Song fromTrack = build(clean(entry.track()), cleanAuthor(entry.artist()), entry.lengthMs(), isrc);
            // A sidecar's track often drops the version its title names ("X (Live)" with track "X"):
            // keep the title's version words too, read from its title half (so an artist named
            // "Live" isn't one).
            Set<String> versions = new LinkedHashSet<>(fromTrack.versions());
            versions.addAll(song(entry.title(), entry.artist(), 0L, null).versions());
            interpretations.add(new Song(fromTrack.title(), fromTrack.artist(), fromTrack.titleText(),
                    fromTrack.artistCompact(), fromTrack.lengthMs(), fromTrack.isrc(), versions));
        }
        List<String> parts = entry.nameParts();
        if (parts.size() == 2) {
            interpretations.add(build(clean(parts.get(1)), cleanAuthor(parts.get(0)), entry.lengthMs(), isrc));
            interpretations.add(build(clean(parts.get(0)), cleanAuthor(parts.get(1)), entry.lengthMs(), isrc));
        } else if (LibraryKeys.isFileKey(entry.key()) && entry.author().isBlank()) {
            interpretations.add(build(stripBrackets(fold(entry.title())), "", entry.lengthMs(), isrc));
        } else {
            interpretations.add(song(entry.title(), entry.author(), entry.lengthMs(), isrc));
        }
        return List.copyOf(interpretations);
    }

    /** 10% of the shorter song's length is allowed on top of the floor, for longer tracks. */
    private static final double RELATIVE_TOLERANCE = 0.10;

    /**
     * The duration gate for a pair of songs: {@code floorMs}, widened to 10% of the shorter song's
     * length when that is larger. A fixed floor alone is too tight for long tracks (a lyrics upload
     * can run noticeably longer than the official video of the same song).
     */
    public static long toleranceMs(long floorMs, long lenA, long lenB) {
        long shorter = Math.min(lenA, lenB);
        long relative = Math.round(RELATIVE_TOLERANCE * shorter);
        return Math.max(floorMs, relative);
    }

    /**
     * The raw similarity of two songs, 0.0-1.0. 0 when the lengths are unknown or further apart than
     * {@code toleranceMs} widened by {@link #toleranceMs}. 1.0 for the same ISRC. 0 when their
     * version words differ. When either side has no artist, the title similarity alone, or 0 for a
     * generic or one-word title.
     */
    public static double score(Song a, Song b, long toleranceMs) {
        if (!durationOk(a, b, toleranceMs)) {
            return 0.0;
        }
        return scoreIgnoringDuration(a, b, true);
    }

    /** Whether {@code a} and {@code b}'s lengths are both known and within the widened tolerance. */
    private static boolean durationOk(Song a, Song b, long floorMs) {
        if (a.lengthMs() <= 0 || b.lengthMs() <= 0) {
            return false;
        }
        long tolerance = toleranceMs(floorMs, a.lengthMs(), b.lengthMs());
        return Math.abs(a.lengthMs() - b.lengthMs()) <= tolerance;
    }

    /**
     * {@link #score}'s title/artist/ISRC/version logic, with the duration gate already decided by
     * the caller (either applied, by {@link #score}, or skipped entirely, by {@link #rawScore}).
     * {@code versionGate} false skips the version check too (for {@link #rawScore}).
     */
    private static double scoreIgnoringDuration(Song a, Song b, boolean versionGate) {
        if (a.isrc() != null && b.isrc() != null && !a.isrc().isBlank() && a.isrc().equalsIgnoreCase(b.isrc())) {
            return 1.0;
        }
        if (versionGate && !a.versions().equals(b.versions())) {
            return 0.0; // e.g. the live or instrumental version of the same song
        }
        double title = jaccard(a.title(), b.title());
        if (!a.hasArtist() || !b.hasArtist()) {
            return isGeneric(a) || isGeneric(b) ? 0.0 : title;
        }
        double artist = a.artistCompact().equals(b.artistCompact()) ? 1.0 : jaccard(a.artist(), b.artist());
        return TITLE_WEIGHT * title + ARTIST_WEIGHT * artist;
    }

    /** The score a pair must reach: {@code minScore}, or at least 0.9 when either side has no artist. */
    public static double requiredScore(Song a, Song b, double minScore) {
        return !a.hasArtist() || !b.hasArtist() ? Math.max(minScore, ARTISTLESS_MIN_SCORE) : minScore;
    }

    /** Whether the two songs are probably the same track. */
    public static boolean matches(Song a, Song b, long toleranceMs, double minScore) {
        double score = score(a, b, toleranceMs);
        return score > 0.0 && score >= requiredScore(a, b, minScore);
    }

    /** The best score among the entry's {@link #interpretations} that match, or empty if none does. */
    public static OptionalDouble matchScore(Song requested, LibraryEntry entry, long toleranceMs, double minScore) {
        double best = -1.0;
        for (Song candidate : interpretations(entry)) {
            if (matches(requested, candidate, toleranceMs, minScore)) {
                best = Math.max(best, score(requested, candidate, toleranceMs));
            }
        }
        return best < 0.0 ? OptionalDouble.empty() : OptionalDouble.of(best);
    }

    /**
     * Why two songs that don't match don't: too far apart in length (even after widening), naming
     * different versions of the song, or just not similar enough. Meaningless (and never called)
     * for a pair that actually matches. {@code minScore} is accepted for parity with
     * {@link #matches} and {@link #requiredScore}; duration and version are the only finer-grained
     * reasons, so it doesn't change which of the three is returned.
     */
    public static MatchDecision.Reason whyNot(Song a, Song b, long floorMs, double minScore) {
        if (!durationOk(a, b, floorMs)) {
            return MatchDecision.Reason.DURATION;
        }
        if (!a.versions().equals(b.versions())) {
            return MatchDecision.Reason.VERSION;
        }
        return MatchDecision.Reason.LOW_SCORE;
    }

    /**
     * {@link #score}, but with the duration and version gates skipped (an unknown or wildly
     * different length, or a live version of the same song, never zeroes it). The ISRC and
     * generic-title gates still apply. Used only to pick the closest candidate to report when
     * nothing actually matches, so {@link #whyNot} can then name the duration or version.
     */
    static double rawScore(Song a, Song b) {
        return scoreIgnoringDuration(a, b, false);
    }

    // ---- cleaning ----

    private static Song build(String titleText, String artistText, long lengthMs, @Nullable String isrc) {
        List<String> titleWords = words(stripTrailingNoise(stripFeaturing(titleText)));
        List<String> artistWords = words(stripFeaturing(artistText));
        return new Song(new LinkedHashSet<>(titleWords), new LinkedHashSet<>(artistWords),
                String.join(" ", titleWords), String.join("", artistWords), lengthMs, isrc, versions(titleText));
    }

    /**
     * The version classes named anywhere in a title, including after a featured artist
     * ("ft. X (Live)"), which the title words themselves drop.
     */
    private static Set<String> versions(String titleText) {
        Set<String> versions = new LinkedHashSet<>();
        for (String word : words(titleText)) {
            String version = VERSION_WORDS.get(word);
            if (version != null) {
                versions.add(version);
            }
        }
        return versions;
    }

    /** Lower-cases and strips accents. */
    private static String fold(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return ACCENTS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).strip();
    }

    /** A title part from a filename: folded, with bracketed noise removed. */
    private static String clean(String text) {
        return stripBrackets(fold(text));
    }

    /** The uploader, folded, without "- Topic", "VEVO" or "Official" suffixes. */
    private static String cleanAuthor(@Nullable String author) {
        String folded = fold(author);
        String previous;
        do {
            previous = folded;
            folded = AUTHOR_SUFFIX.matcher(folded).replaceAll("").strip();
        } while (!folded.equals(previous) && !folded.isEmpty());
        return folded.isEmpty() ? fold(author) : folded;
    }

    /**
     * Drops bracketed groups that are only noise, a featured artist, or a code (a catalogue
     * number or video ID); other groups ("Live", "Radio Edit", "[8D]") keep their words.
     */
    private static String stripBrackets(String text) {
        Matcher m = BRACKETS.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String inside = m.group(1).strip();
            String kept;
            boolean code = m.group().startsWith("[") && CODE.matcher(inside).matches()
                    && !VERSION_WORDS.containsKey(inside);
            if (FEATURING.matcher(inside).lookingAt() || code) {
                kept = " ";
            } else {
                String remainder = NOISE.matcher(inside).replaceAll(" ");
                kept = words(remainder).isEmpty() ? " " : " " + remainder + " ";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(kept));
        }
        m.appendTail(sb);
        return sb.toString().strip();
    }

    /**
     * Splits on " - ", " | " and similar, dropping segments that are only noise ("Remastered 2011").
     * A title that is itself a noise word survives: a lone title ("Clean"), or the second half of
     * "Artist - Title" when the first half is the uploader ("Taylor Swift - Clean").
     */
    private static List<String> segments(String text, String cleanAuthor) {
        String[] all = SEPARATOR.split(text);
        List<String> kept = new ArrayList<>();
        for (String segment : all) {
            if (!words(NOISE.matcher(segment).replaceAll(" ")).isEmpty()) {
                kept.add(segment.strip());
            }
        }
        if (all.length == 2 && kept.size() == 1 && kept.get(0).equals(all[0].strip()) && !cleanAuthor.isEmpty()
                && similarity(cleanAuthor, all[0]) >= 0.5 && !words(all[1]).isEmpty()) {
            kept.add(all[1].strip());
        }
        if (kept.isEmpty() && !words(text).isEmpty()) {
            kept.add(text.strip());
        }
        return kept;
    }

    private static String stripFeaturing(String text) {
        String stripped = FEATURING.matcher(text).replaceAll("");
        return words(stripped).isEmpty() ? text : stripped;
    }

    private static String stripTrailingNoise(String text) {
        String stripped = TRAILING_NOISE.matcher(text).replaceAll("");
        return words(stripped).isEmpty() ? text : stripped;
    }

    private static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>(Arrays.asList(NON_WORD.split(text)));
        words.removeIf(String::isEmpty);
        return Collections.unmodifiableList(words);
    }

    private static boolean isGeneric(Song song) {
        return song.title().size() <= 1 || GENERIC.matcher(song.titleText()).matches();
    }

    /** How alike an uploader and one half of a title are, for deciding which half is the artist. */
    private static double similarity(String author, String part) {
        List<String> a = words(author);
        List<String> p = words(stripFeaturing(part));
        if (String.join("", a).equals(String.join("", p))) {
            return 1.0;
        }
        return jaccard(new LinkedHashSet<>(a), new LinkedHashSet<>(p));
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int shared = 0;
        for (String word : a) {
            if (b.contains(word)) {
                shared++;
            }
        }
        return (double) shared / (a.size() + b.size() - shared);
    }
}
