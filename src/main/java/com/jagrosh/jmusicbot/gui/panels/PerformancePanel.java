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
import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics;
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.*;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSample;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSnapshot;
import net.dv8tion.jda.api.entities.Guild;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Panel for visualizing audio processing performance.
 * Uses JSplitPane for resizable graphs vs stats/events.
 *
 * @author Arif Banai (arif-banai)
 */
public class PerformancePanel extends JPanel {
    
    private static final Color GOOD_COLOR = new Color(46, 204, 113);
    private static final Color WARNING_COLOR = new Color(241, 196, 15);
    private static final Color CRITICAL_COLOR = new Color(231, 76, 60);
    private static final Color GRAPH_BG = new Color(25, 25, 30);
    private static final Color DRIFT_COLOR = new Color(241, 196, 15); // Yellow/amber for drift
    
    private final Bot bot;
    private final JComboBox<GuildItem> guildSelector;
    private final JComboBox<WindowOption> windowSelector;
    
    // Visualization panels
    private final EnhancedTimelinePanel enhancedTimeline;
    private final LatencyGraphPanel latencyGraph;
    private final SchedulerDriftPanel driftChart;
    
    // Guild overview table
    private final JTable guildOverviewTable;
    private final DefaultTableModel guildOverviewModel;
    
    // Stats labels
    private final JLabel healthLabel;
    private final JLabel qualityScoreLabel;
    private final JLabel durationLabel;
    private final JLabel framesLabel;
    private final JLabel missedLabel;
    private final JLabel missRateLabel;
    private final JLabel latencyLabel;
    private final JLabel fpsLabel;
    private final JLabel stuttersLabel;
    private final JLabel stuckLabel;
    private final JLabel gcCountLabel;
    private final JLabel ttffLabel;
    private final JLabel missRateWindowsLabel;
    
    // Event table
    private final JTable eventTable;
    private final DefaultTableModel eventTableModel;
    
    private MetricsSnapshot currentSnapshot;
    private HealthSnapshot healthSnapshot;
    private int selectedWindowSeconds = 30;
    
    private enum WindowOption {
        SECONDS_10(10, "10 seconds"),
        SECONDS_30(30, "30 seconds"),
        MINUTE_1(60, "1 minute"),
        MINUTE_2(120, "2 minutes");
        
        private final int seconds;
        private final String display;
        
        WindowOption(int seconds, String display) {
            this.seconds = seconds;
            this.display = display;
        }
        
        public int getSeconds() { return seconds; }
        
        @Override
        public String toString() { return display; }
    }
    
    public PerformancePanel(Bot bot) {
        super(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        this.bot = bot;
        
        // === TOP: Controls ===
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        
        topPanel.add(new JLabel("Guild:"));
        guildSelector = new JComboBox<>();
        guildSelector.setPreferredSize(new Dimension(130, 22));
        guildSelector.addActionListener(e -> refreshMetrics());
        topPanel.add(guildSelector);
        
        topPanel.add(new JLabel("Window:"));
        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setSelectedItem(WindowOption.SECONDS_30);
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });
        topPanel.add(windowSelector);
        
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshGuildList());
        topPanel.add(refreshBtn);
        
        topPanel.add(Box.createHorizontalStrut(8));
        healthLabel = new JLabel("●");
        healthLabel.setFont(healthLabel.getFont().deriveFont(Font.BOLD, 16f));
        topPanel.add(healthLabel);
        
        qualityScoreLabel = new JLabel("Quality: --%");
        qualityScoreLabel.setFont(qualityScoreLabel.getFont().deriveFont(Font.BOLD, 12f));
        topPanel.add(qualityScoreLabel);
        
        topPanel.add(Box.createHorizontalStrut(12));
        
        JButton exportJsonBtn = new JButton("Export JSON");
        exportJsonBtn.setToolTipText("Export diagnostics to JSON file");
        exportJsonBtn.addActionListener(e -> exportToJson());
        topPanel.add(exportJsonBtn);
        
        JButton copySummaryBtn = new JButton("Copy Summary");
        copySummaryBtn.setToolTipText("Copy summary to clipboard for Discord");
        copySummaryBtn.addActionListener(e -> copyToClipboard());
        topPanel.add(copySummaryBtn);
        
        add(topPanel, BorderLayout.NORTH);
        
        // === GUILD OVERVIEW TABLE (Collapsible) ===
        String[] overviewCols = {"Guild", "Status", "Quality", "Miss Rate", "Stutters", "Stuck", "TTFF"};
        guildOverviewModel = new DefaultTableModel(overviewCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        guildOverviewTable = new JTable(guildOverviewModel);
        guildOverviewTable.setRowHeight(18);
        guildOverviewTable.setFillsViewportHeight(true);
        guildOverviewTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        guildOverviewTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        guildOverviewTable.getColumnModel().getColumn(2).setPreferredWidth(55);
        guildOverviewTable.getColumnModel().getColumn(3).setPreferredWidth(65);
        guildOverviewTable.getColumnModel().getColumn(4).setPreferredWidth(55);
        guildOverviewTable.getColumnModel().getColumn(5).setPreferredWidth(45);
        guildOverviewTable.getColumnModel().getColumn(6).setPreferredWidth(50);
        
        // Select guild when row is clicked
        guildOverviewTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = guildOverviewTable.getSelectedRow();
                if (row >= 0 && row < guildSelector.getItemCount()) {
                    guildSelector.setSelectedIndex(row);
                }
            }
        });
        
        JScrollPane overviewScroll = new JScrollPane(guildOverviewTable);
        overviewScroll.setBorder(new TitledBorder("Guild Overview (click to select)"));
        overviewScroll.setPreferredSize(new Dimension(600, 55)); // More compact
        overviewScroll.setMinimumSize(new Dimension(300, 45));
        overviewScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        // === GRAPHS PANEL (GridBagLayout for weighted rows) ===
        JPanel graphsContent = new JPanel(new GridBagLayout());
        graphsContent.setMinimumSize(new Dimension(500, 200));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(2, 0, 2, 0);
        
        // Row 0: Enhanced Timeline (weight 2.0 - primary visualization)
        enhancedTimeline = new EnhancedTimelinePanel();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.weighty = 2.0;
        graphsContent.add(enhancedTimeline, gbc);
        
        // Row 1: Latency Graph (full width, weight 1.0)
        latencyGraph = new LatencyGraphPanel();
        JPanel latencyContainer = createGraphContainer("Latency", latencyGraph);
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        graphsContent.add(latencyContainer, gbc);
        
        // Row 2: Scheduler Drift (full width, weight 1.0)
        driftChart = new SchedulerDriftPanel();
        JPanel driftContainer = createGraphContainer("Scheduler Drift", driftChart);
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        graphsContent.add(driftContainer, gbc);
        
        JScrollPane graphScroll = new JScrollPane(graphsContent);
        graphScroll.setBorder(null);
        graphScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        graphScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        graphScroll.getHorizontalScrollBar().setUnitIncrement(16);
        graphScroll.getVerticalScrollBar().setUnitIncrement(16);
        graphScroll.setMinimumSize(new Dimension(200, 150));
        
        // === BOTTOM: Stats + Events with resizable split ===
        
        // Stats - 6 rows x 4 columns inside single border
        JPanel statsInner = new JPanel(new GridLayout(6, 4, 8, 2));
        
        durationLabel = createStatLabel("--");
        framesLabel = createStatLabel("--");
        missedLabel = createStatLabel("--");
        missRateLabel = createStatLabel("--%");
        latencyLabel = createStatLabel("-- ms");
        fpsLabel = createStatLabel("-- fps");
        stuttersLabel = createStatLabel("--");
        stuckLabel = createStatLabel("--");
        gcCountLabel = createStatLabel("--");
        ttffLabel = createStatLabel("--");
        missRateWindowsLabel = createStatLabel("--");
        
        // Row 1
        statsInner.add(createFixedLabel("Duration:"));
        statsInner.add(durationLabel);
        statsInner.add(createFixedLabel("Latency:"));
        statsInner.add(latencyLabel);
        // Row 2
        statsInner.add(createFixedLabel("Frames:"));
        statsInner.add(framesLabel);
        statsInner.add(createFixedLabel("Miss Rate:"));
        statsInner.add(missRateLabel);
        // Row 3
        statsInner.add(createFixedLabel("Missed:"));
        statsInner.add(missedLabel);
        statsInner.add(createFixedLabel("Stutters:"));
        statsInner.add(stuttersLabel);
        // Row 4
        statsInner.add(createFixedLabel("Rate:"));
        statsInner.add(fpsLabel);
        statsInner.add(createFixedLabel("Stuck:"));
        statsInner.add(stuckLabel);
        // Row 5
        statsInner.add(createFixedLabel("GC Events:"));
        statsInner.add(gcCountLabel);
        statsInner.add(createFixedLabel("TTFF:"));
        statsInner.add(ttffLabel);
        // Row 6
        statsInner.add(createFixedLabel("Miss 10s/60s:"));
        statsInner.add(missRateWindowsLabel);
        statsInner.add(new JLabel()); // Empty cell
        statsInner.add(new JLabel()); // Empty cell
        
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(new TitledBorder("Statistics"));
        statsPanel.add(statsInner, BorderLayout.CENTER);
        statsPanel.setMinimumSize(new Dimension(200, 80));
        statsPanel.setPreferredSize(new Dimension(280, 100));
        
        // Events table
        String[] cols = {"Time", "Type", "Details", "Sev"};
        eventTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        eventTable = new JTable(eventTableModel);
        eventTable.setFillsViewportHeight(true);
        eventTable.setRowHeight(16);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(35);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(35);
        
        JScrollPane tableScroll = new JScrollPane(eventTable);
        tableScroll.setBorder(new TitledBorder("Events"));
        tableScroll.setMinimumSize(new Dimension(150, 80));
        tableScroll.setPreferredSize(new Dimension(300, 100));
        
        // Horizontal split between Stats and Events
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statsPanel, tableScroll);
        bottomSplit.setResizeWeight(0.4); // Stats gets 40%, Events gets 60%
        bottomSplit.setDividerSize(5);
        bottomSplit.setContinuousLayout(true);
        bottomSplit.setBorder(null);
        bottomSplit.setMinimumSize(new Dimension(200, 80));
        bottomSplit.setPreferredSize(new Dimension(600, 100));
        
        // === SPLIT PANE: Graphs (middle, more space) vs Stats/Events (bottom, less space) ===
        JSplitPane graphsAndStatsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, graphScroll, bottomSplit);
        graphsAndStatsSplit.setResizeWeight(0.75); // Graphs get 75% of space
        graphsAndStatsSplit.setDividerSize(5);
        graphsAndStatsSplit.setContinuousLayout(true);
        graphsAndStatsSplit.setBorder(null);
        
        // === MAIN SPLIT: Guild Overview (top, small) vs Graphs+Stats (bottom, large) ===
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, overviewScroll, graphsAndStatsSplit);
        mainSplit.setResizeWeight(0.1); // Guild overview gets 10% of space
        mainSplit.setDividerSize(5);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);
        
        add(mainSplit, BorderLayout.CENTER);
    }
    
    private JPanel createGraphContainer(String title, JPanel graph) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(new TitledBorder(title));
        container.add(graph, BorderLayout.CENTER);
        return container;
    }
    
    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(10f));
        return label;
    }
    
    private JLabel createFixedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(Color.GRAY);
        return label;
    }
    
    public void refreshGuildList() {
        guildSelector.removeAllItems();
        guildOverviewModel.setRowCount(0);
        
        if (bot == null || bot.getJDA() == null) return;
        
        List<Guild> guilds = bot.getJDA().getGuilds();
        for (Guild g : guilds) {
            if (g.getAudioManager().isConnected()) {
                guildSelector.addItem(new GuildItem(g));
                
                // Add to overview table
                var handler = g.getAudioManager().getSendingHandler();
                if (handler instanceof AudioHandler ah) {
                    MetricsSnapshot snap = ah.getPerformanceMetrics().getSnapshot(selectedWindowSeconds);
                    String status = ah.getPlayer().getPlayingTrack() != null 
                        ? (ah.getPlayer().isPaused() ? "Paused" : "Playing") 
                        : "Idle";
                    
                    guildOverviewModel.addRow(new Object[]{
                        g.getName(),
                        status,
                        snap.qualityScore() + "%",
                        String.format("%.2f%%", snap.missRatePercent()),
                        snap.stutterCount(),
                        snap.stuckCount(),
                        snap.timeToFirstFrameMs() > 0 ? snap.timeToFirstFrameMs() + "ms" : "-"
                    });
                }
            }
        }
        if (guildSelector.getItemCount() == 0) {
            guildSelector.addItem(new GuildItem(null));
        }
    }
    
    private void refreshMetrics() {
        // Always get health snapshot (system-wide, not guild-specific)
        healthSnapshot = SystemHealthMonitor.getInstance().getSnapshot(selectedWindowSeconds);
        
        GuildItem sel = (GuildItem) guildSelector.getSelectedItem();
        if (sel == null || sel.guild() == null) {
            currentSnapshot = null;
            enhancedTimeline.updateSnapshot(null, 0, selectedWindowSeconds);
            updateDisplay();
            return;
        }
        
        var handler = sel.guild().getAudioManager().getSendingHandler();
        if (!(handler instanceof AudioHandler ah)) {
            currentSnapshot = null;
            enhancedTimeline.updateSnapshot(null, 0, selectedWindowSeconds);
            updateDisplay();
            return;
        }
        
        currentSnapshot = ah.getPerformanceMetrics().getSnapshot(selectedWindowSeconds);
        enhancedTimeline.updateSnapshot(currentSnapshot, sel.guild().getIdLong(), selectedWindowSeconds);
        updateDisplay();
    }
    
    private void updateDisplay() {
        if (currentSnapshot == null) {
            healthLabel.setForeground(Color.GRAY);
            qualityScoreLabel.setText("Quality: --%");
            durationLabel.setText("--");
            framesLabel.setText("--");
            missedLabel.setText("--");
            missRateLabel.setText("--%");
            latencyLabel.setText("-- ms");
            fpsLabel.setText("-- fps");
            stuttersLabel.setText("--");
            stuckLabel.setText("--");
            gcCountLabel.setText("--");
            ttffLabel.setText("--");
            missRateWindowsLabel.setText("--");
            eventTableModel.setRowCount(0);
            repaintGraphs();
            return;
        }
        
        HealthStatus health = currentSnapshot.healthStatus();
        healthLabel.setForeground(switch (health) {
            case GOOD -> GOOD_COLOR;
            case WARNING -> WARNING_COLOR;
            case CRITICAL -> CRITICAL_COLOR;
        });
        
        int score = currentSnapshot.qualityScore();
        qualityScoreLabel.setText("Quality: " + score + "%");
        qualityScoreLabel.setForeground(score >= 90 ? GOOD_COLOR : score >= 70 ? WARNING_COLOR : CRITICAL_COLOR);
        
        durationLabel.setText(currentSnapshot.formattedDuration());
        framesLabel.setText(formatNum(currentSnapshot.totalFramesProvided()));
        missedLabel.setText(String.valueOf(currentSnapshot.totalFramesMissed()));
        missedLabel.setForeground(currentSnapshot.totalFramesMissed() > 0 ? CRITICAL_COLOR : null);
        missRateLabel.setText(String.format("%.2f%%", currentSnapshot.missRatePercent()));
        // Show avg latency with p95 - use microseconds for sub-millisecond values
        double avgLat = currentSnapshot.avgLatencyMs();
        double p95Lat = currentSnapshot.p95LatencyMs();
        boolean useMicroseconds = avgLat < 0.5 && p95Lat < 0.5;
        
        if (useMicroseconds) {
            // Convert to microseconds for better readability
            double avgUs = avgLat * 1000;
            double p95Us = p95Lat * 1000;
            if (p95Lat > 0) {
                latencyLabel.setText(String.format("%.0f (p95: %.0f) μs", avgUs, p95Us));
            } else {
                latencyLabel.setText(String.format("%.0f μs", avgUs));
            }
        } else {
            if (p95Lat > 0) {
                latencyLabel.setText(String.format("%.2f (p95: %.2f) ms", avgLat, p95Lat));
            } else {
                latencyLabel.setText(String.format("%.2f ms", avgLat));
            }
        }
        fpsLabel.setText(String.format("%.1f fps", currentSnapshot.framesPerSecond()));
        stuttersLabel.setText(String.valueOf(currentSnapshot.stutterCount()));
        stuttersLabel.setForeground(currentSnapshot.stutterCount() > 0 ? WARNING_COLOR : null);
        stuckLabel.setText(String.valueOf(currentSnapshot.stuckCount()));
        stuckLabel.setForeground(currentSnapshot.stuckCount() > 0 ? CRITICAL_COLOR : null);
        gcCountLabel.setText(String.valueOf(currentSnapshot.gcEvents().length));
        
        // TTFF (Time to First Frame)
        long ttff = currentSnapshot.timeToFirstFrameMs();
        if (ttff > 0) {
            ttffLabel.setText(ttff + "ms");
            ttffLabel.setForeground(ttff > 500 ? WARNING_COLOR : ttff > 1000 ? CRITICAL_COLOR : null);
        } else {
            ttffLabel.setText("--");
            ttffLabel.setForeground(null);
        }
        
        // Miss rate windows (10s / 60s)
        missRateWindowsLabel.setText(String.format("%.2f%% / %.2f%%", 
            currentSnapshot.missRate10s(), currentSnapshot.missRate60s()));
        
        updateEventTable();
        repaintGraphs();
    }
    
    private void updateEventTable() {
        eventTableModel.setRowCount(0);
        if (currentSnapshot == null) return;
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        
        for (StutterEvent e : currentSnapshot.stutterEvents()) {
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "Stut",
                e.missedFrames() + "f (" + e.durationMs() + "ms)",
                e.severity().name().substring(0, 3)
            });
        }
        
        for (PerformanceMetrics.StuckEvent e : currentSnapshot.stuckEvents()) {
            String details = e.trackTitle() != null 
                ? truncate(e.trackTitle(), 20) + " (" + e.thresholdMs() + "ms)"
                : "Track stuck (" + e.thresholdMs() + "ms)";
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "Stck",
                details,
                e.severity().name().substring(0, 3)
            });
        }
        
        for (GCMonitor.GCEvent e : currentSnapshot.gcEvents()) {
            eventTableModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                "GC",
                e.collectorName().replace(" Generation", "").replace("Copy", "YG") + " (" + e.durationMs() + "ms)",
                e.severity().name().substring(0, 3)
            });
        }
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }
    
    private void repaintGraphs() {
        enhancedTimeline.repaint();
        latencyGraph.repaint();
        driftChart.repaint();
    }
    
    private String formatNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
    
    public void updateMetrics() {
        refreshMetrics();
    }
    
    /**
     * Exports current metrics to a JSON file.
     */
    private void exportToJson() {
        if (currentSnapshot == null) {
            JOptionPane.showMessageDialog(this, 
                "No metrics data available. Select a guild with active voice first.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Diagnostics");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        
        // Generate default filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        chooser.setSelectedFile(new File("jmusicbot_diagnostics_" + timestamp + ".json"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try {
                String json = currentSnapshot.toJson();
                Files.writeString(file.toPath(), json);
                JOptionPane.showMessageDialog(this,
                    "Diagnostics exported to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Copies a summary to the clipboard for sharing on Discord.
     */
    private void copyToClipboard() {
        if (currentSnapshot == null) {
            JOptionPane.showMessageDialog(this,
                "No metrics data available. Select a guild with active voice first.",
                "Copy Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String summary = currentSnapshot.toDiscordSummary();
        StringSelection selection = new StringSelection(summary);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        
        JOptionPane.showMessageDialog(this,
            "Summary copied to clipboard!",
            "Copied", JOptionPane.INFORMATION_MESSAGE);
    }
    
    // ===== Graph Panels =====
    
    private class LatencyGraphPanel extends JPanel {
        private static final Color GRID_COLOR = new Color(50, 50, 55);
        private static final Color P95_COLOR = new Color(255, 140, 0); // Orange
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 20) { g2.dispose(); return; }
            
            int margin = 35; // Increased for microsecond labels
            int graphW = w - margin - 15;
            int graphH = h - 20; // More bottom margin for time labels
            int graphTop = 5;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, graphTop, graphW, graphH);
            
            if (currentSnapshot == null || currentSnapshot.latencyBuckets().length == 0) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }
            
            LatencyBucket[] buckets = currentSnapshot.latencyBuckets();
            double p95Latency = currentSnapshot.p95LatencyMs();
            
            // Find max data latency
            double maxDataLat = 0;
            for (LatencyBucket b : buckets) {
                maxDataLat = Math.max(maxDataLat, b.max());
            }
            
            // Determine if we should use microsecond scale
            // Use microseconds when max latency is below 0.5ms (500μs)
            boolean useMicroseconds = maxDataLat < 0.5 && p95Latency < 0.5;
            
            // Scale factor: 1.0 for ms, 1000.0 for μs
            double scaleFactor = useMicroseconds ? 1000.0 : 1.0;
            String unit = useMicroseconds ? "μs" : "ms";
            
            // Convert to display units
            double maxDataDisplay = maxDataLat * scaleFactor;
            double p95Display = p95Latency * scaleFactor;
            
            // Determine scale max in display units
            double scaleMax;
            if (useMicroseconds) {
                // Microsecond scales: 10μs, 25μs, 50μs, 100μs, 250μs, 500μs
                if (maxDataDisplay <= 10) scaleMax = 10;
                else if (maxDataDisplay <= 25) scaleMax = 25;
                else if (maxDataDisplay <= 50) scaleMax = 50;
                else if (maxDataDisplay <= 100) scaleMax = 100;
                else if (maxDataDisplay <= 250) scaleMax = 250;
                else scaleMax = 500;
            } else {
                // Millisecond scales: 1ms, 2ms, 5ms, 10ms, 20ms, etc.
                if (maxDataDisplay <= 1) scaleMax = 1;
                else if (maxDataDisplay <= 2) scaleMax = 2;
                else if (maxDataDisplay <= 5) scaleMax = 5;
                else if (maxDataDisplay <= 10) scaleMax = 10;
                else if (maxDataDisplay <= 20) scaleMax = 20;
                else scaleMax = Math.ceil(maxDataDisplay * 1.2);
            }
            
            g2.setFont(g2.getFont().deriveFont(9f));
            
            // Draw horizontal grid lines at appropriate intervals
            double gridStep;
            if (useMicroseconds) {
                // Microsecond grid steps
                gridStep = scaleMax <= 25 ? 5 : scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : scaleMax <= 250 ? 50 : 100;
            } else {
                // Millisecond grid steps
                gridStep = scaleMax <= 2 ? 0.5 : scaleMax <= 5 ? 1.0 : scaleMax <= 10 ? 2.0 : 5.0;
            }
            
            for (double val = gridStep; val < scaleMax; val += gridStep) {
                int y = graphTop + graphH - (int) (val / scaleMax * graphH);
                g2.setColor(GRID_COLOR);
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Color.GRAY);
                String label = val == (int) val ? String.format("%.0f", val) : String.format("%.1f", val);
                g2.drawString(label, 2, y + 4);
            }
            
            // Y-axis labels with unit
            g2.setColor(Color.GRAY);
            g2.drawString(String.format("%.0f%s", scaleMax, unit), 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);
            
            // Draw min-max range as shaded area (convert ms to display units)
            g2.setColor(new Color(70, 130, 180, 40));
            for (int i = 0; i < buckets.length - 1; i++) {
                int x1 = margin + i * graphW / buckets.length;
                int x2 = margin + (i + 1) * graphW / buckets.length;
                double min1 = buckets[i].min() * scaleFactor;
                double max1 = buckets[i].max() * scaleFactor;
                double min2 = buckets[i + 1].min() * scaleFactor;
                double max2 = buckets[i + 1].max() * scaleFactor;
                int y1min = graphTop + graphH - (int) (Math.min(min1, scaleMax) / scaleMax * graphH);
                int y1max = graphTop + graphH - (int) (Math.min(max1, scaleMax) / scaleMax * graphH);
                int y2min = graphTop + graphH - (int) (Math.min(min2, scaleMax) / scaleMax * graphH);
                int y2max = graphTop + graphH - (int) (Math.min(max2, scaleMax) / scaleMax * graphH);
                g2.fillPolygon(new int[]{x1, x2, x2, x1}, new int[]{y1max, y2max, y2min, y1min}, 4);
            }
            
            // Draw p95 horizontal reference line (dashed)
            if (p95Display > 0 && p95Display <= scaleMax) {
                int p95Y = graphTop + graphH - (int) (p95Display / scaleMax * graphH);
                g2.setColor(P95_COLOR);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
                    10, new float[]{4, 4}, 0));
                g2.drawLine(margin, p95Y, margin + graphW, p95Y);
                g2.setStroke(new BasicStroke(1));
                g2.drawString("p95", margin + graphW + 2, p95Y + 4);
            }
            
            // Draw average latency line (solid blue)
            g2.setColor(new Color(70, 130, 180));
            g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < buckets.length - 1; i++) {
                int x1 = margin + i * graphW / buckets.length;
                int x2 = margin + (i + 1) * graphW / buckets.length;
                double avg1 = buckets[i].avg() * scaleFactor;
                double avg2 = buckets[i + 1].avg() * scaleFactor;
                int y1 = graphTop + graphH - (int) (Math.min(avg1, scaleMax) / scaleMax * graphH);
                int y2 = graphTop + graphH - (int) (Math.min(avg2, scaleMax) / scaleMax * graphH);
                g2.drawLine(x1, y1, x2, y2);
            }
            
            // Draw time axis labels
            g2.setStroke(new BasicStroke(1));
            g2.setFont(g2.getFont().deriveFont(8f));
            g2.setColor(Color.GRAY);
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 : 30;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(GRID_COLOR);
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Color.GRAY);
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }
            
            // Border
            g2.setColor(Color.GRAY);
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }
    
    private class SchedulerDriftPanel extends JPanel {
        private static final Color GRID_COLOR = new Color(50, 50, 55);
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 20) { g2.dispose(); return; }
            
            int margin = 35;
            int graphW = w - margin - 10;
            int graphH = h - 20;
            int graphTop = 5;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, graphTop, graphW, graphH);
            
            if (healthSnapshot == null || healthSnapshot.isEmpty()) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }
            
            HealthSample[] samples = healthSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, "Collecting...", w, h);
                g2.dispose();
                return;
            }
            
            // Find max drift for scaling - use adaptive scale
            long maxDrift = 10;
            for (HealthSample s : samples) {
                maxDrift = Math.max(maxDrift, s.schedulerDriftMs());
            }
            
            // Adaptive scaling: 25ms, 50ms, 100ms, 200ms, 500ms
            long scaleMax;
            if (maxDrift <= 25) scaleMax = 25;
            else if (maxDrift <= 50) scaleMax = 50;
            else if (maxDrift <= 100) scaleMax = 100;
            else if (maxDrift <= 200) scaleMax = 200;
            else if (maxDrift <= 500) scaleMax = 500;
            else scaleMax = ((maxDrift / 100) + 1) * 100;
            
            g2.setFont(g2.getFont().deriveFont(9f));
            
            // Draw horizontal grid lines
            long gridStep = scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : scaleMax <= 200 ? 50 : 100;
            for (long ms = gridStep; ms < scaleMax; ms += gridStep) {
                int y = graphTop + graphH - (int) ((double) ms / scaleMax * graphH);
                g2.setColor(GRID_COLOR);
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Color.GRAY);
                g2.drawString(ms + "", 2, y + 4);
            }
            
            // Y-axis labels
            g2.setColor(Color.GRAY);
            g2.drawString(scaleMax + "ms", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);
            
            // Draw drift bars
            int barWidth = Math.max(2, graphW / samples.length - 1);
            for (int i = 0; i < samples.length; i++) {
                long drift = samples[i].schedulerDriftMs();
                if (drift <= 0) continue;
                
                int x = margin + (i * graphW / samples.length);
                int barHeight = (int) ((double) drift / scaleMax * graphH);
                barHeight = Math.min(barHeight, graphH); // Clamp to graph height
                
                // Color based on severity
                if (drift > 100) {
                    g2.setColor(CRITICAL_COLOR);
                } else if (drift > 50) {
                    g2.setColor(WARNING_COLOR);
                } else {
                    g2.setColor(DRIFT_COLOR);
                }
                
                g2.fillRect(x, graphTop + graphH - barHeight, barWidth, barHeight);
            }
            
            // Draw time axis labels
            g2.setFont(g2.getFont().deriveFont(8f));
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 : 30;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(GRID_COLOR);
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Color.GRAY);
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }
            
            // Border
            g2.setColor(Color.GRAY);
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }
    
    private void drawCentered(Graphics2D g2, String text, int w, int h) {
        g2.setColor(Color.GRAY);
        int tw = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, w / 2 - tw / 2, h / 2 + 4);
    }
    
    private record GuildItem(Guild guild) {
        @Override
        public String toString() {
            return guild != null ? guild.getName() : "(No active voice)";
        }
    }
}
