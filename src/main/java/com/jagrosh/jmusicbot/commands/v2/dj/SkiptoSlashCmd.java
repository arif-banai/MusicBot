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

import java.util.Collections;

/**
 * DJ slash command to skip to a specific position in the queue.
 */
public class SkiptoSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public SkiptoSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "skipto";
        this.help = "skips to the specified song";
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "position", "queue position to skip to", true)
                        .setMinValue(1)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        int position = (int) event.getOption("position").getAsLong();
        int queueSize = musicService.getQueueSize(event.getGuild());

        if (position < 1 || position > queueSize)
        {
            event.reply(event.getClient().getError() + " Position must be a valid integer between 1 and " + queueSize + "!")
                    .setEphemeral(true).queue();
            return;
        }

        String trackTitle = musicService.skipToPosition(event.getGuild(), position);
        if (trackTitle != null)
        {
            event.reply(event.getClient().getSuccess() + " Skipped to **" + trackTitle + "**").queue();
        }
    }
}
