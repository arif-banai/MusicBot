/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.audio.RequestMetadata.UserInfo;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.awt.Color;
import java.util.List;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class FormatUtil {

    public static String formatUsername(String username, String discrim)
    {
        if(discrim == null || discrim.equals("0000"))
        {
            return username;
        }
        else
        {
            return username + "#" + discrim;
        }
    }

    public static String formatUsername(UserInfo userinfo)
    {
        return formatUsername(userinfo.username, userinfo.discrim);
    }

    public static String formatUsername(User user)
    {
        return formatUsername(user.getName(), user.getDiscriminator());
    }
    
    public static String volumeIcon(int volume)
    {
        if(volume == 0)
            return "\uD83D\uDD07"; // 🔇
        if(volume < 30)
            return "\uD83D\uDD08"; // 🔈
        if(volume < 70)
            return "\uD83D\uDD09"; // 🔉
        return "\uD83D\uDD0A";     // 🔊
    }

    /**
     * Generates a 12-segment progress bar for track playback.
     * 
     * @param percent The progress as a value between 0.0 and 1.0. 
     *                Use negative values (e.g., -1) for "no music" state (all segments empty).
     * @return A string representing the progress bar with 🔘 at the current position and ▬ for other segments.
     */
    public static String progressBar(double percent)
    {
        StringBuilder str = new StringBuilder();
        for(int i = 0; i < 12; i++)
        {
            if(i == (int)(percent * 12))
                str.append("\uD83D\uDD18"); // 🔘
            else
                str.append("▬");
        }
        return str.toString();
    }
    
    public static String listOfTChannels(List<TextChannel> list, String query)
    {
        String out = " Multiple text channels found matching \""+query+"\":";
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getName()+" (<#"+list.get(i).getId()+">)";
        if(list.size()>6)
            out+="\n**And "+(list.size()-6)+" more...**";
        return out;
    }
    
    public static String listOfVChannels(List<VoiceChannel> list, String query)
    {
        String out = " Multiple voice channels found matching \""+query+"\":";
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getAsMention()+" (ID:"+list.get(i).getId()+")";
        if(list.size()>6)
            out+="\n**And "+(list.size()-6)+" more...**";
        return out;
    }
    
    public static String listOfRoles(List<Role> list, String query)
    {
        String out = " Multiple roles found matching \""+query+"\":";
        for(int i=0; i<6 && i<list.size(); i++)
            out+="\n - "+list.get(i).getName()+" (ID:"+list.get(i).getId()+")";
        if(list.size()>6)
            out+="\n**And "+(list.size()-6)+" more...**";
        return out;
    }
    
    public static String filter(String input)
    {
        return input.replace("\u202E","")
                .replace("@everyone", "@\u0435veryone") // cyrillic letter e
                .replace("@here", "@h\u0435re") // cyrillic letter e
                .trim();
    }

    public static String getTrackTitle(AudioTrack track) {
        String title = track.getInfo().title;
        if (track instanceof LocalAudioTrack && (title == null || title.equals("Unknown title"))) {
            String identifier = track.getIdentifier();
            int lastSeparator = Math.max(identifier.lastIndexOf('/'), identifier.lastIndexOf('\\'));
            return (lastSeparator != -1) ? identifier.substring(lastSeparator + 1) : identifier;
        }

        // Truncate if the title is too long for Discord displays
        if (title != null && title.length() > 100) {
            title = title.substring(0, 97) + "...";
        }

        return title;
    }

    /**
     * Formats a single track line for use in embeds (e.g. playlist details preview), in the same style as
     * Queue/History: duration plus linked title, without the " - @user" part.
     *
     * @param track the loaded track
     * @return string like {@code `[MM:SS]` [**Title**](url)} or {@code `[MM:SS]` **Title**} for non-http URIs
     */
    public static String formatTrackLineForEmbed(AudioTrack track)
    {
        if (track == null)
        {
            return "`[?:??]` **Could not load**";
        }
        String entry = "`[" + TimeUtil.formatTime(track.getDuration()) + "]` ";
        var trackInfo = track.getInfo();
        String title = getTrackTitle(track);
        String safeTitle = filter(title == null ? "" : title);
        return entry + (trackInfo.uri != null && trackInfo.uri.startsWith("http")
                ? "[**" + safeTitle + "**](" + trackInfo.uri + ")"
                : "**" + safeTitle + "**");
    }
    
    /**
     * Builds a standardized Discord embed message displaying a numbered list of candidate audio tracks.
     * <p>
     * Constructs an {@link EmbedBuilder} formatted with track indices, artist names, hyperlinked 
     * sanitized titles, and formatted durations. Uses the first track's artwork URL as a thumbnail 
     * when available.
     * </p>
     *
     * @param tracks the list of candidate {@link AudioTrack} objects to present for selection
     * @return a pre-configured {@link EmbedBuilder} ready to be rendered and sent in a Discord channel
     */
    public static EmbedBuilder createMultiTrackEmbed(List<AudioTrack> tracks)
    {
        EmbedBuilder builder = new EmbedBuilder().setColor(Color.decode("#070707")).setTitle("🎵 Track Selection");

        if (!tracks.isEmpty() && tracks.get(0).getInfo().artworkUrl != null)
        {
            builder.setThumbnail(tracks.get(0).getInfo().artworkUrl);
        }

        for (int i = 0; i < tracks.size(); i++)
        {
            AudioTrack track = tracks.get(i);

            String safeTitle = getFormattedTitle(track.getInfo().title);

            String fieldName = "`" + (i + 1) + ".` " + track.getInfo().author;
            String fieldValue = "[" + safeTitle + "](" + track.getInfo().uri + ") `("
                    + TimeUtil.formatTime(track.getDuration()) + ")`";

            builder.addField(fieldName, fieldValue, false);
        }

        return builder;
    }
    
    /**
     * Sanitizes and truncates an audio track title for clean display in Discord embeds.
     * <p>
     * Removes square brackets, trims surrounding whitespace, and caps the title length to a maximum
     * of 50 characters to prevent layout overflow in embed fields.
     * </p>
     *
     * @param title the raw track title to sanitize and format
     * @return the cleaned, truncated title ending with {@code "..."} if truncated, or {@code "Unknown Title"} if null
     */
    public static String getFormattedTitle(String title)
    {
        if (title == null)
        {
            return "Unknown Title";
        }

        String trimmedTitle = title.trim().replaceAll("[\\[\\]]", "");
        int maxLength = 50; // title + " " + (1:00:00)
        if (trimmedTitle.length() > maxLength)
        {
            return trimmedTitle.substring(0, maxLength) + "...";
        }

        return trimmedTitle;
    }
}
