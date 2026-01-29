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
import com.jagrosh.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * DJ slash command to set the repeat mode.
 */
public class RepeatSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public RepeatSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "repeat";
        this.help = "re-adds music to the queue when finished";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "mode", "repeat mode", false)
                        .addChoice("off", "off")
                        .addChoice("all", "all")
                        .addChoice("single", "single")
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        RepeatMode currentMode = musicService.getRepeatMode(event.getGuild());
        RepeatMode newMode;

        if (event.getOption("mode") == null)
        {
            // Toggle between off and all
            newMode = (currentMode == RepeatMode.OFF) ? RepeatMode.ALL : RepeatMode.OFF;
        }
        else
        {
            String modeArg = event.getOption("mode").getAsString();
            switch (modeArg.toLowerCase())
            {
                case "off":
                    newMode = RepeatMode.OFF;
                    break;
                case "all":
                    newMode = RepeatMode.ALL;
                    break;
                case "single":
                    newMode = RepeatMode.SINGLE;
                    break;
                default:
                    event.reply(event.getClient().getError() + " Valid options are `off`, `all` or `single`")
                            .setEphemeral(true).queue();
                    return;
            }
        }

        musicService.setRepeatMode(event.getGuild(), newMode);
        event.reply(event.getClient().getSuccess() + " Repeat mode is now `" + newMode.getUserFriendlyName() + "`").queue();
    }
}
