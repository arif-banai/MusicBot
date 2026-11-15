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

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;

/**
 * {@code /library}: yt-dlp library commands. Only its subcommands run; registered only when
 * yt-dlp is configured.
 */
public class LibrarySlashCmd extends SlashCommand
{
    public LibrarySlashCmd(Bot bot)
    {
        this.name = "library";
        this.help = "yt-dlp library commands";
        this.guildOnly = true;
        this.category = new Category("DJ");
        this.children = new SlashCommand[]{ new LibraryUnlinkSlashCmd(bot), new LibraryPanelSlashCmd(bot) };
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        // Discord only invokes the subcommands.
    }
}
