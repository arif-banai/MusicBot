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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.gui.model.BotStatusData;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.util.List;

/**
 * Panel displaying connected guilds with detailed view on selection.
 * Shows guild list on left, details on right when a guild is selected.
 *
 * @author Arif Banai (arif-banai)
 */
public class StatusPanel extends JPanel {
    
    private final Bot bot;
    private final DefaultListModel<GuildListItem> guildListModel;
    private final JList<GuildListItem> guildList;
    private final JPanel detailsPanel;
    private final JLabel noSelectionLabel;
    
    // Detail labels
    private final JLabel guildNameLabel;
    private final JLabel guildIdLabel;
    private final JLabel memberCountLabel;
    private final JLabel voiceStatusLabel;
    private final JLabel voiceChannelLabel;
    private final JLabel nowPlayingLabel;
    private final JLabel queueSizeLabel;
    private final JLabel volumeLabel;
    private final JLabel repeatModeLabel;
    
    private List<Guild> currentGuilds = List.of();
    
    public StatusPanel(Bot bot) {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        
        this.bot = bot;
        
        // Initialize guild list
        guildListModel = new DefaultListModel<>();
        guildList = new JList<>(guildListModel);
        guildList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        guildList.setCellRenderer(new GuildListCellRenderer());
        guildList.addListSelectionListener(this::onGuildSelected);
        
        JScrollPane listScrollPane = new JScrollPane(guildList);
        listScrollPane.setBorder(new TitledBorder("Connected Guilds"));
        listScrollPane.setPreferredSize(new Dimension(250, 0));
        
        // Initialize detail labels
        guildNameLabel = new JLabel("-");
        guildIdLabel = new JLabel("-");
        memberCountLabel = new JLabel("-");
        voiceStatusLabel = new JLabel("-");
        voiceChannelLabel = new JLabel("-");
        nowPlayingLabel = new JLabel("-");
        queueSizeLabel = new JLabel("-");
        volumeLabel = new JLabel("-");
        repeatModeLabel = new JLabel("-");
        
        // Create details panel
        detailsPanel = createDetailsPanel();
        detailsPanel.setVisible(false);
        
        // No selection label
        noSelectionLabel = new JLabel("Select a guild to view details", SwingConstants.CENTER);
        noSelectionLabel.setForeground(Color.GRAY);
        noSelectionLabel.setFont(noSelectionLabel.getFont().deriveFont(Font.ITALIC, 14f));
        
        // Right panel with card layout for switching between no-selection and details
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new TitledBorder("Guild Details"));
        rightPanel.add(noSelectionLabel, BorderLayout.CENTER);
        
        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScrollPane, rightPanel);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.3);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    /**
     * Creates the details panel with all guild information fields.
     */
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(4, 4, 4, 8);
        labelGbc.gridx = 0;
        
        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.anchor = GridBagConstraints.WEST;
        valueGbc.insets = new Insets(4, 0, 4, 4);
        valueGbc.gridx = 1;
        valueGbc.weightx = 1.0;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // Guild Info Section
        addSectionHeader(panel, "Guild Information", row++, labelGbc);
        addDetailRow(panel, "Name:", guildNameLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "ID:", guildIdLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "Members:", memberCountLabel, row++, labelGbc, valueGbc);
        
        // Voice Section
        row++; // spacer
        addSectionHeader(panel, "Voice Status", row++, labelGbc);
        addDetailRow(panel, "Status:", voiceStatusLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "Channel:", voiceChannelLabel, row++, labelGbc, valueGbc);
        
        // Playback Section
        row++; // spacer
        addSectionHeader(panel, "Playback", row++, labelGbc);
        addDetailRow(panel, "Now Playing:", nowPlayingLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "Queue Size:", queueSizeLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "Volume:", volumeLabel, row++, labelGbc, valueGbc);
        addDetailRow(panel, "Repeat:", repeatModeLabel, row++, labelGbc, valueGbc);
        
        // Filler to push content to top
        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.gridx = 0;
        fillerGbc.gridy = row;
        fillerGbc.weighty = 1.0;
        fillerGbc.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), fillerGbc);
        
        return panel;
    }
    
    private void addSectionHeader(JPanel panel, String text, int row, GridBagConstraints gbc) {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panel.add(header, gbc);
        gbc.gridwidth = 1;
    }
    
    private void addDetailRow(JPanel panel, String labelText, JLabel valueLabel, int row,
                              GridBagConstraints labelGbc, GridBagConstraints valueGbc) {
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.GRAY);
        
        labelGbc.gridy = row;
        valueGbc.gridy = row;
        
        panel.add(label, labelGbc);
        panel.add(valueLabel, valueGbc);
    }
    
    /**
     * Handles guild selection from the list.
     */
    private void onGuildSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        
        GuildListItem selected = guildList.getSelectedValue();
        if (selected == null) {
            showNoSelection();
            return;
        }
        
        updateDetailsForGuild(selected.guild());
    }
    
    /**
     * Shows the "no selection" state.
     */
    private void showNoSelection() {
        Container parent = detailsPanel.getParent();
        if (parent != null) {
            parent.remove(detailsPanel);
            parent.add(noSelectionLabel, BorderLayout.CENTER);
            parent.revalidate();
            parent.repaint();
        }
    }
    
    /**
     * Shows detailed information for the selected guild.
     */
    private void updateDetailsForGuild(Guild guild) {
        // Update labels
        guildNameLabel.setText(guild.getName());
        guildIdLabel.setText(guild.getId());
        memberCountLabel.setText(String.valueOf(guild.getMemberCount()));
        
        // Voice status
        boolean inVoice = guild.getAudioManager().isConnected();
        if (inVoice) {
            voiceStatusLabel.setText("Connected");
            voiceStatusLabel.setForeground(new Color(46, 204, 113));
            AudioChannel channel = guild.getAudioManager().getConnectedChannel();
            voiceChannelLabel.setText(channel != null ? channel.getName() : "Unknown");
        } else {
            voiceStatusLabel.setText("Not Connected");
            voiceStatusLabel.setForeground(Color.GRAY);
            voiceChannelLabel.setText("-");
        }
        
        // Playback info
        updatePlaybackInfo(guild);
        
        // Show details panel
        Container parent = noSelectionLabel.getParent();
        if (parent != null) {
            parent.remove(noSelectionLabel);
            parent.add(detailsPanel, BorderLayout.CENTER);
            detailsPanel.setVisible(true);
            parent.revalidate();
            parent.repaint();
        }
    }
    
    /**
     * Updates playback information for a guild.
     */
    private void updatePlaybackInfo(Guild guild) {
        try {
            var sendingHandler = guild.getAudioManager().getSendingHandler();
            if (!(sendingHandler instanceof AudioHandler handler)) {
                setNoPlaybackInfo();
                return;
            }
            
            var player = handler.getPlayer();
            var track = player != null ? player.getPlayingTrack() : null;
            
            if (track == null) {
                nowPlayingLabel.setText("Nothing playing");
                nowPlayingLabel.setForeground(Color.GRAY);
            } else {
                String title = track.getInfo().title;
                if (title.length() > 40) {
                    title = title.substring(0, 37) + "...";
                }
                nowPlayingLabel.setText(title);
                nowPlayingLabel.setForeground(null); // default color
            }
            
            // Queue size
            int queueSize = handler.getQueue().size();
            queueSizeLabel.setText(queueSize + " track" + (queueSize != 1 ? "s" : ""));
            
            // Volume
            int volume = player != null ? player.getVolume() : 100;
            volumeLabel.setText(volume + "%");
            
            // Repeat mode - fetched from settings manager
            var repeatMode = bot.getSettingsManager().getSettings(guild).getRepeatMode();
            repeatModeLabel.setText(repeatMode != null ? repeatMode.getUserFriendlyName() : "Off");
            
        } catch (Exception e) {
            setNoPlaybackInfo();
        }
    }
    
    private void setNoPlaybackInfo() {
        nowPlayingLabel.setText("-");
        nowPlayingLabel.setForeground(Color.GRAY);
        queueSizeLabel.setText("-");
        volumeLabel.setText("-");
        repeatModeLabel.setText("-");
    }
    
    /**
     * Updates the panel with shared status data from MainFrame.
     *
     * @param statusData the shared status data
     */
    public void updateStatus(BotStatusData statusData) {
        this.currentGuilds = statusData.guilds();
        
        // Remember selected guild
        GuildListItem selected = guildList.getSelectedValue();
        String selectedId = selected != null ? selected.guild().getId() : null;
        
        // Update guild list
        guildListModel.clear();
        for (Guild guild : currentGuilds) {
            boolean inVoice = guild.getAudioManager().isConnected();
            guildListModel.addElement(new GuildListItem(guild, inVoice));
        }
        
        // Restore selection if still exists
        if (selectedId != null) {
            for (int i = 0; i < guildListModel.size(); i++) {
                if (guildListModel.get(i).guild().getId().equals(selectedId)) {
                    guildList.setSelectedIndex(i);
                    // Update details for selected guild
                    updateDetailsForGuild(guildListModel.get(i).guild());
                    break;
                }
            }
        }
    }
    
    /**
     * Record to hold guild info for the list.
     */
    private record GuildListItem(Guild guild, boolean inVoice) {
        @Override
        public String toString() {
            return guild.getName();
        }
    }
    
    /**
     * Custom cell renderer for guild list items.
     */
    private static class GuildListCellRenderer extends DefaultListCellRenderer {
        private static final Color VOICE_ACTIVE_COLOR = new Color(46, 204, 113);
        
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof GuildListItem item) {
                setText(item.guild().getName());
                if (item.inVoice() && !isSelected) {
                    setForeground(VOICE_ACTIVE_COLOR);
                }
                // Add voice indicator
                if (item.inVoice()) {
                    setText("♪ " + item.guild().getName());
                }
            }
            
            return this;
        }
    }
}
