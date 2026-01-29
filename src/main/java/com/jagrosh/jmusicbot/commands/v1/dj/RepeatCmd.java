/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.v1.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.RepeatMode;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class RepeatCmd extends DJCommand
{
    private final MusicService musicService;

    public RepeatCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "repeat";
        this.help = "re-adds music to the queue when finished";
        this.arguments = "[off|all|single]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
    }

    // override musiccommand's execute because we don't actually care where this is used
    @Override
    protected void execute(CommandEvent event)
    {
        String args = event.getArgs();
        RepeatMode value;
        RepeatMode currentMode = musicService.getRepeatMode(event.getGuild());

        if (args.isEmpty())
        {
            value = (currentMode == RepeatMode.OFF) ? RepeatMode.ALL : RepeatMode.OFF;
        }
        else if (args.equalsIgnoreCase("false") || args.equalsIgnoreCase("off"))
        {
            value = RepeatMode.OFF;
        }
        else if (args.equalsIgnoreCase("true") || args.equalsIgnoreCase("on") || args.equalsIgnoreCase("all"))
        {
            value = RepeatMode.ALL;
        }
        else if (args.equalsIgnoreCase("one") || args.equalsIgnoreCase("single"))
        {
            value = RepeatMode.SINGLE;
        }
        else
        {
            event.replyError("Valid options are `off`, `all` or `single` (or leave empty to toggle between `off` and `all`)");
            return;
        }

        musicService.setRepeatMode(event.getGuild(), value);
        event.replySuccess("Repeat mode is now `" + value.getUserFriendlyName() + "`");
    }

    @Override
    public void doCommand(CommandEvent event) { /* Intentionally Empty */ }
}
