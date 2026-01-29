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
package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registry that tracks slash command definitions and intelligently upserts
 * commands to Discord only when they have changed.
 */
public class SlashCommandRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger(SlashCommandRegistry.class);
    private static final String HASH_FILE = ".slashcommands.hash";

    /**
     * Registers slash commands with Discord if they have changed since the last registration.
     * 
     * @param jda the JDA instance
     * @param client the CommandClient containing slash commands
     */
    public static void registerIfChanged(JDA jda, CommandClient client)
    {
        List<SlashCommand> slashCommands = client.getSlashCommands();
        if (slashCommands == null || slashCommands.isEmpty())
        {
            LOG.info("No slash commands to register");
            return;
        }

        String currentHash = calculateHash(slashCommands);
        String storedHash = loadStoredHash();

        if (currentHash.equals(storedHash))
        {
            LOG.info("Slash commands unchanged, skipping registration ({} commands)", slashCommands.size());
            return;
        }

        LOG.info("Slash commands changed, registering {} commands with Discord...", slashCommands.size());

        // Build the command data for registration
        List<CommandData> commandData = slashCommands.stream()
                .map(SlashCommand::buildCommandData)
                .collect(Collectors.toList());

        // Register commands globally
        jda.updateCommands()
                .addCommands(commandData)
                .queue(
                        commands -> {
                            LOG.info("Successfully registered {} slash commands globally", commands.size());
                            saveHash(currentHash);
                        },
                        error -> LOG.error("Failed to register slash commands: {}", error.getMessage())
                );
    }

    /**
     * Forces registration of all slash commands regardless of whether they've changed.
     * 
     * @param jda the JDA instance
     * @param client the CommandClient containing slash commands
     */
    public static void forceRegister(JDA jda, CommandClient client)
    {
        List<SlashCommand> slashCommands = client.getSlashCommands();
        if (slashCommands == null || slashCommands.isEmpty())
        {
            LOG.info("No slash commands to register");
            return;
        }

        String currentHash = calculateHash(slashCommands);
        LOG.info("Force registering {} slash commands with Discord...", slashCommands.size());

        List<CommandData> commandData = slashCommands.stream()
                .map(SlashCommand::buildCommandData)
                .collect(Collectors.toList());

        jda.updateCommands()
                .addCommands(commandData)
                .queue(
                        commands -> {
                            LOG.info("Successfully registered {} slash commands globally", commands.size());
                            saveHash(currentHash);
                        },
                        error -> LOG.error("Failed to register slash commands: {}", error.getMessage())
                );
    }

    /**
     * Calculates a hash of all slash command definitions.
     * The hash is based on command name, description, and options.
     */
    private static String calculateHash(List<SlashCommand> commands)
    {
        StringBuilder sb = new StringBuilder();
        for (SlashCommand cmd : commands)
        {
            SlashCommandData data = (SlashCommandData) cmd.buildCommandData();
            sb.append(data.getName()).append(":");
            sb.append(data.getDescription()).append(":");
            // Include options in hash
            data.getOptions().forEach(option -> {
                sb.append(option.getName()).append(",");
                sb.append(option.getDescription()).append(",");
                sb.append(option.getType().name()).append(",");
                sb.append(option.isRequired()).append(",");
                sb.append(option.isAutoComplete()).append(";");
            });
            // Include subcommands if any
            data.getSubcommands().forEach(sub -> {
                sb.append("sub:").append(sub.getName()).append(",");
                sb.append(sub.getDescription()).append(";");
            });
            sb.append("|");
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes)
            {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            LOG.warn("SHA-256 not available, using simple hash");
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /**
     * Loads the previously stored hash from file.
     */
    private static String loadStoredHash()
    {
        try
        {
            Path path = OtherUtil.getPath(HASH_FILE);
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
        }
        catch (NoSuchFileException e)
        {
            LOG.debug("No stored slash command hash found (first run)");
            return "";
        }
        catch (IOException e)
        {
            LOG.warn("Failed to read stored slash command hash: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Saves the hash to file for future comparison.
     */
    private static void saveHash(String hash)
    {
        try
        {
            Path path = OtherUtil.getPath(HASH_FILE);
            Files.write(path, hash.getBytes(StandardCharsets.UTF_8));
            LOG.debug("Saved slash command hash to {}", path.toAbsolutePath());
        }
        catch (IOException e)
        {
            LOG.warn("Failed to save slash command hash: {}", e.getMessage());
        }
    }

    /**
     * Clears the stored hash, forcing re-registration on next startup.
     */
    public static void clearHash()
    {
        try
        {
            Path path = OtherUtil.getPath(HASH_FILE);
            Files.deleteIfExists(path);
            LOG.info("Cleared slash command hash");
        }
        catch (IOException e)
        {
            LOG.warn("Failed to clear slash command hash: {}", e.getMessage());
        }
    }
}
