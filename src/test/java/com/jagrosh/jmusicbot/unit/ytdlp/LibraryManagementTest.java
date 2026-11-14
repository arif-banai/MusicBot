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

import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Library management (search, remove, rescan, stats)")
class LibraryManagementTest {

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

    private static AudioTrackInfo info(String key, String title, String author) {
        return new AudioTrackInfo(title, author, 1000L, key, false, "https://x.example/" + key, null, null);
    }

    @Test
    @DisplayName("search() finds entries by title/artist/track words, and by exact key")
    void search_findsByWordsAndKey() throws IOException {
        Path fileA = Files.createFile(libraryFolder.resolve("A [searchaaaa1].webm"));
        Path fileB = Files.createFile(libraryFolder.resolve("B [searchbbbb2].webm"));
        Path fileC = Files.createFile(libraryFolder.resolve("C [searchcccc3].webm"));
        Files.writeString(libraryFolder.resolve("B [searchbbbb2].info.json"),
                "{ \"artist\": \"Crazy Frog\", \"track\": \"Axel F\" }");
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());

        library.record("searchaaaa1", fileA, info("searchaaaa1", "Axel F", "Crazy Frog"));
        library.record("searchbbbb2", fileB, info("searchbbbb2", "Axel F (Remix)", "Someone Else"));
        library.record("searchcccc3", fileC, info("searchcccc3", "Zzz Title", "Other"));

        List<LibraryEntry> byWords = library.search("axel f", 10);
        assertEquals(List.of("searchaaaa1", "searchbbbb2"),
                byWords.stream().map(LibraryEntry::key).toList());

        List<LibraryEntry> byKey = library.search("searchcccc3", 10);
        assertEquals(List.of("searchcccc3"), byKey.stream().map(LibraryEntry::key).toList());
    }

    @Test
    @DisplayName("remove(deleteFile=false) keeps the file but drops the entry, its aliases and its playlist line")
    void remove_indexOnly_keepsFile_dropsAliasesAndPlaylistLine() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [removeidx01].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("removeidx01", file, info("removeidx01", "Song", "Author"));
        library.alias("requestedAlias1", "removeidx01");
        library.reject("requestedReject1", "removeidx01");

        YtDlpLibrary.RemoveResult result = library.remove("removeidx01", false);

        assertTrue(result.removed());
        assertFalse(result.fileDeleted());
        assertNull(result.problem());
        assertTrue(Files.exists(file), "the file itself is kept");
        assertTrue(library.find("removeidx01").isEmpty());
        assertTrue(library.aliasesTo("removeidx01").isEmpty());
        assertFalse(library.isRejected("requestedReject1", "removeidx01"));
        List<String> lines = Files.readAllLines(playlistFile);
        assertFalse(lines.contains(file.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("remove(deleteFile=true) deletes the media file, its sidecar and its thumbnail")
    void remove_withFile_deletesFileSidecarThumb() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [removefil01].webm"));
        Path sidecar = Files.createFile(libraryFolder.resolve("Song [removefil01].info.json"));
        Path thumb = Files.createFile(libraryFolder.resolve("Song [removefil01].jpg"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("removefil01", file, info("removefil01", "Song", "Author"));

        YtDlpLibrary.RemoveResult result = library.remove("removefil01", true);

        assertTrue(result.removed());
        assertTrue(result.fileDeleted());
        assertNull(result.problem());
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(sidecar));
        assertFalse(Files.exists(thumb));
        assertTrue(library.find("removefil01").isEmpty());
    }

    @Test
    @DisplayName("remove(deleteFile=true) changes nothing when the file can't be deleted")
    void remove_withFile_deleteFails_changesNothing() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [removeerr01].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty(),
                f -> { throw new IOException("locked"); });
        library.record("removeerr01", file, info("removeerr01", "Song", "Author"));

        YtDlpLibrary.RemoveResult result = library.remove("removeerr01", true);

        assertFalse(result.removed());
        assertFalse(result.fileDeleted());
        assertNotNull(result.problem());
        assertTrue(Files.exists(file));
        assertTrue(library.find("removeerr01").isPresent());
        List<String> lines = Files.readAllLines(playlistFile);
        assertTrue(lines.contains(file.toAbsolutePath().toString()));
    }

    @Test
    @DisplayName("rescan() counts added, updated and missing entries, and saves")
    void rescan_countsAddedUpdatedMissing() throws IOException {
        Path fileA = Files.createFile(libraryFolder.resolve("A [rescanaaaa1].webm"));
        Path fileB = Files.createFile(libraryFolder.resolve("B [rescanbbbb2].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("rescanaaaa1", fileA, info("rescanaaaa1", "A", ""));
        library.record("rescanbbbb2", fileB, info("rescanbbbb2", "B", ""));

        // "updated": a sidecar for A appears after it was recorded, so rescan backfills its metadata.
        Files.writeString(libraryFolder.resolve("A [rescanaaaa1].info.json"),
                "{ \"artist\": \"Someone\", \"track\": \"A Track\" }");
        // "missing": B's file is gone.
        Files.delete(fileB);
        // "added": a brand-new, unindexed file.
        Files.createFile(libraryFolder.resolve("C [rescancccc3].webm"));

        YtDlpLibrary.ScanSummary summary = library.rescan();

        assertEquals(1, summary.added());
        assertEquals(1, summary.updated());
        assertEquals(1, summary.missing());
        assertTrue(library.find("rescancccc3").isPresent());
        assertEquals("Someone", library.find("rescanaaaa1").orElseThrow().artist());
        assertTrue(library.find("rescanbbbb2").isEmpty());
    }

    @Test
    @DisplayName("rescan() right after record() still has the just-recorded entry")
    void rescan_keepsEntryRecordedJustBefore() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [rescankeep1].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("rescankeep1", file, info("rescankeep1", "Song", "Author"));

        library.rescan();

        assertTrue(library.find("rescankeep1").isPresent());
    }

    @Test
    @DisplayName("stats() reports the song count, total size on disk, and the library folder")
    void stats_reportsCountSizeAndFolder() throws IOException {
        Path fileA = Files.createFile(libraryFolder.resolve("A [statsaaaa01].webm"));
        Path fileB = Files.createFile(libraryFolder.resolve("B [statsbbbb02].webm"));
        Files.writeString(fileA, "12345");
        Files.writeString(fileB, "1234567890");
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("statsaaaa01", fileA, info("statsaaaa01", "A", ""));
        library.record("statsbbbb02", fileB, info("statsbbbb02", "B", ""));

        YtDlpLibrary.LibraryStats stats = library.stats();

        assertEquals(2, stats.songs());
        assertEquals(15L, stats.totalBytes());
        assertEquals(libraryFolder, stats.folder());
    }

    @Test
    @DisplayName("aliasesTo() returns every requested key aliased to a library song")
    void aliasesTo_returnsAllAliasesForKey() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [aliasesto01].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("aliasesto01", file, info("aliasesto01", "Song", "Author"));
        library.alias("req1", "aliasesto01");
        library.alias("req2", "aliasesto01");

        List<String> aliases = library.aliasesTo("aliasesto01");

        assertEquals(2, aliases.size());
        assertTrue(aliases.containsAll(List.of("req1", "req2")));
    }
}
