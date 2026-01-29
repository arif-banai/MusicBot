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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.SlashEventOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Slash command to remove a track from the queue.
 */
public class RemoveSlashCmd extends MusicSlashCommand
{
    private final MusicService musicService;

    public RemoveSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "remove";
        this.help = "removes a song from the queue";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "position", "queue position to remove, or 'all' to remove all your songs", true)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        String positionArg = event.getOption("position").getAsString();
        SlashEventOutputAdapter output = new SlashEventOutputAdapter(event);

        if (musicService.isQueueEmpty(event.getGuild()))
        {
            output.replyError("There is nothing in the queue!");
            return;
        }

        if (positionArg.equalsIgnoreCase("all"))
        {
            musicService.removeAllTracks(event.getGuild(), event.getMember(), output);
            return;
        }

        int pos;
        try
        {
            pos = Integer.parseInt(positionArg);
        }
        catch (NumberFormatException e)
        {
            output.replyError("Please provide a valid position number or 'all'.");
            return;
        }

        musicService.removeTrack(event.getGuild(), event.getMember(), pos, output);
    }
}
