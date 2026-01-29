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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.commands.v1.TextOutputAdapters.SimpleOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class RemoveCmd extends MusicCommand
{
    private final MusicService musicService;

    public RemoveCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "remove";
        this.help = "removes a song from the queue";
        this.arguments = "<position|ALL>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        SimpleOutputAdapter output = new SimpleOutputAdapter(event);

        if (musicService.isQueueEmpty(event.getGuild()))
        {
            output.replyError("There is nothing in the queue!");
            return;
        }

        if (event.getArgs().equalsIgnoreCase("all"))
        {
            musicService.removeAllTracks(event.getGuild(), event.getMember(), output);
            return;
        }

        int pos;
        try
        {
            pos = Integer.parseInt(event.getArgs());
        }
        catch (NumberFormatException e)
        {
            pos = 0;
        }

        musicService.removeTrack(event.getGuild(), event.getMember(), pos, output);
    }
}
