package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.MusicCommandValidator;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public abstract class MusicSlashCommand extends SlashCommand
{
    protected final Bot bot;
    protected boolean bePlaying;
    protected boolean beListening;

    public MusicSlashCommand(Bot bot)
    {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Music");
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        String errorEmoji = event.getClient().getError();

        boolean valid = MusicCommandValidator.validate(
                event.getGuild(),
                event.getMember(),
                event.getTextChannel(),
                settings,
                bot,
                event.getJDA(),
                bePlaying,
                beListening,
                new MusicCommandValidator.ErrorHandler()
                {
                    @Override
                    public void onTextChannelError(TextChannel requiredChannel)
                    {
                        event.reply(errorEmoji + " You can only use that command in " + requiredChannel.getAsMention() + "!")
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onNotPlayingError()
                    {
                        event.reply(errorEmoji + " There must be music playing to use that!")
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onNotListeningError(AudioChannel requiredChannel)
                    {
                        String channelName = requiredChannel == null ? "a voice channel" : requiredChannel.getAsMention();
                        event.reply(errorEmoji + " You must be listening in " + channelName + " to use that!")
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onAfkChannelError()
                    {
                        event.reply(errorEmoji + " You cannot use that command in an AFK channel!")
                                .setEphemeral(true).queue();
                    }

                    @Override
                    public void onVoiceConnectError(AudioChannel channel)
                    {
                        event.reply(errorEmoji + " I am unable to connect to " + channel.getAsMention() + "!")
                                .setEphemeral(true).queue();
                    }
                }
        );

        if (valid)
        {
            doCommand(event);
        }
    }

    public abstract void doCommand(SlashCommandEvent event);
}