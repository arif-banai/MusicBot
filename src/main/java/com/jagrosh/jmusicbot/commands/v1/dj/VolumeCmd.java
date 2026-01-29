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
import com.jagrosh.jmusicbot.utils.FormatUtil;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class VolumeCmd extends DJCommand
{
    private final MusicService musicService;

    public VolumeCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "volume";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.help = "sets or shows volume";
        this.arguments = "[0-150]";
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int currentVolume = musicService.getVolume(event.getGuild());

        if (event.getArgs().isEmpty())
        {
            event.reply(FormatUtil.volumeIcon(currentVolume) + " Current volume is `" + currentVolume + "`");
        }
        else
        {
            int newVolume;
            try
            {
                newVolume = Integer.parseInt(event.getArgs());
            }
            catch (NumberFormatException e)
            {
                newVolume = -1;
            }

            MusicService.VolumeResult result = musicService.setVolume(event.getGuild(), newVolume);
            if (result == null)
            {
                event.replyError("Volume must be a valid integer between 0 and 150!");
            }
            else
            {
                event.reply(FormatUtil.volumeIcon(result.newVolume) + " Volume changed from `" + result.oldVolume + "` to `" + result.newVolume + "`");
            }
        }
    }
}
