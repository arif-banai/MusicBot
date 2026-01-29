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
package com.jagrosh.jmusicbot.config.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jmusicbot.config.diagnostics.ConfigDiagnostics;
import com.jagrosh.jmusicbot.config.io.ConfigIO;
import com.jagrosh.jmusicbot.config.model.ConfigUpdateType;
import com.jagrosh.jmusicbot.config.render.ConfigRenderer;
import com.typesafe.config.Config;

/**
 * Generates updated configuration files to guide users through config updates.
 * 
 * @author Arif Banai (arif-banai)
 */
public class ConfigUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUpdater.class);
    private static final String BACKUP_SUFFIX = ".bak";
    
    /**
     * Updates the config file in place by backing up the original and writing the migrated config.
     * The original config file is backed up with a .bak extension. If a backup already exists,
     * a numbered suffix is added (e.g., .bak1, .bak2) to avoid overwriting existing backups.
     * 
     * @param userConfigPath the path to the user's config file
     * @param migratedUserConfig the migrated user configuration (without defaults merged)
     * @param diagnostics the diagnostic report
     * @param updateType the type of update (migration, repair, or update)
     * @return the path to the updated config file, or null if update failed
     */
    public static Path generateUpdatedConfig(Path userConfigPath, Config migratedUserConfig, 
                                             ConfigDiagnostics.Report diagnostics,
                                             ConfigUpdateType updateType) {
        try {
            // Normalize to absolute path
            Path normalizedPath = userConfigPath.toAbsolutePath().normalize();
            
            // Backup the original config file if it exists
            if (normalizedPath.toFile().exists()) {
                Path backupPath = findAvailableBackupPath(normalizedPath);
                Files.copy(normalizedPath, backupPath);
                LOGGER.info("Backed up original config to: {}", backupPath);
            }
            
            // Generate HOCON content using ConfigDocument to preserve template style
            String content = ConfigRenderer.generateConfigContent(migratedUserConfig, diagnostics, updateType);
            
            // Write the migrated config to the original location
            ConfigIO.writeConfigFile(normalizedPath, content);
            
            LOGGER.info("Updated config file: {}", normalizedPath);
            return normalizedPath;
        } catch (IOException e) {
            LOGGER.error("Failed to update config file: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Finds an available backup path that doesn't overwrite any existing file.
     * Starts with .bak, then tries .bak1, .bak2, etc.
     * 
     * @param configPath the path to the config file being backed up
     * @return a path that doesn't exist yet
     */
    public static Path findAvailableBackupPath(Path configPath) {
        String fileName = configPath.getFileName().toString();
        Path baseBackupPath = configPath.resolveSibling(fileName + BACKUP_SUFFIX);
        
        if (!Files.exists(baseBackupPath)) {
            return baseBackupPath;
        }
        
        // Find the next available numbered backup
        int counter = 1;
        Path numberedBackupPath;
        do {
            numberedBackupPath = configPath.resolveSibling(fileName + BACKUP_SUFFIX + counter);
            counter++;
        } while (Files.exists(numberedBackupPath));
        
        return numberedBackupPath;
    }
    
    /**
     * Checks if any backup of the config file exists (including numbered backups like .bak1, .bak2, etc.).
     * 
     * @param userConfigPath the path to the user's config file
     * @return true if any backup file exists
     */
    public static boolean backupExists(Path userConfigPath) {
        Path normalizedPath = userConfigPath.toAbsolutePath().normalize();
        String fileName = normalizedPath.getFileName().toString();
        Path baseBackupPath = normalizedPath.resolveSibling(fileName + BACKUP_SUFFIX);
        
        // Check for base backup (.bak)
        if (Files.exists(baseBackupPath)) {
            return true;
        }
        
        // Check for numbered backups (.bak.1, .bak.2, etc.)
        Path parent = normalizedPath.getParent();
        if (parent != null) {
            try {
                return Files.list(parent)
                    .anyMatch(path -> path.getFileName().toString().startsWith(fileName + BACKUP_SUFFIX));
            } catch (IOException e) {
                LOGGER.warn("Failed to check for numbered backups: {}", e.getMessage());
            }
        }
        
        return false;
    }
}
