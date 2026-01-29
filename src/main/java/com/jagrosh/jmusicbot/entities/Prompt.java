/*
 * Copyright 2018 John Grosh (jagrosh)
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

import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Scanner;

/**
 * Implementation of UserInteraction that provides GUI dialogs or CLI interaction
 * based on the runtime environment.
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Prompt implements UserInteraction
{
    private final String title;
    private final String noguiMessage;
    
    private boolean nogui;
    private boolean noprompt;
    private Scanner scanner;
    
    public Prompt(String title)
    {
        this(title, null);
    }
    
    public Prompt(String title, String noguiMessage)
    {
        this(title, noguiMessage, 
             isPropertyEnabled("nogui"), 
             isPropertyEnabled("noprompt"));
    }
    
    private static boolean isPropertyEnabled(String propertyName)
    {
        String prop = System.getProperty(propertyName);
        return prop != null && !"false".equalsIgnoreCase(prop);
    }
    
    public Prompt(String title, String noguiMessage, boolean nogui, boolean noprompt)
    {
        this.title = title;
        this.noguiMessage = noguiMessage == null ? "Switching to nogui mode. You can manually start in nogui mode by including the -Dnogui=true flag." : noguiMessage;
        this.nogui = nogui;
        this.noprompt = noprompt;
    }
    
    @Override
    public boolean isNoGUI()
    {
        return nogui;
    }

    @Override
    public String prompt(String content) {
        if (noprompt)
            return null;

        if (nogui)
            return promptCli(content);

        try {
            return JOptionPane.showInputDialog(null, content, title, JOptionPane.QUESTION_MESSAGE);
        } catch (Exception e) {
            alert(UserInteraction.Level.WARNING, title, noguiMessage);
            return promptCli(content); // preserves your original “retry via CLI” behavior
        }
    }

    @Override
    public void alert(UserInteraction.Level level, String context, String message) {
        if (nogui) {
            logAlert(level, context, message);
            return;
        }

        try {
            JOptionPane.showMessageDialog(
                null,
                htmlMessage(message),
                title,
                optionFor(level)
            );
        } catch (Exception e) {
            nogui = true;
            alert(UserInteraction.Level.WARNING, context, noguiMessage);
            alert(level, context, message);
        }
    }

    private void logAlert(UserInteraction.Level level, String context, String message) {
        var log = LoggerFactory.getLogger(context);
        switch (level) {
            case WARNING -> log.warn(message);
            case ERROR   -> log.error(message);
            default      -> log.info(message);
        }
    }

    private int optionFor(UserInteraction.Level level) {
        return switch (level) {
            case INFO    -> JOptionPane.INFORMATION_MESSAGE;
            case WARNING -> JOptionPane.WARNING_MESSAGE;
            case ERROR   -> JOptionPane.ERROR_MESSAGE;
            default      -> JOptionPane.PLAIN_MESSAGE;
        };
    }

    private static String htmlMessage(String message) {
        return "<html><body><p style='width:400px;'>" + message + "</p></body></html>";
    }

    

    private String promptCli(String content) {
        if (scanner == null)
            scanner = new Scanner(System.in);

        try {
            System.out.println(content);
            return scanner.hasNextLine()
                ? scanner.nextLine()
                : null;
        } catch (Exception e) {
            alert(UserInteraction.Level.ERROR, title, "Unable to read input from command line.");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @deprecated Use {@link UserInteraction.Level} instead.
     *             This alias is kept for backward compatibility.
     */
    @Deprecated(since = "0.5.0", forRemoval = true)
    public static class Level {
        public static final UserInteraction.Level INFO = UserInteraction.Level.INFO;
        public static final UserInteraction.Level WARNING = UserInteraction.Level.WARNING;
        public static final UserInteraction.Level ERROR = UserInteraction.Level.ERROR;
        
        private Level() {} // Prevent instantiation
    }
}
