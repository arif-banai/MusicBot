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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YtDlpLibrary Tests")
class YtDlpLibraryTest {

    @TempDir
    Path tempDir;

    private Path libraryFolder;
    private Path playlistFile;

    @BeforeEach
    void setUp() throws IOException {
        libraryFolder = tempDir.resolve("ytdlp-library");
        playlistFile = tempDir.resolve("playlists").resolve("ytdlp-library.txt");
        Files.createDirectories(libraryFolder);
    }

    private static AudioTrackInfo sampleInfo(String videoId) {
        return new AudioTrackInfo("Song Title", "Author Name", 12345L, videoId, false,
                "https://www.youtube.com/watch?v=" + videoId, "https://example.com/art.jpg", null);
    }

    @Test
    @DisplayName("videoIdFromFileName() parses a bracket suffix")
    void videoIdFromFileName_parsesBracketSuffix() {
        Optional<String> id = YtDlpLibrary.videoIdFromFileName("Axel F [k85mRPqvMbE].webm");

        assertEquals(Optional.of("k85mRPqvMbE"), id);
    }

    @Test
    @DisplayName("videoIdFromFileName() handles underscores and dashes in the ID")
    void videoIdFromFileName_handlesUnderscoreAndDash() {
        Optional<String> id = YtDlpLibrary.videoIdFromFileName("x [a_b-c_d1234].opus");

        assertEquals(Optional.of("a_b-c_d1234"), id);
    }

    @Test
    @DisplayName("videoIdFromFileName() is empty when there is no bracket suffix")
    void videoIdFromFileName_noSuffixIsEmpty() {
        Optional<String> id = YtDlpLibrary.videoIdFromFileName("Thiago.opus");

        assertEquals(Optional.empty(), id);
    }

    @Test
    @DisplayName("videoIdFromFileName() rejects bracketed catalogue numbers that aren't 11 characters")
    void videoIdFromFileName_rejectsCatalogueNumbers() {
        assertEquals(Optional.empty(), YtDlpLibrary.videoIdFromFileName("A. Giuliano Lomonte - Thiago [HARLO004].opus"));
        assertEquals(Optional.empty(), YtDlpLibrary.videoIdFromFileName("Anck - Ingenue [UTR035].opus"));
    }

    @Test
    @DisplayName("record() then findById() returns the recorded metadata")
    void record_thenFindById_returnsMetadata() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000001].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);

        AudioTrackInfo info = sampleInfo("vid00000001");
        library.record("vid00000001", file, info);

        Optional<LibraryEntry> found = library.find("vid00000001");
        assertTrue(found.isPresent());
        LibraryEntry entry = found.get();
        assertEquals("Song Title", entry.title());
        assertEquals("Author Name", entry.author());
        assertEquals(12345L, entry.lengthMs());
        assertEquals("https://example.com/art.jpg", entry.artworkUrl());
        assertEquals("https://www.youtube.com/watch?v=vid00000001", entry.uri());
    }

    @Test
    @DisplayName("record() appends the playlist file once even when called twice for the same file")
    void record_appendsPlaylistOnce() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000002].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        AudioTrackInfo info = sampleInfo("vid00000002");

        library.record("vid00000002", file, info);
        library.record("vid00000002", file, info);

        List<String> lines = Files.readAllLines(playlistFile);
        assertEquals(1, lines.size());
        assertEquals(file.toAbsolutePath().toString(), lines.get(0));
    }

    @Test
    @DisplayName("load() restores the index from library.json")
    void load_restoresIndexFromJson() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000003].webm");
        Files.createFile(file);
        YtDlpLibrary first = new YtDlpLibrary(libraryFolder, playlistFile);
        first.record("vid00000003", file, sampleInfo("vid00000003"));

        YtDlpLibrary second = new YtDlpLibrary(libraryFolder, playlistFile);
        second.load();

        Optional<LibraryEntry> found = second.find("vid00000003");
        assertTrue(found.isPresent());
        LibraryEntry entry = found.get();
        assertEquals("Song Title", entry.title());
        assertEquals("Author Name", entry.author());
        assertEquals(12345L, entry.lengthMs());
        assertEquals("https://example.com/art.jpg", entry.artworkUrl());
        assertEquals("https://www.youtube.com/watch?v=vid00000003", entry.uri());
    }

    @Test
    @DisplayName("load() adopts files with a bracket suffix that have no index entry")
    void load_adoptsUnindexedBracketFiles() throws IOException {
        Path file = libraryFolder.resolve("Some Song [abcdefghijk].m4a");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);

        library.load();

        Optional<LibraryEntry> found = library.find("abcdefghijk");
        assertTrue(found.isPresent());
        assertEquals("Some Song", found.get().title());
    }

    @Test
    @DisplayName("load() does not adopt files whose bracketed suffix is a catalogue number, not a video ID")
    void load_doesNotAdoptCatalogueNumberFiles() throws IOException {
        Path file = libraryFolder.resolve("Anck - Ingenue [UTR035].opus");
        Files.createFile(file);
        // A playable file: v2 imports it under its filename, never under the catalogue number.
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile,
                f -> Optional.of(new YtDlpLibrary.ProbeResult(null, null, 1000L)));

        library.load();

        assertTrue(library.find("UTR035").isEmpty());
        assertEquals("file:Anck - Ingenue [UTR035].opus", library.findByFile(file).orElseThrow().key());
    }

    @Test
    @DisplayName("load() doesn't rewrite the index just because an indexed file is missing")
    void load_missingFileOnly_leavesIndexUntouched() throws IOException {
        Path axel = Files.createFile(libraryFolder.resolve("Axel F [k85mRPqvMbE].webm"));
        writeV1Index("k85mRPqvMbE", axel);
        Files.delete(axel);
        byte[] before = Files.readAllBytes(libraryFolder.resolve("library.json"));

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.load();

        assertTrue(library.find("k85mRPqvMbE").isEmpty());
        assertArrayEquals(before, Files.readAllBytes(libraryFolder.resolve("library.json")));
    }

    @Test
    @DisplayName("an indexed file found under the same name in a moved library folder keeps its details")
    void load_movedLibraryFolder_relocatesEntriesByFileName() throws IOException {
        Path oldFolder = tempDir.resolve("old-place");
        Files.createDirectories(oldFolder);
        writeV1Index("k85mRPqvMbE", oldFolder.resolve("Crazy Frog - Axel F (Official Video) [k85mRPqvMbE].webm"));
        Path moved = Files.createFile(libraryFolder.resolve("Crazy Frog - Axel F (Official Video) [k85mRPqvMbE].webm"));

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.load();

        LibraryEntry entry = library.find("k85mRPqvMbE").orElseThrow();
        assertEquals(moved.toAbsolutePath().normalize(), entry.file());
        assertEquals("Crazy Frog", entry.author());
        assertFalse(entry.adopted());
    }

    @Test
    @DisplayName("findById() is a miss once the file is gone")
    void findById_missingFileIsMiss() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000004].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.record("vid00000004", file, sampleInfo("vid00000004"));

        Files.delete(file);

        assertTrue(library.find("vid00000004").isEmpty());
    }

    @Test
    @DisplayName("findByFile() matches a normalized, differently-spelled path")
    void findByFile_matchesNormalizedPath() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000005].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.record("vid00000005", file, sampleInfo("vid00000005"));

        Path dotForm = libraryFolder.resolve(".").resolve(file.getFileName());

        Optional<LibraryEntry> found = library.findByFile(dotForm);
        assertTrue(found.isPresent());
        assertEquals("vid00000005", found.get().key());
    }

    @Test
    @DisplayName("findByFile() follows a re-record to a new path: the old path misses, the new one hits")
    void findByFile_reRecordAtNewPath_movesTheLookup() throws IOException {
        Path oldFile = libraryFolder.resolve("Old Name [vid00000008].webm");
        Path newFile = libraryFolder.resolve("New Name [vid00000008].opus");
        Files.createFile(oldFile);
        Files.createFile(newFile);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.record("vid00000008", oldFile, sampleInfo("vid00000008"));

        library.record("vid00000008", newFile, sampleInfo("vid00000008"));

        assertTrue(library.findByFile(oldFile).isEmpty());
        assertEquals("vid00000008", library.findByFile(newFile).orElseThrow().key());
    }

    @Test
    @DisplayName("findByFile() misses a file that was deleted before load()")
    void findByFile_fileDeletedBeforeLoad_misses() throws IOException {
        Path kept = libraryFolder.resolve("Kept [vid00000009].webm");
        Path gone = libraryFolder.resolve("Gone [vid00000010].webm");
        Files.createFile(kept);
        Files.createFile(gone);
        new YtDlpLibrary(libraryFolder, playlistFile).record("vid00000010", gone, sampleInfo("vid00000010"));
        Files.delete(gone);

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.load();

        assertTrue(library.findByFile(gone).isEmpty());
        assertTrue(library.find("vid00000010").isEmpty());
        assertEquals("vid00000009", library.findByFile(kept).orElseThrow().key());
    }

    @Test
    @DisplayName("findByFile() cost doesn't grow with the library size (no scan of every entry per lookup)")
    void findByFile_costIndependentOfLibrarySize() throws IOException {
        // Timing-based: queueing the whole library runs one findByFile per track, so a lookup that
        // scans every entry makes that O(N^2). Compare the per-lookup cost for a small and a large
        // library (misses, so neither side pays for Files.exists); a scan costs ~20x more on the
        // large one, a map lookup about the same.
        YtDlpLibrary small = adoptedLibrary("small", 150);
        YtDlpLibrary large = adoptedLibrary("large", 3000);
        Path probe = tempDir.resolve("elsewhere").resolve("Not In Library [zzzzzzzzzzz].webm");

        long smallNanos = bestLookupNanos(small, probe);
        long largeNanos = bestLookupNanos(large, probe);

        double ratio = (double) largeNanos / Math.max(1L, smallNanos);
        assertTrue(ratio < 5.0, "a 20x larger library made lookups " + String.format("%.1f", ratio)
                + "x slower (" + smallNanos + " ns vs " + largeNanos + " ns for the batch)");
    }

    private YtDlpLibrary adoptedLibrary(String name, int size) throws IOException {
        Path folder = tempDir.resolve(name);
        Files.createDirectories(folder);
        for (int i = 0; i < size; i++) {
            Files.createFile(folder.resolve("Song " + i + " [" + String.format("v%010d", i) + "].webm"));
        }
        YtDlpLibrary library = new YtDlpLibrary(folder, tempDir.resolve(name + ".txt"));
        library.load();
        assertTrue(library.find(String.format("v%010d", size - 1)).isPresent());
        return library;
    }

    /** Best of several timed batches of findByFile misses, after a warm-up batch. */
    private static long bestLookupNanos(YtDlpLibrary library, Path probe) {
        int lookups = 2000;
        for (int i = 0; i < lookups; i++) {
            assertTrue(library.findByFile(probe).isEmpty());
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < lookups; i++) {
                library.findByFile(probe);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    @Test
    @DisplayName("record() tolerates unrelated unparseable lines already in the playlist file")
    void record_tolerateMalformedExistingPlaylistLines() throws IOException {
        Files.createDirectories(playlistFile.getParent());
        Files.writeString(playlistFile, "https://www.youtube.com/watch?v=abc\n" + "trailing space \n");

        Path file = libraryFolder.resolve("Song Title [vid00000006].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);

        library.record("vid00000006", file, sampleInfo("vid00000006"));

        List<String> lines = Files.readAllLines(playlistFile);
        assertTrue(lines.contains(file.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("record() starts a new line when the playlist file lacks a trailing newline")
    void record_appendsOnNewLineWhenPlaylistLacksTrailingNewline() throws IOException {
        Files.createDirectories(playlistFile.getParent());
        Path otherFile = libraryFolder.resolve("Other Song [zzzzzzzzzzz].webm");
        Files.writeString(playlistFile, otherFile.toAbsolutePath().toString()); // no trailing newline

        Path file = libraryFolder.resolve("Song Title [vid00000007].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);

        library.record("vid00000007", file, sampleInfo("vid00000007"));

        List<String> lines = Files.readAllLines(playlistFile);
        assertEquals(2, lines.size());
        assertEquals(otherFile.toAbsolutePath().toString(), lines.get(0));
        assertEquals(file.toAbsolutePath().toString(), lines.get(1));
    }

    // ---- richer sidecar metadata ----

    @Test
    @DisplayName("record() reads the sidecar's artist, track and isrc after a download")
    void record_readsSidecar() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("X [abc123def45].webm"));
        Files.writeString(libraryFolder.resolve("X [abc123def45].info.json"), """
                { "artist": "Crazy Frog", "track": "Axel F", "isrc": "GBAYE0900001" }
                """);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());

        LibraryEntry entry = library.record("abc123def45", file, sampleInfo("abc123def45"));

        assertEquals("Crazy Frog", entry.artist());
        assertEquals("Axel F", entry.track());
        assertEquals("GBAYE0900001", entry.isrc());
    }

    @Test
    @DisplayName("the index round-trips the new metadata fields, and an index without them loads with nulls")
    void index_roundTripsMetadata() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("X [abc123def46].webm"));
        Files.writeString(libraryFolder.resolve("X [abc123def46].info.json"), """
                { "artist": "Crazy Frog", "track": "Axel F", "album": "Crazy Hits",
                  "release_year": 2009, "isrc": "GBAYE0900001" }
                """);
        YtDlpLibrary first = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        first.record("abc123def46", file, sampleInfo("abc123def46"));

        YtDlpLibrary second = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        second.load();

        LibraryEntry reloaded = second.find("abc123def46").orElseThrow();
        assertEquals("Crazy Frog", reloaded.artist());
        assertEquals("Axel F", reloaded.track());
        assertEquals("Crazy Hits", reloaded.album());
        assertEquals(2009, reloaded.releaseYear());
        assertEquals("GBAYE0900001", reloaded.isrc());

        Path plain = Files.createFile(libraryFolder.resolve("Plain [plain000001].webm"));
        second.record("plain000001", plain, sampleInfo("plain000001"));
        LibraryEntry plainEntry = second.find("plain000001").orElseThrow();
        assertNull(plainEntry.artist());
        assertNull(plainEntry.track());
        assertNull(plainEntry.album());
        assertNull(plainEntry.releaseYear());
        assertNull(plainEntry.isrc());
    }

    @Test
    @DisplayName("fillMissing() keeps the entry's stored artist and ISRC when filling in missing details")
    void fillMissing_preservesStoredMetadata() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("X [fillmiss01].webm"));
        Files.writeString(libraryFolder.resolve("X [fillmiss01].info.json"),
                "{ \"artist\": \"Crazy Frog\", \"isrc\": \"GBAYE0900001\" }");
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        AudioTrackInfo noArtwork = new AudioTrackInfo("X", "", 0L, "fillmiss01", false,
                "https://www.youtube.com/watch?v=fillmiss01", null, null);
        library.record("fillmiss01", file, noArtwork);
        assertNull(library.find("fillmiss01").orElseThrow().artworkUrl());

        AudioTrackInfo real = new AudioTrackInfo("X", "Crazy Frog", 5000L, "fillmiss01", false,
                "https://www.youtube.com/watch?v=fillmiss01", "https://example.com/art.jpg", null);
        LibraryEntry filled = library.fillMissing("fillmiss01", real).orElseThrow();

        assertEquals("https://example.com/art.jpg", filled.artworkUrl());
        assertEquals("Crazy Frog", filled.artist());
        assertEquals("GBAYE0900001", filled.isrc());
    }

    // ---- schema v2, aliases and rejections ----

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Writes a v1 library.json shaped like the real one: a flat map of video ID to entry. */
    private void writeV1Index(String videoId, Path file) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode entry = root.putObject(videoId);
        entry.put("file", file.toAbsolutePath().toString());
        entry.put("title", "Crazy Frog - Axel F (Official Video)");
        entry.put("author", "Crazy Frog");
        entry.put("lengthMs", 172000);
        entry.put("artworkUrl", "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
        entry.put("uri", "https://www.youtube.com/watch?v=" + videoId);
        JSON.writerWithDefaultPrettyPrinter().writeValue(libraryFolder.resolve("library.json").toFile(), root);
    }

    private JsonNode readIndex() throws IOException {
        return JSON.readTree(libraryFolder.resolve("library.json").toFile());
    }

    @Test
    @DisplayName("a v1 library.json (flat video ID map) loads, and the next save writes v2")
    void v1Index_loads_andNextSaveWritesV2() throws IOException {
        Path axel = Files.createFile(libraryFolder.resolve("Crazy Frog - Axel F (Official Video) [k85mRPqvMbE].webm"));
        writeV1Index("k85mRPqvMbE", axel);

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, file -> Optional.empty());
        library.load();

        LibraryEntry entry = library.find("k85mRPqvMbE").orElseThrow();
        assertEquals("Crazy Frog - Axel F (Official Video)", entry.title());
        assertEquals("Crazy Frog", entry.author());
        assertEquals(172000L, entry.lengthMs());
        assertEquals("https://www.youtube.com/watch?v=k85mRPqvMbE", entry.uri());
        assertEquals("youtube", entry.source());
        assertFalse(entry.adopted());
        assertTrue(readIndex().has("k85mRPqvMbE"), "loading alone doesn't rewrite a v1 index");

        Path other = Files.createFile(libraryFolder.resolve("Other [vid00000011].webm"));
        library.record("vid00000011", other, sampleInfo("vid00000011"));

        JsonNode saved = readIndex();
        assertEquals(2, saved.get("version").asInt());
        assertEquals("Crazy Frog", saved.get("entries").get("k85mRPqvMbE").get("author").asText());
        assertEquals(axel.toAbsolutePath().toString(), saved.get("entries").get("k85mRPqvMbE").get("file").asText());
        assertTrue(saved.get("entries").has("vid00000011"));
        assertFalse(saved.has("k85mRPqvMbE"), "entries live under \"entries\" in v2");
    }

    @Test
    @DisplayName("a v1 adopted entry (no author, no length) stays adopted after loading")
    void v1AdoptedEntry_staysAdopted() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Some Song [abcdefghijk].m4a"));
        ObjectNode root = JSON.createObjectNode();
        ObjectNode entry = root.putObject("abcdefghijk");
        entry.put("file", file.toAbsolutePath().toString());
        entry.put("title", "Some Song");
        entry.put("author", "");
        entry.put("lengthMs", 0);
        entry.putNull("artworkUrl");
        entry.put("uri", "https://www.youtube.com/watch?v=abcdefghijk");
        JSON.writeValue(libraryFolder.resolve("library.json").toFile(), root);

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.load();

        assertTrue(library.find("abcdefghijk").orElseThrow().adopted());
    }

    @Test
    @DisplayName("v2 round-trips entries, aliases and rejections")
    void v2_roundTripsAliasesAndRejections() throws IOException {
        Path yt = Files.createFile(libraryFolder.resolve("Song Title [vid00000012].webm"));
        Path sc = Files.createFile(libraryFolder.resolve("Song Title [soundcloud-123456].mp3"));
        YtDlpLibrary first = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        first.record("vid00000012", yt, sampleInfo("vid00000012"));
        String scKey = "soundcloud:https://soundcloud.com/a/song";
        first.record(scKey, sc, new AudioTrackInfo("SC Song", "SC Author", 2000L, "O:https://x", false,
                "https://soundcloud.com/a/song", null, null));
        first.alias("otherUpload1", "vid00000012");
        first.reject("otherUpload2", "vid00000012");

        YtDlpLibrary second = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        second.load();

        assertEquals("vid00000012", second.resolve("otherUpload1").orElseThrow().key());
        assertTrue(second.isRejected("otherUpload2", "vid00000012"));
        assertFalse(second.isRejected("otherUpload1", "vid00000012"));
        LibraryEntry scEntry = second.find(scKey).orElseThrow();
        assertEquals("SC Song", scEntry.title());
        assertEquals("soundcloud", scEntry.source());
        assertEquals(sc.toAbsolutePath().normalize(), scEntry.file());
        assertEquals(2, second.entries().size());
    }

    @Test
    @DisplayName("resolve() prefers the exact key over an alias, and falls back to the alias target")
    void resolve_prefersExactKey() throws IOException {
        Path a = Files.createFile(libraryFolder.resolve("A [vid00000013].webm"));
        Path b = Files.createFile(libraryFolder.resolve("B [vid00000014].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000013", a, sampleInfo("vid00000013"));
        library.record("vid00000014", b, sampleInfo("vid00000014"));

        library.alias("vid00000013", "vid00000014");
        library.alias("requested01", "vid00000014");

        assertEquals("vid00000013", library.resolve("vid00000013").orElseThrow().key());
        assertEquals("vid00000014", library.resolve("requested01").orElseThrow().key());
        assertTrue(library.find("requested01").isEmpty(), "find() never follows aliases");
        assertTrue(library.resolve("nothing0000").isEmpty());
    }

    @Test
    @DisplayName("unalias() removes the alias, persists it, and returns the old target")
    void unalias_removesAndPersists() throws IOException {
        Path a = Files.createFile(libraryFolder.resolve("A [vid00000015].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000015", a, sampleInfo("vid00000015"));
        library.alias("requested02", "vid00000015");

        assertEquals(Optional.of("vid00000015"), library.unalias("requested02"));
        assertEquals(Optional.empty(), library.unalias("requested02"));
        assertTrue(library.resolve("requested02").isEmpty());

        YtDlpLibrary reloaded = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        reloaded.load();
        assertTrue(reloaded.resolve("requested02").isEmpty());
    }

    @Test
    @DisplayName("an alias to an entry whose file is gone resolves to nothing")
    void resolve_aliasTargetFileGone_isEmpty() throws IOException {
        Path a = Files.createFile(libraryFolder.resolve("A [vid00000016].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000016", a, sampleInfo("vid00000016"));
        library.alias("requested03", "vid00000016");

        Files.delete(a);

        assertTrue(library.resolve("requested03").isEmpty());
    }

    @Test
    @DisplayName("entries() is a snapshot that doesn't change when the library does")
    void entries_isSnapshot() throws IOException {
        Path a = Files.createFile(libraryFolder.resolve("A [vid00000017].webm"));
        Path b = Files.createFile(libraryFolder.resolve("B [vid00000018].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000017", a, sampleInfo("vid00000017"));

        List<LibraryEntry> snapshot = library.entries();
        library.record("vid00000018", b, sampleInfo("vid00000018"));

        assertEquals(1, snapshot.size());
        assertEquals(2, library.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.get(0)));
    }

    @Test
    @DisplayName("a non-YouTube key is recorded with its source, and survives a reload")
    void nonYoutubeKey_recordAndReload() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Clip [vimeo-76979871].m4a"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vimeo:76979871", file, new AudioTrackInfo("Clip", "Owner", 5000L, "76979871", false,
                "https://vimeo.com/76979871", null, null));

        YtDlpLibrary reloaded = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        reloaded.load();

        LibraryEntry entry = reloaded.find("vimeo:76979871").orElseThrow();
        assertEquals("vimeo", entry.source());
        assertEquals("https://vimeo.com/76979871", entry.uri());
        assertEquals(1, reloaded.entries().size());
    }

    @Test
    @DisplayName("bad entries and malformed aliases/rejections are skipped one by one")
    void corruptFields_areTolerated() throws IOException {
        Path good = Files.createFile(libraryFolder.resolve("Good [vid00000019].webm"));
        String json = """
                {
                  "version": 2,
                  "entries": {
                    "vid00000019": { "file": %s, "title": "Good", "author": "A", "lengthMs": 1000, "uri": "u",
                                     "someFutureField": [1, 2, 3] },
                    "noFile00001": { "title": "No file" },
                    "badFile0001": { "file": 42 },
                    "notAnObject": "oops"
                  },
                  "aliases": { "requested04": "vid00000019", "badAlias001": 7, "danglingAl1": "missingKey1" },
                  "rejections": [ ["requested05", "vid00000019"], ["only-one"], "nope", [1, 2], ["a", "missingKey1"] ]
                }
                """.formatted(JSON.writeValueAsString(good.toAbsolutePath().toString()));
        Files.writeString(libraryFolder.resolve("library.json"), json);

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.load();

        assertEquals("Good", library.find("vid00000019").orElseThrow().title());
        assertTrue(library.find("noFile00001").isEmpty());
        assertTrue(library.find("badFile0001").isEmpty());
        assertTrue(library.find("notAnObject").isEmpty());
        assertEquals("vid00000019", library.resolve("requested04").orElseThrow().key());
        assertTrue(library.resolve("danglingAl1").isEmpty());
        assertTrue(library.isRejected("requested05", "vid00000019"));
        assertFalse(library.isRejected("a", "missingKey1"));
    }

    @Test
    @DisplayName("a v2 index with missing or mistyped schema fields still loads its entries")
    void missingSchemaFields_areTolerated() throws IOException {
        Path good = Files.createFile(libraryFolder.resolve("Good [vid00000020].webm"));
        Files.writeString(libraryFolder.resolve("library.json"), """
                { "version": 2,
                  "entries": { "vid00000020": { "file": %s, "title": "Good" } },
                  "aliases": [],
                  "rejections": {} }
                """.formatted(JSON.writeValueAsString(good.toAbsolutePath().toString())));

        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.load();

        LibraryEntry entry = library.find("vid00000020").orElseThrow();
        assertEquals("Good", entry.title());
        assertEquals("", entry.author());
        assertEquals(0L, entry.lengthMs());
        assertEquals("https://www.youtube.com/watch?v=vid00000020", entry.uri());
    }

    @Test
    @DisplayName("an unreadable library.json is copied aside before anything overwrites it")
    void unparseableIndex_isCopiedAside() throws IOException {
        Files.writeString(libraryFolder.resolve("library.json"), "{ this is not json");
        Path file = Files.createFile(libraryFolder.resolve("Song [vid00000021].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());

        library.load();
        library.record("vid00000021", file, sampleInfo("vid00000021"));

        assertEquals("{ this is not json", Files.readString(libraryFolder.resolve("library.json.corrupt")));
        assertEquals(2, readIndex().get("version").asInt());
    }

    @Test
    @DisplayName("reject() is per pair and persisted")
    void reject_isPerPair() throws IOException {
        Path a = Files.createFile(libraryFolder.resolve("A [vid00000022].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000022", a, sampleInfo("vid00000022"));

        library.reject("requested06", "vid00000022");
        library.reject("requested06", "vid00000022");

        assertTrue(library.isRejected("requested06", "vid00000022"));
        assertFalse(library.isRejected("vid00000022", "requested06"));
        assertEquals(1, readIndex().get("rejections").size());
    }

    @Test
    @DisplayName("saves made while rescans run all land, on disk and in memory")
    void savesDuringRescans_allLand() throws Exception {
        Path file = libraryFolder.resolve("Song Title [vid00000030].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        library.record("vid00000030", file, sampleInfo("vid00000030"));

        int saves = 300;
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> scanError = new java.util.concurrent.atomic.AtomicReference<>();
        Thread scanner = new Thread(() -> {
            try {
                while (!done.get()) {
                    library.rescan();
                }
            } catch (Throwable t) {
                scanError.set(t);
            }
        });
        scanner.start();
        List<String> failures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < saves; i++) {
                try {
                    library.alias("requested-" + i, "vid00000030");
                } catch (IOException ex) {
                    failures.add(i + ": " + ex);
                }
            }
        } finally {
            done.set(true);
            scanner.join(10_000);
        }

        assertNull(scanError.get(), "rescan failed");
        assertEquals(List.of(), failures, "saves failed while a rescan ran");
        for (int i = 0; i < saves; i++) {
            assertEquals(Optional.of("vid00000030"), library.aliasTarget("requested-" + i), "in memory: " + i);
        }
        YtDlpLibrary reloaded = new YtDlpLibrary(libraryFolder, playlistFile, f -> Optional.empty());
        reloaded.load();
        for (int i = 0; i < saves; i++) {
            assertEquals(Optional.of("vid00000030"), reloaded.aliasTarget("requested-" + i), "on disk: " + i);
        }
    }
}
