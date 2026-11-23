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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Searches Bilibili (哔哩哔哩) for a query and offers the results for selection.
 *
 * <p>Bilibili titles are long and packed with brackets and emoji, so results are trimmed
 * and show the uploader on a second line. Search results cannot say whether a video is
 * member-only (充电专属), so a pick is checked when it is made: the bot loads the picked
 * video, which it would do before playing anyway, and says so if it cannot be played.
 *
 * @author Arif Banai (arif-banai)
 */
public class BiliSearchCmd extends SearchCmd
{
    /** Longest title shown in the results menu, in characters. */
    static final int MAX_TITLE_LENGTH = 55;

    public BiliSearchCmd(Bot bot)
    {
        super(bot);
        this.searchPrefix = "bilisearch:";
        this.name = "bilisearch";
        this.help = "searches Bilibili for a provided query";
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected String formatChoice(AudioTrack track)
    {
        return formatChoice(track.getInfo().title, track.getInfo().author, track.getDuration(), track.getInfo().uri);
    }

    /**
     * Renders one result: duration and a trimmed, linked title, then the uploader below.
     */
    public static String formatChoice(String title, String author, long durationMs, String uri)
    {
        String line = "`[" + TimeUtil.formatTime(durationMs) + "]` [**"
                + escape(trim(FormatUtil.filter(title == null ? "" : title), MAX_TITLE_LENGTH)) + "**](" + uri + ")";
        if(author == null || author.isBlank())
            return line;
        return line + "\n  by " + escape(FormatUtil.filter(author));
    }

    @Override
    protected void onSelection(CommandEvent event, AudioTrack track)
    {
        String uri = track.getInfo().uri;
        bot.getPlayerManager().loadItemOrdered(event.getGuild(), uri,
                bot.getAudioLoadWrapper().wrap(uri, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack loaded)
            {
                queueSelection(event, loaded);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                if(playlist.getTracks().isEmpty())
                    noMatches();
                else
                    queueSelection(event, playlist.getTracks().get(0));
            }

            @Override
            public void noMatches()
            {
                event.replyWarning("That Bilibili video is no longer available.");
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                if(exception.severity == FriendlyException.Severity.COMMON)
                    event.replyWarning(exception.getMessage());
                else
                    event.replyError("Couldn't load that Bilibili video.");
            }
        }));
    }

    /**
     * Shortens to {@code max} characters, counting emoji as one, and marks the cut.
     */
    static String trim(String text, int max)
    {
        if(text.codePointCount(0, text.length()) <= max)
            return text;
        return text.substring(0, text.offsetByCodePoints(0, max - 1)).trim() + "…";
    }

    /**
     * Escapes the characters that would break the link or restyle the text. Titles like
     * {@code [Lofi|雨天]} otherwise end the link text early.
     */
    static String escape(String text)
    {
        return text.replaceAll("([\\\\\\[\\]*_`~|])", "\\\\$1");
    }
}
