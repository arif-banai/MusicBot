package com.jagrosh.jmusicbot.commands.v1.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.service.MusicService;

/**
 * Command that provides users the ability to move a track in the playlist.
 */
public class MoveTrackCmd extends DJCommand
{
    private final MusicService musicService;

    public MoveTrackCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "movetrack";
        this.help = "move a track in the current queue to a different position";
        this.arguments = "<from> <to>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event)
    {
        int from;
        int to;

        String[] parts = event.getArgs().split("\\s+", 2);
        if (parts.length < 2)
        {
            event.replyError("Please include two valid indexes.");
            return;
        }

        try
        {
            from = Integer.parseInt(parts[0]);
            to = Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException e)
        {
            event.replyError("Please provide two valid indexes.");
            return;
        }

        if (from == to)
        {
            event.replyError("Can't move a track to the same position.");
            return;
        }

        if (!musicService.isValidQueuePosition(event.getGuild(), from))
        {
            event.replyError("`" + from + "` is not a valid position in the queue!");
            return;
        }
        if (!musicService.isValidQueuePosition(event.getGuild(), to))
        {
            event.replyError("`" + to + "` is not a valid position in the queue!");
            return;
        }

        String trackTitle = musicService.moveTrackPosition(event.getGuild(), from, to);
        if (trackTitle != null)
        {
            event.replySuccess("Moved **" + trackTitle + "** from position `" + from + "` to `" + to + "`.");
        }
    }
}