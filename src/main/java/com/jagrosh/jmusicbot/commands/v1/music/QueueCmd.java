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
import com.jagrosh.jdautilities.menu.Paginator;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.MusicCommand;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.concurrent.TimeUnit;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueueCmd extends MusicCommand
{
    private final Paginator.Builder builder;
    private final MusicService musicService;

    public QueueCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "queue";
        this.help = "shows the current queue";
        this.arguments = "[pagenum]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
        this.botPermissions = new Permission[]{Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS};
        builder = new Paginator.Builder()
                .setColumns(1)
                .setFinalAction(m -> {
                    try
                    {
                        m.clearReactions().queue();
                    }
                    catch (PermissionException ignore)
                    {
                    }
                })
                .setItemsPerPage(10)
                .waitOnSinglePage(false)
                .useNumberedItems(true)
                .showPageNumbers(true)
                .wrapPageEnds(true)
                .setEventWaiter(bot.getWaiter())
                .setTimeout(1, TimeUnit.MINUTES);
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int pagenum = 1;
        try
        {
            pagenum = Integer.parseInt(event.getArgs());
        }
        catch (NumberFormatException ignore)
        {
        }

        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
        if (queueInfo == null || queueInfo.isEmpty())
        {
            MusicService.NowPlayingInfo npInfo = musicService.getNowPlayingInfo(event.getGuild(), event.getJDA());
            MessageCreateData embed = npInfo != null && npInfo.isPlaying ? npInfo.nowPlayingMessage : (npInfo != null ? npInfo.noMusicMessage : null);

            if (embed != null)
            {
                MessageCreateData built = new MessageCreateBuilder()
                        .setContent(event.getClient().getWarning() + " There is no music in the queue!")
                        .setEmbeds(embed.getEmbeds().get(0)).build();
                event.reply(built, m ->
                {
                    if (npInfo != null && npInfo.isPlaying)
                        bot.getNowplayingHandler().setLastNPMessage(m);
                });
            }
            else
            {
                event.replyWarning("There is no music in the queue!");
            }
            return;
        }

        String successEmoji = event.getClient().getSuccess();
        builder.setText((i1, i2) -> musicService.formatQueueTitle(queueInfo, successEmoji))
                .setItems(queueInfo.tracks)
                .setUsers(event.getAuthor())
                .setColor(event.getSelfMember().getColors().getPrimary());
        builder.build().paginate(event.getChannel(), pagenum);
    }
}
