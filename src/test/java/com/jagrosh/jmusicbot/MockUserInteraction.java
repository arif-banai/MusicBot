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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.entities.UserInteraction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Mock implementation of UserInteraction for testing.
 * Allows configuring responses for prompt() calls and captures alert() calls.
 */
public class MockUserInteraction implements UserInteraction {
    private final List<AlertCall> alertCalls = new ArrayList<>();
    private final List<String> promptCalls = new ArrayList<>();
    
    private Function<String, String> promptResponseProvider = null;
    private final List<String> promptResponseQueue = new ArrayList<>();
    private int promptResponseIndex = 0;
    private boolean nogui = false;
    
    /**
     * Sets a function that provides responses to prompt() calls.
     * The function receives the prompt message and returns the response.
     */
    public void setPromptResponse(Function<String, String> provider) {
        this.promptResponseProvider = provider;
    }
    
    /**
     * Sets a simple string response for all prompt() calls.
     */
    public void setPromptResponse(String response) {
        this.promptResponseProvider = msg -> response;
        this.promptResponseQueue.clear();
        this.promptResponseIndex = 0;
    }
    
    /**
     * Adds a response to the queue for sequential prompt() calls.
     * Each call to prompt() will return the next response in the queue.
     */
    public void addPromptResponse(String response) {
        this.promptResponseQueue.add(response);
        this.promptResponseProvider = msg -> {
            if (promptResponseIndex < promptResponseQueue.size()) {
                return promptResponseQueue.get(promptResponseIndex++);
            }
            return null;
        };
    }
    
    /**
     * Sets prompt to return null (simulating cancellation/no input).
     */
    public void setPromptCancelled() {
        this.promptResponseProvider = msg -> null;
    }
    
    /**
     * Sets whether running in no-GUI mode.
     */
    public void setNoGUI(boolean nogui) {
        this.nogui = nogui;
    }
    
    @Override
    public String prompt(String message) {
        promptCalls.add(message);
        if (promptResponseProvider != null) {
            return promptResponseProvider.apply(message);
        }
        return null;
    }
    
    @Override
    public void alert(Level level, String context, String message) {
        alertCalls.add(new AlertCall(level, context, message));
    }
    
    @Override
    public boolean isNoGUI() {
        return nogui;
    }
    
    /**
     * Gets all alert calls made to this mock.
     */
    public List<AlertCall> getAlertCalls() {
        return new ArrayList<>(alertCalls);
    }
    
    /**
     * Gets all prompt calls made to this mock.
     */
    public List<String> getPromptCalls() {
        return new ArrayList<>(promptCalls);
    }
    
    /**
     * Clears all recorded calls.
     */
    public void clear() {
        alertCalls.clear();
        promptCalls.clear();
        promptResponseQueue.clear();
        promptResponseIndex = 0;
    }
    
    /**
     * Gets the last alert call, if any.
     */
    public AlertCall getLastAlert() {
        return alertCalls.isEmpty() ? null : alertCalls.get(alertCalls.size() - 1);
    }
    
    /**
     * Gets the last prompt call, if any.
     */
    public String getLastPrompt() {
        return promptCalls.isEmpty() ? null : promptCalls.get(promptCalls.size() - 1);
    }
    
    /**
     * Represents an alert call.
     */
    public static class AlertCall {
        private final Level level;
        private final String context;
        private final String message;
        
        public AlertCall(Level level, String context, String message) {
            this.level = level;
            this.context = context;
            this.message = message;
        }
        
        public Level getLevel() {
            return level;
        }
        
        public String getContext() {
            return context;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return String.format("AlertCall[level=%s, context=%s, message=%s]", level, context, message);
        }
    }
}
