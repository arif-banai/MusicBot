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
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Collections;

/**
 * Slash command to show the current queue.
 */
public class QueueSlashCmd extends MusicSlashCommand
{
    private static final int TRACKS_PER_PAGE = 10;
    private final MusicService musicService;

    public QueueSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "queue";
        this.help = "shows the current queue";
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "page", "page number to display", false)
                        .setMinValue(1)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        int page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;

        MusicService.QueueInfo queueInfo = musicService.getQueueInfo(event.getGuild(), event.getJDA());
        if (queueInfo == null || queueInfo.isEmpty())
        {
            MusicService.NowPlayingInfo npInfo = musicService.getNowPlayingInfo(event.getGuild(), event.getJDA());
            if (npInfo != null)
            {
                MessageCreateData embed = npInfo.isPlaying ? npInfo.nowPlayingMessage : npInfo.noMusicMessage;
                if (embed != null)
                {
                    MessageCreateData built = new MessageCreateBuilder()
                            .setContent(event.getClient().getWarning() + " There is no music in the queue!")
                            .setEmbeds(embed.getEmbeds().get(0)).build();
                    event.reply(built).queue(hook ->
                    {
                        if (npInfo.isPlaying)
                            hook.retrieveOriginal().queue(msg -> bot.getNowplayingHandler().setLastNPMessage(msg));
                    });
                    return;
                }
            }
            event.reply(event.getClient().getWarning() + " There is no music in the queue!").setEphemeral(true).queue();
            return;
        }

        // Build paginated response
        int totalPages = (int) Math.ceil((double) queueInfo.tracks.length / TRACKS_PER_PAGE);
        if (page > totalPages)
        {
            page = totalPages;
        }

        int startIndex = (page - 1) * TRACKS_PER_PAGE;
        int endIndex = Math.min(startIndex + TRACKS_PER_PAGE, queueInfo.tracks.length);

        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++)
        {
            sb.append("`").append(i + 1).append(".` ").append(queueInfo.tracks[i]).append("\n");
        }

        String title = musicService.formatQueueTitle(queueInfo, event.getClient().getSuccess());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Queue - Page " + page + "/" + totalPages)
                .setDescription(sb.toString())
                .setFooter("Total: " + queueInfo.tracks.length + " tracks | Duration: " + TimeUtil.formatTime(queueInfo.totalDuration))
                .setColor(event.getMember().getColor());

        event.reply(title).addEmbeds(embed.build()).queue();
    }
}
