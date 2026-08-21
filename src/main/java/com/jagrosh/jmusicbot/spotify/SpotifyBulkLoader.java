package com.jagrosh.jmusicbot.spotify;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.PlaylistExecutionTimer;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Bulk loader component responsible for asynchronously resolving and enqueuing batches of Spotify tracks.
 * <p>
 * This class coordinates the lookup of Spotify metadata against YouTube search queries using LavaPlayer.
 * It ensures track order preservation while leveraging multi-threaded parallel resolution for bulk playlists
 * and albums.
 * </p>
 *
 * <h2>Key Architecture & Concurrency Rules:</h2>
 * <ul>
 *   <li><b>Cancellation Token Tracking:</b> Tracks active load operations per guild via {@link #ACTIVE_LOAD_TOKENS}.
 *       Interrupted loads (e.g., via {@code /stop} or new play commands) are cleanly aborted using {@link #cancelLoading(long)}.</li>
 *   <li><b>Sequential First-Track Execution:</b> Loads and enqueues the first track immediately to provide instant
 *       audio playback and Discord UI feedback before firing background resolution tasks for remaining items.</li>
 *   <li><b>Position-Preserved Array Mapping:</b> Resolves remaining tracks concurrently using a fixed thread pool
 *       while inserting audio tracks into an index-aligned array to guarantee original playlist ordering upon enqueuing.</li>
 * </ul>
 */
public class SpotifyBulkLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(SpotifyBulkLoader.class);

    private static final ConcurrentHashMap<Long, Long> ACTIVE_LOAD_TOKENS = new ConcurrentHashMap<>();
    private static final AtomicLong TOKEN_GENERATOR = new AtomicLong(0);

    /**
     * Cancels any active Spotify background loading task for the specified guild.
     * <p>
     * Should be invoked by command handlers (such as {@code /stop}, {@code /skip}, or {@code /clear})
     * to immediately interrupt ongoing background YouTube searches and queue operations.
     * </p>
     *
     * @param guildId the Discord snowflake ID of the target guild
     */
    public static void cancelLoading(long guildId)
    {
        ACTIVE_LOAD_TOKENS.remove(guildId);
        LOG.debug("Cancelled active Spotify bulk load for guild {}", guildId);
    }

    /**
     * Checks whether an ongoing load operation token has been invalidated or cancelled.
     *
     * @param guildId the Discord snowflake ID of the guild
     * @param token   the unique execution token assigned at startup
     * @return {@code true} if the token is no longer active or has been overridden; {@code false} otherwise
     */
    private static boolean isCancelled(long guildId, long token)
    {
        Long activeToken = ACTIVE_LOAD_TOKENS.get(guildId);
        return activeToken == null || activeToken != token;
    }

    /**
     * Resolves a batch of Spotify track metadata entries against YouTube search endpoints and enqueues
     * the resulting tracks sequentially into the guild's playback queue.
     * <p>
     * <b>Workflow:</b>
     * <ol>
     *   <li>Generates a unique execution token and registers it for the target guild.</li>
     *   <li>Searches YouTube for the primary track, selecting the best match via {@link SpotifyTrackMatcher}.</li>
     *   <li>Enqueues the first track and updates the Discord {@link InteractionHook}.</li>
     *   <li>Spawns a bounded thread pool to resolve all remaining tracks concurrently without blocking Discord gateway threads.</li>
     *   <li>Enqueues the resolved tracks in original array index order once background searches complete.</li>
     * </ol>
     * </p>
     *
     * @param bot          the core bot instance providing configuration and player managers
     * @param guild        the target Discord guild
     * @param member       the guild member who initiated the command
     * @param channel      the text channel where feedback should be posted
     * @param result       the parsed {@link SpotifyResult} holding track records
     * @param musicService the music management service handling queueing logic
     * @param hook         the Discord slash interaction hook for deferred message edits
     */
    public static void loadPlaylist(Bot bot, Guild guild, Member member, TextChannel channel,
            SpotifyResult result, MusicService musicService, InteractionHook hook)
    {
        if (result == null || result.tracks() == null || result.tracks().isEmpty())
            return;

        long guildId = guild.getIdLong();
        long loadToken = TOKEN_GENERATOR.incrementAndGet();
        ACTIVE_LOAD_TOKENS.put(guildId, loadToken);

        String successEmoji = bot.getConfig().getSuccess();
        String warningEmoji = bot.getConfig().getWarning();

        SpotifyTrack firstTrack = result.tracks().get(0);
        String firstTitle = firstTrack.title() != null ? firstTrack.title() : "";
        String firstArtist = firstTrack.artist() != null ? firstTrack.artist() : "";
        Long firstDurationMs = firstTrack.durationMs() > 0 ? (long) firstTrack.durationMs() : null;
        String firstQuery = (firstTitle + " " + firstArtist).trim();

        bot.getPlayerManager().loadItemOrdered(guild, "ytsearch:" + firstQuery,
                bot.getAudioLoadWrapper().wrap(firstQuery, new AudioLoadResultHandler() {

                    @Override
                    public void trackLoaded(AudioTrack t)
                    {
                        processFirstTrackAndContinue(t);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist p)
                    {
                        if (!p.getTracks().isEmpty())
                        {
                            AudioTrack bestMatch = SpotifyTrackMatcher.selectBestMatch(p.getTracks(), firstTitle,
                                    firstArtist, firstDurationMs);
                            processFirstTrackAndContinue(bestMatch != null ? bestMatch : p.getTracks().get(0));
                        } 
                        else
                        {
                            noMatches();
                        }
                    }

                    @Override
                    public void noMatches()
                    {
                        ACTIVE_LOAD_TOKENS.remove(guildId, loadToken);
						hook.editOriginal(
								warningEmoji + " Could not find a match for the first track: **" + firstTitle + "**")
								.setComponents(Collections.emptyList()).queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException e)
                    {
                        ACTIVE_LOAD_TOKENS.remove(guildId, loadToken);
						hook.editOriginal(warningEmoji + " Failed to load first track: " + e.getMessage())
								.setComponents(Collections.emptyList()).queue();
                    }

                    private void processFirstTrackAndContinue(AudioTrack track)
                    {
                        if (isCancelled(guildId, loadToken))
                        {
                            LOG.debug("Spotify bulk load cancelled before processing first track for guild {}", guildId);
                            return;
                        }

                        if (track == null)
                        {
                            noMatches();
                            return;
                        }

                        MusicService.TrackAddResult addResult = musicService.addTrackToQueue(guild, member, track,
                                "Spotify: " + track.getInfo().uri, channel);

                        String addMsg;

                        if (addResult == null)
                        {
                            addMsg = FormatUtil.filter(warningEmoji + " " + musicService.formatTooLongError(track));

                            if (result.tracks().size() == 1)
                            {
                                ACTIVE_LOAD_TOKENS.remove(guildId, loadToken);
                                hook.editOriginal(addMsg).setComponents(Collections.emptyList()).queue();
                                return;
                            }
                        } 
                        else
                        {
                            addMsg = FormatUtil.filter(successEmoji + " " + addResult.formattedMessage);
                        }

                        if (result.tracks().size() == 1)
                        {
                            ACTIVE_LOAD_TOKENS.remove(guildId, loadToken);
                            hook.editOriginal(addMsg).setComponents(Collections.emptyList()).queue();
                            return;
                        }
                        
						hook.editOriginal(
								addMsg + "\n🔄 Loading **" + (result.tracks().size() - 1) + "** additional tracks.")
								.setComponents(Collections.emptyList()).queue();

                        loadRemainingTracks(addMsg, loadToken);
                    }

                    /**
                     * Executes parallel YouTube searches for the remaining playlist items and streams resolved 
                     * tracks into the queue sequentially while maintaining strict playlist order and measuring timing metrics.
                     */
                    private void loadRemainingTracks(String addMsg, long token)
                    {
                        int totalTracks = result.tracks().size();
                        int remainingCount = totalTracks - 1;

                        if (remainingCount <= 0)
                        {
                            ACTIVE_LOAD_TOKENS.remove(guildId, token);
                            return;
                        }
                        
                        PlaylistExecutionTimer timer = PlaylistExecutionTimer.start();

                        AudioTrack[] resolvedTracks = new AudioTrack[totalTracks];
                        boolean[] completedIndices = new boolean[totalTracks];

                        AtomicInteger completedTasks = new AtomicInteger(0);
                        AtomicInteger loadedCount = new AtomicInteger(0);
                        AtomicInteger nextTrackIndex = new AtomicInteger(1);

                        ExecutorService executor = Executors.newFixedThreadPool(3);

                        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                        if (handler != null)
                        {
                            handler.setLastReason(member.getUser().getName() + " added a Spotify playlist.");
                        }

                        Runnable drainReadyTracks = () -> {
                            synchronized (resolvedTracks)
                            {
                                while (nextTrackIndex.get() < totalTracks)
                                {
                                    int curr = nextTrackIndex.get();
                                    if (!completedIndices[curr])
                                    {
                                        break;
                                    }

                                    if (!isCancelled(guildId, token))
                                    {
                                        AudioTrack track = resolvedTracks[curr];
                                        if (track != null && !musicService.isTooLong(track) && handler != null)
                                        {
                                            SpotifyTrack queuedTrack = result.tracks().get(curr);
                                            String searchQuery = (queuedTrack.title() + " " + queuedTrack.artist()).trim();

                                            handler.addTrack(new QueuedTrack(track,
                                                    new RequestMetadata(member.getUser(),
                                                            new RequestMetadata.RequestInfo(searchQuery, track.getInfo().uri),
                                                            channel.getIdLong())));
                                            loadedCount.incrementAndGet();
                                        }
                                    }

                                    nextTrackIndex.incrementAndGet();
                                }
                            }
                        };

                        for (int i = 1; i < totalTracks; i++)
                        {
                            final int index = i;
                            final SpotifyTrack st = result.tracks().get(i);
                            final String sTitle = st.title() != null ? st.title() : "";
                            final String sArtist = st.artist() != null ? st.artist() : "";
                            final Long sDurationMs = st.durationMs() > 0 ? (long) st.durationMs() : null;
                            final String trackQuery = (sTitle + " " + sArtist).trim();

                            executor.submit(() -> {
                                try
                                {
                                    if (isCancelled(guildId, token))
                                        return;

                                    bot.getPlayerManager().loadItem("ytsearch:" + trackQuery, new AudioLoadResultHandler() {

                                        @Override
                                        public void trackLoaded(AudioTrack t)
                                        {
                                            resolvedTracks[index] = t;
                                        }

                                        @Override
                                        public void playlistLoaded(AudioPlaylist p)
                                        {
                                            if (!p.getTracks().isEmpty())
                                            {
                                                AudioTrack bestMatch = SpotifyTrackMatcher
                                                        .selectBestMatch(p.getTracks(), sTitle, sArtist, sDurationMs);
                                                resolvedTracks[index] = bestMatch;
                                            }
                                        }

                                        @Override
                                        public void noMatches() {}

                                        @Override
                                        public void loadFailed(FriendlyException e) {}

                                    }).get();
                                }
                                catch (Exception e)
                                {
                                    LOG.warn("Failed to search YouTube for: \"{}\"", trackQuery, e);
                                }
                                finally
                                {
                                    synchronized (resolvedTracks)
                                    {
                                        completedIndices[index] = true;
                                    }

                                    drainReadyTracks.run();

                                    if (completedTasks.incrementAndGet() == remainingCount)
                                    {
                                        executor.shutdown();
                                        try
                                        {
                                        	LOG.info("Finished Spotify bulk load for guild {}: {}", 
                                                    guildId, timer.getFormattedSummary(loadedCount.get()));
                                            if (!isCancelled(guildId, token))
                                            {
												hook.editOriginal(addMsg + "\n" + successEmoji + " Loaded **"
														+ loadedCount.get() + "** additional tracks!")
														.setComponents(Collections.emptyList()).queue();
                                            }
                                        }
                                        catch (Exception ex)
                                        {
                                            LOG.error("Error during Spotify bulk queueing completion for guild {}", guildId, ex);
                                        }
                                        finally
                                        {
                                            ACTIVE_LOAD_TOKENS.remove(guildId, token);
                                        }
                                    }
                                }
                            });
                        }
                    }
                }));
    }
}