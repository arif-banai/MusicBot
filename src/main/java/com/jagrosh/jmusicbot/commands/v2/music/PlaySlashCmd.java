package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.InteractionHookOutputAdapter;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.SlashEventOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaySlashCmd extends MusicSlashCommand
{
    private final String loadingEmoji;
    private final MusicService musicService;

    public PlaySlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.help = "plays the provided song";
        this.options = Collections.singletonList(new OptionData(OptionType.STRING, "query", "path to song OR song title OR URL", false).setAutoComplete(true));
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        if (event.getOption("query") == null)
        {
            musicService.play(event.getGuild(), event.getMember(), "", event.getTextChannel(),
                    new SlashEventOutputAdapter(event));
            return;
        }

        String args = event.getOption("query").getAsString();
        event.reply(loadingEmoji + " Loading... `[" + args + "]`").queue(hook -> {
            musicService.play(event.getGuild(), event.getMember(), args, event.getTextChannel(),
                    new InteractionHookOutputAdapter(hook, event.getJDA(), event.getClient().getWarning()));
        });
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event)
    {
        String input = event.getFocusedOption().getValue();
        if(input.isEmpty())
        {
            event.replyChoices().queue();
            return;
        }

        if(isUrlOrPath(input))
        {
            event.replyChoices(new Command.Choice(input, input)).queue();
            return;
        }

        bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + input, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                event.replyChoices(new Command.Choice(track.getInfo().title, track.getInfo().uri)).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                event.replyChoices(buildChoicesFromPlaylist(playlist)).queue();
            }

            @Override
            public void noMatches()
            {
                event.replyChoices().queue();
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                event.replyChoices().queue();
            }
        });
    }

    /**
     * Checks if the input looks like a URL or file path (skip searching in that case).
     */
    private static boolean isUrlOrPath(String input)
    {
        return input.startsWith("http://") || input.startsWith("https://")
                || input.contains(":\\") || input.startsWith("/") || input.contains("\\");
    }

    /**
     * Builds autocomplete choices from a playlist, limited to 10 results.
     * Truncates titles longer than 100 characters (Discord's limit).
     */
    private static List<Command.Choice> buildChoicesFromPlaylist(AudioPlaylist playlist)
    {
        List<Command.Choice> choices = new ArrayList<>();
        int limit = Math.min(playlist.getTracks().size(), 10);
        for(int i = 0; i < limit; i++)
        {
            AudioTrack track = playlist.getTracks().get(i);
            String title = truncateTitle(track.getInfo().title);
            choices.add(new Command.Choice(title, track.getInfo().uri));
        }
        return choices;
    }

    /**
     * Truncates a title to fit Discord's 100 character limit for choice names.
     */
    private static String truncateTitle(String title)
    {
        if(title.length() > 100)
            return title.substring(0, 97) + "...";
        return title;
    }
}
