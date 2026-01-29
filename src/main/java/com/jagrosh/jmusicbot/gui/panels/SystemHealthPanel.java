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

import com.jagrosh.jmusicbot.audio.GCMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSample;
import com.jagrosh.jmusicbot.audio.SystemHealthMonitor.HealthSnapshot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Panel for displaying system health metrics including CPU, memory, GC events.
 * @author Arif Banai (arif-banai)
 */
public class SystemHealthPanel extends JPanel {
    
    private static final Color GOOD_COLOR = new Color(46, 204, 113);
    private static final Color WARNING_COLOR = new Color(241, 196, 15);
    private static final Color CRITICAL_COLOR = new Color(231, 76, 60);
    private static final Color GRAPH_BG = new Color(25, 25, 30);
    private static final Color CPU_COLOR = new Color(52, 152, 219);
    private static final Color HEAP_COLOR = new Color(155, 89, 182);
    private static final Color GC_COLOR = new Color(230, 126, 34);
    
    private final JComboBox<WindowOption> windowSelector;
    
    // Charts
    private final CpuChartPanel cpuChart;
    private final HeapChartPanel heapChart;
    private final GcChartPanel gcChart;
    
    // Stats labels
    private final JLabel statusLabel;
    private final JLabel cpuLabel;
    private final JLabel heapLabel;
    private final JLabel gcCountLabel;
    private final JLabel gcTimeLabel;
    private final JLabel threadsLabel;
    private final JLabel allocRateLabel;
    
    private HealthSnapshot currentSnapshot;
    private int selectedWindowSeconds = 60;
    
    private enum WindowOption {
        SECONDS_30(30, "30 seconds"),
        MINUTE_1(60, "1 minute"),
        MINUTE_2(120, "2 minutes"),
        MINUTE_5(300, "5 minutes");
        
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
    
    public SystemHealthPanel() {
        super(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        
        // === TOP: Controls ===
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        
        topPanel.add(new JLabel("Window:"));
        windowSelector = new JComboBox<>(WindowOption.values());
        windowSelector.setSelectedItem(WindowOption.MINUTE_1);
        windowSelector.addActionListener(e -> {
            WindowOption sel = (WindowOption) windowSelector.getSelectedItem();
            if (sel != null) {
                selectedWindowSeconds = sel.getSeconds();
                refreshMetrics();
            }
        });
        topPanel.add(windowSelector);
        
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshMetrics());
        topPanel.add(refreshBtn);
        
        topPanel.add(Box.createHorizontalStrut(8));
        statusLabel = new JLabel("●");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        topPanel.add(statusLabel);
        
        add(topPanel, BorderLayout.NORTH);
        
        // === CHARTS (GridBagLayout for better space allocation) ===
        JPanel chartsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        // Row 0: CPU and Heap side by side (weight 1.0 each)
        cpuChart = new CpuChartPanel();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        chartsPanel.add(createChartContainer("CPU Usage (%)", cpuChart), gbc);
        
        heapChart = new HeapChartPanel();
        gbc.gridx = 1;
        chartsPanel.add(createChartContainer("Heap Memory", heapChart), gbc);
        
        // Row 1: GC Events (full width, weight 1.0)
        gcChart = new GcChartPanel();
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        chartsPanel.add(createChartContainer("GC Events", gcChart), gbc);
        
        // === STATS BAR (3 rows x 4 columns for more info) ===
        JPanel statsPanel = new JPanel(new GridLayout(3, 4, 8, 2));
        statsPanel.setBorder(new TitledBorder("Current Stats"));
        
        cpuLabel = createStatLabel("--%");
        heapLabel = createStatLabel("--");
        gcCountLabel = createStatLabel("--");
        gcTimeLabel = createStatLabel("--");
        threadsLabel = createStatLabel("--");
        allocRateLabel = createStatLabel("--");
        
        // Row 1: CPU and Heap
        statsPanel.add(createFixedLabel("CPU:"));
        statsPanel.add(cpuLabel);
        statsPanel.add(createFixedLabel("Heap:"));
        statsPanel.add(heapLabel);
        
        // Row 2: GC stats
        statsPanel.add(createFixedLabel("GC Count:"));
        statsPanel.add(gcCountLabel);
        statsPanel.add(createFixedLabel("GC Time:"));
        statsPanel.add(gcTimeLabel);
        
        // Row 3: Threads and Alloc Rate
        statsPanel.add(createFixedLabel("Threads:"));
        statsPanel.add(threadsLabel);
        statsPanel.add(createFixedLabel("Alloc Rate:"));
        statsPanel.add(allocRateLabel);
        
        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartsPanel, statsPanel);
        splitPane.setResizeWeight(0.80);
        splitPane.setDividerSize(5);
        splitPane.setContinuousLayout(true);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    private JPanel createChartContainer(String title, JPanel chart) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(new TitledBorder(title));
        container.add(chart, BorderLayout.CENTER);
        return container;
    }
    
    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }
    
    private JLabel createFixedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(Color.GRAY);
        return label;
    }
    
    public void refreshMetrics() {
        currentSnapshot = SystemHealthMonitor.getInstance().getSnapshot(selectedWindowSeconds);
        updateDisplay();
    }
    
    private void updateDisplay() {
        if (currentSnapshot == null || currentSnapshot.isEmpty()) {
            statusLabel.setForeground(Color.GRAY);
            cpuLabel.setText("--%");
            heapLabel.setText("--");
            gcCountLabel.setText("--");
            gcTimeLabel.setText("--");
            threadsLabel.setText("--");
            allocRateLabel.setText("--");
            repaintCharts();
            return;
        }
        
        // Update status indicator
        statusLabel.setForeground(switch (currentSnapshot.status()) {
            case GOOD -> GOOD_COLOR;
            case WARNING -> WARNING_COLOR;
            case CRITICAL -> CRITICAL_COLOR;
        });
        
        // Update stat labels
        if (currentSnapshot.currentProcessCpu() >= 0) {
            cpuLabel.setText(String.format("%.1f%%", currentSnapshot.currentProcessCpu()));
            cpuLabel.setForeground(currentSnapshot.currentProcessCpu() > 80 ? CRITICAL_COLOR : 
                                   currentSnapshot.currentProcessCpu() > 50 ? WARNING_COLOR : null);
        } else {
            cpuLabel.setText("N/A");
        }
        
        heapLabel.setText(currentSnapshot.formattedHeap());
        heapLabel.setForeground(currentSnapshot.currentHeapPercent() > 80 ? CRITICAL_COLOR :
                               currentSnapshot.currentHeapPercent() > 60 ? WARNING_COLOR : null);
        
        GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(selectedWindowSeconds);
        gcCountLabel.setText(String.valueOf(gcEvents.length));
        
        long totalGcTime = 0;
        for (GCMonitor.GCEvent e : gcEvents) {
            totalGcTime += e.durationMs();
        }
        gcTimeLabel.setText(totalGcTime + " ms");
        gcTimeLabel.setForeground(totalGcTime > 500 ? CRITICAL_COLOR : 
                                  totalGcTime > 100 ? WARNING_COLOR : null);
        
        threadsLabel.setText(String.valueOf(currentSnapshot.currentThreadCount()));
        
        if (currentSnapshot.avgAllocationRate() > 0) {
            double mbPerSec = currentSnapshot.avgAllocationRate() / (1024 * 1024);
            allocRateLabel.setText(String.format("%.1f MB/s", mbPerSec));
        } else {
            allocRateLabel.setText("--");
        }
        
        repaintCharts();
    }
    
    private void repaintCharts() {
        cpuChart.repaint();
        heapChart.repaint();
        gcChart.repaint();
    }
    
    public void updateMetrics() {
        refreshMetrics();
    }
    
    // ===== Chart Panels =====
    
    private class CpuChartPanel extends JPanel {
        private static final Color GRID_COLOR = new Color(50, 50, 55);
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int margin = 30;
            int graphW = w - margin - 5;
            int graphH = h - 20;
            int graphTop = 5;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, graphTop, graphW, graphH);
            
            // Draw horizontal grid lines at 25%, 50%, 75%
            g2.setFont(g2.getFont().deriveFont(9f));
            for (int pct = 25; pct < 100; pct += 25) {
                int y = graphTop + graphH - (int) (pct / 100.0 * graphH);
                g2.setColor(GRID_COLOR);
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Color.GRAY);
                g2.drawString(pct + "", 2, y + 4);
            }
            
            // Y-axis labels
            g2.setColor(Color.GRAY);
            g2.drawString("100%", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);
            
            if (currentSnapshot == null || currentSnapshot.isEmpty()) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }
            
            HealthSample[] samples = currentSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, "Collecting...", w, h);
                g2.dispose();
                return;
            }
            
            // Draw CPU area fill
            g2.setColor(new Color(CPU_COLOR.getRed(), CPU_COLOR.getGreen(), CPU_COLOR.getBlue(), 40));
            int[] xPoints = new int[samples.length + 2];
            int[] yPoints = new int[samples.length + 2];
            for (int i = 0; i < samples.length; i++) {
                double cpu = Math.max(0, samples[i].processCpuPercent());
                xPoints[i] = margin + (i * graphW / samples.length);
                yPoints[i] = graphTop + graphH - (int) (cpu / 100.0 * graphH);
            }
            xPoints[samples.length] = margin + graphW;
            yPoints[samples.length] = graphTop + graphH;
            xPoints[samples.length + 1] = margin;
            yPoints[samples.length + 1] = graphTop + graphH;
            g2.fillPolygon(xPoints, yPoints, samples.length + 2);
            
            // Draw CPU line
            g2.setColor(CPU_COLOR);
            g2.setStroke(new BasicStroke(2f));
            
            for (int i = 0; i < samples.length - 1; i++) {
                double cpu1 = Math.max(0, samples[i].processCpuPercent());
                double cpu2 = Math.max(0, samples[i + 1].processCpuPercent());
                
                int x1 = margin + (i * graphW / samples.length);
                int x2 = margin + ((i + 1) * graphW / samples.length);
                int y1 = graphTop + graphH - (int) (cpu1 / 100.0 * graphH);
                int y2 = graphTop + graphH - (int) (cpu2 / 100.0 * graphH);
                
                g2.drawLine(x1, y1, x2, y2);
            }
            
            g2.setStroke(new BasicStroke(1));
            g2.setColor(Color.GRAY);
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }
    
    private class HeapChartPanel extends JPanel {
        private static final Color GRID_COLOR = new Color(50, 50, 55);
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int margin = 30;
            int graphW = w - margin - 5;
            int graphH = h - 20;
            int graphTop = 5;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, graphTop, graphW, graphH);
            
            // Draw horizontal grid lines at 25%, 50%, 75%
            g2.setFont(g2.getFont().deriveFont(9f));
            for (int pct = 25; pct < 100; pct += 25) {
                int y = graphTop + graphH - (int) (pct / 100.0 * graphH);
                g2.setColor(GRID_COLOR);
                g2.drawLine(margin, y, margin + graphW, y);
                g2.setColor(Color.GRAY);
                g2.drawString(pct + "", 2, y + 4);
            }
            
            // Y-axis labels
            g2.setColor(Color.GRAY);
            g2.drawString("100%", 2, graphTop + 10);
            g2.drawString("0", 2, graphTop + graphH - 2);
            
            if (currentSnapshot == null || currentSnapshot.isEmpty()) {
                drawCentered(g2, "No data", w, h);
                g2.dispose();
                return;
            }
            
            HealthSample[] samples = currentSnapshot.samples();
            if (samples.length < 2) {
                drawCentered(g2, "Collecting...", w, h);
                g2.dispose();
                return;
            }
            
            // Draw heap fill
            g2.setColor(new Color(HEAP_COLOR.getRed(), HEAP_COLOR.getGreen(), HEAP_COLOR.getBlue(), 50));
            int[] xPoints = new int[samples.length + 2];
            int[] yPoints = new int[samples.length + 2];
            
            for (int i = 0; i < samples.length; i++) {
                xPoints[i] = margin + (i * graphW / samples.length);
                yPoints[i] = graphTop + graphH - (int) (samples[i].heapUsedPercent() / 100.0 * graphH);
            }
            xPoints[samples.length] = margin + graphW;
            yPoints[samples.length] = graphTop + graphH;
            xPoints[samples.length + 1] = margin;
            yPoints[samples.length + 1] = graphTop + graphH;
            
            g2.fillPolygon(xPoints, yPoints, samples.length + 2);
            
            // Draw heap line
            g2.setColor(HEAP_COLOR);
            g2.setStroke(new BasicStroke(2f));
            
            for (int i = 0; i < samples.length - 1; i++) {
                int x1 = margin + (i * graphW / samples.length);
                int x2 = margin + ((i + 1) * graphW / samples.length);
                int y1 = graphTop + graphH - (int) (samples[i].heapUsedPercent() / 100.0 * graphH);
                int y2 = graphTop + graphH - (int) (samples[i + 1].heapUsedPercent() / 100.0 * graphH);
                
                g2.drawLine(x1, y1, x2, y2);
            }
            
            g2.setStroke(new BasicStroke(1));
            g2.setColor(Color.GRAY);
            g2.drawRect(margin, graphTop, graphW, graphH);
            g2.dispose();
        }
    }
    
    private class GcChartPanel extends JPanel {
        private static final Color GRID_COLOR = new Color(50, 50, 55);
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth(), h = getHeight();
            int margin = 35;
            int graphW = w - margin - 10;
            int graphH = h - 25;
            int graphTop = 5;
            
            g2.setColor(GRAPH_BG);
            g2.fillRect(margin, graphTop, graphW, graphH);
            
            GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(selectedWindowSeconds);
            
            long windowMs = selectedWindowSeconds * 1000L;
            long now = System.currentTimeMillis();
            long startTime = now - windowMs;
            
            // Find max duration for scaling
            long maxDuration = 10;
            for (GCMonitor.GCEvent e : gcEvents) {
                maxDuration = Math.max(maxDuration, e.durationMs());
            }
            // Use adaptive scale
            long scaleMax = maxDuration <= 10 ? 10 : maxDuration <= 25 ? 25 : 
                           maxDuration <= 50 ? 50 : maxDuration <= 100 ? 100 : 
                           ((maxDuration / 50) + 1) * 50;
            
            // Draw horizontal grid lines
            g2.setFont(g2.getFont().deriveFont(9f));
            long gridStep = scaleMax <= 25 ? 5 : scaleMax <= 50 ? 10 : scaleMax <= 100 ? 25 : 50;
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
            
            // Draw time axis labels
            int timeInterval = selectedWindowSeconds <= 30 ? 10 : selectedWindowSeconds <= 60 ? 15 : 
                              selectedWindowSeconds <= 120 ? 30 : 60;
            for (int sec = 0; sec <= selectedWindowSeconds; sec += timeInterval) {
                int x = margin + (int) ((double) sec / selectedWindowSeconds * graphW);
                g2.setColor(GRID_COLOR);
                g2.drawLine(x, graphTop, x, graphTop + graphH);
                g2.setColor(Color.GRAY);
                g2.drawString("-" + (selectedWindowSeconds - sec) + "s", x - 8, h - 3);
            }
            
            if (gcEvents.length == 0) {
                g2.setColor(Color.GRAY);
                g2.drawString("No GC events", w / 2 - 30, graphTop + graphH / 2);
            } else {
                // Draw GC bars
                for (GCMonitor.GCEvent e : gcEvents) {
                    if (e.timestamp() < startTime) continue;
                    
                    int x = margin + (int) ((e.timestamp() - startTime) / (double) windowMs * graphW);
                    int barHeight = (int) ((double) e.durationMs() / scaleMax * graphH);
                    barHeight = Math.min(barHeight, graphH);
                    
                    g2.setColor(e.severity() == GCMonitor.GCEvent.Severity.SEVERE ? CRITICAL_COLOR :
                               e.severity() == GCMonitor.GCEvent.Severity.MODERATE ? WARNING_COLOR : GC_COLOR);
                    g2.fillRect(x - 3, graphTop + graphH - barHeight, 6, barHeight);
                }
            }
            
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
}
