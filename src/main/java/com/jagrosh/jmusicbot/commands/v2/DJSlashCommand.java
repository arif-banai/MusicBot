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
package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;

/**
 * Base class for DJ-level slash commands.
 * Extends MusicSlashCommand and adds DJ permission checking.
 */
public abstract class DJSlashCommand extends MusicSlashCommand
{
    public DJSlashCommand(Bot bot)
    {
        super(bot);
        this.category = new Category("DJ");
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        if (!DJCommand.checkDJPermission(bot, event.getGuild(), event.getMember()))
        {
            event.reply(event.getClient().getError() + " You need to be a DJ to use this command!")
                    .setEphemeral(true).queue();
            return;
        }
        doDJCommand(event);
    }

    /**
     * Override this method to implement the DJ command logic.
     */
    public abstract void doDJCommand(SlashCommandEvent event);
}
