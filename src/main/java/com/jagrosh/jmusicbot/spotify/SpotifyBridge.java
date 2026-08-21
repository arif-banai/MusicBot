package com.jagrosh.jmusicbot.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.PythonScriptManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge utility for invoking the local Python scraper script to extract Spotify metadata.
 * <p>
 * This class acts as the execution layer between the Java application and the external Python process
 * ({@code scraper.py}). It handles subprocess execution, stream consumption, concurrency bounding,
 * memory caching, and conversion of JSON payloads into type-safe {@link SpotifyResult} records.
 * </p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Concurrency Controls:</b> Uses a {@link Semaphore} to limit concurrent Python process invocations
 *       and prevent system resource exhaustion or rate-limiting.</li>
 *   <li><b>In-Memory Caching:</b> Integrates with {@link SpotifyCache} to store and serve previously resolved
 *       metadata across bot requests.</li>
 *   <li><b>Automatic Cache Pre-seeding:</b> When retrieving container entities (albums or playlists), individual
 *       track records are automatically cached to optimize subsequent single-track lookups.</li>
 *   <li><b>Async Execution:</b> Offers non-blocking lookups via {@link CompletableFuture} backed by a dedicated thread pool.</li>
 * </ul>
 */
public class SpotifyBridge
{
	private static final Logger LOG = LoggerFactory.getLogger(SpotifyBridge.class);

	private static final SpotifyCache CACHE = new SpotifyCache();
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final int MAX_CONCURRENT_SCRAPERS = 4;
	private static final Semaphore SCRAPER_SEMAPHORE = new Semaphore(MAX_CONCURRENT_SCRAPERS, true);

	private static final SpotifyType SMOKE_TEST_TYPE = SpotifyType.TRACK;
	private static final String SMOKE_TEST_ID = "02vw0tjLamMJAzMlCSiNH3";

	private static boolean enabled = false;

	/**
     * Validates that the Python interpreter, {@code scraper.py} script extraction, and scraper dependencies
     * are present and functional at startup by executing a pre-flight smoke test.
     *
     * @param config the bot configuration instance containing global settings
     */
	public static void init(BotConfig config)
    {
        try
        {
            File scriptFile = PythonScriptManager.getScriptFile();
            if (scriptFile == null || !scriptFile.exists())
            {
                enabled = false;
                LOG.error("SpotifyBridge startup failed: Unable to locate or extract scraper.py from resources.");
                return;
            }

            SpotifyResult testResult = executeScript(SMOKE_TEST_TYPE, SMOKE_TEST_ID);

            if (testResult != null && testResult.success())
            {
                enabled = true;
                LOG.info("SpotifyBridge initialized successfully. Pre-flight check passed.");
            }
            else
            {
                enabled = false;
                String reason = (testResult != null) ? testResult.errorMessage() : "Null result from script";
                LOG.warn("SpotifyBridge pre-flight verification failed: {}. Spotify features will be disabled.", reason);
            }
        }
        catch (Exception e)
        {
            enabled = false;
            LOG.warn("SpotifyBridge initialization exception: {}. Spotify integration will be disabled.", e.getMessage(), e);
        }
    }

	/**
     * Retrieves metadata for a given Spotify entity, checking the in-memory JVM cache before spawning
     * an external Python scraping process.
     *
     * @param type the {@link SpotifyType} entity classification ({@code TRACK}, {@code EPISODE}, {@code PLAYLIST}, or {@code ALBUM})
     * @param id   the 22-character Base62 Spotify resource identifier
     * @return a {@link SpotifyResult} containing parsed track records on success, or an error payload on failure
     */
	public static SpotifyResult getTrackInfo(SpotifyType type, String id)
    {
        if (type == null)
            return SpotifyResult.failure("Invalid Spotify type");

        if (!isEnabled())
        {
            LOG.warn("Spotify request for [{}:{}] dropped because SpotifyBridge is disabled.", type.getValue(), id);
            return SpotifyResult.failure("Spotify integration is disabled");
        }

        Optional<SpotifyResult> cachedResult = CACHE.get(type.getValue(), id);
        if (cachedResult.isPresent())
        {
            return cachedResult.get();
        }

        SpotifyResult result = executeScript(type, id);

        if (result != null && result.success())
        {
            CACHE.put(type.getValue(), id, result);
        }

        return result;
    }

	/**
     * String overload for {@link #getTrackInfo(SpotifyType, String)} for backwards compatibility.
     *
     * @param typeStr the string representation of the Spotify entity type
     * @param id      the 22-character Base62 Spotify resource identifier
     * @return a {@link SpotifyResult} containing parsed track records on success, or an error payload on failure
     */
    public static SpotifyResult getTrackInfo(String typeStr, String id)
    {
        return getTrackInfo(SpotifyType.fromString(typeStr), id);
    }

    /**
     * Checks whether the Spotify integration is active and verified.
     *
     * @return {@code true} if startup pre-flight checks passed; {@code false} otherwise
     */
	public static boolean isEnabled()
	{
		return enabled;
	}

	/**
     * Spawns an external Python subprocess to execute {@code scraper.py} and parse its output.
     *
     * <p><b>Execution Workflow:</b></p>
     * <ol>
     *   <li>Acquires a permit from {@link #SCRAPER_SEMAPHORE} (5-second timeout).</li>
     *   <li>Executes {@code scraper.py} via {@link ProcessBuilder} with a 20-second hard timeout.</li>
     *   <li>Asynchronously drains stdout and stderr streams to prevent subprocess deadlocks.</li>
     *   <li>Parses standard single-line JSON payloads into {@link SpotifyResult} objects via {@link TrackPayloadParser}.</li>
     *   <li>Pre-seeds individual track items into {@link SpotifyCache} for playlist and album responses.</li>
     *   <li>Releases the semaphore permit in a {@code finally} block.</li>
     * </ol>
     *
     * @param type the {@link SpotifyType} entity classification
     * @param id   the 22-character Base62 Spotify resource identifier
     * @return a {@link SpotifyResult} containing the outcome of the scraping operation
     */
	private static SpotifyResult executeScript(SpotifyType type, String id)
    {
        boolean permitAcquired = false;
        try
        {
            permitAcquired = SCRAPER_SEMAPHORE.tryAcquire(5, TimeUnit.SECONDS);
            if (!permitAcquired)
            {
                LOG.warn("Scraper concurrency limit reached; dropping request for [{}:{}]", type.getValue(), id);
                return SpotifyResult.failure("Concurrency limit reached");
            }

            File scriptFile = PythonScriptManager.getScriptFile();
            if (scriptFile == null)
            {
                return SpotifyResult.failure("scraper.py missing");
            }

            String pythonPath = PythonScriptManager.getPythonExecutablePath();
            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptFile.getAbsolutePath(), type.getValue(), id);
            pb.redirectErrorStream(false);

            Process p = pb.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        sb.append(line);
                    }
                }
                catch (IOException e)
                {
                    LOG.warn("Error reading stdout for [{}:{}]", type.getValue(), id, e);
                }
                return sb.toString();
            });

            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream())))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(line);
                    }
                }
                catch (IOException e)
                {
                    LOG.warn("Error reading stderr for [{}:{}]", type.getValue(), id, e);
                }
                return sb.toString();
            });

            boolean finished = p.waitFor(20, TimeUnit.SECONDS);
            if (!finished)
            {
                p.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                return SpotifyResult.failure("Python script timed out");
            }

            String rawOutput = stdoutFuture.get(2, TimeUnit.SECONDS).trim();
            String errorOutput = stderrFuture.get(2, TimeUnit.SECONDS).trim();
            int exitCode = p.exitValue();

            if (exitCode != 0)
            {
                String message = !rawOutput.isEmpty() ? rawOutput : errorOutput;
                LOG.error("Python scraper failed with exit code {}: {}", exitCode, message);
                return SpotifyResult.failure(!message.isEmpty() ? message : "Python process exited with error code " + exitCode);
            }

            if (!rawOutput.isEmpty())
            {
                JsonNode root = OBJECT_MAPPER.readTree(rawOutput);

                if (root.has("error"))
                {
                    return SpotifyResult.failure(root.get("error").asText());
                }

                JsonNode resultsNode = root.get("results");
                if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty())
                {
                    return SpotifyResult.failure("No results array returned");
                }

                JsonNode itemNode = resultsNode.get(0);
                if (itemNode.has("error") || (itemNode.has("success") && !itemNode.get("success").asBoolean()))
                {
                    String itemError = itemNode.has("error") ? itemNode.get("error").asText() : "Unknown item error";
                    return SpotifyResult.failure(itemError);
                }

                SpotifyResult fullResult = TrackPayloadParser.parseTrackPayload(itemNode);

                if (fullResult.success() && (type == SpotifyType.PLAYLIST || type == SpotifyType.ALBUM))
                {
                    preseedTracks(fullResult.tracks());
                }

                return fullResult;
            }
        }
        catch (Exception e)
        {
            LOG.error("Exception when executing Python script: {}", e.getMessage(), e);
            return SpotifyResult.failure(e.getMessage());
        }
        finally
        {
            if (permitAcquired)
            {
                SCRAPER_SEMAPHORE.release();
            }
        }

        return SpotifyResult.failure("Empty response from scraper");
    }

	/**
     * Pre-populates the in-memory JVM cache with individual track records returned from a batch playlist
     * or album resolution.
     *
     * @param tracks the list of {@link SpotifyTrack} records to store in the single-track cache
     */
	public static void preseedTracks(List<SpotifyTrack> tracks)
    {
        if (tracks != null && !tracks.isEmpty())
        {
            CACHE.populateIndividualTrackCache(tracks);
        }
    }

	/**
	 * Dedicated thread pool executor for dispatching asynchronous Spotify metadata lookup tasks.
	 * <p>
	 * Bounded by {@link #MAX_CONCURRENT_SCRAPERS} to prevent asynchronous calls from overloading system
	 * thread resources. Configured with a custom thread factory that assigns recognizable thread names
	 * ({@code "SpotifyBridge-Worker"}) for streamlined logging and stack trace diagnostics.
	 */
    private static final ExecutorService BRIDGE_EXECUTOR = Executors.newFixedThreadPool(MAX_CONCURRENT_SCRAPERS,
            r -> new Thread(r, "SpotifyBridge-Worker"));

    /**
     * Asynchronously retrieves Spotify track metadata without blocking the calling thread.
     *
     * @param type the {@link SpotifyType} entity classification
     * @param id   the 22-character Base62 Spotify resource identifier
     * @return a {@link CompletableFuture} emitting a {@link SpotifyResult} upon completion
     */
    public static CompletableFuture<SpotifyResult> getTrackInfoAsync(SpotifyType type, String id)
    {
        return CompletableFuture.supplyAsync(() -> getTrackInfo(type, id), BRIDGE_EXECUTOR);
    }
    
    /**
     * String overload for {@link #getTrackInfoAsync(SpotifyType, String)}.
     *
     * @param typeStr the string representation of the Spotify entity type
     * @param id      the 22-character Base62 Spotify resource identifier
     * @return a {@link CompletableFuture} emitting a {@link SpotifyResult} upon completion
     */
    public static CompletableFuture<SpotifyResult> getTrackInfoAsync(String typeStr, String id)
    {
        return getTrackInfoAsync(SpotifyType.fromString(typeStr), id);
    }
}