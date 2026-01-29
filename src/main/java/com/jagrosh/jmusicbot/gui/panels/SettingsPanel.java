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

import com.jagrosh.jmusicbot.gui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Settings panel for configuring GUI appearance and viewing configuration.
 *
 * @author Arif Banai (arif-banai)
 */
public class SettingsPanel extends JPanel {
    
    private final JComboBox<ThemeManager.Theme> themeComboBox;
    private final JSpinner fontSizeSpinner;
    
    public SettingsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Create main content panel with vertical layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Appearance section
        JPanel appearancePanel = createAppearanceSection();
        contentPanel.add(appearancePanel);
        contentPanel.add(Box.createVerticalStrut(16));
        
        // Configuration section
        JPanel configPanel = createConfigSection();
        contentPanel.add(configPanel);
        contentPanel.add(Box.createVerticalStrut(16));
        
        // Info section
        JPanel infoPanel = createInfoSection();
        contentPanel.add(infoPanel);
        
        // Add filler to push content to top
        contentPanel.add(Box.createVerticalGlue());
        
        // Initialize combo box reference
        themeComboBox = (JComboBox<ThemeManager.Theme>) findComponentByName(appearancePanel, "themeComboBox");
        fontSizeSpinner = (JSpinner) findComponentByName(appearancePanel, "fontSizeSpinner");
        
        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
    }
    
    /**
     * Creates the appearance settings section.
     */
    private JPanel createAppearanceSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Appearance"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Theme selector
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Theme:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JComboBox<ThemeManager.Theme> themeBox = new JComboBox<>(ThemeManager.Theme.values());
        themeBox.setName("themeComboBox");
        themeBox.setSelectedItem(ThemeManager.getCurrentTheme());
        themeBox.addActionListener(e -> {
            ThemeManager.Theme selected = (ThemeManager.Theme) themeBox.getSelectedItem();
            if (selected != null) {
                ThemeManager.setTheme(selected);
            }
        });
        panel.add(themeBox, gbc);
        
        // Font size spinner
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Font Size:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        SpinnerNumberModel fontModel = new SpinnerNumberModel(
            ThemeManager.getBaseFontSize(), 8, 24, 1
        );
        JSpinner fontSpinner = new JSpinner(fontModel);
        fontSpinner.setName("fontSizeSpinner");
        fontSpinner.addChangeListener(e -> {
            int size = (Integer) fontSpinner.getValue();
            ThemeManager.setBaseFontSize(size);
        });
        panel.add(fontSpinner, gbc);
        
        // Note about persistence
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel noteLabel = new JLabel("<html><i>Note: Theme and font size changes are applied immediately but not saved to config.</i></html>");
        noteLabel.setFont(noteLabel.getFont().deriveFont(10f));
        noteLabel.setForeground(Color.GRAY);
        panel.add(noteLabel, gbc);
        
        return panel;
    }
    
    /**
     * Creates the configuration info section.
     */
    private JPanel createConfigSection() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new TitledBorder("Configuration"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        
        // Open config folder button
        JButton openFolderButton = new JButton("Open Config Folder");
        openFolderButton.addActionListener(e -> openConfigFolder());
        buttonPanel.add(openFolderButton);
        
        // Open config file button
        JButton openFileButton = new JButton("Open config.txt");
        openFileButton.addActionListener(e -> openConfigFile());
        buttonPanel.add(openFileButton);
        
        panel.add(buttonPanel, BorderLayout.CENTER);
        
        JLabel pathLabel = new JLabel("<html><i>Config location: " + getConfigPath() + "</i></html>");
        pathLabel.setFont(pathLabel.getFont().deriveFont(10f));
        pathLabel.setForeground(Color.GRAY);
        pathLabel.setBorder(new EmptyBorder(0, 8, 4, 8));
        panel.add(pathLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Creates the system info section.
     */
    private JPanel createInfoSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("System Information"));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 8, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        
        // Java version
        gbc.gridy = 0;
        panel.add(createInfoRow("Java Version:", System.getProperty("java.version")), gbc);
        
        // Java vendor
        gbc.gridy = 1;
        panel.add(createInfoRow("Java Vendor:", System.getProperty("java.vendor")), gbc);
        
        // OS
        gbc.gridy = 2;
        panel.add(createInfoRow("Operating System:", 
            System.getProperty("os.name") + " " + System.getProperty("os.version")), gbc);
        
        // Current theme
        gbc.gridy = 3;
        panel.add(createInfoRow("Current Theme:", ThemeManager.getCurrentTheme().getDisplayName()), gbc);
        
        // FlatLaf version
        gbc.gridy = 4;
        panel.add(createInfoRow("FlatLaf:", "3.7"), gbc);
        
        return panel;
    }
    
    /**
     * Creates an info row with label and value.
     */
    private JPanel createInfoRow(String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD));
        row.add(labelComponent);
        row.add(new JLabel(value));
        return row;
    }
    
    /**
     * Gets the config file path.
     */
    private String getConfigPath() {
        File configFile = new File("config.txt");
        try {
            return configFile.getCanonicalPath();
        } catch (IOException e) {
            return configFile.getAbsolutePath();
        }
    }
    
    /**
     * Opens the config folder in the system file manager.
     */
    private void openConfigFolder() {
        try {
            File configFile = new File("config.txt");
            File folder = configFile.getParentFile();
            if (folder == null) {
                folder = new File(".");
            }
            Desktop.getDesktop().open(folder.getCanonicalFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not open config folder: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Opens the config file in the default text editor.
     */
    private void openConfigFile() {
        try {
            File configFile = new File("config.txt");
            if (configFile.exists()) {
                Desktop.getDesktop().edit(configFile);
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Config file not found: " + configFile.getAbsolutePath(),
                    "File Not Found",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not open config file: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Finds a component by name within a container.
     */
    private Component findComponentByName(Container container, String name) {
        for (Component comp : container.getComponents()) {
            if (name.equals(comp.getName())) {
                return comp;
            }
            if (comp instanceof Container) {
                Component found = findComponentByName((Container) comp, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
