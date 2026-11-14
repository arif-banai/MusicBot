package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryKeys;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MessageFormatter {
    private static final String NP_PREFIX = "np_";
    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 150;

    public static MessageCreateData buildNowPlayingMessage(Bot bot, NowPlayingInfo info) {
        if (info.track == null)
            return buildNoMusicPlayingMessage(bot, info);

        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());
        boolean showButtons = settings.showNowPlayingButtons(bot.getConfig());

        return minimalMessage
                ? buildMinimalNowPlayingMessage(bot, info, showButtons)
                : buildFullNowPlayingMessage(bot, info, showButtons);
    }

    private static MessageCreateData buildFullNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        eb.setAuthor(info.guild.getName(), null, info.guild.getIconUrl());

        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        eb.setDescription(buildPlaybackStatusDescription(bot, info, repeatMode, false));

        String rawAuthor = info.track.getInfo().author;
        String author = rawAuthor == null ? null : FormatUtil.filter(rawAuthor);
        if (author != null && (!author.isEmpty() && !author.equalsIgnoreCase("unknown artist"))) {
            eb.addField("Author", author, false);
        }

        eb.addField("Duration", TimeUtil.formatTime(info.duration), true);
        eb.addField("Queue", String.valueOf(info.queueSize), true);
        eb.addField("Volume", info.volume + "%", true);

        if (repeatMode != RepeatMode.OFF) {
            eb.addField("Repeat", repeatMode.getEmoji() + " " + repeatMode.getUserFriendlyName(), true);
        }

        String requesterDetails = buildRequesterDetails(info);
        if (requesterDetails != null) {
            eb.addField("Requester", requesterDetails, false);
        }

        String artworkUrl = resolveArtworkUrl(bot, info);
        if (artworkUrl != null) {
            eb.setThumbnail(artworkUrl);
        }
        if (info.footerInfo != null && !info.footerInfo.isEmpty()) {
            eb.setFooter(info.footerInfo);
        }

        mb.setEmbeds(eb.build());
        if (showButtons) {
            applyNowPlayingButtons(mb, bot, info, repeatMode);
        }
        return mb.build();
    }

    private static MessageCreateData buildMinimalNowPlayingMessage(Bot bot, NowPlayingInfo info, boolean showButtons) {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info)));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        String title = FormatUtil.filter(FormatUtil.getTrackTitle(info.track));
        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception ignored) {
            eb.setTitle(title);
        }
        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        eb.setDescription(buildPlaybackStatusDescription(bot, info, repeatMode, true));

        if (showButtons) {
            applyNowPlayingButtons(mb, bot, info, repeatMode);
        }

        mb.setEmbeds(eb.build());
        return mb.build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        Settings settings = bot.getSettingsManager().getSettings(info.guild);
        boolean minimalMessage = settings.useMinimalNowPlayingMessage(bot.getConfig());

        if (minimalMessage) {
            return buildNoMusicPlayingMessageMinimal(bot, info);
        }

        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing...**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static MessageCreateData buildNoMusicPlayingMessageMinimal(Bot bot, NowPlayingInfo info) {
        String descriptionText = bot.getConfig().showNpProgressBar()
                ? AudioHandler.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(info.volume)
                : AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume);

        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing in** " + getNowPlayingLocationName(info)))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(descriptionText)
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build())
                .build();
    }

    private static final String YTDLP_WAITING_LINE = AudioHandler.PAUSE_EMOJI
            + " Waiting: YouTube blocked this song. Choose in the yt-dlp download prompt.";

    private static String buildPlaybackStatusLine(Bot bot, NowPlayingInfo info) {
        if (info.isAwaitingYtDlpGate) {
            // A yt-dlp prompt is open for this track: it isn't playing, so no progress bar or
            // elapsed time, which would otherwise make it look like normal playback.
            return YTDLP_WAITING_LINE;
        }
        String statusEmoji = info.isPaused ? AudioHandler.PAUSE_EMOJI : AudioHandler.PLAY_EMOJI;
        String timeDisplay = "`[" + TimeUtil.formatTime(info.position) + "/" + TimeUtil.formatTime(info.duration) + "]`";
        if (bot.getConfig().showNpProgressBar()) {
            double progress = info.duration > 0 ? (double) info.position / info.duration : 0;
            return statusEmoji + " " + FormatUtil.progressBar(progress) + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
        }
        return statusEmoji + " " + timeDisplay + " " + FormatUtil.volumeIcon(info.volume);
    }

    private static String buildMetadataSummaryLine(NowPlayingInfo info, RepeatMode repeatMode, boolean minimalLayout) {
        StringBuilder summary = new StringBuilder("**Source:** ").append(sourceNameForTrack(info));
        if (!minimalLayout) {
            return summary.toString();
        }

        String queuedLabel = formatQueuedLabel(info.queueSize);
        if (queuedLabel != null) {
            summary.append(" • ").append(queuedLabel);
        }
        summary.append("\n**Volume:** ").append(info.volume).append("%");
        if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.SINGLE) {
            summary.append(" • **Repeat:** ").append(repeatMode.getUserFriendlyName());
        }
        return summary.toString();
    }

    private static String buildPlaybackStatusDescription(Bot bot, NowPlayingInfo info, RepeatMode repeatMode, boolean minimalLayout) {
        return buildPlaybackStatusLine(bot, info) + "\n" + buildMetadataSummaryLine(info, repeatMode, minimalLayout);
    }

    private static String formatQueuedLabel(int queueSize) {
        if (queueSize <= 0) {
            return null;
        }
        return queueSize == 1 ? "1 song queued" : queueSize + " songs queued";
    }

    private static String getNowPlayingLocationName(NowPlayingInfo info) {
        if (info.guild.getSelfMember() != null
                && info.guild.getSelfMember().getVoiceState() != null
                && info.guild.getSelfMember().getVoiceState().getChannel() != null) {
            return info.guild.getSelfMember().getVoiceState().getChannel().getName();
        }
        return info.guild.getName();
    }

    private static String sourceNameForTrack(NowPlayingInfo info)
    {
        return sourceLabel(info.track);
    }

    /**
     * The source shown for a track: the lavaplayer source name, or for any {@link LibraryAudioTrack}
     * {@code "<source> (library)"} &mdash; using its display source if it has one that's meaningful
     * (not the local file manager used to actually play it), otherwise falling back to the source
     * implied by its own library key &mdash; or plain {@code "library"} when neither is known
     * (e.g. a file imported with no recognised source).
     */
    private static String sourceLabel(AudioTrack track)
    {
        if (track instanceof LibraryAudioTrack libraryTrack)
        {
            AudioSourceManager display = libraryTrack.getSourceManager();
            String sourceName = display != null ? display.getSourceName() : null;
            if (sourceName == null || sourceName.isBlank() || "local".equals(sourceName))
            {
                sourceName = LibraryKeys.sourceOf(libraryTrack.getKey()).orElse(null);
            }
            return sourceName != null && !sourceName.isBlank() ? sourceName + " (library)" : "library";
        }
        return track.getSourceManager() != null
                ? track.getSourceManager().getSourceName()
                : "Unknown";
    }

    /**
     * Whether the now-playing "Download" button should be shown: {@code ytdlp.downloadButton} is
     * on, yt-dlp is enabled, the track has a library key, it isn't already in the library (directly
     * or through an alias), and it isn't already a {@link LibraryAudioTrack}.
     */
    private static boolean showYtDlpDownloadButton(Bot bot, NowPlayingInfo info)
    {
        if (info.track == null || info.track instanceof LibraryAudioTrack)
        {
            return false;
        }
        YtDlpService ytDlp = bot.getYtDlp();
        if (ytDlp == null || !ytDlp.isEnabled() || !ytDlp.settings().downloadButton())
        {
            return false;
        }
        Optional<String> key = LibraryKeys.of(info.track);
        return key.isPresent() && ytDlp.library().resolve(key.get()).isEmpty();
    }

    private static String resolveArtworkUrl(Bot bot, NowPlayingInfo info)
    {
        if (info.track instanceof LocalAudioTrack || !bot.getConfig().useNPImages())
            return null;
        var artworkUrl = info.track.getInfo().artworkUrl;
        if (artworkUrl == null || artworkUrl.isEmpty())
            artworkUrl = "https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg";
        return artworkUrl;
    }

    private static String buildRequesterDetails(NowPlayingInfo info)
    {
        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L)
        {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            return (u == null) ? FormatUtil.formatUsername(rm.user) : u.getAsMention();
        }
        return null;
    }

    private static void applyNowPlayingButtons(MessageCreateBuilder mb, Bot bot, NowPlayingInfo info, RepeatMode repeatMode) {
        boolean canGoPrevious = info.position > 5000 || info.previousTrackCount > 0;
        boolean canShuffle = info.queueSize > 1;
        boolean canVolDown = info.volume > MIN_VOLUME;
        boolean canVolUp = info.volume < MAX_VOLUME;

        Button repeatButton = switch (repeatMode) {
            case ALL -> Button.primary(nowPlayingButtonId("repeat"), "Repeat All").withEmoji(Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
            case SINGLE -> Button.primary(nowPlayingButtonId("repeat"), "Repeat One").withEmoji(Emoji.fromUnicode("\uD83D\uDD02")); // 🔂
            default -> Button.secondary(nowPlayingButtonId("repeat"), "Repeat").withEmoji(Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
        };

        Button previousButton = Button.secondary(nowPlayingButtonId("previous"), "Prev").withEmoji(Emoji.fromUnicode("\u23EE")) // ⏮
                .withDisabled(!canGoPrevious);
        Button pauseButton = (info.isPaused
                ? Button.primary(nowPlayingButtonId("pause"), "Resume").withEmoji(Emoji.fromUnicode("\u25B6")) // ▶
                : Button.primary(nowPlayingButtonId("pause"), "Pause").withEmoji(Emoji.fromUnicode("\u23F8"))) // ⏸
                .withDisabled(info.isAwaitingYtDlpGate);
        Button shuffleButton = Button.secondary(nowPlayingButtonId("shuffle"), "Shuffle").withEmoji(Emoji.fromUnicode("\uD83D\uDD00")) // 🔀
                .withDisabled(!canShuffle);
        Button volumeDownButton = Button.secondary(nowPlayingButtonId("voldown"), "Vol -").withEmoji(Emoji.fromUnicode("\uD83D\uDD09")) // 🔉
                .withDisabled(!canVolDown);
        Button volumeUpButton = Button.secondary(nowPlayingButtonId("volup"), "Vol +").withEmoji(Emoji.fromUnicode("\uD83D\uDD0A")) // 🔊
                .withDisabled(!canVolUp);
        Button favoriteButton = (info.isCurrentTrackFavorited
                ? Button.success(nowPlayingButtonId("favorite"), "Favorite")
                : Button.secondary(nowPlayingButtonId("favorite"), "Favorite"))
                .withEmoji(Emoji.fromUnicode("\u2B50")); // ⭐

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                previousButton,
                pauseButton,
                Button.secondary(nowPlayingButtonId("skip"), "Skip").withEmoji(Emoji.fromUnicode("\u23ED")), // ⏭
                Button.danger(nowPlayingButtonId("stop"), "Stop").withEmoji(Emoji.fromUnicode("\u23F9")) // ⏹
        ));
        rows.add(ActionRow.of(
                shuffleButton,
                repeatButton,
                favoriteButton,
                volumeDownButton,
                volumeUpButton
        ));
        if (showYtDlpDownloadButton(bot, info)) {
            rows.add(ActionRow.of(
                    Button.secondary(ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE, "Download")
                            .withEmoji(Emoji.fromUnicode("\u2B07")) // ⬇
            ));
        }
        mb.setComponents(rows);
    }

    private static String nowPlayingButtonId(String action) {
        return NP_PREFIX + action;
    }
}
