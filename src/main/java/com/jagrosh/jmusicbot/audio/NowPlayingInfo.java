package com.jagrosh.jmusicbot.audio;

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
    public final String footerInfo;

    public NowPlayingInfo(AudioTrack track, Guild guild, boolean isPaused, int volume, int queueSize, String footerInfo) {
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
        this.footerInfo = footerInfo;
    }
}
