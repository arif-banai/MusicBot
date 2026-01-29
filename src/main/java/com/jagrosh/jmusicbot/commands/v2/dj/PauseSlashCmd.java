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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;

/**
 * Slash command to pause the current song.
 */
public class PauseSlashCmd extends DJSlashCommand
{
    public PauseSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "pause";
        this.help = "pauses the current song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        if (handler.getPlayer().isPaused())
        {
            event.reply(event.getClient().getWarning() + " The player is already paused! Use `/play` to unpause!")
                    .setEphemeral(true).queue();
            return;
        }

        handler.getPlayer().setPaused(true);
        event.reply(event.getClient().getSuccess() + " Paused **" + handler.getPlayer().getPlayingTrack().getInfo().title
                + "**. Use `/play` to unpause!").queue();
    }
}
