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

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Factory methods for creating consistently styled Swing components.
 * Ensures uniform appearance across the application.
 *
 * @author Arif Banai (arif-banai)
 */
public final class StyledComponents {
    
    // Standard spacing values
    public static final int PADDING_SMALL = 4;
    public static final int PADDING_MEDIUM = 8;
    public static final int PADDING_LARGE = 16;
    
    // Standard font sizes
    public static final float FONT_SMALL = 10f;
    public static final float FONT_NORMAL = 12f;
    public static final float FONT_LARGE = 14f;
    public static final float FONT_HEADING = 16f;
    
    private StyledComponents() {
        // Utility class - prevent instantiation
    }
    
    // ============ Buttons ============
    
    /**
     * Creates a standard button with text.
     */
    public static JButton createButton(String text) {
        return new JButton(text);
    }
    
    /**
     * Creates a button with text and icon.
     */
    public static JButton createButton(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.setIconTextGap(PADDING_SMALL);
        return button;
    }
    
    /**
     * Creates an icon-only button with tooltip.
     */
    public static JButton createIconButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        return button;
    }
    
    /**
     * Creates a primary action button (styled for emphasis).
     */
    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "default");
        return button;
    }
    
    // ============ Labels ============
    
    /**
     * Creates a standard label.
     */
    public static JLabel createLabel(String text) {
        return new JLabel(text);
    }
    
    /**
     * Creates a label with icon.
     */
    public static JLabel createLabel(String text, Icon icon) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setIconTextGap(PADDING_SMALL);
        return label;
    }
    
    /**
     * Creates a heading label (larger, bold text).
     */
    public static JLabel createHeadingLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, FONT_HEADING));
        return label;
    }
    
    /**
     * Creates a secondary/muted label (smaller, gray text).
     */
    public static JLabel createSecondaryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(FONT_SMALL));
        label.setForeground(Color.GRAY);
        return label;
    }
    
    /**
     * Creates an error label (red text).
     */
    public static JLabel createErrorLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(231, 76, 60));
        return label;
    }
    
    /**
     * Creates a success label (green text).
     */
    public static JLabel createSuccessLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(46, 204, 113));
        return label;
    }
    
    // ============ Panels ============
    
    /**
     * Creates a panel with standard padding.
     */
    public static JPanel createPaddedPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(createPaddingBorder());
        return panel;
    }
    
    /**
     * Creates a panel with specified layout and standard padding.
     */
    public static JPanel createPaddedPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBorder(createPaddingBorder());
        return panel;
    }
    
    /**
     * Creates a titled panel with border.
     */
    public static JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(createTitledBorder(title));
        return panel;
    }
    
    /**
     * Creates a titled panel with specified layout.
     */
    public static JPanel createTitledPanel(String title, LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBorder(createTitledBorder(title));
        return panel;
    }
    
    /**
     * Creates a horizontal flow panel (left-aligned).
     */
    public static JPanel createHorizontalPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, PADDING_SMALL, 0));
    }
    
    /**
     * Creates a vertical box panel.
     */
    public static JPanel createVerticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }
    
    // ============ Scroll Panes ============
    
    /**
     * Creates a scroll pane with standard settings.
     */
    public static JScrollPane createScrollPane(Component view) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }
    
    /**
     * Creates a scroll pane with titled border.
     */
    public static JScrollPane createTitledScrollPane(Component view, String title) {
        JScrollPane scrollPane = createScrollPane(view);
        scrollPane.setBorder(createTitledBorder(title));
        return scrollPane;
    }
    
    // ============ Text Components ============
    
    /**
     * Creates a standard text field.
     */
    public static JTextField createTextField() {
        return new JTextField();
    }
    
    /**
     * Creates a text field with specified columns.
     */
    public static JTextField createTextField(int columns) {
        return new JTextField(columns);
    }
    
    /**
     * Creates a read-only text area with monospace font.
     */
    public static JTextArea createConsoleTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }
    
    // ============ Borders ============
    
    /**
     * Creates a standard padding border.
     */
    public static Border createPaddingBorder() {
        return new EmptyBorder(PADDING_MEDIUM, PADDING_MEDIUM, PADDING_MEDIUM, PADDING_MEDIUM);
    }
    
    /**
     * Creates a padding border with custom size.
     */
    public static Border createPaddingBorder(int padding) {
        return new EmptyBorder(padding, padding, padding, padding);
    }
    
    /**
     * Creates a titled border.
     */
    public static Border createTitledBorder(String title) {
        return new TitledBorder(title);
    }
    
    /**
     * Creates a titled border with inner padding.
     */
    public static Border createTitledBorderWithPadding(String title) {
        return new CompoundBorder(
            new TitledBorder(title),
            new EmptyBorder(PADDING_SMALL, PADDING_SMALL, PADDING_SMALL, PADDING_SMALL)
        );
    }
    
    // ============ Separators ============
    
    /**
     * Creates a horizontal separator.
     */
    public static JSeparator createHorizontalSeparator() {
        return new JSeparator(SwingConstants.HORIZONTAL);
    }
    
    /**
     * Creates a vertical separator with specified height.
     */
    public static JSeparator createVerticalSeparator(int height) {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, height));
        return separator;
    }
    
    // ============ Spacing ============
    
    /**
     * Creates a horizontal spacer.
     */
    public static Component createHorizontalStrut(int width) {
        return Box.createHorizontalStrut(width);
    }
    
    /**
     * Creates a vertical spacer.
     */
    public static Component createVerticalStrut(int height) {
        return Box.createVerticalStrut(height);
    }
    
    /**
     * Creates flexible horizontal glue.
     */
    public static Component createHorizontalGlue() {
        return Box.createHorizontalGlue();
    }
    
    /**
     * Creates flexible vertical glue.
     */
    public static Component createVerticalGlue() {
        return Box.createVerticalGlue();
    }
}
