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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.LibraryKeys;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary.ProbeResult;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("YtDlpLibrary import scan Tests")
class LibraryImportScanTest {

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private Path playlistFile;
    private FakeProber prober;

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        playlistFile = tempDir.resolve("playlists").resolve("ytdlp-library.txt");
        Files.createDirectories(libraryFolder);
        prober = new FakeProber();
    }

    private YtDlpLibrary loadLibrary() throws IOException {
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, prober);
        library.load();
        return library;
    }

    private Path audio(String name) throws IOException {
        return Files.writeString(libraryFolder.resolve(name), "audio");
    }

    private static String fileKey(String name) {
        return "file:" + name;
    }

    @Test
    @DisplayName("a YouTube sidecar gives a full entry keyed by the video ID, without probing")
    void youtubeSidecar_givesFullEntry() throws IOException {
        Path file = audio("Axel F.webm");
        Files.writeString(libraryFolder.resolve("Axel F.info.json"), """
                { "extractor_key": "Youtube", "extractor": "youtube", "id": "k85mRPqvMbE",
                  "webpage_url": "https://www.youtube.com/watch?v=k85mRPqvMbE",
                  "title": "Crazy Frog - Axel F (Official Video)", "uploader": "Crazy Frog",
                  "duration": 172.4, "thumbnail": "https://i.ytimg.com/vi/k85mRPqvMbE/maxresdefault.jpg" }
                """);

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find("k85mRPqvMbE").orElseThrow();
        assertEquals(file.toAbsolutePath().normalize(), entry.file());
        assertEquals("Crazy Frog - Axel F (Official Video)", entry.title());
        assertEquals("Crazy Frog", entry.author());
        assertEquals(172_400L, entry.lengthMs());
        assertEquals("https://i.ytimg.com/vi/k85mRPqvMbE/maxresdefault.jpg", entry.artworkUrl());
        assertEquals("https://www.youtube.com/watch?v=k85mRPqvMbE", entry.uri());
        assertEquals("youtube", entry.source());
        assertFalse(entry.adopted());
        assertTrue(prober.calls.isEmpty(), "sidecar files are not probed: " + prober.calls);
    }

    @Test
    @DisplayName("a SoundCloud sidecar is keyed exactly as LibraryKeys.of keys a SoundCloud track")
    void soundcloudSidecar_keyedLikeLavaplayerTrack() throws IOException {
        String permalink = "https://soundcloud.com/crazyfrog/axel-f";
        audio("Axel F [soundcloud-123456789].mp3");
        Files.writeString(libraryFolder.resolve("Axel F [soundcloud-123456789].info.json"), """
                { "extractor_key": "Soundcloud", "id": "123456789", "webpage_url": "%s",
                  "title": "Axel F", "uploader": "Crazy Frog", "duration": 170 }
                """.formatted(permalink));
        AudioSourceManager source = mock(AudioSourceManager.class);
        when(source.getSourceName()).thenReturn("soundcloud");
        AudioTrack track = mock(AudioTrack.class);
        when(track.getSourceManager()).thenReturn(source);
        when(track.getInfo()).thenReturn(new AudioTrackInfo("Axel F", "Crazy Frog", 170_000L,
                "O:https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/x/stream/hls", false, permalink,
                null, null));

        YtDlpLibrary library = loadLibrary();

        String key = LibraryKeys.of(track).orElseThrow();
        LibraryEntry entry = library.find(key).orElseThrow();
        assertEquals("Axel F", entry.title());
        assertEquals("soundcloud", entry.source());
        assertEquals(permalink, entry.uri());
        assertEquals(170_000L, entry.lengthMs());
    }

    @Test
    @DisplayName("a sidecar from a site the bot can't play is keyed by filename but keeps its details")
    void unknownExtractorSidecar_fileKey() throws IOException {
        Path file = audio("Clip.m4a");
        Files.writeString(libraryFolder.resolve("Clip.info.json"), """
                { "extractor_key": "Twitter", "id": "1", "webpage_url": "https://x.com/a/status/1",
                  "title": "A clip", "uploader": "someone", "duration": 12 }
                """);

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find(fileKey("Clip.m4a")).orElseThrow();
        assertEquals("A clip", entry.title());
        assertEquals("someone", entry.author());
        assertEquals(12_000L, entry.lengthMs());
        assertNull(entry.source());
        assertEquals(file.toAbsolutePath().normalize().toString(), entry.uri(), "file: entries play from their path");
    }

    @Test
    @DisplayName("an unreadable sidecar falls through to the next metadata source")
    void brokenSidecar_fallsThrough() throws IOException {
        audio("Song [abcdefghijk].webm");
        Files.writeString(libraryFolder.resolve("Song [abcdefghijk].info.json"), "{ broken");

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find("abcdefghijk").orElseThrow();
        assertEquals("Song", entry.title());
        assertTrue(entry.adopted());
    }

    @Test
    @DisplayName("a YouTube [id] file is adopted as in v1, without probing")
    void youtubeIdFile_adoptedWithoutProbe() throws IOException {
        audio("Some Song [abcdefghijk].m4a");

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find("abcdefghijk").orElseThrow();
        assertEquals("Some Song", entry.title());
        assertEquals("", entry.author());
        assertEquals(0L, entry.lengthMs());
        assertTrue(entry.adopted());
        assertTrue(prober.calls.isEmpty());
    }

    @Test
    @DisplayName("a [<source>-<id>] suffix that happens to be 11 characters is not read as a YouTube ID")
    void sourceSuffix_isNotAYoutubeId() throws IOException {
        assertEquals(Optional.empty(), YtDlpLibrary.videoIdFromFileName("Clip [vimeo-12345].m4a"));
        assertEquals(Optional.empty(), YtDlpLibrary.videoIdFromFileName("Clip [twitch-1234].m4a"));
        assertEquals(Optional.empty(), YtDlpLibrary.videoIdFromFileName("Clip [bandcamp-12].m4a"));
        audio("Clip [vimeo-12345].m4a");
        prober.result("Clip [vimeo-12345].m4a", new ProbeResult(null, null, 5_000L));

        YtDlpLibrary library = loadLibrary();

        assertTrue(library.find("vimeo-12345").isEmpty());
        assertEquals("Clip [vimeo-12345]", library.find(fileKey("Clip [vimeo-12345].m4a")).orElseThrow().title());
    }

    @Test
    @DisplayName("embedded tags give the title, artist and length")
    void tags_giveTitleArtistLength() throws IOException {
        audio("track01.mp3");
        prober.result("track01.mp3", new ProbeResult("Axel F", "Crazy Frog", 172_000L));

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find(fileKey("track01.mp3")).orElseThrow();
        assertEquals("Axel F", entry.title());
        assertEquals("Crazy Frog", entry.author());
        assertEquals(172_000L, entry.lengthMs());
        assertEquals(List.of(), entry.nameParts(), "tags name the artist; no filename guessing");
        assertTrue(entry.adopted(), "details came from the file, not from a real track");
        assertNull(entry.source());
    }

    @Test
    @DisplayName("filename only: \"Artist - Title\" and \"Title - Artist\" both store the same unordered pair")
    void filenameOnly_storesUnorderedPair() throws IOException {
        audio("Crazy Frog - Axel F.mp3");
        audio("Axel F - Crazy Frog.ogg");
        prober.result("Crazy Frog - Axel F.mp3", new ProbeResult(null, null, 172_000L));
        prober.result("Axel F - Crazy Frog.ogg", new ProbeResult(null, null, 171_000L));

        YtDlpLibrary library = loadLibrary();

        LibraryEntry a = library.find(fileKey("Crazy Frog - Axel F.mp3")).orElseThrow();
        LibraryEntry b = library.find(fileKey("Axel F - Crazy Frog.ogg")).orElseThrow();
        assertEquals("Crazy Frog - Axel F", a.title());
        assertEquals("", a.author(), "the order of the halves is never assumed");
        assertEquals(172_000L, a.lengthMs());
        assertEquals("Axel F - Crazy Frog", b.title());
        assertEquals(Set.of("Crazy Frog", "Axel F"), Set.copyOf(a.nameParts()));
        assertEquals(Set.copyOf(a.nameParts()), Set.copyOf(b.nameParts()));
        assertEquals(2, a.nameParts().size());
    }

    @Test
    @DisplayName("a name with no \" - \" or with several is artist-less, with no name parts")
    void noOrSeveralDashes_artistLess() throws IOException {
        audio("Axel F.mp3");
        audio("Crazy Frog - Axel F - Remix.mp3");
        audio("Crazy-Frog-Axel-F.mp3");

        YtDlpLibrary library = loadLibrary();

        for (String name : List.of("Axel F.mp3", "Crazy Frog - Axel F - Remix.mp3", "Crazy-Frog-Axel-F.mp3")) {
            LibraryEntry entry = library.find(fileKey(name)).orElseThrow();
            assertEquals("", entry.author(), name);
            assertEquals(List.of(), entry.nameParts(), name);
        }
    }

    @Test
    @DisplayName("a trailing catalogue code stays in the title but not in the name parts")
    void catalogueCode_keptInTitleOnly() throws IOException {
        audio("Anck - Ingenue [UTR035].opus");

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find(fileKey("Anck - Ingenue [UTR035].opus")).orElseThrow();
        assertEquals("Anck - Ingenue [UTR035]", entry.title());
        assertEquals(Set.of("Anck", "Ingenue"), Set.copyOf(entry.nameParts()));
    }

    @Test
    @DisplayName("a playable file with no metadata gets a file: key and its filename as title")
    void noMetadata_getsFileKey() throws IOException {
        Path file = audio("Mystery.ogg");

        YtDlpLibrary library = loadLibrary();

        LibraryEntry entry = library.find(fileKey("Mystery.ogg")).orElseThrow();
        assertEquals("Mystery", entry.title());
        assertEquals("", entry.author());
        assertEquals(0L, entry.lengthMs());
        assertEquals(file.toAbsolutePath().normalize().toString(), entry.uri());
        assertEquals(entry, library.findByFile(file).orElseThrow());
    }

    @Test
    @DisplayName("files the probe can't play, sidecars, the index and temp files are not indexed")
    void nonAudio_notIndexed() throws IOException {
        audio("cover.jpg");
        prober.unplayable("cover.jpg");
        audio("Song.mp3");
        Files.writeString(libraryFolder.resolve("Song.info.json"), "{ \"extractor_key\": \"Twitter\", \"title\": \"S\" }");
        audio("Half [abcdefghijk].webm.part");
        audio("Frag.mp4.part-Frag3");
        audio("Other.ytdl");
        audio("library12345.json.tmp");
        prober.throwFor("Broken.flac");
        audio("Broken.flac");

        YtDlpLibrary library = loadLibrary();

        assertEquals(List.of("Song.mp3"),
                library.entries().stream().map(e -> e.file().getFileName().toString()).toList());
        assertFalse(prober.calls.contains("Song.info.json"));
        assertFalse(prober.calls.contains("library.json"));
        assertFalse(prober.calls.contains("Half [abcdefghijk].webm.part"));
        assertFalse(prober.calls.contains("library12345.json.tmp"));
    }

    @Test
    @DisplayName("playlists, cue sheets, archives and PDFs beside the songs are never probed")
    void nonAudioExtensions_neverProbed() throws IOException {
        for (String name : List.of("booklet.pdf", "scans.zip", "album.m3u", "album.m3u8", "album.cue",
                "notes.txt", "cover.jpg", "cover.png", "cover.webp", "album.nfo", "data.json")) {
            audio(name);
        }
        audio("Song.mp3");

        YtDlpLibrary library = loadLibrary();

        assertEquals(List.of("Song.mp3"), prober.calls);
        assertEquals(1, library.entries().size());
    }

    @Test
    @DisplayName("a probe that throws a linkage or stack-overflow error skips that file and the scan goes on")
    void probeError_skipsFile() throws IOException {
        audio("A.mp3");
        audio("Linkage.flac");
        prober.errorFor("Linkage.flac", new NoClassDefFoundError("some/Codec"));
        audio("Overflow.ogg");
        prober.errorFor("Overflow.ogg", new StackOverflowError());
        audio("Z.mp3");

        YtDlpLibrary library = loadLibrary();

        assertEquals(List.of("A.mp3", "Z.mp3"),
                library.entries().stream().map(e -> e.file().getFileName().toString()).toList());
    }

    @Test
    @DisplayName("load() backfills an already-indexed entry's metadata from a sidecar that appears later")
    void load_backfillsIndexedEntryFromSidecar() throws IOException {
        audio("Song.mp3");
        prober.result("Song.mp3", new ProbeResult("Song", "Band", 100_000L));
        loadLibrary(); // indexes the probed entry (no artist/track/isrc) and saves
        Path index = libraryFolder.resolve("library.json");
        byte[] before = Files.readAllBytes(index);

        Files.writeString(libraryFolder.resolve("Song.info.json"), """
                { "artist": "Crazy Frog", "track": "Axel F", "isrc": "GBAYE0900001" }
                """);

        YtDlpLibrary second = loadLibrary();

        LibraryEntry entry = second.find(fileKey("Song.mp3")).orElseThrow();
        assertEquals("Crazy Frog", entry.artist());
        assertEquals("Axel F", entry.track());
        assertEquals("GBAYE0900001", entry.isrc());
        assertFalse(java.util.Arrays.equals(before, Files.readAllBytes(index)), "the backfill is saved");
    }

    @Test
    @DisplayName("a load that fails after an index existed blocks saving, so the index isn't overwritten")
    void failedLoad_blocksSaves() throws IOException {
        audio("Song.mp3");
        prober.result("Song.mp3", new ProbeResult("Song", "Band", 100_000L));
        loadLibrary(); // writes library.json
        Path index = libraryFolder.resolve("library.json");
        byte[] before = Files.readAllBytes(index);
        Path boom = audio("Boom.mp3");
        prober.errorFor("Boom.mp3", new OutOfMemoryError("simulated"));

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, prober);
        assertThrows(OutOfMemoryError.class, library::load);

        assertThrows(IOException.class, () -> library.alias("k85mRPqvMbE", fileKey("Song.mp3")));
        assertThrows(IOException.class, () -> library.record("abcdefghijk", boom,
                new AudioTrackInfo("t", "a", 1L, "abcdefghijk", false, "u")));
        assertArrayEquals(before, Files.readAllBytes(index));
    }

    @Test
    @DisplayName("a failed load with no index yet doesn't block saving")
    void failedLoad_noIndex_savesAllowed() throws IOException {
        Path boom = audio("Boom.mp3");
        prober.errorFor("Boom.mp3", new OutOfMemoryError("simulated"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, prober);
        assertThrows(OutOfMemoryError.class, library::load);

        library.record("abcdefghijk", boom, new AudioTrackInfo("t", "a", 1L, "abcdefghijk", false, "u"));

        assertTrue(Files.exists(libraryFolder.resolve("library.json")));
    }

    @Test
    @DisplayName("the load summary counts the songs whose length is unknown")
    void loadSummary_countsUnknownLength() throws IOException {
        audio("Song [abcdefghijk].webm"); // adopted from its [id]: no length yet
        audio("Known.mp3");
        prober.result("Known.mp3", new ProbeResult("Known", "Band", 100_000L));
        audio("Unknown.mp3");
        prober.result("Unknown.mp3", new ProbeResult("Unknown", "Band", 0L));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(YtDlpLibrary.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Level previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.addAppender(appender);
        try {
            loadLibrary();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        List<String> summaries = appender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("Library: "))
                .toList();
        assertEquals(1, summaries.size(), summaries::toString);
        assertTrue(summaries.get(0).contains("2 with unknown length"), summaries.get(0));
    }

    @Test
    @DisplayName("the second start doesn't probe files the first start indexed")
    void secondStart_doesNotReprobe() throws IOException {
        audio("Crazy Frog - Axel F.mp3");
        prober.result("Crazy Frog - Axel F.mp3", new ProbeResult(null, null, 172_000L));
        loadLibrary();
        assertEquals(1, prober.calls.size());

        YtDlpLibrary second = loadLibrary();

        assertEquals(1, prober.calls.size(), "probed again: " + prober.calls);
        LibraryEntry entry = second.find(fileKey("Crazy Frog - Axel F.mp3")).orElseThrow();
        assertEquals(172_000L, entry.lengthMs());
        assertEquals(Set.of("Crazy Frog", "Axel F"), Set.copyOf(entry.nameParts()));
        assertTrue(entry.adopted());
    }

    @Test
    @DisplayName("a deleted file drops its entry and the aliases and rejections that point at it")
    void deletedFile_dropsEntryAliasesRejections() throws IOException {
        Path file = audio("Crazy Frog - Axel F.mp3");
        audio("Kept.mp3");
        YtDlpLibrary first = loadLibrary();
        String key = fileKey("Crazy Frog - Axel F.mp3");
        first.alias("k85mRPqvMbE", key);
        first.reject("otherUpload", key);
        first.alias("keptAlias01", fileKey("Kept.mp3"));

        Files.delete(file);
        YtDlpLibrary second = loadLibrary();

        assertTrue(second.find(key).isEmpty());
        assertTrue(second.resolve("k85mRPqvMbE").isEmpty());
        assertFalse(second.isRejected("otherUpload", key));
        assertTrue(second.resolve("keptAlias01").isPresent());
        String saved = Files.readString(libraryFolder.resolve("library.json"));
        assertFalse(saved.contains("k85mRPqvMbE"), "the dropped alias is gone from disk too: " + saved);
        assertFalse(saved.contains("otherUpload"), saved);

        Files.writeString(file, "audio again");
        assertTrue(loadLibrary().resolve("k85mRPqvMbE").isEmpty(), "a restored file doesn't revive old aliases");
    }

    @Test
    @DisplayName("load() writes nothing when the library folder doesn't exist")
    void missingFolder_writesNothing() throws IOException {
        Path absent = tempDir.resolve("no-library");
        YtDlpLibrary library = new YtDlpLibrary(absent, playlistFile, prober);

        library.load();

        assertFalse(Files.exists(absent));
        assertTrue(library.entries().isEmpty());
    }

    @Test
    @DisplayName("an unchanged library isn't rewritten at load")
    void unchangedLibrary_notRewritten() throws IOException {
        audio("Song [abcdefghijk].webm");
        loadLibrary();

        assertFalse(Files.exists(libraryFolder.resolve("library.json")),
                "adopting [id] files alone doesn't write the index (as in v1)");
    }

    @Test
    @DisplayName("the default prober reads a real file's length through lavaplayer's local source")
    void defaultProber_readsLength() throws IOException {
        Path wav = libraryFolder.resolve("Tone - Test.wav");
        writeWav(wav, 44_100, 2, 44_100); // 1 s

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.load();

        LibraryEntry entry = library.find(fileKey("Tone - Test.wav")).orElseThrow();
        assertEquals("Tone - Test", entry.title(), "lavaplayer's \"Unknown title\" is treated as missing");
        assertEquals("", entry.author(), "lavaplayer's \"Unknown artist\" is treated as missing");
        assertEquals(1000L, entry.lengthMs(), 5);
        assertEquals(Set.of("Tone", "Test"), Set.copyOf(entry.nameParts()));
    }

    @Test
    @DisplayName("the default prober skips files lavaplayer can't play")
    void defaultProber_skipsNonAudio() throws IOException {
        Files.writeString(libraryFolder.resolve("notes.bin"), "these bytes are not any audio format at all");

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.load();

        assertTrue(library.entries().isEmpty());
    }

    /** Writes a PCM16 little-endian WAV of silence. */
    private static void writeWav(Path file, int sampleRate, int channels, int frames) throws IOException {
        int dataLength = frames * channels * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataLength).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * 2)
                .putShort((short) (channels * 2)).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataLength);
        Files.write(file, b.array());
    }

    /**
     * Records which files were probed. By default a file is playable with no metadata; individual
     * files can be given tags, made unplayable, or made to throw.
     */
    private static final class FakeProber implements YtDlpLibrary.Prober {
        final List<String> calls = new CopyOnWriteArrayList<>();
        private final Map<String, Optional<ProbeResult>> results = new ConcurrentHashMap<>();
        private final Set<String> throwing = ConcurrentHashMap.newKeySet();
        private final Map<String, Error> errors = new ConcurrentHashMap<>();

        void result(String fileName, ProbeResult result) {
            results.put(fileName, Optional.of(result));
        }

        void unplayable(String fileName) {
            results.put(fileName, Optional.empty());
        }

        void throwFor(String fileName) {
            throwing.add(fileName);
        }

        void errorFor(String fileName, Error error) {
            errors.put(fileName, error);
        }

        @Override
        public Optional<ProbeResult> probe(Path file) {
            String name = file.getFileName().toString();
            calls.add(name);
            if (throwing.contains(name)) {
                throw new IllegalStateException("probe failed");
            }
            Error error = errors.get(name);
            if (error != null) {
                throw error;
            }
            return results.getOrDefault(name, Optional.of(new ProbeResult(null, null, 0L)));
        }
    }
}
