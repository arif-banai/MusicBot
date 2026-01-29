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
package com.jagrosh.jmusicbot.commands.v1.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.service.MusicService;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SkiptoCmd extends DJCommand
{
    private final MusicService musicService;

    public SkiptoCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "skipto";
        this.help = "skips to the specified song";
        this.arguments = "<position>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int index;
        try
        {
            index = Integer.parseInt(event.getArgs());
        }
        catch (NumberFormatException e)
        {
            event.replyError("`" + event.getArgs() + "` is not a valid integer!");
            return;
        }

        int queueSize = musicService.getQueueSize(event.getGuild());
        if (index < 1 || index > queueSize)
        {
            event.replyError("Position must be a valid integer between 1 and " + queueSize + "!");
            return;
        }

        String trackTitle = musicService.skipToPosition(event.getGuild(), index);
        if (trackTitle != null)
        {
            event.replySuccess("Skipped to **" + trackTitle + "**");
        }
    }
}
