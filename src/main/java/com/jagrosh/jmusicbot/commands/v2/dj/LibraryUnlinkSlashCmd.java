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
package com.jagrosh.jmusicbot.commands.v2.dj;

import java.io.IOException;
import java.util.Optional;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

/**
 * {@code /library unlink} (DJs only): removes the link between the song now playing and the
 * library song it was matched to, so the next request streams it again.
 */
public class LibraryUnlinkSlashCmd extends DJSlashCommand
{
    /** What an unlink attempt replies with; {@code unlinked} is false when there was nothing to unlink. */
    public record Result(boolean unlinked, String text)
    {
    }

    public LibraryUnlinkSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "unlink";
        this.help = "unlinks the current song from the library song it was matched to";
        this.bePlaying = false;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        Result result = unlinkCurrent(bot, event.getGuild());
        String emoji = result.unlinked() ? event.getClient().getSuccess() : event.getClient().getWarning();
        event.reply(emoji + " " + result.text()).setEphemeral(true).queue();
    }

    /** Unlinks the song now playing in the guild, if it is linked. Shared with the prefix command. */
    public static Result unlinkCurrent(Bot bot, Guild guild)
    {
        YtDlpService ytDlp = bot.getYtDlp();
        AudioTrack playing = guild != null && guild.getAudioManager().getSendingHandler() instanceof AudioHandler handler
                ? handler.getPlayer().getPlayingTrack() : null;
        if (ytDlp == null || playing == null)
            return new Result(false, "The current song isn't linked to a library song");
        Optional<YtDlpService.Unlinked> unlinked;
        try
        {
            unlinked = ytDlp.unlink(playing);
        }
        catch (IOException ex)
        {
            return new Result(false, "Couldn't save the library: " + ex.getMessage());
        }
        return unlinked
                .map(u -> new Result(true, "Unlinked **" + escape(u.requested()) + "** from library song **"
                        + escape(u.librarySong()) + "**"))
                .orElseGet(() -> new Result(false, "The current song isn't linked to a library song"));
    }

    private static String escape(String text)
    {
        return MarkdownSanitizer.escape(FormatUtil.filter(text));
    }
}
