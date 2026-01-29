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
package com.jagrosh.jmusicbot.gui.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Manages FlatLaf theme initialization and runtime theme switching.
 * This class must be initialized BEFORE any Swing components are created.
 *
 * @author Arif Banai (arif-banai)
 */
public final class ThemeManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);
    
    /**
     * Available themes for the application.
     */
    public enum Theme {
        LIGHT("Light", "light"),
        DARK("Dark", "dark"),
        DARCULA("Darcula", "darcula"),
        INTELLIJ("IntelliJ", "intellij");
        
        private final String displayName;
        private final String configKey;
        
        Theme(String displayName, String configKey) {
            this.displayName = displayName;
            this.configKey = configKey;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getConfigKey() {
            return configKey;
        }
        
        /**
         * Parses a theme from its config key, defaulting to DARK if not found.
         */
        public static Theme fromConfigKey(String key) {
            if (key == null || key.isBlank()) {
                return DARK;
            }
            String normalizedKey = key.toLowerCase().trim();
            for (Theme theme : values()) {
                if (theme.configKey.equals(normalizedKey)) {
                    return theme;
                }
            }
            LOG.warn("Unknown theme '{}', defaulting to Dark", key);
            return DARK;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private static Theme currentTheme = Theme.DARK;
    private static boolean initialized = false;
    private static int baseFontSize = 12;
    
    private ThemeManager() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Initializes FlatLaf with the specified theme.
     * MUST be called before any Swing components are created.
     *
     * @param themeName the theme config key (light, dark, darcula, intellij)
     */
    public static void initialize(String themeName) {
        initialize(Theme.fromConfigKey(themeName), 12);
    }
    
    /**
     * Initializes FlatLaf with the specified theme and font size.
     * MUST be called before any Swing components are created.
     *
     * @param themeName the theme config key (light, dark, darcula, intellij)
     * @param fontSize the base font size
     */
    public static void initialize(String themeName, int fontSize) {
        initialize(Theme.fromConfigKey(themeName), fontSize);
    }
    
    /**
     * Initializes FlatLaf with the specified theme.
     * MUST be called before any Swing components are created.
     *
     * @param theme the theme to apply
     * @param fontSize the base font size
     */
    public static void initialize(Theme theme, int fontSize) {
        if (initialized) {
            LOG.debug("ThemeManager already initialized, switching theme instead");
            setTheme(theme);
            return;
        }
        
        currentTheme = theme;
        baseFontSize = Math.max(8, Math.min(24, fontSize)); // Clamp between 8-24
        
        // Configure FlatLaf before initialization
        configureFlatLaf();
        
        try {
            applyThemeLookAndFeel(theme);
            initialized = true;
            LOG.info("Initialized FlatLaf with {} theme (font size: {})", theme.getDisplayName(), baseFontSize);
        } catch (Exception e) {
            LOG.error("Failed to initialize FlatLaf, falling back to system L&F", e);
            tryFallbackLookAndFeel();
        }
    }
    
    /**
     * Configures FlatLaf global settings before initialization.
     */
    private static void configureFlatLaf() {
        // Enable rounded corners for components
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        
        // Enable menu bar embedding on macOS
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "JMusicBot");
        
        // Enable FlatLaf extras
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ScrollBar.showButtons", true);
        
        // Set default font size using UIManager
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, baseFontSize));
    }
    
    /**
     * Applies the specified theme's Look and Feel.
     */
    private static void applyThemeLookAndFeel(Theme theme) throws UnsupportedLookAndFeelException {
        FlatLaf laf = switch (theme) {
            case LIGHT -> new FlatLightLaf();
            case DARK -> new FlatDarkLaf();
            case DARCULA -> new FlatDarculaLaf();
            case INTELLIJ -> new FlatIntelliJLaf();
        };
        UIManager.setLookAndFeel(laf);
    }
    
    /**
     * Falls back to system Look and Feel if FlatLaf fails.
     */
    private static void tryFallbackLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            initialized = true;
        } catch (Exception e) {
            LOG.error("Failed to set system L&F, using default", e);
        }
    }
    
    /**
     * Changes the theme at runtime and updates all windows.
     *
     * @param theme the new theme to apply
     */
    public static void setTheme(Theme theme) {
        if (theme == currentTheme) {
            return;
        }
        
        try {
            applyThemeLookAndFeel(theme);
            currentTheme = theme;
            
            // Update all existing windows
            updateAllWindows();
            
            LOG.info("Switched to {} theme", theme.getDisplayName());
        } catch (Exception e) {
            LOG.error("Failed to switch to {} theme", theme.getDisplayName(), e);
        }
    }
    
    /**
     * Updates the Look and Feel of all existing windows.
     */
    private static void updateAllWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.pack();
        }
    }
    
    /**
     * Gets the current theme.
     */
    public static Theme getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * Gets all available themes.
     */
    public static List<Theme> getAvailableThemes() {
        return List.of(Theme.values());
    }
    
    /**
     * Checks if a dark theme is currently active.
     */
    public static boolean isDarkTheme() {
        return currentTheme == Theme.DARK || currentTheme == Theme.DARCULA;
    }
    
    /**
     * Gets the current base font size.
     */
    public static int getBaseFontSize() {
        return baseFontSize;
    }
    
    /**
     * Sets the base font size and updates all windows.
     */
    public static void setBaseFontSize(int size) {
        int newSize = Math.max(8, Math.min(24, size));
        if (newSize == baseFontSize) {
            return;
        }
        
        baseFontSize = newSize;
        
        // Update font defaults
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, baseFontSize));
        
        // Reapply theme to update fonts
        try {
            applyThemeLookAndFeel(currentTheme);
            updateAllWindows();
            LOG.info("Updated base font size to {}", baseFontSize);
        } catch (Exception e) {
            LOG.error("Failed to update font size", e);
        }
    }
    
    /**
     * Checks if ThemeManager has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
