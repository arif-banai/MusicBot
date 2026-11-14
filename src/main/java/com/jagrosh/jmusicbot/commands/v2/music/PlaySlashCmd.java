package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.InteractionHookOutputAdapter;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.SlashEventOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.utils.QueryNormalizer;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class PlaySlashCmd extends MusicSlashCommand
{
    /**
     * How long autocomplete waits for search results before answering with no choices. Discord
     * allows 3 seconds from the interaction's creation, which also covers gateway delivery and
     * the round trip of the answer itself.
     */
    static final long AUTOCOMPLETE_DEADLINE_MS = 2000;

    /** Discord's limit for an autocomplete choice's name and string value. */
    private static final int MAX_CHOICE_LENGTH = 100;

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
            musicService.play(event.getGuild(), event.getMember(), "", event.getGuildChannel(),
                    new SlashEventOutputAdapter(event));
            return;
        }

        String args = event.getOption("query").getAsString();
        event.reply(loadingEmoji + " Loading... `[" + args + "]`").queue(hook -> {
            musicService.play(event.getGuild(), event.getMember(), args, event.getGuildChannel(),
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

        String normalized = QueryNormalizer.normalize(input);
        if(isUrlOrPath(normalized))
        {
            // Discord rejects a choice value over 100 characters, and normalization can add
            // "https://" to one that fit; offer no choice then (the typed text can still be sent).
            if(normalized.length() > MAX_CHOICE_LENGTH)
                event.replyChoices().queue();
            else
                event.replyChoices(new Command.Choice(truncateTitle(normalized), normalized)).queue();
            return;
        }

        // Autocomplete cannot be deferred: Discord drops it (10062 Unknown interaction) unless the
        // choices arrive within 3 seconds. A YouTube search can take longer, so answer with no
        // choices at the deadline and drop a result that arrives after it.
        AtomicBoolean answered = new AtomicBoolean(false);
        ScheduledFuture<?> fallback = bot.getThreadpool().schedule(() ->
        {
            if (answered.compareAndSet(false, true))
            {
                event.replyChoices().queue();
            }
        }, AUTOCOMPLETE_DEADLINE_MS, TimeUnit.MILLISECONDS);
        Consumer<Runnable> answerOnce = reply ->
        {
            if (answered.compareAndSet(false, true))
            {
                if (fallback != null)
                {
                    fallback.cancel(false);
                }
                reply.run();
            }
        };

        // Unordered: on the guild's ordering key (MusicService.play's) every keystroke's search would
        // run one after another, and a real /play would wait behind all the abandoned ones.
        bot.getPlayerManager().loadItem("ytsearch:" + input,
                bot.getAudioLoadWrapper().wrap("ytsearch:" + input, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                String title = track.getInfo().title;
                answerOnce.accept(() -> event.replyChoices(new Command.Choice(truncateTitle(title), track.getInfo().uri)).queue());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                answerOnce.accept(() -> event.replyChoices(buildChoicesFromPlaylist(playlist)).queue());
            }

            @Override
            public void noMatches()
            {
                answerOnce.accept(() -> event.replyChoices().queue());
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                answerOnce.accept(() -> event.replyChoices().queue());
            }
        }));
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
            String title = track.getInfo().title;
            choices.add(new Command.Choice(truncateTitle(title), track.getInfo().uri));
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
