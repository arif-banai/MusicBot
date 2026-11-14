package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

/**
 * Represents information about the current playing track
 */
public class NowPlayingInfo {
    public final AudioTrack track;
    public final Guild guild;
    public final boolean isPaused;
    public final long position;
    public final long duration;
    public final int volume;
    public final int queueSize;
    public final int previousTrackCount;
    public final boolean isCurrentTrackFavorited;
    public final String footerInfo;
    /** True while the playing track is a yt-dlp download-prompt hold that hasn't been answered yet. */
    public final boolean isAwaitingYtDlpGate;

    public NowPlayingInfo(AudioTrack track, Guild guild, boolean isPaused, int volume, int queueSize, String footerInfo) {
        this(track, guild, isPaused, volume, queueSize, 0, false, footerInfo);
    }

    public NowPlayingInfo(
            AudioTrack track,
            Guild guild,
            boolean isPaused,
            int volume,
            int queueSize,
            int previousTrackCount,
            String footerInfo
    ) {
        this(track, guild, isPaused, volume, queueSize, previousTrackCount, false, footerInfo);
    }

    public NowPlayingInfo(
            AudioTrack track,
            Guild guild,
            boolean isPaused,
            int volume,
            int queueSize,
            int previousTrackCount,
            boolean isCurrentTrackFavorited,
            String footerInfo
    ) {
        this.track = track;
        this.guild = guild;
        this.isPaused = isPaused;
        this.position = track == null
                ? 0
                : track.getPosition();
        this.duration = track == null
                ? 0
                : track.getDuration();
        this.volume = volume;
        this.queueSize = queueSize;
        this.previousTrackCount = Math.max(0, previousTrackCount);
        this.isCurrentTrackFavorited = isCurrentTrackFavorited;
        this.footerInfo = footerInfo;
        this.isAwaitingYtDlpGate = track instanceof LibraryAudioTrack libraryTrack && libraryTrack.isAwaitingGate();
    }
}
