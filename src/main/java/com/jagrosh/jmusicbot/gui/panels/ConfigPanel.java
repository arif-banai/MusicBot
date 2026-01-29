/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.gui.panels;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.update.ConfigUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration panel for viewing and editing bot configuration.
 * Allows editing of safe configuration options and saving to config.txt.
 * Sensitive options (token, owner) and dangerous options (eval) are excluded.
 *
 * @author Arif Banai (arif-banai)
 */
public class ConfigPanel extends JPanel {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConfigPanel.class);
    
    private final BotConfig config;
    
    // Commands section
    private final JTextField prefixField;
    private final JTextField altPrefixField;
    private final JTextField helpWordField;
    
    // Presence section
    private final JTextField gameField;
    private final JComboBox<String> statusComboBox;
    private final JCheckBox songInStatusCheckBox;
    
    // Voice section
    private final JCheckBox stayInChannelCheckBox;
    private final JSpinner aloneTimeSpinner;
    
    // Playback section
    private final JSpinner maxSecondsSpinner;
    private final JSpinner maxYTPlaylistPagesSpinner;
    private final JSpinner skipRatioSpinner;
    private final JCheckBox useYouTubeOAuthCheckBox;
    
    // UI/Emojis section
    private final JTextField successEmojiField;
    private final JTextField warningEmojiField;
    private final JTextField errorEmojiField;
    private final JTextField loadingEmojiField;
    private final JTextField searchingEmojiField;
    
    // Other section
    private final JCheckBox npImagesCheckBox;
    private final JCheckBox updateAlertsCheckBox;
    private final JComboBox<String> logLevelComboBox;
    private final JTextField playlistsFolderField;
    
    /**
     * Creates the configuration panel.
     *
     * @param bot the bot instance
     */
    public ConfigPanel(Bot bot) {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        
        this.config = bot.getConfig();
        
        // Initialize all input components
        // Commands
        prefixField = new JTextField(15);
        altPrefixField = new JTextField(15);
        helpWordField = new JTextField(15);
        
        // Presence
        gameField = new JTextField(20);
        statusComboBox = new JComboBox<>(new String[]{"ONLINE", "IDLE", "DND", "INVISIBLE"});
        songInStatusCheckBox = new JCheckBox("Show current song in status");
        
        // Voice
        stayInChannelCheckBox = new JCheckBox("Stay in voice channel after queue ends");
        aloneTimeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3600, 1));
        
        // Playback
        maxSecondsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 86400, 60));
        maxYTPlaylistPagesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        skipRatioSpinner = new JSpinner(new SpinnerNumberModel(0.55, 0.0, 1.0, 0.05));
        useYouTubeOAuthCheckBox = new JCheckBox("Use YouTube OAuth for playback");
        
        // UI/Emojis
        successEmojiField = new JTextField(5);
        warningEmojiField = new JTextField(5);
        errorEmojiField = new JTextField(5);
        loadingEmojiField = new JTextField(5);
        searchingEmojiField = new JTextField(5);
        
        // Other
        npImagesCheckBox = new JCheckBox("Show YouTube thumbnails in now playing");
        updateAlertsCheckBox = new JCheckBox("Alert owner about updates");
        logLevelComboBox = new JComboBox<>(new String[]{"off", "error", "warn", "info", "debug", "trace", "all"});
        playlistsFolderField = new JTextField(20);
        
        // Build UI
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        contentPanel.add(createCommandsSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createPresenceSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createVoiceSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createPlaybackSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createEmojisSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(createOtherSection());
        contentPanel.add(Box.createVerticalGlue());
        
        // Scroll pane for content
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        
        // Bottom panel with buttons and warning
        add(createBottomPanel(), BorderLayout.SOUTH);
        
        // Load current values
        loadCurrentValues();
    }
    
    /**
     * Creates the Commands configuration section.
     */
    private JPanel createCommandsSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Commands"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Prefix and Alt Prefix
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Prefix:"), gbc);
        gbc.gridx = 1;
        panel.add(prefixField, gbc);
        
        gbc.gridx = 2;
        panel.add(new JLabel("Alt Prefix:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        panel.add(altPrefixField, gbc);
        
        // Row 1: Help Word
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Help Word:"), gbc);
        gbc.gridx = 1;
        panel.add(helpWordField, gbc);
        
        return panel;
    }
    
    /**
     * Creates the Presence configuration section.
     */
    private JPanel createPresenceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Presence"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Game Status
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Game Status:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(gameField, gbc);
        
        // Row 1: Online Status and Song in Status
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Online Status:"), gbc);
        gbc.gridx = 1;
        panel.add(statusComboBox, gbc);
        
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(songInStatusCheckBox, gbc);
        
        return panel;
    }
    
    /**
     * Creates the Voice configuration section.
     */
    private JPanel createVoiceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Voice"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Stay in Channel
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(stayInChannelCheckBox, gbc);
        
        // Row 1: Alone Time
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Alone Time Until Stop (seconds):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(aloneTimeSpinner, gbc);
        
        return panel;
    }
    
    /**
     * Creates the Playback configuration section.
     */
    private JPanel createPlaybackSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Playback"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Max Seconds and Skip Ratio
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Max Track Seconds (0=unlimited):"), gbc);
        gbc.gridx = 1;
        panel.add(maxSecondsSpinner, gbc);
        
        gbc.gridx = 2;
        panel.add(new JLabel("Skip Ratio:"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        panel.add(skipRatioSpinner, gbc);
        
        // Row 1: Max YT Playlist Pages
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Max YouTube Playlist Pages:"), gbc);
        gbc.gridx = 1;
        panel.add(maxYTPlaylistPagesSpinner, gbc);
        
        // Row 2: YouTube OAuth
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 4;
        panel.add(useYouTubeOAuthCheckBox, gbc);
        
        return panel;
    }
    
    /**
     * Creates the UI/Emojis configuration section.
     */
    private JPanel createEmojisSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("UI / Emojis"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Success, Warning, Error
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Success:"), gbc);
        gbc.gridx = 1;
        panel.add(successEmojiField, gbc);
        
        gbc.gridx = 2;
        panel.add(new JLabel("Warning:"), gbc);
        gbc.gridx = 3;
        panel.add(warningEmojiField, gbc);
        
        gbc.gridx = 4;
        panel.add(new JLabel("Error:"), gbc);
        gbc.gridx = 5;
        gbc.weightx = 1.0;
        panel.add(errorEmojiField, gbc);
        
        // Row 1: Loading, Searching
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Loading:"), gbc);
        gbc.gridx = 1;
        panel.add(loadingEmojiField, gbc);
        
        gbc.gridx = 2;
        panel.add(new JLabel("Searching:"), gbc);
        gbc.gridx = 3;
        panel.add(searchingEmojiField, gbc);
        
        return panel;
    }
    
    /**
     * Creates the Other configuration section.
     */
    private JPanel createOtherSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Other"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: NP Images, Update Alerts
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(npImagesCheckBox, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(updateAlertsCheckBox, gbc);
        
        // Row 1: Log Level
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Log Level:"), gbc);
        gbc.gridx = 1;
        panel.add(logLevelComboBox, gbc);
        
        // Row 2: Playlists Folder
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Playlists Folder:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(playlistsFolderField, gbc);
        
        return panel;
    }
    
    /**
     * Creates the bottom panel with save/reset buttons and warning.
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        
        JButton saveButton = new JButton("Save Changes");
        saveButton.addActionListener(e -> saveConfiguration());
        buttonPanel.add(saveButton);
        
        JButton resetButton = new JButton("Reset to Current");
        resetButton.addActionListener(e -> loadCurrentValues());
        buttonPanel.add(resetButton);
        
        panel.add(buttonPanel, BorderLayout.CENTER);
        
        // Warning label
        JLabel warningLabel = new JLabel("<html><i>Note: Changes require bot restart to take effect.</i></html>");
        warningLabel.setFont(warningLabel.getFont().deriveFont(10f));
        warningLabel.setForeground(Color.GRAY);
        warningLabel.setBorder(new EmptyBorder(4, 8, 0, 8));
        panel.add(warningLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Loads current configuration values into the input fields.
     */
    private void loadCurrentValues() {
        // Commands
        prefixField.setText(config.getPrefix());
        String altPrefix = config.getAltPrefix();
        altPrefixField.setText(altPrefix != null ? altPrefix : "NONE");
        helpWordField.setText(config.getHelp());
        
        // Presence
        String gameStatus = config.isGameNone() ? "NONE" : 
            (config.getGame() != null ? config.getGame().getName() : "DEFAULT");
        gameField.setText(gameStatus);
        statusComboBox.setSelectedItem(config.getStatus().name());
        songInStatusCheckBox.setSelected(config.getSongInStatus());
        
        // Voice
        stayInChannelCheckBox.setSelected(config.getStay());
        aloneTimeSpinner.setValue((int) config.getAloneTimeUntilStop());
        
        // Playback
        maxSecondsSpinner.setValue((int) config.getMaxSeconds());
        maxYTPlaylistPagesSpinner.setValue(config.getMaxYTPlaylistPages());
        skipRatioSpinner.setValue(config.getSkipRatio());
        useYouTubeOAuthCheckBox.setSelected(config.useYouTubeOauth());
        
        // UI/Emojis
        successEmojiField.setText(config.getSuccess());
        warningEmojiField.setText(config.getWarning());
        errorEmojiField.setText(config.getError());
        loadingEmojiField.setText(config.getLoading());
        searchingEmojiField.setText(config.getSearching());
        
        // Other
        npImagesCheckBox.setSelected(config.useNPImages());
        updateAlertsCheckBox.setSelected(config.useUpdateAlerts());
        logLevelComboBox.setSelectedItem(config.getLogLevel());
        playlistsFolderField.setText(config.getPlaylistsFolder());
    }
    
    /**
     * Saves the configuration to the config file.
     */
    private void saveConfiguration() {
        try {
            Path configPath = ConfigIO.getConfigPath();
            
            // Read current config file
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            
            // Build updates map
            Map<String, String> updates = buildUpdatesMap();
            
            // Apply updates to config content
            String updatedContent = applyConfigUpdates(content, updates);
            
            // Create backup using the same resolution logic as config migration
            Path backupPath = ConfigUpdater.findAvailableBackupPath(configPath);
            Files.copy(configPath, backupPath);
            
            // Write updated config
            ConfigIO.writeConfigFile(configPath, updatedContent);
            
            LOG.info("Configuration saved successfully to {}", configPath);
            
            JOptionPane.showMessageDialog(
                this,
                "Configuration saved successfully!\n\nPlease restart the bot for changes to take effect.\n\nBackup created: " + backupPath.getFileName(),
                "Configuration Saved",
                JOptionPane.INFORMATION_MESSAGE
            );
            
        } catch (IOException ex) {
            LOG.error("Failed to save configuration", ex);
            JOptionPane.showMessageDialog(
                this,
                "Failed to save configuration: " + ex.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Builds a map of config keys to their new values.
     */
    private Map<String, String> buildUpdatesMap() {
        Map<String, String> updates = new HashMap<>();
        
        // Commands
        updates.put("commands.prefix", quoteString(prefixField.getText()));
        updates.put("commands.altPrefix", quoteString(altPrefixField.getText()));
        updates.put("commands.help", quoteString(helpWordField.getText()));
        
        // Presence
        updates.put("presence.game", quoteString(gameField.getText()));
        updates.put("presence.status", quoteString((String) statusComboBox.getSelectedItem()));
        updates.put("presence.songInStatus", String.valueOf(songInStatusCheckBox.isSelected()));
        
        // Voice
        updates.put("voice.stayInChannel", String.valueOf(stayInChannelCheckBox.isSelected()));
        updates.put("voice.aloneTimeUntilStopSeconds", String.valueOf(aloneTimeSpinner.getValue()));
        
        // Playback
        updates.put("playback.maxTrackSeconds", String.valueOf(maxSecondsSpinner.getValue()));
        updates.put("playback.maxYouTubePlaylistPages", String.valueOf(maxYTPlaylistPagesSpinner.getValue()));
        updates.put("playback.skipRatio", String.valueOf(skipRatioSpinner.getValue()));
        updates.put("playback.youtube.useOAuth", String.valueOf(useYouTubeOAuthCheckBox.isSelected()));
        
        // UI/Emojis
        updates.put("ui.emojis.success", quoteString(successEmojiField.getText()));
        updates.put("ui.emojis.warning", quoteString(warningEmojiField.getText()));
        updates.put("ui.emojis.error", quoteString(errorEmojiField.getText()));
        updates.put("ui.emojis.loading", quoteString(loadingEmojiField.getText()));
        updates.put("ui.emojis.searching", quoteString(searchingEmojiField.getText()));
        
        // Other
        updates.put("nowPlaying.images", String.valueOf(npImagesCheckBox.isSelected()));
        updates.put("updates.alerts", String.valueOf(updateAlertsCheckBox.isSelected()));
        updates.put("logging.level", quoteString((String) logLevelComboBox.getSelectedItem()));
        updates.put("paths.playlistsFolder", quoteString(playlistsFolderField.getText()));
        
        return updates;
    }
    
    /**
     * Quotes a string value for HOCON format.
     */
    private String quoteString(String value) {
        if (value == null) {
            return "\"\"";
        }
        // Escape backslashes and quotes
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
    
    /**
     * Applies config updates to the content using regex pattern matching.
     * Handles both nested (e.g., presence.status) and flat key formats.
     */
    private String applyConfigUpdates(String content, Map<String, String> updates) {
        String result = content;
        
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Extract the leaf key (e.g., "status" from "presence.status")
            String leafKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
            
            // Try to find and replace the value in the config
            // Match pattern: leafKey = value (with optional quotes and whitespace)
            String pattern = "(?m)^(\\s*" + java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = regex.matcher(result);
            
            // Find the right occurrence based on context (section)
            String section = key.contains(".") ? key.substring(0, key.indexOf('.')) : null;
            
            if (section != null) {
                // Find section and update within it
                result = updateKeyInSection(result, section, leafKey, value);
            } else {
                // Simple replacement for top-level keys
                result = matcher.replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(value));
            }
        }
        
        return result;
    }
    
    /**
     * Updates a key within a specific section of the config.
     */
    private String updateKeyInSection(String content, String section, String leafKey, String value) {
        // Handle nested sections like "playback.youtube.useOAuth"
        String[] sectionParts = section.split("\\.");
        
        // Find the section start
        StringBuilder sectionPattern = new StringBuilder();
        for (String part : sectionParts) {
            sectionPattern.append("(?s).*?").append(java.util.regex.Pattern.quote(part)).append("\\s*\\{");
        }
        
        java.util.regex.Pattern sectionRegex = java.util.regex.Pattern.compile(
            "(?s)(" + java.util.regex.Pattern.quote(sectionParts[sectionParts.length - 1]) + "\\s*\\{[^}]*?" + 
            java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)"
        );
        
        java.util.regex.Matcher matcher = sectionRegex.matcher(content);
        
        if (matcher.find()) {
            return matcher.replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(value));
        }
        
        // Fallback: simple pattern match for the key
        String pattern = "(?m)^(\\s*" + java.util.regex.Pattern.quote(leafKey) + "\\s*=\\s*)([^\r\n]+)";
        return content.replaceFirst(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(value));
    }
}
