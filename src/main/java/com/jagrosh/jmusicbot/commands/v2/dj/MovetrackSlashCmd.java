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

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;

/**
 * DJ slash command to move a track in the queue.
 */
public class MovetrackSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public MovetrackSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "movetrack";
        this.help = "move a track in the current queue to a different position";
        this.options = Arrays.asList(
                new OptionData(OptionType.INTEGER, "from", "current position of the track", true).setMinValue(1),
                new OptionData(OptionType.INTEGER, "to", "new position for the track", true).setMinValue(1)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        int from = (int) event.getOption("from").getAsLong();
        int to = (int) event.getOption("to").getAsLong();

        if (from == to)
        {
            event.reply(event.getClient().getError() + " Can't move a track to the same position.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!musicService.isValidQueuePosition(event.getGuild(), from))
        {
            event.reply(event.getClient().getError() + " `" + from + "` is not a valid position in the queue!")
                    .setEphemeral(true).queue();
            return;
        }
        if (!musicService.isValidQueuePosition(event.getGuild(), to))
        {
            event.reply(event.getClient().getError() + " `" + to + "` is not a valid position in the queue!")
                    .setEphemeral(true).queue();
            return;
        }

        String trackTitle = musicService.moveTrackPosition(event.getGuild(), from, to);
        if (trackTitle != null)
        {
            event.reply(event.getClient().getSuccess() + " Moved **" + trackTitle + "** from position `" + from + "` to `" + to + "`.")
                    .queue();
        }
    }
}
