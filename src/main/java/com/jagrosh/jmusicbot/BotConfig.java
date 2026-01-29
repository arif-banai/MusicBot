/*
 * Copyright 2018 John Grosh (jagrosh)
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
package com.jagrosh.jmusicbot;

import static com.jagrosh.jmusicbot.config.model.ConfigOption.*;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator;
import com.jagrosh.jmusicbot.config.validation.ConfigValidator.ValidationResult;
import com.jagrosh.jmusicbot.config.migration.ConfigMigration;
import com.jagrosh.jmusicbot.config.migration.ConfigMigrationException;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * 
 * @author John Grosh (jagrosh)
 */
public class BotConfig {
    private final static Logger LOGGER = LoggerFactory.getLogger(BotConfig.class);

    private final UserInteraction userInteraction;

    private Path path = null;
    private String token, prefix, altprefix, helpWord, playlistsFolder, logLevel,
            successEmoji, warningEmoji, errorEmoji, loadingEmoji, searchingEmoji,
            evalEngine;
    private boolean stayInChannel, songInGame, npImages, updatealerts, useEval, dbots, useYouTubeOauth;
    private long owner, maxSeconds, aloneTimeUntilStop;
    private int maxYTPlaylistPages, maxHistorySize;
    private double skipratio;
    private OnlineStatus status;
    private Activity game;
    private Config aliases, transforms;
    private Set<AudioSource> enabledAudioSources;

    private boolean valid = false;

    public BotConfig(UserInteraction userInteraction) {
        this.userInteraction = userInteraction;
    }

    public void load() {
        valid = false;

        try {
            path = ConfigIO.getConfigPath();
            
            // Load and migrate config
            ConfigLoadResult loadResult = loadAndMigrateConfig();
            
            // Run diagnostics and update config file if needed
            ConfigLoadResult updatedResult = runDiagnosticsAndUpdate(loadResult);
            
            // Load all config values
            loadConfigValues(updatedResult.mergedConfig, updatedResult.migratedUserConfig);

            // Validate required fields and write if needed
            if (!validateRequiredFields()) {
                return;
            }

            valid = true;
        } catch (ConfigException ex) {
            userInteraction.alert(Level.ERROR, "Config",
                    ex + ": " + ex.getMessage() + "\n\nConfig Location: " + path.toAbsolutePath().toString());
        } catch (ConfigMigrationException ex) {
            LOGGER.error("Config migration failed: {}", ex.getMessage());
            userInteraction.alert(Level.ERROR, "Config Migration",
                    "Failed to migrate configuration: " + ex.getMessage() + "\n\nConfig Location: " + path.toAbsolutePath().toString());
        }
    }
    
    /**
     * Loads raw config, detects migration need, and returns migrated + merged configs.
     * Parses each resource only once to avoid redundant I/O.
     */
    private ConfigLoadResult loadAndMigrateConfig() {
        // Parse each resource exactly once
        Config rawUserConfig = ConfigLoader.loadRawUserConfig(path);
        Config defaults = ConfigIO.loadDefaults();
        
        // Detect versions for migration check
        int userVersion = ConfigMigration.detectVersion(rawUserConfig);
        int latestVersion = ConfigMigration.getLatestVersion(defaults);

        // Use overloads that accept already-parsed configs to avoid re-parsing
        Config migratedUserConfig = ConfigLoader.loadMigratedUserConfig(rawUserConfig, defaults);
        Config mergedConfig = ConfigLoader.mergeWithDefaults(migratedUserConfig, defaults);

        return new ConfigLoadResult(migratedUserConfig, mergedConfig, defaults, userVersion, latestVersion);
    }
    
    /**
     * Runs diagnostics and updates config file if migration occurred or issues detected.
     * Returns updated configs if file was modified.
     */
    private ConfigLoadResult runDiagnosticsAndUpdate(ConfigLoadResult loadResult) {
        // Fresh install - skip diagnostics since user will be prompted for required fields
        if (loadResult.migratedUserConfig.isEmpty()) {
            return loadResult;
        }
        
        ConfigDiagnostics.Report diagnostics = ConfigDiagnostics.analyze(
                loadResult.migratedUserConfig, loadResult.mergedConfig, loadResult.defaults);
        
        logDiagnostics(diagnostics);
        
        if (loadResult.migrationOccurred() || diagnostics.hasIssues()) {
            // Determine update type based on original version and diagnostics
            boolean hasMissingKeys = !diagnostics.getMissingRequired().isEmpty() 
                    || !diagnostics.getMissingOptional().isEmpty();
            ConfigUpdateType updateType = ConfigUpdateType.determine(
                    loadResult.originalVersion, loadResult.latestVersion, hasMissingKeys);
            
            Path updatedConfigPath = ConfigUpdater.generateUpdatedConfig(
                    path, loadResult.migratedUserConfig, diagnostics, updateType);
            if (updatedConfigPath != null) {
                logConfigUpdate(updateType, updatedConfigPath);
                
                // Reload configs from the updated file - reuse defaults since they haven't changed
                Config rawUserConfig = ConfigLoader.loadRawUserConfig(path);
                Config migratedUserConfig = ConfigLoader.loadMigratedUserConfig(rawUserConfig, loadResult.defaults);
                Config mergedConfig = ConfigLoader.mergeWithDefaults(migratedUserConfig, loadResult.defaults);
                return new ConfigLoadResult(migratedUserConfig, mergedConfig, 
                        loadResult.defaults, loadResult.originalVersion, loadResult.latestVersion);
            }
        }
        return loadResult;
    }
    
    /**
     * Logs diagnostic issues at appropriate level.
     */
    private void logDiagnostics(ConfigDiagnostics.Report diagnostics) {
        if (diagnostics.hasIssues()) {
            if (diagnostics.hasErrors()) {
                LOGGER.error("Config diagnostics - {}", diagnostics.generateMessage());
            } else if (diagnostics.hasWarnings()) {
                LOGGER.warn("Config diagnostics - {}", diagnostics.generateMessage());
            }
        }
    }
    
    /**
     * Logs config update message with appropriate wording based on update type.
     */
    private void logConfigUpdate(ConfigUpdateType updateType, Path updatedConfigPath) {
        LOGGER.info("Config file {} and updated: {}. Original backed up with .bak extension.", 
                updateType.getPastTenseVerb(), updatedConfigPath);
    }
    
    /**
     * Validates token and owner, prompting user if needed. Returns false if validation fails.
     */
    private boolean validateRequiredFields() {
        ValidationResult tokenResult = ConfigValidator.validateToken(token, userInteraction, path);
        if (!tokenResult.isValid()) {
            return false;
        }
        token = tokenResult.getValue();
        boolean needsWrite = tokenResult.needsWrite();

        ValidationResult ownerResult = ConfigValidator.validateOwner(owner, userInteraction, path);
        if (!ownerResult.isValid()) {
            return false;
        }
        owner = ownerResult.getValue();
        needsWrite = needsWrite || ownerResult.needsWrite();

        if (needsWrite) {
            writeToFile();
        }
        return true;
    }
    
    /**
     * Holds the result of loading and migrating config.
     */
    private static class ConfigLoadResult {
        final Config migratedUserConfig;
        final Config mergedConfig;
        final Config defaults;
        final int originalVersion;
        final int latestVersion;
        
        ConfigLoadResult(Config migratedUserConfig, Config mergedConfig, 
                        Config defaults, int originalVersion, int latestVersion) {
            this.migratedUserConfig = migratedUserConfig;
            this.mergedConfig = mergedConfig;
            this.defaults = defaults;
            this.originalVersion = originalVersion;
            this.latestVersion = latestVersion;
        }
        
        boolean migrationOccurred() {
            return originalVersion < latestVersion;
        }
    }
    
    /**
     * Loads all configuration values from the merged config.
     */
    private void loadConfigValues(Config config, Config migratedUserConfig) {
        // set values using ConfigOption enum for type safety and standardization
        token = TOKEN.getString(config);
        prefix = PREFIX.getString(config);
        // Handle altPrefix null value by defaulting to "NONE"
        altprefix = ALTPREFIX.hasValue(config) ? ALTPREFIX.getString(config) : "NONE";
        helpWord = HELP_WORD.getString(config);
        owner = OWNER.getLong(config);
        successEmoji = SUCCESS_EMOJI.getString(config);
        warningEmoji = WARNING_EMOJI.getString(config);
        errorEmoji = ERROR_EMOJI.getString(config);
        loadingEmoji = LOADING_EMOJI.getString(config);
        searchingEmoji = SEARCHING_EMOJI.getString(config);
        game = OtherUtil.parseGame(GAME.getString(config));
        status = OtherUtil.parseStatus(STATUS.getString(config));
        stayInChannel = STAY_IN_CHANNEL.getBoolean(config);
        songInGame = SONG_IN_GAME.getBoolean(config);
        npImages = NP_IMAGES.getBoolean(config);
        updatealerts = UPDATE_ALERTS.getBoolean(config);
        logLevel = LOG_LEVEL.getString(config);
        useEval = USE_EVAL.getBoolean(config);
        evalEngine = EVAL_ENGINE.getString(config);
        maxSeconds = MAX_SECONDS.getLong(config);
        maxYTPlaylistPages = MAX_YT_PLAYLIST_PAGES.getInt(config);
        maxHistorySize = MAX_HISTORY_SIZE.getInt(config);
        useYouTubeOauth = USE_YOUTUBE_OAUTH.getBoolean(config);
        aloneTimeUntilStop = ALONE_TIME_UNTIL_STOP.getLong(config);
        playlistsFolder = PLAYLISTS_FOLDER.getString(config);
        aliases = ALIASES.getConfig(config);
        transforms = TRANSFORMS.getConfig(config);
        
        // Handle audiosources - pass migrated user config to check which sources were explicitly set
        loadAudioSources(config, migratedUserConfig);
        
        skipratio = SKIP_RATIO.getDouble(config);
        dbots = owner == 113156185389092864L;
    }
    
    /**
     * Loads audio sources configuration.
     * All sources are enabled by default (from reference.conf).
     * Users can disable specific sources by setting them to false.
     */
    private void loadAudioSources(Config config, Config migratedUserConfig) {
        if (AUDIO_SOURCES.hasValue(config)) {
            try {
                Config audioSourcesConfig = AUDIO_SOURCES.getConfig(config);
                Set<AudioSource> enabled = new java.util.LinkedHashSet<>();
                
                // Iterate sources in priority order (platform-specific first, catch-alls last)
                // This ensures the LinkedHashSet maintains the correct registration order
                for (AudioSource source : AudioSource.valuesSortedByPriority()) {
                    String sourceKey = source.getConfigName();
                    if (audioSourcesConfig.hasPath(sourceKey) && audioSourcesConfig.getBoolean(sourceKey)) {
                        enabled.add(source);
                    }
                }
                
                // If no sources ended up enabled (all set to false), enable all sources
                enabledAudioSources = enabled.isEmpty() ? allAudioSourcesInOrder() : enabled;
            } catch (ConfigException e) {
                LOGGER.warn("Failed to parse audioSources config, defaulting to all enabled: {}", e.getMessage());
                enabledAudioSources = allAudioSourcesInOrder();
            }
        } else {
            // Key not found, enable all sources
            enabledAudioSources = allAudioSourcesInOrder();
        }
        
        LOGGER.info("Enabled audio sources: {}", 
                    enabledAudioSources.stream()
                            .map(AudioSource::getConfigName)
                            .collect(Collectors.toList()));
    }
    
    /**
     * Returns all audio sources sorted by registration priority.
     * Platform-specific sources come first, catch-all sources (HTTP, LOCAL) come last.
     */
    private static Set<AudioSource> allAudioSourcesInOrder() {
        return new java.util.LinkedHashSet<>(AudioSource.valuesSortedByPriority());
    }

    private void writeToFile() {
        try {
            String content = ConfigIO.loadDefaultConfig()
                    .replace("BOT_TOKEN_HERE", token)
                    .replace("0 # OWNER ID", Long.toString(owner))
                    .trim();
            ConfigIO.writeConfigFile(path, content);
        } catch (Exception ex) {
            userInteraction.alert(Level.WARNING, "Config", "Failed to write new config options to config.txt: " + ex
                    + "\nPlease make sure that the files are not on your desktop or some other restricted area.\n\nConfig Location: "
                    + path.toAbsolutePath().toString());
        }
    }

    /**
     * Generates a default configuration file.
     * 
     * @param userInteraction The user interaction handler for displaying progress and errors
     */
    public static void writeDefaultConfig(UserInteraction userInteraction) {
        userInteraction.alert(Level.INFO, "JMusicBot Config", "Generating default config file");
        Path path = ConfigIO.getConfigPath();
        try {
            userInteraction.alert(Level.INFO, "JMusicBot Config",
                    "Writing default config file to " + path.toAbsolutePath().toString());
            ConfigIO.writeConfigFile(path, ConfigIO.loadDefaultConfig());
        } catch (Exception ex) {
            userInteraction.alert(Level.ERROR, "JMusicBot Config",
                    "An error occurred writing the default config file: " + ex.getMessage());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public String getConfigLocation() {
        return path.toFile().getAbsolutePath();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAltPrefix() {
        return "NONE".equalsIgnoreCase(altprefix) ? null : altprefix;
    }

    public String getToken() {
        return token;
    }

    public double getSkipRatio() {
        return skipratio;
    }

    public long getOwnerId() {
        return owner;
    }

    public String getSuccess() {
        return successEmoji;
    }

    public String getWarning() {
        return warningEmoji;
    }

    public String getError() {
        return errorEmoji;
    }

    public String getLoading() {
        return loadingEmoji;
    }

    public String getSearching() {
        return searchingEmoji;
    }

    public Activity getGame() {
        return game;
    }

    public boolean isGameNone() {
        return game != null && game.getName().equalsIgnoreCase("none");
    }

    public OnlineStatus getStatus() {
        return status;
    }

    public String getHelp() {
        return helpWord;
    }

    public boolean getStay() {
        return stayInChannel;
    }

    public boolean getSongInStatus() {
        return songInGame;
    }

    public String getPlaylistsFolder() {
        return playlistsFolder;
    }

    public boolean getDBots() {
        return dbots;
    }

    public boolean useUpdateAlerts() {
        return updatealerts;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public boolean useEval() {
        return useEval;
    }

    public String getEvalEngine() {
        return evalEngine;
    }

    public boolean useNPImages() {
        return npImages;
    }

    public long getMaxSeconds() {
        return maxSeconds;
    }

    public int getMaxYTPlaylistPages() {
        return maxYTPlaylistPages;
    }

    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    public boolean useYouTubeOauth() {
        return useYouTubeOauth;
    }

    public String getMaxTime() {
        return TimeUtil.formatTime(maxSeconds * 1000);
    }

    public long getAloneTimeUntilStop() {
        return aloneTimeUntilStop;
    }

    public boolean isTooLong(AudioTrack track) {
        if (maxSeconds <= 0)
            return false;
        return Math.round(track.getDuration() / 1000.0) > maxSeconds;
    }

    public String[] getAliases(String command) {
        try {
            return aliases.getStringList(command).toArray(new String[0]);
        } catch (NullPointerException | ConfigException.Missing e) {
            return new String[0];
        }
    }

    public Config getTransforms() {
        return transforms;
    }

    public Set<AudioSource> getEnabledAudioSources() {
        return enabledAudioSources;
    }

    public boolean isAudioSourceEnabled(AudioSource source) {
        // If the set is empty, no sources are enabled
        if (enabledAudioSources.isEmpty())
            return false;
        return enabledAudioSources.contains(source);
    }
}
