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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;

import java.util.List;

/**
 * Slash command to show available playlists.
 */
public class PlaylistsSlashCmd extends MusicSlashCommand
{
    public PlaylistsSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "playlists";
        this.help = "shows the available playlists";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = false;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        if (!bot.getPlaylistLoader().folderExists())
            bot.getPlaylistLoader().createFolder();

        if (!bot.getPlaylistLoader().folderExists())
        {
            event.reply(event.getClient().getWarning() + " Playlists folder does not exist and could not be created!")
                    .setEphemeral(true).queue();
            return;
        }

        List<String> list = bot.getPlaylistLoader().getPlaylistNames();
        if (list == null)
        {
            event.reply(event.getClient().getError() + " Failed to load available playlists!")
                    .setEphemeral(true).queue();
        }
        else if (list.isEmpty())
        {
            event.reply(event.getClient().getWarning() + " There are no playlists in the Playlists folder!")
                    .setEphemeral(true).queue();
        }
        else
        {
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Available playlists:\n");
            list.forEach(str -> builder.append("`").append(str).append("` "));
            builder.append("\nUse `/play playlist:<name>` to play a playlist");
            event.reply(builder.toString()).queue();
        }
    }
}
