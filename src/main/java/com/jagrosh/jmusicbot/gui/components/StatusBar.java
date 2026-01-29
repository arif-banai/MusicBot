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
package com.jagrosh.jmusicbot.gui.components;

import com.jagrosh.jmusicbot.gui.model.BotStatusData;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Status bar component that displays connection status and bot statistics.
 * Shows at the bottom of the main frame.
 *
 * @author Arif Banai (arif-banai)
 */
public class StatusBar extends JPanel {
    
    private final JLabel connectionLabel;
    private final JLabel guildsLabel;
    private final JLabel voiceLabel;
    private final JLabel uptimeLabel;
    private final JLabel memoryLabel;
    
    private static final Color CONNECTED_COLOR = new Color(46, 204, 113);  // Green
    private static final Color DISCONNECTED_COLOR = new Color(231, 76, 60); // Red
    
    public StatusBar() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(4, 8, 4, 8));
        
        // Left section - connection status
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        connectionLabel = createStatusLabel("Disconnected");
        guildsLabel = createStatusLabel("Guilds: 0");
        voiceLabel = createStatusLabel("Voice: 0");
        uptimeLabel = createStatusLabel("Uptime: --");
        
        leftPanel.add(connectionLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(guildsLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(voiceLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(uptimeLabel);
        
        // Right section - memory usage
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        memoryLabel = createStatusLabel("Memory: --");
        rightPanel.add(memoryLabel);
        
        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
        
        // Start memory monitoring
        startMemoryMonitor();
    }
    
    private JLabel createStatusLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }
    
    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 16));
        return separator;
    }
    
    /**
     * Updates the status bar with current bot status data.
     *
     * @param statusData the shared status data
     * @param uptimeString formatted uptime string
     */
    public void updateStatus(BotStatusData statusData, String uptimeString) {
        if (statusData.connected()) {
            connectionLabel.setText("Connected");
            connectionLabel.setForeground(CONNECTED_COLOR);
        } else {
            connectionLabel.setText("Disconnected");
            connectionLabel.setForeground(DISCONNECTED_COLOR);
        }
        
        guildsLabel.setText("Guilds: " + statusData.guildCount());
        voiceLabel.setText("Voice: " + statusData.voiceConnections());
        uptimeLabel.setText("Uptime: " + uptimeString);
    }
    
    /**
     * Starts a timer to periodically update memory usage.
     */
    private void startMemoryMonitor() {
        Timer memoryTimer = new Timer(5000, e -> updateMemoryUsage());
        memoryTimer.setInitialDelay(0);
        memoryTimer.start();
    }
    
    /**
     * Updates the memory usage display.
     */
    private void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        memoryLabel.setText(String.format("Memory: %d/%d MB", usedMemory, maxMemory));
    }
}
