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
package com.jagrosh.jmusicbot.entities;

/**
 * Interface for user interaction capabilities.
 * Provides abstraction for prompting users and showing alerts,
 * allowing different implementations (GUI, CLI, headless, etc.).
 * 
 * @author Arif Banai (arif-banai)
 */
public interface UserInteraction {
    
    /**
     * Severity levels for alert messages.
     */
    enum Level {
        INFO, WARNING, ERROR
    }
    
    /**
     * Prompts the user for input.
     * 
     * @param message The message to display to the user
     * @return The user's input, or null if cancelled/no input
     */
    String prompt(String message);
    
    /**
     * Shows an alert message to the user.
     * 
     * @param level The severity level of the alert
     * @param context The context/category of the alert
     * @param message The message to display
     */
    void alert(Level level, String context, String message);
    
    /**
     * Checks if running in no-GUI mode.
     * 
     * @return true if running without GUI, false otherwise
     */
    boolean isNoGUI();
}
