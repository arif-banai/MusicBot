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
        Path fileD = Files.createFile(libraryFolder.resolve("D [searchdddd4].webm"));
        Files.writeString(libraryFolder.resolve("B [searchbbbb2].info.json"),
                "{ \"artist\": \"Crazy Frog\", \"track\": \"Axel F\" }");
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());

        library.record("searchaaaa1", fileA, info("searchaaaa1", "Axel F", "Crazy Frog"));
        library.record("searchbbbb2", fileB, info("searchbbbb2", "Axel F (Remix)", "Someone Else"));
        library.record("searchcccc3", fileC, info("searchcccc3", "Zzz Title", "Other"));
        // No sidecar artist/track: only the title itself names the artist, split on " - ".
        library.record("searchdddd4", fileD, info("searchdddd4", "Sunrise Avenue - Axel F Reprise", "Uploader"));

        List<LibraryEntry> byWords = library.search("axel f", 10);
        assertEquals(List.of("searchaaaa1", "searchbbbb2", "searchdddd4"),
                byWords.stream().map(LibraryEntry::key).toList());

        List<LibraryEntry> byArtistInTitle = library.search("sunrise avenue", 10);
        assertEquals(List.of("searchdddd4"), byArtistInTitle.stream().map(LibraryEntry::key).toList());

        List<LibraryEntry> byKey = library.search("searchcccc3", 10);
        assertEquals(List.of("searchcccc3"), byKey.stream().map(LibraryEntry::key).toList());
    }

    @Test
    @DisplayName("search() also matches the entry's author (uploader)")
    void search_matchesAuthor() throws IOException {
        Path fileA = Files.createFile(libraryFolder.resolve("A [searchuplo1].webm"));
        Path fileB = Files.createFile(libraryFolder.resolve("B [searchuplo2].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("searchuplo1", fileA, info("searchuplo1", "Some Song", "Crazy Frog"));
        library.record("searchuplo2", fileB, info("searchuplo2", "Other Song", "Someone Else"));

        List<LibraryEntry> byUploader = library.search("crazy frog", 10);

        assertEquals(List.of("searchuplo1"), byUploader.stream().map(LibraryEntry::key).toList());
    }

    @Test
    @DisplayName("remove(deleteFile=false) keeps the file but drops the entry, its aliases and its playlist line")
    void remove_indexOnly_keepsFile_dropsAliasesAndPlaylistLine() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Song [removeidx01].webm"));
        Path sidecar = Files.createFile(libraryFolder.resolve("Song [removeidx01].info.json"));
        Path thumb = Files.createFile(libraryFolder.resolve("Song [removeidx01].jpg"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("removeidx01", file, info("removeidx01", "Song", "Author"));
        library.alias("requestedAlias1", "removeidx01");
        library.reject("requestedReject1", "removeidx01");

        YtDlpLibrary.RemoveResult result = library.remove("removeidx01", false);

        assertTrue(result.removed());
        assertFalse(result.fileDeleted());
        assertNull(result.problem());
        assertTrue(Files.exists(file), "the file itself is kept");
        assertTrue(Files.exists(sidecar), "index-only keeps the sidecar too");
        assertTrue(Files.exists(thumb), "index-only keeps the thumbnail too");
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
    @DisplayName("remove(deleteFile=true) refuses to delete a file outside the library folder")
    void remove_withFile_outsideLibraryFolder_refusesToDelete() throws IOException {
        Path outside = Files.createFile(tempDir.resolve("outside.webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("outsideone1", outside, info("outsideone1", "Song", "Author"));

        YtDlpLibrary.RemoveResult result = library.remove("outsideone1", true);

        assertFalse(result.removed());
        assertFalse(result.fileDeleted());
        assertEquals("That file is outside the library folder; not deleting it.", result.problem());
        assertTrue(Files.exists(outside));
        assertTrue(library.find("outsideone1").isPresent());
    }

    @Test
    @DisplayName("remove() rewrites the playlist file, keeping every other line")
    void remove_rewritesPlaylist_keepsOtherLines() throws IOException {
        Path fileA = Files.createFile(libraryFolder.resolve("A [keeplineaa1].webm"));
        Path fileB = Files.createFile(libraryFolder.resolve("B [keeplinebb2].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("keeplineaa1", fileA, info("keeplineaa1", "A", "Author"));
        library.record("keeplinebb2", fileB, info("keeplinebb2", "B", "Author"));

        library.remove("keeplineaa1", false);

        List<String> lines = Files.readAllLines(playlistFile);
        assertFalse(lines.contains(fileA.toAbsolutePath().toString()));
        assertTrue(lines.contains(fileB.toAbsolutePath().toString()));
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
    @DisplayName("a sidecar with no artist, track or ISRC is read once, then never again (marker saved in library.json)")
    void backfill_readsEachSidecarOnce() throws IOException {
        Path file = Files.createFile(libraryFolder.resolve("Old [backfill001].webm"));
        Files.writeString(libraryFolder.resolve("Old [backfill001].info.json"), "{ \"title\": \"Old\" }");
        // An index written before sidecar metadata was read: no artist/track/isrc, no marker.
        Files.writeString(libraryFolder.resolve("library.json"), "{\"version\": 2, \"entries\": {\"backfill001\": "
                + "{\"file\": " + com.fasterxml.jackson.databind.node.TextNode.valueOf(file.toString()) + ", "
                + "\"title\": \"Old\", \"author\": \"Someone\", \"lengthMs\": 1000}}, \"aliases\": {}, \"rejections\": []}");
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        YtDlpLibrary.SidecarReader counting = f -> {
            reads.incrementAndGet();
            return com.jagrosh.jmusicbot.ytdlp.SidecarMetadata.read(f);
        };

        YtDlpLibrary first = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty(),
                Files::delete, counting);
        first.load();
        assertEquals(1, reads.get(), "the upgrade load reads the sidecar once");
        assertTrue(Files.readString(libraryFolder.resolve("library.json")).contains("\"metadataChecked\" : true"));

        first.rescan();
        YtDlpLibrary second = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty(),
                Files::delete, counting);
        second.load();

        assertEquals(1, reads.get(), "neither a rescan nor the next startup reads it again");
        assertTrue(second.find("backfill001").isPresent());
    }

    @Test
    @DisplayName("rescan() probes without holding the library lock, and keeps a link saved while it ran")
    void rescan_probesOutsideLock_keepsConcurrentChange() throws Exception {
        Path fileA = Files.createFile(libraryFolder.resolve("A [concurrent1].webm"));
        java.util.concurrent.atomic.AtomicBoolean firstProbe = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean otherThreadDone = new java.util.concurrent.atomic.AtomicBoolean();
        YtDlpLibrary[] holder = new YtDlpLibrary[1];
        YtDlpLibrary.Prober prober = f -> {
            if (firstProbe.compareAndSet(true, false)) {
                // Another thread (the player, a panel) uses the library while the scan probes.
                Thread other = new Thread(() -> {
                    try {
                        holder[0].find("concurrent1");
                        holder[0].alias("requested01", "concurrent1");
                        // A download finishing mid-rescan (plan Review Focus 4).
                        Path downloaded = Files.createFile(libraryFolder.resolve("D [concurrent2].webm"));
                        holder[0].record("concurrent2", downloaded, info("concurrent2", "D", "Author"));
                        otherThreadDone.set(true);
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
                other.start();
                try {
                    other.join(2_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return java.util.Optional.of(new YtDlpLibrary.ProbeResult("New", "Artist", 1000L));
        };
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, prober);
        holder[0] = library;
        library.record("concurrent1", fileA, info("concurrent1", "A", "Author"));
        Files.writeString(libraryFolder.resolve("New Song.mp3"), "x"); // unindexed: rescan probes it

        YtDlpLibrary.ScanSummary summary = library.rescan();

        assertTrue(otherThreadDone.get(), "the other thread wasn't blocked by the scan");
        assertEquals(1, summary.added());
        assertEquals("concurrent1", library.aliasTarget("requested01").orElseThrow(),
                "the link saved during the scan survives it");
        assertEquals("Author", library.find("concurrent2").orElseThrow().author(),
                "the song recorded during the scan survives it, with its recorded details");
        assertTrue(library.find("file:New Song.mp3").isPresent());
    }

    @Test
    @DisplayName("a failed save at load fails only that write: saving (and Rescan) still work afterwards")
    void load_failedSave_doesNotBlockLaterSaves() throws IOException {
        Files.writeString(libraryFolder.resolve("New Song.mp3"), "x"); // probed, so load wants to save
        Path indexDir = Files.createDirectories(libraryFolder.resolve("library.json").resolve("occupied"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile,
                f -> java.util.Optional.of(new YtDlpLibrary.ProbeResult("New", "Artist", 1000L)));

        assertThrows(IOException.class, library::load);
        assertTrue(library.find("file:New Song.mp3").isPresent(), "the scan still landed in memory");

        Files.delete(indexDir);
        Files.delete(indexDir.getParent());
        assertDoesNotThrow(library::rescan);
        assertTrue(Files.isRegularFile(libraryFolder.resolve("library.json")));
    }

    @Test
    @DisplayName("rescan() is refused with a clear problem when the index couldn't be read at startup")
    void rescan_refusedWhenSaveBlocked() throws IOException {
        Files.writeString(libraryFolder.resolve("library.json"), "{ not json");
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty(),
                Files::delete, f -> java.util.Optional.empty());
        // Make copying the unreadable index aside fail: a directory already sits at its target name.
        Files.createDirectories(libraryFolder.resolve("library.json.corrupt").resolve("occupied"));
        library.load();

        IOException ex = assertThrows(IOException.class, library::rescan);

        assertEquals("The library index couldn't be read at startup; fix or remove library.json and restart.",
                ex.getMessage());
    }

    @Test
    @DisplayName("the service runs rescan, remove and stats on the library executor, not the prompt/background one")
    void service_runsPanelWorkOnLibraryExecutor() throws Exception {
        Path file = Files.createFile(libraryFolder.resolve("Song [svcexec0001].webm"));
        YtDlpLibrary library = new YtDlpLibrary(libraryFolder, playlistFile, f -> java.util.Optional.empty());
        library.record("svcexec0001", file, info("svcexec0001", "Song", "Author"));
        java.util.List<Runnable> background = new java.util.ArrayList<>();
        java.util.List<Runnable> libraryWork = new java.util.ArrayList<>();
        com.jagrosh.jmusicbot.ytdlp.YtDlpService service = new com.jagrosh.jmusicbot.ytdlp.YtDlpService(
                com.jagrosh.jmusicbot.ytdlp.YtDlpSettings.disabled(), library,
                org.mockito.Mockito.mock(com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader.class),
                org.mockito.Mockito.mock(com.jagrosh.jmusicbot.ytdlp.ProcessRunner.class),
                background::add, libraryWork::add);

        var rescan = service.rescanLibrary();
        var stats = service.libraryStats();
        var remove = service.removeFromLibrary("svcexec0001", false, 1L);
        service.runLibraryWork(() -> { });

        assertTrue(background.isEmpty(), "nothing went to the background (prompt) executor");
        assertEquals(4, libraryWork.size());
        libraryWork.forEach(Runnable::run);
        assertEquals(0, rescan.get().added());
        assertEquals(1, stats.get().songs());
        assertTrue(remove.get().removed());
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
