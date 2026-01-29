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

import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor;
import com.jagrosh.jmusicbot.audio.TrackLoadingMonitor.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Panel for visualizing track loading health and performance by source.
 * Shows load times, success rates, and recent load history.
 *
 * @author Arif Banai (arif-banai)
 */
public class SourceHealthPanel extends JPanel {
    
    private static final Color GOOD_COLOR = new Color(46, 204, 113);
    private static final Color WARNING_COLOR = new Color(241, 196, 15);
    private static final Color CRITICAL_COLOR = new Color(231, 76, 60);
    private static final Color GRAPH_BG = new Color(25, 25, 30);
    
    private final JComboBox<WindowOption> windowSelector;
    
    // Stats labels
    private final JLabel totalLoadsLabel;
    private final JLabel successRateLabel;
    private final JLabel avgLoadTimeLabel;
    private final JLabel p95LoadTimeLabel;
    private final JLabel failedLoadsLabel;
    
    // Source table
    private final JTable sourceTable;
    private final DefaultTableModel sourceTableModel;
    
    // Recent loads table
    private final JTable recentLoadsTable;
    private final DefaultTableModel recentLoadsModel;
    
    // Visualization
    private final LoadTimeChartPanel loadTimeChart;
    private final SuccessPiePanel successPie;
    
    private LoadingSnapshot currentSnapshot;
    private int selectedWindowSeconds = 60;
    
    private enum WindowOption {
        SECONDS_30(30, "30 seconds"),
        MINUTE_1(60, "1 minute"),
        MINUTE_5(300, "5 minutes"),
        MINUTE_10(600, "10 minutes");
        
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
    
    public SourceHealthPanel() {
        super(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        
        // === TOP: Controls and Summary Stats ===
        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        
        // Controls row
        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        controlsRow.add(new JLabel("Window:"));
        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setSelectedItem(WindowOption.MINUTE_1);
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });
        controlsRow.add(windowSelector);
        
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshMetrics());
        controlsRow.add(refreshBtn);
        
        topPanel.add(controlsRow, BorderLayout.NORTH);
        
        // Summary stats row
        JPanel statsRow = new JPanel(new GridLayout(1, 5, 8, 0));
        statsRow.setBorder(new TitledBorder("Summary"));
        
        totalLoadsLabel = createStatLabel("Total: --");
        successRateLabel = createStatLabel("Success: --%");
        avgLoadTimeLabel = createStatLabel("Avg: --ms");
        p95LoadTimeLabel = createStatLabel("p95: --ms");
        failedLoadsLabel = createStatLabel("Failed: --");
        
        statsRow.add(totalLoadsLabel);
        statsRow.add(successRateLabel);
        statsRow.add(avgLoadTimeLabel);
        statsRow.add(p95LoadTimeLabel);
        statsRow.add(failedLoadsLabel);
        
        topPanel.add(statsRow, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        
        // === CENTER: Charts and Tables ===
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        
        // Top row: Charts
        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        
        loadTimeChart = new LoadTimeChartPanel();
        JPanel chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBorder(new TitledBorder("Load Time by Source"));
        chartContainer.add(loadTimeChart, BorderLayout.CENTER);
        chartsPanel.add(chartContainer);
        
        successPie = new SuccessPiePanel();
        JPanel pieContainer = new JPanel(new BorderLayout());
        pieContainer.setBorder(new TitledBorder("Success Rate by Source"));
        pieContainer.add(successPie, BorderLayout.CENTER);
        chartsPanel.add(pieContainer);
        
        centerPanel.add(chartsPanel);
        
        // Bottom row: Tables
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        
        // Source stats table
        String[] sourceCols = {"Source", "Loads", "Success%", "Avg (ms)", "p95 (ms)"};
        sourceTableModel = new DefaultTableModel(sourceCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        sourceTable = new JTable(sourceTableModel);
        sourceTable.setRowHeight(18);
        sourceTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        sourceTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        sourceTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        sourceTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        sourceTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        
        // Color-code success rate column
        sourceTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof String s) {
                    try {
                        double rate = Double.parseDouble(s.replace("%", ""));
                        if (rate >= 95) c.setForeground(GOOD_COLOR);
                        else if (rate >= 80) c.setForeground(WARNING_COLOR);
                        else c.setForeground(CRITICAL_COLOR);
                    } catch (NumberFormatException ignored) {}
                }
                return c;
            }
        });
        
        JScrollPane sourceScroll = new JScrollPane(sourceTable);
        sourceScroll.setBorder(new TitledBorder("Sources"));
        tablesPanel.add(sourceScroll);
        
        // Recent loads table
        String[] recentCols = {"Time", "Source", "Result", "Duration", "Track/Error"};
        recentLoadsModel = new DefaultTableModel(recentCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        recentLoadsTable = new JTable(recentLoadsModel);
        recentLoadsTable.setRowHeight(18);
        recentLoadsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        recentLoadsTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        recentLoadsTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        recentLoadsTable.getColumnModel().getColumn(3).setPreferredWidth(55);
        recentLoadsTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        
        // Color-code result column
        recentLoadsTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof String s) {
                    if (s.contains("LOADED")) c.setForeground(GOOD_COLOR);
                    else if (s.equals("NO_MATCHES")) c.setForeground(WARNING_COLOR);
                    else if (s.equals("LOAD_FAILED")) c.setForeground(CRITICAL_COLOR);
                }
                return c;
            }
        });
        
        JScrollPane recentScroll = new JScrollPane(recentLoadsTable);
        recentScroll.setBorder(new TitledBorder("Recent Loads"));
        tablesPanel.add(recentScroll);
        
        centerPanel.add(tablesPanel);
        
        add(centerPanel, BorderLayout.CENTER);
    }
    
    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }
    
    /**
     * Refreshes metrics from TrackLoadingMonitor.
     */
    public void refreshMetrics() {
        currentSnapshot = TrackLoadingMonitor.getInstance().getSnapshot(selectedWindowSeconds);
        updateDisplay();
    }
    
    private void updateDisplay() {
        if (currentSnapshot == null) {
            totalLoadsLabel.setText("Total: --");
            successRateLabel.setText("Success: --%");
            avgLoadTimeLabel.setText("Avg: --ms");
            p95LoadTimeLabel.setText("p95: --ms");
            failedLoadsLabel.setText("Failed: --");
            sourceTableModel.setRowCount(0);
            recentLoadsModel.setRowCount(0);
            repaintCharts();
            return;
        }
        
        // Update summary stats
        totalLoadsLabel.setText("Total: " + currentSnapshot.loadsInWindow());
        
        double successRate = currentSnapshot.successRatePercent();
        successRateLabel.setText(String.format("Success: %.1f%%", successRate));
        successRateLabel.setForeground(successRate >= 95 ? GOOD_COLOR : 
                                       successRate >= 80 ? WARNING_COLOR : CRITICAL_COLOR);
        
        avgLoadTimeLabel.setText(String.format("Avg: %.0fms", currentSnapshot.avgLoadDurationMs()));
        p95LoadTimeLabel.setText(String.format("p95: %.0fms", currentSnapshot.p95LoadDurationMs()));
        
        failedLoadsLabel.setText("Failed: " + currentSnapshot.failedInWindow());
        failedLoadsLabel.setForeground(currentSnapshot.failedInWindow() > 0 ? CRITICAL_COLOR : null);
        
        // Update source table
        updateSourceTable();
        
        // Update recent loads table
        updateRecentLoadsTable();
        
        repaintCharts();
    }
    
    private void updateSourceTable() {
        sourceTableModel.setRowCount(0);
        
        for (String source : currentSnapshot.trackedSources()) {
            SourceStats stats = TrackLoadingMonitor.getInstance().getSourceStats(source);
            if (stats == null) continue;
            
            sourceTableModel.addRow(new Object[]{
                source,
                stats.getTotalLoads(),
                String.format("%.1f%%", stats.getSuccessRate()),
                String.format("%.0f", stats.getAverageDurationMs()),
                String.format("%.0f", stats.getP95DurationMs())
            });
        }
    }
    
    private void updateRecentLoadsTable() {
        recentLoadsModel.setRowCount(0);
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        LoadEvent[] events = currentSnapshot.recentEvents();
        
        // Show most recent 20 events, reversed so newest is at top
        int start = Math.max(0, events.length - 20);
        for (int i = events.length - 1; i >= start; i--) {
            LoadEvent e = events[i];
            
            String details = e.trackOrPlaylistName() != null 
                ? truncate(e.trackOrPlaylistName(), 40)
                : e.errorMessage() != null ? truncate(e.errorMessage(), 40) : "-";
            
            recentLoadsModel.addRow(new Object[]{
                sdf.format(new Date(e.timestamp())),
                e.source(),
                e.result().name(),
                e.loadDurationMs() + "ms",
                details
            });
        }
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }
    
    private void repaintCharts() {
        loadTimeChart.repaint();
        successPie.repaint();
    }
    
    // ===== Chart Panels =====
    
    private class LoadTimeChartPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int margin = 40;
            int graphW = w - margin - 10;
            int graphH = h - 30;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, 10, graphW, graphH);
            
            if (currentSnapshot == null || currentSnapshot.trackedSources().length == 0) {
                g2.setColor(Color.GRAY);
                g2.drawString("No data", w / 2 - 20, h / 2);
                g2.dispose();
                return;
            }
            
            String[] sources = currentSnapshot.trackedSources();
            int barCount = sources.length;
            int barWidth = Math.max(20, (graphW - 10) / barCount - 10);
            
            // Find max duration for scaling
            double maxDuration = 100;
            for (String source : sources) {
                SourceStats stats = TrackLoadingMonitor.getInstance().getSourceStats(source);
                if (stats != null) {
                    maxDuration = Math.max(maxDuration, stats.getP95DurationMs());
                }
            }
            maxDuration = Math.ceil(maxDuration / 100) * 100; // Round up to nearest 100
            
            // Draw Y axis labels
            g2.setFont(g2.getFont().deriveFont(8f));
            g2.setColor(Color.GRAY);
            g2.drawString(String.format("%.0f", maxDuration), 5, 15);
            g2.drawString("0", 5, 10 + graphH);
            
            // Draw bars
            for (int i = 0; i < sources.length; i++) {
                String source = sources[i];
                SourceStats stats = TrackLoadingMonitor.getInstance().getSourceStats(source);
                if (stats == null) continue;
                
                int x = margin + 5 + i * (barWidth + 10);
                
                // Avg bar
                double avgHeight = (stats.getAverageDurationMs() / maxDuration) * graphH;
                g2.setColor(new Color(70, 130, 180));
                g2.fillRect(x, 10 + graphH - (int) avgHeight, barWidth / 2 - 2, (int) avgHeight);
                
                // P95 bar (outline)
                double p95Height = (stats.getP95DurationMs() / maxDuration) * graphH;
                g2.setColor(new Color(255, 140, 0));
                g2.fillRect(x + barWidth / 2, 10 + graphH - (int) p95Height, barWidth / 2 - 2, (int) p95Height);
                
                // Source label
                g2.setColor(Color.GRAY);
                String label = truncate(source, 8);
                g2.drawString(label, x, h - 5);
            }
            
            // Legend
            g2.setFont(g2.getFont().deriveFont(9f));
            g2.setColor(new Color(70, 130, 180));
            g2.fillRect(w - 70, 15, 10, 10);
            g2.setColor(Color.GRAY);
            g2.drawString("Avg", w - 55, 24);
            
            g2.setColor(new Color(255, 140, 0));
            g2.fillRect(w - 70, 30, 10, 10);
            g2.setColor(Color.GRAY);
            g2.drawString("p95", w - 55, 39);
            
            g2.setColor(Color.GRAY);
            g2.drawRect(margin, 10, graphW, graphH);
            g2.dispose();
        }
    }
    
    private class SuccessPiePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(10, 10, w - 20, h - 20);
            
            if (currentSnapshot == null || currentSnapshot.loadsInWindow() == 0) {
                g2.setColor(Color.GRAY);
                g2.drawString("No data", w / 2 - 20, h / 2);
                g2.dispose();
                return;
            }
            
            int success = currentSnapshot.successInWindow();
            int failed = currentSnapshot.failedInWindow();
            int noMatch = currentSnapshot.noMatchInWindow();
            int total = success + failed + noMatch;
            
            int diameter = Math.min(w, h) - 60;
            int x = (w - diameter) / 2;
            int y = 20;
            
            // Draw pie slices
            int startAngle = 0;
            
            if (success > 0) {
                int arc = (int) ((success * 360.0) / total);
                g2.setColor(GOOD_COLOR);
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
                startAngle += arc;
            }
            
            if (noMatch > 0) {
                int arc = (int) ((noMatch * 360.0) / total);
                g2.setColor(WARNING_COLOR);
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
                startAngle += arc;
            }
            
            if (failed > 0) {
                int arc = 360 - startAngle; // Remainder
                g2.setColor(CRITICAL_COLOR);
                g2.fillArc(x, y, diameter, diameter, startAngle, arc);
            }
            
            // Legend
            int legendY = y + diameter + 15;
            g2.setFont(g2.getFont().deriveFont(9f));
            
            g2.setColor(GOOD_COLOR);
            g2.fillRect(10, legendY, 10, 10);
            g2.setColor(Color.GRAY);
            g2.drawString("Success: " + success, 25, legendY + 9);
            
            g2.setColor(WARNING_COLOR);
            g2.fillRect(w / 3, legendY, 10, 10);
            g2.setColor(Color.GRAY);
            g2.drawString("No Match: " + noMatch, w / 3 + 15, legendY + 9);
            
            g2.setColor(CRITICAL_COLOR);
            g2.fillRect(2 * w / 3, legendY, 10, 10);
            g2.setColor(Color.GRAY);
            g2.drawString("Failed: " + failed, 2 * w / 3 + 15, legendY + 9);
            
            g2.setColor(Color.GRAY);
            g2.drawRect(10, 10, w - 20, h - 20);
            g2.dispose();
        }
    }
}
