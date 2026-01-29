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

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for loading and caching icons, with support for FlatLaf SVG icons.
 * Provides commonly used icons for the application.
 *
 * @author Arif Banai (arif-banai)
 */
public final class IconFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(IconFactory.class);
    
    // Icon cache for reuse
    private static final Map<String, Icon> iconCache = new ConcurrentHashMap<>();
    
    // Default icon size
    private static final int DEFAULT_SIZE = 16;
    
    private IconFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Available icon types for the application.
     */
    public enum IconType {
        // Status icons
        CONNECTED("connected", "icons/connected.svg"),
        DISCONNECTED("disconnected", "icons/disconnected.svg"),
        
        // Tab icons
        CONSOLE("console", "icons/console.svg"),
        STATUS("status", "icons/status.svg"),
        SETTINGS("settings", "icons/settings.svg"),
        
        // Action icons
        PLAY("play", "icons/play.svg"),
        PAUSE("pause", "icons/pause.svg"),
        STOP("stop", "icons/stop.svg"),
        SKIP("skip", "icons/skip.svg"),
        VOLUME("volume", "icons/volume.svg"),
        
        // Misc icons
        SEARCH("search", "icons/search.svg"),
        CLEAR("clear", "icons/clear.svg"),
        COPY("copy", "icons/copy.svg"),
        REFRESH("refresh", "icons/refresh.svg");
        
        private final String name;
        private final String path;
        
        IconType(String name, String path) {
            this.name = name;
            this.path = path;
        }
        
        public String getName() {
            return name;
        }
        
        public String getPath() {
            return path;
        }
    }
    
    /**
     * Gets an icon by type with default size.
     *
     * @param type the icon type
     * @return the icon, or a placeholder if not found
     */
    public static Icon getIcon(IconType type) {
        return getIcon(type, DEFAULT_SIZE);
    }
    
    /**
     * Gets an icon by type with specified size.
     *
     * @param type the icon type
     * @param size the icon size
     * @return the icon, or a placeholder if not found
     */
    public static Icon getIcon(IconType type, int size) {
        String cacheKey = type.getName() + "_" + size;
        
        return iconCache.computeIfAbsent(cacheKey, k -> {
            try {
                // Try to load as SVG first
                FlatSVGIcon icon = new FlatSVGIcon(type.getPath(), size, size);
                if (icon.hasFound()) {
                    return icon;
                }
            } catch (Exception e) {
                LOG.debug("Could not load SVG icon: {}", type.getPath());
            }
            
            // Return a simple colored placeholder icon
            return createPlaceholderIcon(type, size);
        });
    }
    
    /**
     * Gets a FlatLaf built-in icon by name.
     * These icons are included with FlatLaf and adapt to the current theme.
     *
     * @param name the icon name (e.g., "actions/search.svg")
     * @return the icon, or null if not found
     */
    public static Icon getFlatLafIcon(String name) {
        return getFlatLafIcon(name, DEFAULT_SIZE);
    }
    
    /**
     * Gets a FlatLaf built-in icon by name with specified size.
     *
     * @param name the icon name
     * @param size the icon size
     * @return the icon, or null if not found
     */
    public static Icon getFlatLafIcon(String name, int size) {
        String cacheKey = "flatlaf_" + name + "_" + size;
        
        return iconCache.computeIfAbsent(cacheKey, k -> {
            try {
                FlatSVGIcon icon = new FlatSVGIcon("com/formdev/flatlaf/icons/" + name, size, size);
                if (icon.hasFound()) {
                    return icon;
                }
            } catch (Exception e) {
                LOG.debug("Could not load FlatLaf icon: {}", name);
            }
            return null;
        });
    }
    
    /**
     * Creates a simple placeholder icon when the real icon is not available.
     */
    private static Icon createPlaceholderIcon(IconType type, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw a colored circle as placeholder
                Color color = getPlaceholderColor(type);
                g2d.setColor(color);
                g2d.fillOval(x + 2, y + 2, size - 4, size - 4);
                
                // Draw first letter of icon name
                g2d.setColor(Color.WHITE);
                g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, size * 0.5f));
                String letter = type.getName().substring(0, 1).toUpperCase();
                FontMetrics fm = g2d.getFontMetrics();
                int textX = x + (size - fm.stringWidth(letter)) / 2;
                int textY = y + (size + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(letter, textX, textY);
                
                g2d.dispose();
            }
            
            @Override
            public int getIconWidth() {
                return size;
            }
            
            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
    
    /**
     * Gets a color for the placeholder icon based on icon type.
     */
    private static Color getPlaceholderColor(IconType type) {
        return switch (type) {
            case CONNECTED -> new Color(46, 204, 113);  // Green
            case DISCONNECTED, STOP, CLEAR -> new Color(231, 76, 60);  // Red
            case PLAY, SKIP -> new Color(52, 152, 219);  // Blue
            case PAUSE -> new Color(241, 196, 15);  // Yellow
            case SETTINGS -> new Color(155, 89, 182);  // Purple
            case CONSOLE -> new Color(52, 73, 94);  // Dark blue-gray
            case STATUS -> new Color(26, 188, 156);  // Teal
            default -> new Color(149, 165, 166);  // Gray
        };
    }
    
    /**
     * Clears the icon cache.
     */
    public static void clearCache() {
        iconCache.clear();
    }
}
