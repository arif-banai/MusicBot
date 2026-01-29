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
import com.jagrosh.jmusicbot.audio.PerformanceMetrics.*;
import com.jagrosh.jmusicbot.audio.VoiceConnectionMonitor;
import com.jagrosh.jmusicbot.audio.VoiceConnectionMonitor.VoiceEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Enhanced timeline panel with multi-layer correlation view.
 * Displays track events, frame status, stutters, voice events, and system events
 * on synchronized time axes for correlation analysis.
 *
 * <p>Data Layers (top to bottom):
 * <ol>
 *   <li>Track Layer - Track start/end markers</li>
 *   <li>Frame Layer - Binary band showing provided (green) vs missed (red) frames</li>
 *   <li>Stutter Layer - Stutter circles + stuck event markers</li>
 *   <li>Voice Layer - Connect/disconnect/reconnect markers</li>
 *   <li>System Layer - GC markers</li>
 * </ol>
 *
 * @author Arif Banai (arif-banai)
 */
public class EnhancedTimelinePanel extends JPanel {
    
    private static final Color GRAPH_BG = new Color(25, 25, 30);
    private static final Color GRID_COLOR = new Color(50, 50, 55);
    private static final Color GOOD_COLOR = new Color(46, 204, 113);
    private static final Color WARNING_COLOR = new Color(241, 196, 15);
    private static final Color CRITICAL_COLOR = new Color(231, 76, 60);
    private static final Color VOICE_COLOR = new Color(100, 149, 237); // Cornflower blue
    private static final Color GC_COLOR = new Color(155, 89, 182); // Purple
    private static final Color TRACK_START_COLOR = new Color(46, 204, 113); // Green
    private static final Color TRACK_END_COLOR = new Color(231, 76, 60); // Red
    private static final Color TRACK_EXCEPTION_COLOR = new Color(241, 196, 15); // Yellow
    
    private static final int MIN_LAYER_HEIGHT = 26;
    private static final int MARGIN_LEFT = 70; // Increased for better label visibility
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_TOP = 20; // Account for TitledBorder
    private static final int MARGIN_BOTTOM = 22;
    
    private static final String[] LAYER_NAMES = {"Track", "Frames", "Events", "Voice", "System"};
    
    private MetricsSnapshot snapshot;
    private int windowSeconds = 60;
    private long guildId;
    
    // Tooltip tracking
    private String tooltipText = null;
    
    public EnhancedTimelinePanel() {
        // Set minimum size but allow growth to fill available space
        setPreferredSize(new Dimension(600, MIN_LAYER_HEIGHT * 5 + MARGIN_TOP + MARGIN_BOTTOM));
        setMinimumSize(new Dimension(300, MIN_LAYER_HEIGHT * 5 + MARGIN_TOP + MARGIN_BOTTOM));
        setBorder(new TitledBorder("Enhanced Timeline"));
        
        // Add mouse motion listener for tooltips
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateTooltip(e.getX(), e.getY());
            }
        });
        
        setToolTipText(""); // Enable tooltips
    }
    
    @Override
    public String getToolTipText(MouseEvent event) {
        if (tooltipText != null && !tooltipText.isEmpty()) {
            return tooltipText;
        }
        return null;
    }
    
    /**
     * Updates the panel with new snapshot data.
     */
    public void updateSnapshot(MetricsSnapshot snapshot, long guildId, int windowSeconds) {
        this.snapshot = snapshot;
        this.guildId = guildId;
        this.windowSeconds = windowSeconds;
        repaint();
    }
    
    private void updateTooltip(int mouseX, int mouseY) {
        if (snapshot == null) {
            tooltipText = null;
            return;
        }
        
        int graphW = getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
        if (mouseX < MARGIN_LEFT || mouseX > getWidth() - MARGIN_RIGHT) {
            tooltipText = null;
            return;
        }
        
        // Calculate dynamic layer height (same as paintComponent)
        int availableH = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
        int layerH = Math.max(MIN_LAYER_HEIGHT, availableH / 5);
        
        long windowMs = windowSeconds * 1000L;
        long now = System.currentTimeMillis();
        long startTime = now - windowMs;
        
        // Calculate time at mouse position
        double relX = (mouseX - MARGIN_LEFT) / (double) graphW;
        long timeAtMouse = startTime + (long) (relX * windowMs);
        
        // Determine which layer using dynamic height
        int layerIndex = (mouseY - MARGIN_TOP) / layerH;
        if (layerIndex < 0 || layerIndex >= 5) {
            tooltipText = null;
            return;
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<b>Time:</b> ").append(sdf.format(new Date(timeAtMouse))).append("<br>");
        
        // Find nearby events based on layer
        switch (layerIndex) {
            case 0 -> findNearbyTrackEvent(timeAtMouse, sb, sdf);
            case 1 -> findNearbyFrameInfo(timeAtMouse, sb);
            case 2 -> findNearbyStutterEvent(timeAtMouse, sb, sdf);
            case 3 -> findNearbyVoiceEvent(timeAtMouse, sb, sdf);
            case 4 -> findNearbyGCEvent(timeAtMouse, sb, sdf);
        }
        
        sb.append("</html>");
        tooltipText = sb.length() > 20 ? sb.toString() : null;
    }
    
    private void findNearbyTrackEvent(long time, StringBuilder sb, SimpleDateFormat sdf) {
        if (snapshot.trackEvents() == null) return;
        
        for (TrackEvent e : snapshot.trackEvents()) {
            if (Math.abs(e.timestamp() - time) < 2000) {
                sb.append("<b>Track:</b> ").append(e.type().name()).append("<br>");
                if (e.trackTitle() != null) {
                    sb.append(truncate(e.trackTitle(), 30)).append("<br>");
                }
                break;
            }
        }
    }
    
    private void findNearbyFrameInfo(long time, StringBuilder sb) {
        // Estimate frame index based on time
        FrameMetric[] frames = snapshot.frameHistory();
        if (frames == null || frames.length == 0) return;
        
        // Find frames near this time
        int providedCount = 0;
        int missedCount = 0;
        for (FrameMetric f : frames) {
            if (Math.abs(f.timestamp() - time) < 500) {
                if (f.frameProvided()) providedCount++;
                else missedCount++;
            }
        }
        
        if (providedCount + missedCount > 0) {
            sb.append("<b>Frames (±0.5s):</b><br>");
            sb.append("Provided: ").append(providedCount).append("<br>");
            sb.append("Missed: ").append(missedCount).append("<br>");
        }
    }
    
    private void findNearbyStutterEvent(long time, StringBuilder sb, SimpleDateFormat sdf) {
        for (StutterEvent e : snapshot.stutterEvents()) {
            if (Math.abs(e.timestamp() - time) < 2000) {
                sb.append("<b>Stutter:</b><br>");
                sb.append("Missed: ").append(e.missedFrames()).append(" frames<br>");
                sb.append("Duration: ").append(e.durationMs()).append("ms<br>");
                sb.append("Severity: ").append(e.severity().name()).append("<br>");
                break;
            }
        }
        
        for (StuckEvent e : snapshot.stuckEvents()) {
            if (Math.abs(e.timestamp() - time) < 2000) {
                sb.append("<b>Stuck Event:</b><br>");
                sb.append("Threshold: ").append(e.thresholdMs()).append("ms<br>");
                if (e.trackTitle() != null) {
                    sb.append("Track: ").append(truncate(e.trackTitle(), 25)).append("<br>");
                }
                break;
            }
        }
    }
    
    private void findNearbyVoiceEvent(long time, StringBuilder sb, SimpleDateFormat sdf) {
        VoiceEvent[] events = VoiceConnectionMonitor.getInstance().getRecentEvents(guildId, windowSeconds);
        for (VoiceEvent e : events) {
            if (Math.abs(e.timestamp() - time) < 2000) {
                sb.append("<b>Voice:</b> ").append(e.type().name()).append("<br>");
                if (e.fromChannel() != null) {
                    sb.append("From: ").append(e.fromChannel()).append("<br>");
                }
                if (e.toChannel() != null) {
                    sb.append("To: ").append(e.toChannel()).append("<br>");
                }
                break;
            }
        }
    }
    
    private void findNearbyGCEvent(long time, StringBuilder sb, SimpleDateFormat sdf) {
        for (GCMonitor.GCEvent e : snapshot.gcEvents()) {
            if (Math.abs(e.timestamp() - time) < 2000) {
                sb.append("<b>GC Event:</b><br>");
                sb.append("Collector: ").append(e.collectorName()).append("<br>");
                sb.append("Duration: ").append(e.durationMs()).append("ms<br>");
                sb.append("Severity: ").append(e.severity().name()).append("<br>");
                break;
            }
        }
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        int graphW = w - MARGIN_LEFT - MARGIN_RIGHT;
        
        // Dynamically calculate layer height to fill available space
        int availableH = h - MARGIN_TOP - MARGIN_BOTTOM;
        int layerH = Math.max(MIN_LAYER_HEIGHT, availableH / 5);
        int graphH = layerH * 5;
        
        if (graphW < 50 || graphH < 50) {
            g2.dispose();
            return;
        }
        
        // Background
        g2.setColor(GRAPH_BG);
        g2.fillRect(MARGIN_LEFT, MARGIN_TOP, graphW, graphH);
        
        // Draw layer labels with larger, bolder font - scale font with layer height
        float fontSize = Math.min(14f, Math.max(11f, layerH / 2.5f));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < LAYER_NAMES.length; i++) {
            int y = MARGIN_TOP + i * layerH + layerH / 2 + (int)(fontSize / 3);
            g2.drawString(LAYER_NAMES[i], 5, y);
        }
        
        // Draw layer separators
        g2.setColor(GRID_COLOR);
        for (int i = 0; i <= 5; i++) {
            int y = MARGIN_TOP + i * layerH;
            g2.drawLine(MARGIN_LEFT, y, MARGIN_LEFT + graphW, y);
        }
        
        // Time grid with labels
        int gridInterval = windowSeconds <= 30 ? 10 : windowSeconds <= 60 ? 15 : 30;
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
        for (int sec = 0; sec <= windowSeconds; sec += gridInterval) {
            int x = MARGIN_LEFT + (int) ((double) sec / windowSeconds * graphW);
            g2.setColor(GRID_COLOR);
            g2.drawLine(x, MARGIN_TOP, x, MARGIN_TOP + graphH);
            g2.setColor(Color.GRAY);
            String label = "-" + (windowSeconds - sec) + "s";
            int labelWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - labelWidth / 2, MARGIN_TOP + graphH + 14);
        }
        
        // Draw "now" marker with emphasis
        g2.setColor(new Color(120, 120, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(MARGIN_LEFT + graphW, MARGIN_TOP, MARGIN_LEFT + graphW, MARGIN_TOP + graphH);
        g2.setStroke(new BasicStroke(1));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 9f));
        g2.drawString("now", MARGIN_LEFT + graphW - 14, MARGIN_TOP + graphH + 14);
        
        if (snapshot == null) {
            g2.setColor(Color.GRAY);
            g2.drawString("No data", w / 2 - 20, MARGIN_TOP + graphH / 2);
            g2.dispose();
            return;
        }
        
        long windowMs = windowSeconds * 1000L;
        long now = System.currentTimeMillis();
        long startTime = now - windowMs;
        
        // Layer 0: Track events
        drawTrackLayer(g2, MARGIN_LEFT, MARGIN_TOP, graphW, layerH, startTime, windowMs);
        
        // Layer 1: Frame binary band
        drawFrameLayer(g2, MARGIN_LEFT, MARGIN_TOP + layerH, graphW, layerH, startTime, windowMs);
        
        // Layer 2: Stutter/Stuck events
        drawEventLayer(g2, MARGIN_LEFT, MARGIN_TOP + 2 * layerH, graphW, layerH, startTime, windowMs);
        
        // Layer 3: Voice events
        drawVoiceLayer(g2, MARGIN_LEFT, MARGIN_TOP + 3 * layerH, graphW, layerH, startTime, windowMs);
        
        // Layer 4: System (GC) events
        drawSystemLayer(g2, MARGIN_LEFT, MARGIN_TOP + 4 * layerH, graphW, layerH, startTime, windowMs);
        
        // Border
        g2.setColor(Color.GRAY);
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP, graphW, graphH);
        
        g2.dispose();
    }
    
    private void drawTrackLayer(Graphics2D g2, int x, int y, int w, int h, long startTime, long windowMs) {
        if (snapshot.trackEvents() == null) return;
        
        int midY = y + h / 2;
        
        for (TrackEvent e : snapshot.trackEvents()) {
            if (e.timestamp() < startTime) continue;
            
            int ex = x + (int) ((e.timestamp() - startTime) / (double) windowMs * w);
            
            switch (e.type()) {
                case STARTED -> {
                    // Green diamond for start
                    g2.setColor(TRACK_START_COLOR);
                    int[] xp = {ex, ex + 5, ex, ex - 5};
                    int[] yp = {midY - 5, midY, midY + 5, midY};
                    g2.fillPolygon(xp, yp, 4);
                }
                case ENDED -> {
                    // Red square for end
                    g2.setColor(TRACK_END_COLOR);
                    g2.fillRect(ex - 4, midY - 4, 8, 8);
                }
                case EXCEPTION -> {
                    // Yellow warning triangle
                    g2.setColor(TRACK_EXCEPTION_COLOR);
                    int[] xp = {ex, ex + 5, ex - 5};
                    int[] yp = {midY - 5, midY + 5, midY + 5};
                    g2.fillPolygon(xp, yp, 3);
                }
                case STUCK -> {
                    // X marker
                    g2.setColor(CRITICAL_COLOR);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawLine(ex - 4, midY - 4, ex + 4, midY + 4);
                    g2.drawLine(ex - 4, midY + 4, ex + 4, midY - 4);
                    g2.setStroke(new BasicStroke(1));
                }
            }
        }
    }
    
    private void drawFrameLayer(Graphics2D g2, int x, int y, int w, int h, long startTime, long windowMs) {
        FrameMetric[] frames = snapshot.frameHistory();
        if (frames == null || frames.length == 0) return;
        
        int bandY = y + h / 4;
        int bandH = h / 2;
        
        // Aggregate frames into buckets for visualization
        int bucketCount = Math.min(w, frames.length);
        int framesPerBucket = Math.max(1, frames.length / bucketCount);
        
        for (int i = 0; i < bucketCount; i++) {
            int startIdx = i * framesPerBucket;
            int endIdx = Math.min(startIdx + framesPerBucket, frames.length);
            
            int provided = 0;
            int missed = 0;
            for (int j = startIdx; j < endIdx; j++) {
                if (frames[j].frameProvided()) provided++;
                else missed++;
            }
            
            int total = provided + missed;
            if (total == 0) continue;
            
            double provideRatio = (double) provided / total;
            int bx = x + (int) ((double) i / bucketCount * w);
            int bw = Math.max(1, w / bucketCount);
            
            // Color based on ratio - more lenient thresholds for realistic display
            if (provideRatio >= 0.995) {       // 99.5%+ = green (excellent)
                g2.setColor(GOOD_COLOR);
            } else if (provideRatio >= 0.95) { // 95%+ = yellow (minor issues)
                g2.setColor(WARNING_COLOR);
            } else {                           // <95% = red (significant issues)
                g2.setColor(CRITICAL_COLOR);
            }
            
            g2.fillRect(bx, bandY, bw, bandH);
        }
        
        // Draw border around band
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(x, bandY, w, bandH);
    }
    
    private void drawEventLayer(Graphics2D g2, int x, int y, int w, int h, long startTime, long windowMs) {
        int midY = y + h / 2;
        
        // Draw stutter events as circles
        for (StutterEvent e : snapshot.stutterEvents()) {
            if (e.timestamp() < startTime) continue;
            
            int ex = x + (int) ((e.timestamp() - startTime) / (double) windowMs * w);
            int size = switch (e.severity()) {
                case SEVERE -> 10;
                case MODERATE -> 7;
                case MINOR -> 5;
                default -> 3;
            };
            
            g2.setColor(switch (e.severity()) {
                case SEVERE -> CRITICAL_COLOR;
                case MODERATE -> WARNING_COLOR;
                case MINOR -> new Color(255, 200, 100);
                default -> Color.GRAY;
            });
            
            g2.fillOval(ex - size / 2, midY - size / 2, size, size);
        }
        
        // Draw stuck events as X markers
        for (StuckEvent e : snapshot.stuckEvents()) {
            if (e.timestamp() < startTime) continue;
            
            int ex = x + (int) ((e.timestamp() - startTime) / (double) windowMs * w);
            
            g2.setColor(CRITICAL_COLOR);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(ex - 5, midY - 5, ex + 5, midY + 5);
            g2.drawLine(ex - 5, midY + 5, ex + 5, midY - 5);
            g2.setStroke(new BasicStroke(1));
        }
    }
    
    private void drawVoiceLayer(Graphics2D g2, int x, int y, int w, int h, long startTime, long windowMs) {
        VoiceEvent[] events = VoiceConnectionMonitor.getInstance().getRecentEvents(guildId, windowSeconds);
        int midY = y + h / 2;
        
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 11f));
        
        for (VoiceEvent e : events) {
            if (e.timestamp() < startTime) continue;
            
            int ex = x + (int) ((e.timestamp() - startTime) / (double) windowMs * w);
            
            switch (e.type()) {
                case CONNECTED -> {
                    g2.setColor(VOICE_COLOR);
                    g2.drawString("↑", ex - 4, midY + 4);
                }
                case DISCONNECTED -> {
                    g2.setColor(VOICE_COLOR.darker());
                    g2.drawString("↓", ex - 4, midY + 4);
                }
                case CHANNEL_MOVE, RECONNECT -> {
                    g2.setColor(WARNING_COLOR);
                    g2.drawString("↻", ex - 5, midY + 4);
                }
            }
        }
    }
    
    private void drawSystemLayer(Graphics2D g2, int x, int y, int w, int h, long startTime, long windowMs) {
        int midY = y + h / 2;
        
        // Draw GC events as triangles
        for (GCMonitor.GCEvent e : snapshot.gcEvents()) {
            if (e.timestamp() < startTime) continue;
            
            int ex = x + (int) ((e.timestamp() - startTime) / (double) windowMs * w);
            
            g2.setColor(switch (e.severity()) {
                case SEVERE -> CRITICAL_COLOR;
                case MODERATE -> WARNING_COLOR;
                case MINOR, MINIMAL -> GC_COLOR;
            });
            
            int size = switch (e.severity()) {
                case SEVERE -> 10;
                case MODERATE -> 7;
                default -> 5;
            };
            
            int[] xp = {ex, ex + size / 2, ex - size / 2};
            int[] yp = {midY - size / 2, midY + size / 2, midY + size / 2};
            g2.fillPolygon(xp, yp, 3);
        }
    }
}
