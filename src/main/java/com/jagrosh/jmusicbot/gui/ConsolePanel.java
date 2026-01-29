/*
 * Copyright 2017 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.gui;

import com.jagrosh.jmusicbot.utils.ConsoleUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Enhanced console panel with monospace font, search, filtering, and controls.
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 * @author Arif Banai (arif-banai)
 */
public class ConsolePanel extends JPanel {
    
    private final JTextArea textArea;
    private final JScrollPane scrollPane;
    private final JTextField searchField;
    private final JCheckBox autoScrollCheckbox;
    private final JLabel matchCountLabel;
    
    private static final Font CONSOLE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 0, 100);
    
    public ConsolePanel() {
        super(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        
        // Initialize text area with redirected streams
        textArea = ConsoleUtil.redirectSystemStreams();
        configureTextArea();
        
        // Create scroll pane
        scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        // Create toolbar
        JPanel toolbar = createToolbar();
        
        // Create search panel
        JPanel searchPanel = createSearchPanel();
        searchField = (JTextField) ((JPanel) searchPanel.getComponent(0)).getComponent(1);
        matchCountLabel = (JLabel) searchPanel.getComponent(1);
        
        // Top panel with toolbar and search
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.add(toolbar, BorderLayout.WEST);
        topPanel.add(searchPanel, BorderLayout.CENTER);
        
        // Auto-scroll checkbox
        autoScrollCheckbox = new JCheckBox("Auto-scroll", true);
        autoScrollCheckbox.setFont(autoScrollCheckbox.getFont().deriveFont(11f));
        topPanel.add(autoScrollCheckbox, BorderLayout.EAST);
        
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    /**
     * Configures the text area with appropriate settings.
     */
    private void configureTextArea() {
        textArea.setFont(CONSOLE_FONT);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new Insets(4, 4, 4, 4));
        
        // Add caret listener for auto-scroll behavior
        textArea.addCaretListener(e -> {
            if (autoScrollCheckbox != null && autoScrollCheckbox.isSelected()) {
                textArea.setCaretPosition(textArea.getDocument().getLength());
            }
        });
    }
    
    /**
     * Creates the toolbar with action buttons.
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        
        // Clear button
        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear console output");
        clearButton.addActionListener(e -> clearConsole());
        toolbar.add(clearButton);
        
        // Copy button
        JButton copyButton = new JButton("Copy");
        copyButton.setToolTipText("Copy all console output to clipboard");
        copyButton.addActionListener(e -> copyToClipboard());
        toolbar.add(copyButton);
        
        // Copy selection button
        JButton copySelectionButton = new JButton("Copy Selection");
        copySelectionButton.setToolTipText("Copy selected text to clipboard");
        copySelectionButton.addActionListener(e -> copySelectionToClipboard());
        toolbar.add(copySelectionButton);
        
        return toolbar;
    }
    
    /**
     * Creates the search panel with text field and match count.
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        
        JPanel searchInputPanel = new JPanel(new BorderLayout(4, 0));
        JLabel searchLabel = new JLabel("Search:");
        JTextField searchInput = new JTextField();
        searchInput.setToolTipText("Type to search (Enter to find next, Esc to clear)");
        
        searchInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch(searchInput.getText());
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchInput.setText("");
                    clearHighlights();
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_ENTER && e.getKeyCode() != KeyEvent.VK_ESCAPE) {
                    performSearch(searchInput.getText());
                }
            }
        });
        
        searchInputPanel.add(searchLabel, BorderLayout.WEST);
        searchInputPanel.add(searchInput, BorderLayout.CENTER);
        
        JLabel matchCount = new JLabel("0 matches");
        matchCount.setFont(matchCount.getFont().deriveFont(11f));
        matchCount.setForeground(Color.GRAY);
        
        panel.add(searchInputPanel, BorderLayout.CENTER);
        panel.add(matchCount, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * Clears all console output.
     */
    private void clearConsole() {
        SwingUtilities.invokeLater(() -> textArea.setText(""));
    }
    
    /**
     * Copies all console text to the clipboard.
     */
    private void copyToClipboard() {
        String text = textArea.getText();
        if (!text.isEmpty()) {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }
    
    /**
     * Copies selected text to the clipboard.
     */
    private void copySelectionToClipboard() {
        String selectedText = textArea.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            StringSelection selection = new StringSelection(selectedText);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }
    
    /**
     * Performs a search and highlights matching text.
     */
    private void performSearch(String searchText) {
        clearHighlights();
        
        if (searchText == null || searchText.isEmpty()) {
            matchCountLabel.setText("0 matches");
            return;
        }
        
        String content = textArea.getText().toLowerCase();
        String search = searchText.toLowerCase();
        
        Highlighter highlighter = textArea.getHighlighter();
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT_COLOR);
        
        int count = 0;
        int index = 0;
        
        while ((index = content.indexOf(search, index)) >= 0) {
            try {
                highlighter.addHighlight(index, index + search.length(), painter);
                count++;
                index += search.length();
            } catch (BadLocationException e) {
                break;
            }
        }
        
        matchCountLabel.setText(count + " match" + (count != 1 ? "es" : ""));
        
        // Scroll to first match
        if (count > 0) {
            int firstMatch = content.indexOf(search);
            if (firstMatch >= 0) {
                try {
                    Rectangle rect = textArea.modelToView2D(firstMatch).getBounds();
                    textArea.scrollRectToVisible(rect);
                } catch (BadLocationException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Clears all search highlights.
     */
    private void clearHighlights() {
        textArea.getHighlighter().removeAllHighlights();
    }
    
    /**
     * Gets the underlying text area.
     */
    public JTextArea getTextArea() {
        return textArea;
    }
    
    /**
     * Scrolls to the bottom of the console.
     */
    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            textArea.setCaretPosition(textArea.getDocument().getLength());
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
}
