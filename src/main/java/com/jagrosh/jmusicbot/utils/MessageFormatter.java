package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.NowPlayingInfo;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MessageFormatter {

    public static MessageCreateData buildNowPlayingMessage(Bot bot, NowPlayingInfo info) {
        if (info.track == null)
            return buildNoMusicPlayingMessage(bot, info);

        MessageCreateBuilder mb = new MessageCreateBuilder();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(info.guild.getSelfMember().getColors().getPrimary());
        eb.setAuthor(info.guild.getName(), null, info.guild.getIconUrl());

        // Handle local file names using the util method
        String title = FormatUtil.getTrackTitle(info.track);

        try {
            eb.setTitle(title, info.track.getInfo().uri);
        } catch (Exception e) {
            eb.setTitle(title);
        }

        if (info.track.getInfo().author != null && (!info.track.getInfo().author.isEmpty() && !info.track.getInfo().author.equalsIgnoreCase( "unknown artist") )) {
            eb.addField("Author", info.track.getInfo().author, false);
        }

        StringBuilder description = new StringBuilder();
        description.append("**Playing from:** ").append(info.track.getSourceManager().getSourceName());
        eb.setDescription(description.toString());

        eb.addField("Duration", TimeUtil.formatTime(info.duration), true);
        eb.addField("Queue", String.valueOf(info.queueSize), true);
        eb.addField("Volume", info.volume + "%", true);

        RepeatMode repeatMode = bot.getSettingsManager().getSettings(info.guild).getRepeatMode();
        if (repeatMode != RepeatMode.OFF) {
            eb.addField("Repeat", repeatMode.getEmoji() + " " + repeatMode.getUserFriendlyName(), true);
        }

        RequestMetadata rm = info.track.getUserData(RequestMetadata.class);
        if (rm != null && rm.getOwner() != 0L) {
            User u = info.guild.getJDA().getUserById(rm.user.id);
            String requester = (u == null) ? FormatUtil.formatUsername(rm.user) : u.getAsMention();
            eb.addField("Requester", requester, false);
        }

        if (!(info.track instanceof LocalAudioTrack)  && bot.getConfig().useNPImages()) {
            var thumbnailUrl = info.track.getInfo().artworkUrl;
            if (thumbnailUrl == null || thumbnailUrl.isEmpty())
                thumbnailUrl = "https://img.youtube.com/vi/" + info.track.getIdentifier() + "/mqdefault.jpg";
            eb.setThumbnail(thumbnailUrl);
        }

        if (info.footerInfo != null && !info.footerInfo.isEmpty())
            eb.setFooter(info.footerInfo);

        mb.setEmbeds(eb.build());

        // Add interactive buttons using ActionRow.of for better compatibility
        Button repeatButton = switch (repeatMode) {
            case ALL -> Button.primary("repeat", Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
            case SINGLE -> Button.primary("repeat", Emoji.fromUnicode("\uD83D\uDD02")); // 🔂
            default -> Button.secondary("repeat", Emoji.fromUnicode("\uD83D\uDD01")); // 🔁
        };

        mb.setComponents(
                ActionRow.of(
                        Button.secondary("previous", Emoji.fromUnicode("\u23EE")), // Previous ⏮
                        info.isPaused
                                ? Button.primary("pause", Emoji.fromUnicode("\u25B6"))    // Pause ⏸
                                : Button.secondary("pause", Emoji.fromUnicode("\u23F8")), // or Resume ▶
                        Button.secondary("skip", Emoji.fromUnicode("\u23ED")), // Skip ⏭
                        Button.secondary("stop", Emoji.fromUnicode("\u23F9")) // Stop ⏹
                ),
                ActionRow.of(
                        Button.secondary("shuffle", Emoji.fromUnicode("\uD83D\uDD00")), // Shuffle 🔀
                        repeatButton, // Repeat cycle
                        Button.secondary("voldown", Emoji.fromUnicode("\uD83D\uDD09")), // Vol Down 🔉
                        Button.secondary("volup", Emoji.fromUnicode("\uD83D\uDD0A")) // Vol Up 🔊
                )
        );

        return mb.build();
    }

    public static MessageCreateData buildNoMusicPlayingMessage(Bot bot, NowPlayingInfo info) {
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(bot.getConfig().getSuccess() + " **Now Playing...**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music playing")
                        .setDescription(AudioHandler.STOP_EMOJI + " " + FormatUtil.volumeIcon(info.volume))
                        .setColor(info.guild.getSelfMember().getColors().getPrimary())
                        .build()).build();
    }
}
