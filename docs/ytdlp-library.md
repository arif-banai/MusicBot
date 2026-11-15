# yt-dlp Library

How the optional yt-dlp library works: what it stores, how it finds the same song across uploads, and the rules contributors should keep. For operator setup (installing yt-dlp, `config.txt` examples, the settings table), see the [yt-dlp Library section of the README](../README.md#yt-dlp-library-optional). Every key is also documented in `src/main/resources/reference.conf`.

---

## Overview

- The library is a folder of audio files on disk, filled by the operator's own [yt-dlp](https://github.com/yt-dlp/yt-dlp) install. Songs in it play from disk, so the library can be used when a stream won't play.
- The feature is off unless `ytdlp.path` is set. At startup the bot runs `yt-dlp --version`. If that fails, the feature stays off until the next restart.
- **Modes** (`ytdlp.mode`):
  - `prompt` (default): when a YouTube song fails to play, the queue pauses on it and the bot posts **Download & play** and **Download all YouTube songs in queue** buttons in the request channel. Only the requester or a DJ can press them. After `promptTimeoutSeconds` with no press, the song is skipped.
  - `always`: every YouTube song plays through the library. A song not yet in the library is downloaded first, and the next song in the queue is downloaded in the background.
- **Download button:** now-playing shows a **Download** button (DJs only) that saves the current song without interrupting playback. It appears only when `downloadButton` is on, yt-dlp is enabled, the track has a library key (see below), it is not already a library track, and it is not already in the library, directly or through an alias.
- Library tracks keep the original song's title, artist, length, artwork and link in the queue, now-playing and history. Their source shows as `<source> (library)`, or `library` when the source is unknown.

### With the feature off

The index is still loaded whenever the library folder exists. Exact library hits, files from the library folder, and YouTube links to indexed songs still play from disk with their recorded details. Nothing is downloaded, no button or suggestion is shown, aliases are not followed, and the `/library` commands are not registered.

---

## Library layout

| Item | Location | Purpose |
|------|----------|---------|
| Library folder | `ytdlp.libraryFolder` (default `ytdlp-library`) | Audio files, plus yt-dlp `.info.json` sidecars and thumbnails |
| Index | `<libraryFolder>/library.json` | Entries, aliases and rejections |
| Library playlist | `<playlistsFolder>/<libraryPlaylist>.txt` | One absolute file path per line, for each song the bot downloaded |

### `library.json`

- Schema version 2: `{"version": 2, "entries": {...}, "aliases": {...}, "rejections": [[from, to], ...]}`.
- **Entries** are keyed by library key. Each holds the file path, title, author, length, artwork URL, page URL and source. They can also hold `artist`, `track`, `album`, `releaseYear` and `isrc` from a sidecar, and `nameParts` (see Import scan).
- **Aliases** map a requested key to a library key: "this other upload is the same song".
- **Rejections** are ordered pairs a person marked as "not the same song".
- The older flat `videoId → entry` map is still read. Saving always writes version 2.
- Loading is tolerant: a malformed entry, alias or rejection is skipped on its own. An index that can't be parsed at all is copied to `library.json.corrupt` first. If that copy fails, the index is not saved for the rest of the run, and Rescan is refused.

### Library keys (`LibraryKeys`)

| Source | Key |
|--------|-----|
| YouTube | The bare 11-character video ID |
| SoundCloud | `soundcloud:<track permalink>` |
| Bandcamp, Vimeo, Twitch, NicoNico | `<source>:<lavaplayer identifier>` |
| Imported file with no known source | `file:<filename>` |

- Streams, local files, HTTP links and other sources have no key. They can't be downloaded or matched.
- Downloaded files are named `<title> [<id>].<ext>` for YouTube and `<title> [<source>-<id>].<ext>` for other sources. The index maps key to file, so the name only needs to be unique.

### Downloads (`YtDlpDownloader`, `YtDlpCommand`)

- One download runs at a time across the whole bot, on a single worker thread. The worker waits `delaySeconds` after each download.
- "Next up" requests (the song about to play) run before background requests. Otherwise jobs run in request order.
- Requests for the same key join one job and share its future.
- yt-dlp runs with `-f bestaudio --no-playlist --write-info-json` and the bot's output template, followed by `extraArgs` (extra arguments passed to yt-dlp), then the page URL. ffmpeg is not needed.
- The process is killed after `timeoutSeconds`. The file path is taken from yt-dlp's printed output, never rebuilt from the title.
- A successful download records the entry (reading its sidecar) and appends the file to the library playlist.
- Stop, Play now, or the bot leaving voice withdraws that guild from queued jobs. A job that is already running finishes into the library.

### Import scan

When the bot starts, and on **Rescan**, it indexes any file in the library folder the index doesn't know yet. Operators can copy older downloads in. Each file's details come from the first source that has them:

1. **A yt-dlp `.info.json` sidecar** with the same base name. It gives the key (YouTube, SoundCloud, Bandcamp, Vimeo and NicoNico keys match what a lavaplayer track would produce), title, uploader, length, artwork, and the extra metadata fields. A sidecar from another site, or one whose key is already indexed, gives a `file:` key.
2. **A YouTube `[id]` at the end of the filename.**
3. **Tags and length**, read by probing the file with lavaplayer's local source.
4. **The filename.** A name with exactly one `" - "` keeps its two halves as an unordered pair (`nameParts`), because people write both "Artist - Title" and "Title - Artist". A trailing catalogue code such as `[UTR035]` is ignored for matching.

- Files are never moved, renamed or modified. Sidecars, temp files, the index, and non-audio files (images, text, subtitles) are skipped.
- Only files missing from the index are probed, so restarts stay fast.
- An entry built from a filename or tags is marked `adopted`. Its author, length and artwork are filled in from the first real track that plays it. A `file:` entry never takes an author, because an uploader is often not the artist.
- Each scan logs one summary line, e.g. `Library: N songs (… from sidecars, … from YouTube IDs, … from tags/filenames; …)`.

### Missing and moved files

- A lookup counts an entry as missing if its file is gone. A YouTube song is then downloaded again when needed.
- On load and rescan, an entry whose file no longer exists is dropped, along with aliases pointing at it and rejections of it.
- If the library folder itself was moved, an indexed file is found again by name in the folder's new location.

---

## Matching (`SongMatcher`, `MatchSuggestions`)

When a requested song is not in the library (by key or alias), the bot looks for a library song that is probably the same recording. It then offers it with **Switch to library copy** and **Not the same** buttons.

### Normalisation

- Titles are lower-cased and accents are stripped.
- Upload noise is removed: "Official Video", "Lyrics", "Audio", "HD", "Remastered 2011", featured artists, and bracketed codes or video IDs.
- "Artist - Title" titles are split. If the uploader names the second half, "Title - Artist" is used instead. Otherwise the artist comes from the uploader, minus "- Topic", "VEVO" and "Official".

### Scoring

- **Duration gate:** both lengths must be known, and they may differ by at most `max(durationToleranceSeconds, 10% of the shorter length)`. Otherwise the score is 0.
- **ISRC:** if both sides have the same ISRC, the score is 1.0. The duration gate still applies.
- **Version-word guard:** the version words in each title must agree: instrumental, acoustic, live, karaoke, a cappella, remix, edit, cover, slowed, sped up, nightcore, reverb, 8D, demo. If one side has a version word and the other doesn't, or they name different versions, the score is 0.
- **Score:** 0.7 × title word overlap (Jaccard) + 0.3 × artist word overlap. Artists that are equal with spaces removed count as a full match.
- **No artist on either side:** only the title counts, and it must reach `max(minScore, 0.9)`. Generic or one-word titles (`intro`, `outro`, `interlude`, `untitled`, `track N`) never match.
- **Several readings of each library entry are scored, and the best one counts:**
  - "artist track" from the sidecar;
  - both orders of a filename pair;
  - an artist-less title for imported files;
  - otherwise the title and uploader.
- A candidate is suggested when its score is at least `minScore`. The pair must not be rejected, and the library file must still exist.

### When suggestions are posted

- **Queue time:** a single song queued behind others (`/play`, search picks, the prefix commands) is checked right away. Playlist loads are not checked at queue time. Neither are YouTube songs in `always` mode, which play from the library anyway.
- **Play time:** any other song from a supported source is checked when it starts. This is skipped while a download prompt is open in that guild. A song that already got a queue-time suggestion keeps it rather than getting a second one.
- Each requested/library pair is offered at most once per run per guild, so repeat mode doesn't post it every loop.
- Buttons expire when the song ends, after 10 minutes, or when the bot restarts. A queued song's suggestion also expires when the queue is cleared.

### Switch, reject and memory

- The requester or a DJ can press either button. Each suggestion is answered at most once.
- **Switch on a playing song** replaces the stream with the library copy (see Switch alignment). It does not pull from the queue or trigger the end-of-queue disconnect.
- **Switch on a queued song** swaps that exact queue entry for the library copy, keeping its position and requester. The stream never plays. If the entry has been removed, the presser is told it is no longer in the queue.
- Both kinds of Switch record an **alias**. Later requests for that key play the library copy directly: in the queue, at play time, and for YouTube links. The copy shows the library song's title but keeps the requested track's link, so history and saved playlists still point at what was asked for. Missing author, length or artwork on the library entry is filled in from the requested track.
- **Not the same** records a **rejection**, and that pair is never suggested again.
- `/library unlink` (or the `library unlink` prefix command), DJs only, removes the alias behind the song now playing.

### Decision log

For each song it considers, matching logs one DEBUG line from `com.jagrosh.jmusicbot.ytdlp.MatchSuggestions`:

```
Match for <requestedKey>: best <libraryKey> score 0.91 -> ALREADY_OFFERED
```

The reasons are `SUGGESTED`, `NO_CANDIDATE`, `DURATION`, `VERSION`, `LOW_SCORE`, `REJECTED`, `ALREADY_OFFERED` and `MISSING_FILE`. When nothing matched, the line names the closest candidate by title and artist alone, and the gate it failed. This is the line to read when tuning `minScore` or `durationToleranceSeconds`.

---

## Switch alignment (`SwitchAligner`, `AlignmentJob`, `RecentFrames`)

Uploads of the same song often have different intros. A mid-song Switch therefore tries to start the library copy at the same musical point, not at the same timestamp.

- **History:** `AudioHandler` keeps a ring of the last 30 s (1500 frames) of Opus audio it sent. The ring is filled only while yt-dlp, matching and `alignSwitch` are all on. It is cleared when a track starts, and a seek starts a new run.
- **Method:**
  - The ring and the matching window of the library file are decoded to 8 kHz mono. The window is ±min(length difference + 10 s, 60 s) around the stream's position.
  - Both are turned into multi-band log-energy envelopes, and the envelopes are cross-correlated.
  - A 30 s query is tried first, then a 10 s query.
- **Accept rule:** correlation r ≥ 0.80, and a margin of at least 0.20 over the best peak more than 1 s away. The start point must also fall within the library copy, ending at least 2 s before its end.
- **Fallback:** the library copy starts at 0:00 when:
  - the result is unsure;
  - there is less than about 10 s of contiguous history;
  - the job takes longer than 1 s;
  - an error occurs.
- **What it lines up:**
  - **Works:** uploads of the same recording, such as an official video vs a lyrics video or audio-only upload, including extra intros, outros or silence.
  - **Falls back:** different edits, remixes, live versions and speed or pitch changes.
- A queued Switch never aligns. The copy plays from the start when its turn comes.
- `matching.alignSwitch = false` turns alignment off, and the ring is then not filled. Switch always starts at 0:00.
- Each press logs one INFO line, `Switch alignment for <key>: r=… margin=… offset=… -> <outcome>`. Each query logs a DEBUG line.

---

## `/library` commands (DJs only)

Registered when `ytdlp.path` is set.

- `/library unlink`: removes the alias behind the song now playing. It replies ephemerally with what was unlinked, or says the song isn't linked.
- `/library panel`: an ephemeral panel showing the song count, total size and folder, with three buttons:
  - **Info:** the current song's library entry (title, artist, source, key, file, size, aliases pointing at it, link). Offers **Unlink** when the song plays through an alias.
  - **Search:** a text query matched against title, artist and key using the matcher's word cleaning. Results come as a paged list. Picking one shows **Play**, **Queue**, **Remove…** and **Back**.
  - **Rescan:** re-runs the import scan without a restart, then reports songs added, updated and missing.
- **Remove…** asks for confirmation, with two choices:
  - **Remove from library:** drops the index entry and keeps the file.
  - **Remove and delete file:** also deletes the file, plus its sidecar and thumbnails (best-effort).

  Both drop the song's library playlist line, the aliases pointing at it, and rejections of it. If the file can't be deleted (for example, it is playing and locked), nothing changes and the DJ is told why.
- Every press checks that the presser opened the panel and is still a DJ. Panel state expires after 15 minutes.
- The `library` prefix command supports only `unlink`.

---

## Invariants for contributors

- **Feature off means no change:** with `ytdlp.path` blank, behaviour matches a bot without this feature. The only exception is existing library songs, which still play from disk.
- **One choke point:** every track goes through `YtDlpService.prepareForPlayback` right before the player starts it. Library decisions belong there, or in `wrapLibraryFile` at queue time.
- **Index writes are atomic:** `library.json` is written to a temp file in the same folder and then moved into place. Removing a playlist line rewrites the playlist the same way. New playlist lines are appended, skipping paths already listed. All writes are serialized on the library.
- **Deletes stay inside the library folder:** `YtDlpLibrary.remove` refuses to delete a file outside it. A failed delete leaves the entry untouched.
- **Scans never wipe newer changes:** load and rescan read the folder, probe files and read sidecars without the library lock. They swap the result in only if nothing changed meanwhile, and retry otherwise. The last attempt holds the lock.
- **Slow work stays off locks and event threads:**
  - Index updates from playback (aliases, rejections, filled-in details) run on a background executor.
  - Suggestion scans run on the single `ytdlp-prompt` thread.
  - Alignment runs on the `ytdlp-align` pool.
  - Panel work (rescan, remove, stats, views) runs on the `ytdlp-library` executor after the interaction is acknowledged.
  - Player-event hooks only touch concurrent maps and queue Discord calls.
- **Shared download futures:** never cancel or complete a future returned by the downloader. Use `cancelGuild` instead.
- **Discord calls:** use `.queue()`, never block on a result, and acknowledge each interaction exactly once.
- **One answer per press:** prompts and suggestions are claimed atomically, so a double press or a press racing a timeout acts once at most. Every other press gets "expired".
- **Matching never decides alone:** nothing switches to a library copy without a person pressing Switch.

---

## Configuration keys

All keys live in the `ytdlp { }` block. Full comments are in `reference.conf`, and the setup table is in the README.

| Key | Default | Meaning |
|-----|---------|---------|
| `path` | `""` | yt-dlp executable; blank turns the feature off |
| `mode` | `"prompt"` | `prompt` or `always` |
| `libraryFolder` | `"ytdlp-library"` | Folder holding the library |
| `libraryPlaylist` | `"ytdlp-library"` | Playlist name listing downloaded songs |
| `delaySeconds` | `5` | Pause between downloads |
| `timeoutSeconds` | `300` | Time limit for one download |
| `extraArgs` | `[]` | Extra arguments passed to yt-dlp, after the bot's own |
| `promptTimeoutSeconds` | `120` | How long a `prompt` waits before skipping |
| `downloadButton` | `true` | Show the now-playing Download button |
| `matching.enabled` | `true` | Offer similar library songs |
| `matching.durationToleranceSeconds` | `10` | Minimum length tolerance, 0–60 (widens to 10% of the shorter song) |
| `matching.minScore` | `0.85` | Similarity threshold, 0.0–1.0 |
| `matching.alignSwitch` | `true` | Line up a mid-song Switch instead of starting at 0:00 |

Out-of-range `durationToleranceSeconds` and `minScore` values are clamped, with a warning. An unknown `mode` falls back to `prompt`.

---

## Testing

Unit tests live under `src/test/java/com/jagrosh/jmusicbot/unit/`, because Surefire only runs `**/unit/**`. They never run yt-dlp or touch the network.

| Area | Tests (`unit/ytdlp/` unless noted) |
|------|------------------------------------|
| Settings and command line | `YtDlpSettingsTest`, `YtDlpCommandTest`, `YtDlpExtraArgsRenderingTest`, `SystemProcessRunnerTest` |
| Index, keys, import, removal | `YtDlpLibraryTest`, `LibraryKeysTest`, `LibraryImportScanTest`, `LibraryManagementTest`, `SidecarMetadataTest` |
| Downloads and playback | `YtDlpDownloaderTest`, `LibraryAudioTrackTest`, `PrepareForPlaybackTest`, `LibrarySourceManagerTest`, `LibraryAliasPlaybackTest` |
| Prompt mode | `YtDlpPromptTest`, `PendingPromptsTest`, `unit/listener/YtDlpPromptListenerTest` |
| Matching and suggestions | `SongMatcherTest`, `MatchSuggestionsTest`, `QueuedSuggestionTest` |
| Switch alignment | `SwitchAlignerTest`, `unit/audio/RecentFramesTest`, `AlignmentJobLocalTest` |
| `/library` and Download button | `LibraryPanelTest`, `LibraryUnlinkTest`, `unit/PlaybackControlsListenerDownloadButtonTest` |

`AlignmentJobLocalTest` runs the real decode on a pair of local audio files. It is skipped unless `-Dytdlp.alignTest.dir=<dir>` names a folder holding `library.webm` and `stream.webm`. `-Dytdlp.alignTest.offsetMs` sets the expected offset.
