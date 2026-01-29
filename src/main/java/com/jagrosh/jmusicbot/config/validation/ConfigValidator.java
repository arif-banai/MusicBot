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
package com.jagrosh.jmusicbot.config.validation;

import java.nio.file.Path;

import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;

/**
 * Handles validation of required configuration values.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigValidator {
    private static final String CONTEXT = "Config";
    
    /**
     * Validates the bot token, prompting the user if missing or invalid.
     * 
     * @return ValidationResult containing the token (or null if invalid) and whether a write is needed
     */
    public static ValidationResult validateToken(String token, UserInteraction userInteraction, Path configPath) {
        if (token == null || token.isEmpty() || token.equalsIgnoreCase("BOT_TOKEN_HERE")) {
            String newToken = userInteraction.prompt("Please provide a bot token."
                    + "\nInstructions for obtaining a token can be found here:"
                    + "\nhttps://github.com/jagrosh/MusicBot/wiki/Getting-a-Bot-Token."
                    + "\nBot Token: ");
            if (newToken == null) {
                alertWithConfigLocation(userInteraction, Level.WARNING, 
                        "No token provided! Exiting.", configPath);
                return ValidationResult.invalid();
            }
            return ValidationResult.validWithWrite(newToken);
        }
        return ValidationResult.valid(token);
    }
    
    /**
     * Validates the bot owner ID, prompting the user if missing or invalid.
     * 
     * @return ValidationResult containing the owner ID (or null if invalid) and whether a write is needed
     */
    public static ValidationResult validateOwner(Long owner, UserInteraction userInteraction, Path configPath) {
        if (owner == null || owner <= 0) {
            try {
                String ownerInput = userInteraction.prompt("Owner ID was missing, or the provided owner ID is not valid."
                        + "\nPlease provide the User ID of the bot's owner."
                        + "\nInstructions for obtaining your User ID can be found here:"
                        + "\nhttps://github.com/jagrosh/MusicBot/wiki/Finding-Your-User-ID"
                        + "\nOwner User ID: ");
                if (ownerInput != null) {
                    long newOwner = Long.parseLong(ownerInput);
                    if (newOwner > 0) {
                        return ValidationResult.validWithWrite(newOwner);
                    }
                }
            } catch (NumberFormatException | NullPointerException ex) {
                // Fall through to error
            }
            alertWithConfigLocation(userInteraction, Level.ERROR, 
                    "Invalid User ID! Exiting.", configPath);
            return ValidationResult.invalid();
        }
        return ValidationResult.valid(owner);
    }
    
    /**
     * Shows an alert with the config file location appended.
     */
    private static void alertWithConfigLocation(UserInteraction userInteraction, Level level, 
            String message, Path configPath) {
        userInteraction.alert(level, CONTEXT, 
                message + "\n\nConfig Location: " + configPath.toAbsolutePath().toString());
    }
    
    /**
     * Result of a validation operation.
     */
    public static class ValidationResult {
        private final Object value;
        private final boolean valid;
        private final boolean needsWrite;
        
        public ValidationResult(Object value, boolean valid, boolean needsWrite) {
            this.value = value;
            this.valid = valid;
            this.needsWrite = needsWrite;
        }
        
        /** Creates a valid result with no write needed. */
        public static ValidationResult valid(Object value) {
            return new ValidationResult(value, true, false);
        }
        
        /** Creates a valid result that requires a config file write. */
        public static ValidationResult validWithWrite(Object value) {
            return new ValidationResult(value, true, true);
        }
        
        /** Creates an invalid result. */
        public static ValidationResult invalid() {
            return new ValidationResult(null, false, false);
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public boolean needsWrite() {
            return needsWrite;
        }
    }
}
