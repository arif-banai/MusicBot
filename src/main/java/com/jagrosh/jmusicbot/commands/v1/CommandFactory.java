package com.jagrosh.jmusicbot.commands.v1;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.examples.command.AboutCommand;
import com.jagrosh.jdautilities.examples.command.PingCommand;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.commands.v1.admin.*;
import com.jagrosh.jmusicbot.commands.v1.dj.*;
import com.jagrosh.jmusicbot.commands.v1.general.SettingsCmd;
import com.jagrosh.jmusicbot.commands.v1.music.*;
import com.jagrosh.jmusicbot.commands.v1.owner.*;
import com.jagrosh.jmusicbot.commands.v2.admin.PrefixSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.admin.QueuetypeSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.admin.SetdjSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.admin.SettcSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.admin.SetvcSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.admin.SkipratioSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.ForceskipSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.ForceremoveSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.MovetrackSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.PauseSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.PlaynextSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.RepeatSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.SkiptoSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.StopSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.VolumeSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.NowPlayingSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.PlaySlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.QueueSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.RemoveSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.SearchSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.SeekSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.ShuffleSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.music.SkipSlashCmd;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.OnlineStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class CommandFactory {

    public static CommandClient createCommandClient(BotConfig config, SettingsManager settings, Bot bot) {
        AboutCommand aboutCommand = createAboutCommand();

        CommandClientBuilder cb = new CommandClientBuilder()
            .setPrefix(config.getPrefix())
            .setAlternativePrefix(config.getAltPrefix())
            .setOwnerId(Long.toString(config.getOwnerId()))
            .setEmojis(config.getSuccess(), config.getWarning(), config.getError())
            .setHelpWord(config.getHelp())
            .setLinkedCacheSize(200)
            .setGuildSettingsManager(settings)
            .addCommands(aboutCommand,
                    new PingCommand(),
                    new SettingsCmd(bot),

                    // Lyrics functionality removed - JLyrics dependency removed
                    // new LyricsCmd(bot),
                    new NowPlayingCmd(bot),
                    new PlayCmd(bot),
                    new PlaylistsCmd(bot),
                    new QueueCmd(bot),
                    new RemoveCmd(bot),
                    new SearchCmd(bot),
                    new SCSearchCmd(bot),
                    new SeekCmd(bot),
                    new ShuffleCmd(bot),
                    new SkipCmd(bot),

                    new ForceRemoveCmd(bot),
                    new ForceskipCmd(bot),
                    new MoveTrackCmd(bot),
                    new PauseCmd(bot),
                    new PlaynextCmd(bot),
                    new RepeatCmd(bot),
                    new SkiptoCmd(bot),
                    new StopCmd(bot),
                    new VolumeCmd(bot),

                    new PrefixCmd(bot),
                    new QueueTypeCmd(bot),
                    new SetdjCmd(bot),
                    new SkipratioCmd(bot),
                    new SettcCmd(bot),
                    new SetvcCmd(bot),

                    new AutoplaylistCmd(bot),
                    new DebugCmd(bot),
                    new PlaylistCmd(bot),
                    new SetavatarCmd(bot),
                    new SetgameCmd(bot),
                    new SetnameCmd(bot),
                    new SetstatusCmd(bot),
                    new ShutdownCmd(bot)
            ).addSlashCommands(
                    // Music commands
                    new NowPlayingSlashCmd(bot),
                    new PlaySlashCmd(bot),
                    new PlaylistsSlashCmd(bot),
                    new QueueSlashCmd(bot),
                    new RemoveSlashCmd(bot),
                    new SearchSlashCmd(bot),
                    new SeekSlashCmd(bot),
                    new ShuffleSlashCmd(bot),
                    new SkipSlashCmd(bot),

                    // DJ commands
                    new ForceremoveSlashCmd(bot),
                    new ForceskipSlashCmd(bot),
                    new MovetrackSlashCmd(bot),
                    new PauseSlashCmd(bot),
                    new PlaynextSlashCmd(bot),
                    new RepeatSlashCmd(bot),
                    new SkiptoSlashCmd(bot),
                    new StopSlashCmd(bot),
                    new VolumeSlashCmd(bot),

                    // Admin commands
                    new PrefixSlashCmd(bot),
                    new QueuetypeSlashCmd(bot),
                    new SetdjSlashCmd(bot),
                    new SettcSlashCmd(bot),
                    new SetvcSlashCmd(bot),
                    new SkipratioSlashCmd(bot)
            ).setManualUpsert(true);

        if (config.useEval())
            cb.addCommand(new EvalCmd(bot));

        if (config.getStatus() != OnlineStatus.UNKNOWN)
            cb.setStatus(config.getStatus());

        if (config.getGame() == null)
            cb.useDefaultGame();
        else if (config.isGameNone())
            cb.setActivity(null);
        else
            cb.setActivity(config.getGame());

        return cb.build();
    }

    private static @NotNull AboutCommand createAboutCommand() {
        AboutCommand aboutCommand = new AboutCommand(Color.BLUE.brighter(),
                "a music bot that is [easy to host yourself!](https://github.com/arif-banai/MusicBot) (v" + OtherUtil.getCurrentVersion() + ")",
                new String[]{"High-quality music playback", "FairQueue™ Technology", "Easy to host yourself"},
                JMusicBot.RECOMMENDED_PERMS);
        aboutCommand.setIsAuthor(false);
        aboutCommand.setReplacementCharacter("\uD83C\uDFB6");
        return aboutCommand;
    }
}