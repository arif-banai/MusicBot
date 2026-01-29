package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 * Shared validation logic for music commands (both text and slash commands).
 */
public final class MusicCommandValidator
{
    private MusicCommandValidator() {} // Utility class

    /**
     * Callback interface for handling validation errors.
     */
    public interface ErrorHandler
    {
        void onTextChannelError(TextChannel requiredChannel);
        void onNotPlayingError();
        void onNotListeningError(AudioChannel requiredChannel);
        void onAfkChannelError();
        void onVoiceConnectError(AudioChannel channel);
    }

    /**
     * Validates preconditions for music commands.
     *
     * @param guild         The guild where the command was invoked
     * @param member        The member who invoked the command
     * @param textChannel   The text channel where the command was invoked
     * @param settings      The guild settings
     * @param bot           The bot instance
     * @param jda           The JDA instance (for checking if music is playing)
     * @param bePlaying     Whether music must be playing
     * @param beListening   Whether the user must be listening in voice
     * @param errorHandler  Callback for error messages
     * @return true if validation passed, false if an error was sent
     */
    public static boolean validate(Guild guild, Member member, TextChannel textChannel,
                                   Settings settings, Bot bot, JDA jda,
                                   boolean bePlaying, boolean beListening,
                                   ErrorHandler errorHandler)
    {
        // Check text channel restriction
        TextChannel requiredChannel = settings.getTextChannel(guild);
        if (requiredChannel != null && !textChannel.equals(requiredChannel))
        {
            errorHandler.onTextChannelError(requiredChannel);
            return false;
        }

        // Set up audio handler
        bot.getPlayerManager().setUpHandler(guild);

        // Check if music must be playing
        if (bePlaying)
        {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler == null || !handler.isMusicPlaying(jda))
            {
                errorHandler.onNotPlayingError();
                return false;
            }
        }

        // Check if user must be listening in voice
        if (beListening)
        {
            AudioChannel current = guild.getSelfMember().getVoiceState().getChannel();
            if (current == null)
                current = settings.getVoiceChannel(guild);

            GuildVoiceState userState = member.getVoiceState();
            if (userState.getChannel() == null || userState.isDeafened() ||
                    (current != null && !userState.getChannel().equals(current)))
            {
                errorHandler.onNotListeningError(current);
                return false;
            }

            // Check AFK channel
            VoiceChannel afkChannel = guild.getAfkChannel();
            if (afkChannel != null && afkChannel.equals(userState.getChannel()))
            {
                errorHandler.onAfkChannelError();
                return false;
            }

            // Connect to voice channel if needed
            if (guild.getSelfMember().getVoiceState().getChannel() == null)
            {
                try
                {
                    guild.getAudioManager().openAudioConnection(userState.getChannel());
                }
                catch (PermissionException ex)
                {
                    errorHandler.onVoiceConnectError(userState.getChannel());
                    return false;
                }
            }
        }

        return true;
    }
}
