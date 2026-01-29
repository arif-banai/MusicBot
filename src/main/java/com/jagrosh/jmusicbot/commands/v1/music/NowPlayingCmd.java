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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.Permission;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class NowPlayingCmd extends MusicCommand
{
    private final MusicService musicService;

    public NowPlayingCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "nowplaying";
        this.help = "shows the song that is currently playing";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        MusicService.NowPlayingInfo info = musicService.getNowPlayingInfo(event.getGuild(), event.getJDA());

        if (info == null)
        {
            event.replyWarning("There is no music playing in this server.");
            return;
        }

        if (info.nowPlayingMessage == null || !info.isPlaying)
        {
            event.reply(info.noMusicMessage);
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
        }
        else
        {
            event.reply(info.nowPlayingMessage, msg -> bot.getNowplayingHandler().setLastNPMessage(msg));
        }
    }
}
