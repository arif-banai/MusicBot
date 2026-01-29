/*
 * Copyright 2020 John Grosh <john.a.grosh@gmail.com>.
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
 * @author Whew., Inc.
 */
public class SeekCmd extends MusicCommand
{
    private final MusicService musicService;

    public SeekCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "seek";
        this.help = "seeks the current song";
        this.arguments = "[+ | -] <HH:MM:SS | MM:SS | SS>|<0h0m0s | 0m0s | 0s>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        musicService.seek(event.getGuild(), event.getMember(), event.getArgs(), new SimpleOutputAdapter(event));
    }
}
