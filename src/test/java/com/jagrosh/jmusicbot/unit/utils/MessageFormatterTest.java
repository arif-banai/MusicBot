package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.MessageFormatter;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.ProcessRunner;
import com.jagrosh.jmusicbot.ytdlp.YtDlpDownloader;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.jagrosh.jmusicbot.ytdlp.YtDlpSettings;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageFormatter Tests")
class MessageFormatterTest
{
    @Test
    @DisplayName("buildNowPlayingMessage() passes author text through verbatim in full embed")
    void buildNowPlayingMessage_passesAuthorTextThroughVerbatimInFullEmbed()
    {
        String author = "МР. CREDO [Этой]";

        MessageCreateData message = buildNowPlayingMessage("Test Title", author, false, false, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        MessageEmbed.Field authorField = getField(embed, "Author");
        assertNotNull(authorField);
        assertEquals(author, authorField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout without progress bar still shows elapsed and total time")
    void buildNowPlayingMessage_minimalWithoutProgressBar_stillShowsElapsedAndTotalTime()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, false, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("`["));
        assertFalse(embed.getDescription().contains("▬"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout shows progress bar when enabled")
    void buildNowPlayingMessage_minimalShowsProgressBarWhenEnabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, true, "",
                RepeatMode.OFF, 0, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("▬"));
        assertTrue(embed.getDescription().contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout surfaces source/queue/repeat in description and footerInfo in footer")
    void buildNowPlayingMessage_minimalLayout_surfacesMetadataInDescriptionAndFooterInfoInFooter()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, false, "Playing next song.",
                RepeatMode.ALL, 2, false, 0L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("**Source:** youtube • 2 songs queued\n**Volume:** 50% • **Repeat:** All"));
        assertNull(embed.getFooter());
    }

    @Test
    @DisplayName("buildNoMusicPlayingMessage() full and minimal obey progress bar toggle")
    void buildNoMusicPlayingMessage_obeysProgressBarToggle()
    {
        String fullDisabled = noMusicDescription(false, false);
        String fullEnabled = noMusicDescription(false, true);
        String minimalDisabled = noMusicDescription(true, false);
        String minimalEnabled = noMusicDescription(true, true);

        assertFalse(fullDisabled.contains("▬"));
        assertTrue(fullEnabled.contains("▬"));
        assertFalse(minimalDisabled.contains("▬"));
        assertTrue(minimalEnabled.contains("▬"));
        assertTrue(fullDisabled.contains(AudioHandler.STOP_EMOJI));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() uses namespaced now-playing button IDs")
    void buildNowPlayingMessage_usesNamespacedNowPlayingButtonIds()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 0, false, 0L, true);
        List<String> buttonIds = message.getComponents().stream()
                .flatMap(row -> row.asActionRow().getButtons().stream())
                .map(net.dv8tion.jda.api.components.buttons.Button::getCustomId)
                .toList();
        assertTrue(buttonIds.contains("np_previous"));
        assertTrue(buttonIds.contains("np_pause"));
        assertTrue(buttonIds.contains("np_skip"));
        assertTrue(buttonIds.contains("np_stop"));
        assertTrue(buttonIds.contains("np_shuffle"));
        assertTrue(buttonIds.contains("np_repeat"));
        assertTrue(buttonIds.contains("np_favorite"));
        assertTrue(buttonIds.contains("np_voldown"));
        assertTrue(buttonIds.contains("np_volup"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout includes thumbnail when images enabled")
    void buildNowPlayingMessage_fullLayout_includesThumbnailWhenImagesEnabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, true, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertEquals("https://img.youtube.com/vi/id-1/mqdefault.jpg", embed.getThumbnail().getUrl());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("**Source:** youtube"));
        assertFalse(embed.getDescription().contains("songs queued"));
        assertFalse(embed.getDescription().contains("**Volume:**"));
        assertFalse(embed.getDescription().contains("**Repeat:**"));
        MessageEmbed.Field authorField = getField(embed, "Author");
        assertNotNull(authorField);
        assertEquals("Test Author", authorField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout omits thumbnail when images disabled")
    void buildNowPlayingMessage_fullLayout_omitsThumbnailWhenImagesDisabled()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 2, false, 120_000L, false, false, "id-1");
        MessageEmbed embed = getSingleEmbed(message);
        assertNull(embed.getThumbnail());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout uses original duration/queue/volume fields")
    void buildNowPlayingMessage_fullLayout_usesOriginalStatsFields()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 3, false, 120_000L, false);
        MessageEmbed embed = getSingleEmbed(message);
        assertNull(getField(embed, "Info"));
        MessageEmbed.Field durationField = getField(embed, "Duration");
        MessageEmbed.Field queueField = getField(embed, "Queue");
        MessageEmbed.Field volumeField = getField(embed, "Volume");
        assertNotNull(durationField);
        assertNotNull(queueField);
        assertNotNull(volumeField);
        assertEquals("04:03", durationField.getValue());
        assertEquals("3", queueField.getValue());
        assertEquals("50%", volumeField.getValue());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() full layout progress bar follows toggle")
    void buildNowPlayingMessage_fullLayout_progressBarFollowsToggle()
    {
        MessageCreateData noProgress = buildNowPlayingMessage("Test Title", "Test Author", false, false, "",
                RepeatMode.OFF, 0, false, 20_000L, false);
        MessageCreateData withProgress = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 0, false, 20_000L, false);

        String noProgressDescription = getSingleEmbed(noProgress).getDescription();
        String withProgressDescription = getSingleEmbed(withProgress).getDescription();
        assertNotNull(noProgressDescription);
        assertNotNull(withProgressDescription);
        assertTrue(noProgressDescription.contains("`["));
        assertFalse(noProgressDescription.contains("▬"));
        assertTrue(withProgressDescription.contains("`["));
        assertTrue(withProgressDescription.contains("▬"));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() now-playing buttons expose clearer labels and styles")
    void buildNowPlayingMessage_buttons_useClearLabelsAndStyles()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 5, false, 20_000L, true);
        Button pause = getButton(message, "np_pause");
        Button stop = getButton(message, "np_stop");
        Button repeat = getButton(message, "np_repeat");
        assertNotNull(pause);
        assertNotNull(stop);
        assertNotNull(repeat);
        assertNotNull(getButton(message, "np_favorite"));
        assertEquals("Pause", pause.getLabel());
        assertEquals(ButtonStyle.PRIMARY, pause.getStyle());
        assertEquals("Stop", stop.getLabel());
        assertEquals(ButtonStyle.DANGER, stop.getStyle());
        assertEquals("Repeat", repeat.getLabel());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() favorite button style reflects favorited state")
    void buildNowPlayingMessage_favoriteButtonStyleReflectsFavoritedState()
    {
        MessageCreateData notFavorited = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 1, false, 2_000L, true, false, "id-1", 0, 50, false);
        assertEquals(ButtonStyle.SECONDARY, getButton(notFavorited, "np_favorite").getStyle());

        MessageCreateData favorited = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 1, false, 2_000L, true, false, "id-1", 0, 50, true);
        assertEquals(ButtonStyle.SUCCESS, getButton(favorited, "np_favorite").getStyle());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() now-playing buttons disable boundary actions")
    void buildNowPlayingMessage_buttons_disableBoundaryActions()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", true, true, "",
                RepeatMode.OFF, 1, false, 3_000L, true, false, "id-1", 0);
        assertTrue(getButton(message, "np_previous").isDisabled());
        assertTrue(getButton(message, "np_shuffle").isDisabled());
        assertFalse(getButton(message, "np_voldown").isDisabled());
        assertFalse(getButton(message, "np_volup").isDisabled());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() now-playing buttons enable previous with history and disable volume edges")
    void buildNowPlayingMessage_buttons_enablePreviousWithHistoryAndDisableVolumeEdges()
    {
        MessageCreateData minVolMessage = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.ALL, 3, false, 2_000L, true, false, "id-1", 2, 0);
        assertFalse(getButton(minVolMessage, "np_previous").isDisabled());
        assertTrue(getButton(minVolMessage, "np_voldown").isDisabled());
        assertFalse(getButton(minVolMessage, "np_volup").isDisabled());
        assertEquals("Repeat All", getButton(minVolMessage, "np_repeat").getLabel());

        MessageCreateData maxVolMessage = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.SINGLE, 3, true, 2_000L, true, false, "id-1", 2, 150);
        assertFalse(getButton(maxVolMessage, "np_previous").isDisabled());
        assertFalse(getButton(maxVolMessage, "np_voldown").isDisabled());
        assertTrue(getButton(maxVolMessage, "np_volup").isDisabled());
        assertEquals("Resume", getButton(maxVolMessage, "np_pause").getLabel());
        assertEquals("Repeat One", getButton(maxVolMessage, "np_repeat").getLabel());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() shows a waiting line and no progress bar while a yt-dlp gate is open")
    void awaitingGate_rendersWaitingLine_noProgressBar()
    {
        MessageCreateData message = buildNowPlayingMessageForGatedTrack(new CompletableFuture<>(), true);
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("Waiting"));
        assertTrue(embed.getDescription().contains(
                "Waiting: YouTube blocked this song. Choose in the yt-dlp download prompt."));
        // The prompt can be in a different channel from the now-playing message.
        assertFalse(embed.getDescription().contains("above"));
        assertFalse(embed.getDescription().contains("▬"));
        assertFalse(embed.getDescription().contains("`["));
    }

    @Test
    @DisplayName("buildNowPlayingMessage() renders a normal track exactly as before (no gate involved)")
    void normalTrack_renderingUnchanged()
    {
        MessageCreateData message = buildNowPlayingMessage("Test Title", "Test Author", false, true, "",
                RepeatMode.OFF, 0, false, 20_000L, true);
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertFalse(embed.getDescription().contains("Waiting"));
        assertTrue(embed.getDescription().contains("▬"));
        assertTrue(embed.getDescription().contains("`["));
        assertFalse(getButton(message, "np_pause").isDisabled());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() disables Pause while a yt-dlp gate is open, but keeps Skip/Stop enabled")
    void awaitingGate_disablesPauseButton_keepsSkipAndStopEnabled()
    {
        MessageCreateData message = buildNowPlayingMessageForGatedTrack(new CompletableFuture<>(), true, false);
        assertTrue(getButton(message, "np_pause").isDisabled());
        assertFalse(getButton(message, "np_skip").isDisabled());
        assertFalse(getButton(message, "np_stop").isDisabled());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() minimal layout also shows the waiting line while a yt-dlp gate is open")
    void awaitingGate_minimalLayout_rendersWaitingLine()
    {
        MessageCreateData message = buildNowPlayingMessageForGatedTrack(new CompletableFuture<>(), true, true);
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("Waiting"));
        assertFalse(embed.getDescription().contains("▬"));
        assertTrue(getButton(message, "np_pause").isDisabled());
    }

    @Test
    @DisplayName("buildNowPlayingMessage() returns to normal playback rendering once the gate resolves")
    void gateResolved_rendersNormally_noLongerWaiting()
    {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        gate.complete(null);
        MessageCreateData message = buildNowPlayingMessageForGatedTrack(gate, true, false);
        MessageEmbed embed = getSingleEmbed(message);
        assertNotNull(embed.getDescription());
        assertFalse(embed.getDescription().contains("Waiting"));
        assertTrue(embed.getDescription().contains("▬"));
        assertTrue(embed.getDescription().contains("`["));
        assertFalse(getButton(message, "np_pause").isDisabled());
    }

    @Nested
    @DisplayName("yt-dlp Download button")
    class DownloadButtonTests
    {
        @TempDir
        Path libraryFolder;

        private static final String KEY = "abcdefghijk";

        private YtDlpService ytDlpService(boolean path, boolean downloadButton) {
            YtDlpSettings settings = new YtDlpSettings(path ? "yt-dlp" : "", YtDlpSettings.Mode.PROMPT,
                    libraryFolder, "ytdlp-library", 5, 300, List.of(), 120,
                    downloadButton, true, 5, 0.85, true);
            YtDlpLibrary library = new YtDlpLibrary(libraryFolder, libraryFolder.resolve("pl.txt"));
            return new YtDlpService(settings, library, mock(YtDlpDownloader.class), mock(ProcessRunner.class),
                    Runnable::run);
        }

        private void recordInLibrary(YtDlpService ytDlp) throws IOException {
            Path file = libraryFolder.resolve("Axel F [" + KEY + "].webm");
            Files.writeString(file, "x");
            ytDlp.library().record(KEY, file,
                    new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, KEY, false, "https://example.com/track"));
        }

        /** Builds the message for a plain (non-library) YouTube track with the given yt-dlp wiring. */
        private MessageCreateData message(YtDlpService ytDlp) {
            Bot bot = mock(Bot.class);
            BotConfig config = mock(BotConfig.class);
            SettingsManager settingsManager = mock(SettingsManager.class);
            Settings settings = mock(Settings.class);
            when(bot.getConfig()).thenReturn(config);
            when(bot.getSettingsManager()).thenReturn(settingsManager);
            when(bot.getYtDlp()).thenReturn(ytDlp);
            when(config.useNPImages()).thenReturn(false);

            Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
            when(guild.getName()).thenReturn("Test Guild");
            when(settingsManager.getSettings(guild)).thenReturn(settings);
            when(settings.useMinimalNowPlayingMessage(config)).thenReturn(false);
            when(settings.showNowPlayingButtons(config)).thenReturn(true);
            when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

            AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
            AudioTrackInfo trackInfo = new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, KEY, false,
                    "https://example.com/track");
            when(track.getInfo()).thenReturn(trackInfo);
            when(track.getSourceManager().getSourceName()).thenReturn("youtube");
            when(track.getUserData(RequestMetadata.class)).thenReturn(null);

            NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
            return MessageFormatter.buildNowPlayingMessage(bot, info);
        }

        private MessageCreateData messageMinimal(YtDlpService ytDlp) {
            Bot bot = mock(Bot.class);
            BotConfig config = mock(BotConfig.class);
            SettingsManager settingsManager = mock(SettingsManager.class);
            Settings settings = mock(Settings.class);
            when(bot.getConfig()).thenReturn(config);
            when(bot.getSettingsManager()).thenReturn(settingsManager);
            when(bot.getYtDlp()).thenReturn(ytDlp);

            Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
            when(guild.getName()).thenReturn("Test Guild");
            when(settingsManager.getSettings(guild)).thenReturn(settings);
            when(settings.useMinimalNowPlayingMessage(config)).thenReturn(true);
            when(settings.showNowPlayingButtons(config)).thenReturn(true);
            when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

            AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
            AudioTrackInfo trackInfo = new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, KEY, false,
                    "https://example.com/track");
            when(track.getInfo()).thenReturn(trackInfo);
            when(track.getSourceManager().getSourceName()).thenReturn("youtube");
            when(track.getUserData(RequestMetadata.class)).thenReturn(null);

            NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
            return MessageFormatter.buildNowPlayingMessage(bot, info);
        }

        private boolean hasDownloadButton(MessageCreateData message) {
            return message.getComponents().stream()
                    .flatMap(row -> row.asActionRow().getButtons().stream())
                    .anyMatch(b -> "np_ytdlp_save".equals(b.getCustomId()));
        }

        @Test
        @DisplayName("shown when enabled, the key is present, and it's not already in the library")
        void shown_whenAllConditionsHold() {
            assertTrue(hasDownloadButton(message(ytDlpService(true, true))));
        }

        @Test
        @DisplayName("shown in the minimal layout too")
        void shown_inMinimalLayout() {
            assertTrue(hasDownloadButton(messageMinimal(ytDlpService(true, true))));
        }

        @Test
        @DisplayName("hidden when ytdlp.downloadButton is off")
        void hidden_whenDownloadButtonSettingOff() {
            assertFalse(hasDownloadButton(message(ytDlpService(true, false))));
        }

        @Test
        @DisplayName("hidden when yt-dlp itself is off (no path configured)")
        void hidden_whenYtDlpDisabled() {
            assertFalse(hasDownloadButton(message(ytDlpService(false, true))));
        }

        @Test
        @DisplayName("hidden when there is no yt-dlp service at all")
        void hidden_whenNoYtDlpService() {
            assertFalse(hasDownloadButton(message(null)));
        }

        @Test
        @DisplayName("hidden when the song is already in the library")
        void hidden_whenAlreadyInLibrary() throws IOException {
            YtDlpService ytDlp = ytDlpService(true, true);
            recordInLibrary(ytDlp);
            assertFalse(hasDownloadButton(message(ytDlp)));
        }

        @Test
        @DisplayName("hidden for a track with no library key (e.g. a stream or unsupported source)")
        void hidden_whenNoLibraryKey() {
            YtDlpService ytDlp = ytDlpService(true, true);
            Bot bot = mock(Bot.class);
            BotConfig config = mock(BotConfig.class);
            SettingsManager settingsManager = mock(SettingsManager.class);
            Settings settings = mock(Settings.class);
            when(bot.getConfig()).thenReturn(config);
            when(bot.getSettingsManager()).thenReturn(settingsManager);
            when(bot.getYtDlp()).thenReturn(ytDlp);

            Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
            when(guild.getName()).thenReturn("Test Guild");
            when(settingsManager.getSettings(guild)).thenReturn(settings);
            when(settings.useMinimalNowPlayingMessage(config)).thenReturn(false);
            when(settings.showNowPlayingButtons(config)).thenReturn(true);
            when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

            AudioTrack stream = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
            AudioTrackInfo streamInfo = new AudioTrackInfo("Live", "Someone", 0L, "live-1", true,
                    "https://example.com/live");
            when(stream.getInfo()).thenReturn(streamInfo);
            when(stream.getSourceManager().getSourceName()).thenReturn("twitch");
            when(stream.getUserData(RequestMetadata.class)).thenReturn(null);

            NowPlayingInfo info = new NowPlayingInfo(stream, guild, false, 50, 0, "");
            assertFalse(hasDownloadButton(MessageFormatter.buildNowPlayingMessage(bot, info)));
        }

        @Test
        @DisplayName("hidden for a LibraryAudioTrack (it's already the library copy)")
        void hidden_forLibraryAudioTrack() {
            Bot bot = mock(Bot.class);
            BotConfig config = mock(BotConfig.class);
            SettingsManager settingsManager = mock(SettingsManager.class);
            Settings settings = mock(Settings.class);
            when(bot.getConfig()).thenReturn(config);
            when(bot.getSettingsManager()).thenReturn(settingsManager);
            when(bot.getYtDlp()).thenReturn(ytDlpService(true, true));

            Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
            when(guild.getName()).thenReturn("Test Guild");
            when(settingsManager.getSettings(guild)).thenReturn(settings);
            when(settings.useMinimalNowPlayingMessage(config)).thenReturn(false);
            when(settings.showNowPlayingButtons(config)).thenReturn(true);
            when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

            AudioTrackInfo trackInfo = new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, KEY, false,
                    "https://example.com/track");
            LibraryAudioTrack track = new LibraryAudioTrack(trackInfo, KEY,
                    () -> CompletableFuture.completedFuture(mock(LibraryEntry.class)), null, null);

            NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
            assertFalse(hasDownloadButton(MessageFormatter.buildNowPlayingMessage(bot, info)));
        }
    }

    @Nested
    @DisplayName("Source label")
    class SourceLabelTests
    {
        private String descriptionFor(AudioTrack track) {
            Bot bot = mock(Bot.class);
            BotConfig config = mock(BotConfig.class);
            SettingsManager settingsManager = mock(SettingsManager.class);
            Settings settings = mock(Settings.class);
            when(bot.getConfig()).thenReturn(config);
            when(bot.getSettingsManager()).thenReturn(settingsManager);

            Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
            when(guild.getName()).thenReturn("Test Guild");
            when(settingsManager.getSettings(guild)).thenReturn(settings);
            when(settings.useMinimalNowPlayingMessage(config)).thenReturn(false);
            when(settings.showNowPlayingButtons(config)).thenReturn(false);
            when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

            NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
            return getSingleEmbed(MessageFormatter.buildNowPlayingMessage(bot, info)).getDescription();
        }

        @Test
        @DisplayName("a library track with a display source shows '<source> (library)'")
        void libraryTrack_withDisplaySource() {
            AudioSourceManager youtube = mock(AudioSourceManager.class);
            when(youtube.getSourceName()).thenReturn("youtube");
            AudioTrackInfo trackInfo = new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, "abcdefghijk", false,
                    "https://example.com/track");
            LibraryAudioTrack track = new LibraryAudioTrack(trackInfo, "abcdefghijk",
                    () -> CompletableFuture.completedFuture(mock(LibraryEntry.class)), null, youtube);

            assertTrue(descriptionFor(track).contains("**Source:** youtube (library)"));
        }

        @Test
        @DisplayName("a library track with no display source falls back to the key's own source")
        void libraryTrack_noDisplaySource_usesKeySource() {
            AudioTrackInfo trackInfo = new AudioTrackInfo("A Song", "An Artist", 100_000L,
                    "https://soundcloud.com/a/b", false, "https://soundcloud.com/a/b");
            LibraryAudioTrack track = new LibraryAudioTrack(trackInfo, "soundcloud:https://soundcloud.com/a/b",
                    () -> CompletableFuture.completedFuture(mock(LibraryEntry.class)), null, null);

            assertTrue(descriptionFor(track).contains("**Source:** soundcloud (library)"));
        }

        @Test
        @DisplayName("an imported file with no known source shows plain 'library'")
        void libraryTrack_fileKey_plainLibrary() {
            AudioTrackInfo trackInfo = new AudioTrackInfo("Some Song", "", 100_000L, "/path/to/file.mp3", false,
                    "/path/to/file.mp3");
            LibraryAudioTrack track = new LibraryAudioTrack(trackInfo, "file:Some Song.mp3",
                    () -> CompletableFuture.completedFuture(mock(LibraryEntry.class)), null, null);

            String description = descriptionFor(track);
            assertTrue(description.contains("**Source:** library"));
            assertFalse(description.contains("**Source:** library ("));
        }

        @Test
        @DisplayName("a normal (non-library) track is unaffected")
        void nonLibraryTrack_unaffected() {
            AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
            AudioTrackInfo trackInfo = new AudioTrackInfo("Axel F", "Crazy Frog", 172_000L, "abcdefghijk", false,
                    "https://example.com/track");
            when(track.getInfo()).thenReturn(trackInfo);
            when(track.getSourceManager().getSourceName()).thenReturn("youtube");
            when(track.getUserData(RequestMetadata.class)).thenReturn(null);

            assertTrue(descriptionFor(track).contains("**Source:** youtube"));
            assertFalse(descriptionFor(track).contains("(library)"));
        }
    }

    /** Builds a now-playing message for a real (unmocked) {@link LibraryAudioTrack} with the given gate. */
    private static MessageCreateData buildNowPlayingMessageForGatedTrack(CompletableFuture<Void> gate, boolean showProgressBar)
    {
        return buildNowPlayingMessageForGatedTrack(gate, showProgressBar, false);
    }

    private static MessageCreateData buildNowPlayingMessageForGatedTrack(
            CompletableFuture<Void> gate, boolean showProgressBar, boolean minimalMessage)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(config.useNPImages()).thenReturn(false);

        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getIconUrl()).thenReturn(null);
        when(guild.getSelfMember().getVoiceState().getChannel().getName()).thenReturn("Music VC");
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(settings.showNowPlayingButtons(config)).thenReturn(true);
        when(settings.getRepeatMode()).thenReturn(RepeatMode.OFF);

        AudioTrackInfo trackInfo = new AudioTrackInfo("Crazy Frog - Axel F", "Crazy Frog",
                172_000L, "abcdefghijk", false, "https://example.com/track");
        LibraryAudioTrack track = new LibraryAudioTrack(trackInfo, "abcdefghijk",
                () -> new CompletableFuture<LibraryEntry>(), gate, null);

        NowPlayingInfo info = new NowPlayingInfo(track, guild, false, 50, 0, "");
        return MessageFormatter.buildNowPlayingMessage(bot, info);
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons)
    {
        return buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo, repeatMode,
                queueSize, paused, positionMs, showButtons, false, "id-1", 0, 50);
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons,
            boolean useNpImages,
            String trackIdentifier)
    {
        return buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo, repeatMode,
                queueSize, paused, positionMs, showButtons, useNpImages, trackIdentifier, 0, 50);
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons,
            boolean useNpImages,
            String trackIdentifier,
            int previousTrackCount)
    {
        return buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo, repeatMode,
                queueSize, paused, positionMs, showButtons, useNpImages, trackIdentifier, previousTrackCount, 50);
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons,
            boolean useNpImages,
            String trackIdentifier,
            int previousTrackCount,
            int volume)
    {
        return buildNowPlayingMessage(title, author, minimalMessage, showProgressBar, footerInfo, repeatMode,
                queueSize, paused, positionMs, showButtons, useNpImages, trackIdentifier, previousTrackCount, volume, false);
    }

    private static MessageCreateData buildNowPlayingMessage(
            String title,
            String author,
            boolean minimalMessage,
            boolean showProgressBar,
            String footerInfo,
            RepeatMode repeatMode,
            int queueSize,
            boolean paused,
            long positionMs,
            boolean showButtons,
            boolean useNpImages,
            String trackIdentifier,
            int previousTrackCount,
            int volume,
            boolean isCurrentTrackFavorited)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(config.useNPImages()).thenReturn(useNpImages);

        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getIconUrl()).thenReturn(null);
        when(guild.getSelfMember().getVoiceState().getChannel().getName()).thenReturn("Music VC");
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(settings.showNowPlayingButtons(config)).thenReturn(showButtons);
        when(settings.getRepeatMode()).thenReturn(repeatMode);

        AudioTrack track = mock(AudioTrack.class, RETURNS_DEEP_STUBS);
        AudioTrackInfo trackInfo = new AudioTrackInfo(title, author, 243000L, trackIdentifier, false, "https://example.com/track");
        when(track.getInfo()).thenReturn(trackInfo);
        when(track.getIdentifier()).thenReturn(trackIdentifier);
        when(track.getPosition()).thenReturn(positionMs);
        when(track.getDuration()).thenReturn(243000L);
        when(track.getSourceManager().getSourceName()).thenReturn("youtube");
        when(track.getUserData(RequestMetadata.class)).thenReturn(null);

        NowPlayingInfo info = new NowPlayingInfo(track, guild, paused, volume, queueSize, previousTrackCount,
                isCurrentTrackFavorited, footerInfo);
        return MessageFormatter.buildNowPlayingMessage(bot, info);
    }

    private static String noMusicDescription(boolean minimalMessage, boolean showProgressBar)
    {
        Bot bot = mock(Bot.class);
        BotConfig config = mock(BotConfig.class);
        SettingsManager settingsManager = mock(SettingsManager.class);
        Settings settings = mock(Settings.class);
        Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);

        when(bot.getConfig()).thenReturn(config);
        when(bot.getSettingsManager()).thenReturn(settingsManager);
        when(config.getSuccess()).thenReturn("ok");
        when(config.showNpProgressBar()).thenReturn(showProgressBar);
        when(settingsManager.getSettings(guild)).thenReturn(settings);
        when(settings.useMinimalNowPlayingMessage(config)).thenReturn(minimalMessage);
        when(guild.getName()).thenReturn("Test Guild");
        when(guild.getSelfMember().getVoiceState().getChannel().getName()).thenReturn("Music VC");

        NowPlayingInfo info = new NowPlayingInfo(null, guild, false, 50, 0, "");
        MessageCreateData message = MessageFormatter.buildNoMusicPlayingMessage(bot, info);
        return getSingleEmbed(message).getDescription();
    }

    private static MessageEmbed getSingleEmbed(MessageCreateData message)
    {
        assertEquals(1, message.getEmbeds().size());
        return message.getEmbeds().get(0);
    }

    private static MessageEmbed.Field getField(MessageEmbed embed, String fieldName)
    {
        return embed.getFields().stream()
                .filter(f -> fieldName.equals(f.getName()))
                .findFirst()
                .orElse(null);
    }

    private static Button getButton(MessageCreateData message, String customId)
    {
        return message.getComponents().stream()
                .flatMap(row -> row.asActionRow().getButtons().stream())
                .filter(button -> customId.equals(button.getCustomId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected button with id: " + customId));
    }
}
