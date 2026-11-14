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
package com.jagrosh.jmusicbot.commands.v1.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.commands.v2.dj.LibraryUnlinkSlashCmd;

/**
 * Prefix command {@code library unlink} (DJs only): the prefix form of {@code /library unlink}.
 */
public class LibraryCmd extends DJCommand
{
    public LibraryCmd(Bot bot)
    {
        super(bot);
        this.name = "library";
        this.arguments = "unlink";
        this.help = "unlinks the current song from the library song it was matched to";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = false;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        String args = event.getArgs() == null ? "" : event.getArgs().trim();
        if ("panel".equalsIgnoreCase(args))
        {
            event.replyError("Use `/library panel` to browse and manage the library.");
            return;
        }
        if (!"unlink".equalsIgnoreCase(args))
        {
            event.replyError("Usage: `" + name + " unlink` unlinks the current song from its library song.");
            return;
        }
        LibraryUnlinkSlashCmd.Result result = LibraryUnlinkSlashCmd.unlinkCurrent(bot, event.getGuild());
        if (result.unlinked())
            event.replySuccess(result.text());
        else
            event.replyWarning(result.text());
    }
}
