package com.jagrosh.jmusicbot.spotify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.service.MusicService.OutputAdapter;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

/**
 * Intercepts the resolution of the first track in a Spotify container to present confirmation buttons ("Load Playlist" / "Cancel"),
 * mitigating unintended bulk enqueueing. Listens for user button interactions via JDA EventWaiter before dispatching
 * the bulk loading process to {@link SpotifyBulkLoader}.
 */
public class SpotifyPlaylistPromptHandler implements AudioLoadResultHandler
{
    private final Bot bot;
    private final Guild guild;
    private final Member member;
    private final TextChannel channel;
    private final OutputAdapter output;
    private final SpotifyResult result;

    private final String warningEmoji;
    private final String errorEmoji;

    private final MusicService musicService;
    
    private static final String ID_SPOTIFY_LOAD = "spotify:load_playlist";
    private static final String ID_SPOTIFY_CANCEL = "spotify:cancel_playlist";

    private static final Logger LOG = LoggerFactory.getLogger(SpotifyPlaylistPromptHandler.class);

    /**
     * Constructs a new prompt handler instance for an interactive Spotify playlist load request.
     *
     * @param bot          the core bot instance providing configuration and event waiters
     * @param guild        the target Discord guild
     * @param member       the guild member who initiated the command
     * @param channel      the text channel where prompt messages are posted
     * @param output       the message output adapter for rendering feedback
     * @param result       the parsed {@link SpotifyResult} containing resolved tracks
     * @param musicService the music management service handling audio state
     */
    public SpotifyPlaylistPromptHandler(Bot bot, Guild guild, Member member, TextChannel channel,
            OutputAdapter output, SpotifyResult result, MusicService musicService)
    {
        this.bot = bot;
        this.guild = guild;
        this.member = member;
        this.channel = channel;
        this.output = output;
        this.result = result;
        this.musicService = musicService;

        this.warningEmoji = bot.getConfig().getWarning();
        this.errorEmoji = bot.getConfig().getError();
    }

    
    @Override
    public void trackLoaded(AudioTrack track)
    {
        processFirstTrack(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist)
    {
        if (!playlist.getTracks().isEmpty())
        {
            processFirstTrack(playlist.getTracks().get(0));
        } 
        else
        {
            noMatches();
        }
    }

    @Override
    public void noMatches()
    {
        output.editMessage(warningEmoji + " No results found for the first track.");
    }

    @Override
    public void loadFailed(FriendlyException exception)
    {
        output.editMessage(errorEmoji + " Error loading first track.");
    }

    /**
     * Evaluates the first resolved track against duration constraints and displays the interactive confirmation prompt.
     * <p>
     * Constructs Discord button components ("Load Playlist" and "Cancel") and registers an asynchronous event waiter listener
     * using JDA EventWaiter. If approved, delegates bulk queueing to {@link SpotifyBulkLoader}.
     * </p>
     *
     * @param track the primary resolved {@link AudioTrack}
     */
	private void processFirstTrack(AudioTrack track)
	{
		if (musicService.isTooLong(track))
		{
			output.editMessage(FormatUtil.filter(warningEmoji + " Track too long."));
			return;
		}

		int trackCount = (result != null && result.tracks() != null) ? result.tracks().size() : 0;

		String promptMsg = warningEmoji + " This track has a playlist of **" + trackCount + "** tracks attached.\n"
				+ "⚠️ **Spotify playlists may not load every track, and matches may not always be exact!**\n"
				+ "\t*Only successfully found tracks will be added. Do you still want to load it?*";

		List<Button> buttons = new ArrayList<>();
		buttons.add(Button.success(ID_SPOTIFY_LOAD, Emoji.fromUnicode("\uD83D\uDCE5")).withLabel("Load Playlist"));
		buttons.add(Button.danger(ID_SPOTIFY_CANCEL, Emoji.fromUnicode("\uD83D\uDEAB")).withLabel("Cancel"));

		MessageEditBuilder editBuilder = new MessageEditBuilder().setContent(promptMsg)
				.setComponents(ActionRow.of(buttons));

		LOG.info("Action: PROMPT_CREATED | guild={} | user={} | totalTracks={}", guild.getId(),
				member.getUser().getName(), trackCount);

		output.editMessage(promptMsg, m -> {
			m.editMessage(editBuilder.build()).queue(msg -> {
				bot.getWaiter().waitForEvent(ButtonInteractionEvent.class, e -> e.getMessageId().equals(msg.getId())
						&& e.getUser().getIdLong() == member.getIdLong()
						&& (e.getComponentId().equals(ID_SPOTIFY_LOAD) || e.getComponentId().equals(ID_SPOTIFY_CANCEL)),
						e -> {
							if (e.getComponentId().equals(ID_SPOTIFY_CANCEL))
							{
								msg.delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
								LOG.info("Action: CANCELLED | guild={} | user={}", guild.getId(),
										member.getUser().getName());
								return;
							}
							if (e.getComponentId().equals(ID_SPOTIFY_LOAD))
							{
								e.deferEdit().queue(hook -> {
									hook.editOriginal(
											"🔄 Loading **" + result.tracks().size() + "** additional tracks.")
											.setComponents(Collections.emptyList()).queue();
									SpotifyBulkLoader.loadPlaylist(bot, guild, member, channel, result, musicService,
											hook);
								});
								LOG.info("Action: APPROVED | guild={} | user={} | totalTracks={}", guild.getId(),
										member.getUser().getName(), trackCount);
							}
						}, 30, TimeUnit.SECONDS, () -> {
							msg.delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
							LOG.info("Action: TIMEOUT | guild={} | user={}", guild.getId(), member.getUser().getName());
						});
			});
		});
	}
}