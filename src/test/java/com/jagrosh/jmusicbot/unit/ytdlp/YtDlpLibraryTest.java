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

        Optional<LibraryEntry> found = library.findById("vid00000001");
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

        Optional<LibraryEntry> found = second.findById("vid00000003");
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

        Optional<LibraryEntry> found = library.findById("abcdefghijk");
        assertTrue(found.isPresent());
        assertEquals("Some Song", found.get().title());
    }

    @Test
    @DisplayName("load() does not adopt files whose bracketed suffix is a catalogue number, not a video ID")
    void load_doesNotAdoptCatalogueNumberFiles() throws IOException {
        Path file = libraryFolder.resolve("Anck - Ingenue [UTR035].opus");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);

        library.load();

        assertTrue(library.findById("UTR035").isEmpty());
        assertTrue(library.findByFile(file).isEmpty());
    }

    @Test
    @DisplayName("findById() is a miss once the file is gone")
    void findById_missingFileIsMiss() throws IOException {
        Path file = libraryFolder.resolve("Song Title [vid00000004].webm");
        Files.createFile(file);
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile);
        library.record("vid00000004", file, sampleInfo("vid00000004"));

        Files.delete(file);

        assertTrue(library.findById("vid00000004").isEmpty());
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
        assertEquals("vid00000005", found.get().videoId());
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
}
