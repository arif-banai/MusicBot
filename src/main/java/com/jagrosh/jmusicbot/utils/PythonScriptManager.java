package com.jagrosh.jmusicbot.utils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager utility for locating Python interpreter binaries and managing embedded script extraction.
 * <p>
 * Handles extraction and version synchronization of the embedded {@code scraper.py} script from JAR resources
 * to the working directory using SHA-256 hash verification. Provides OS-aware resolution of Python executables,
 * prioritizing custom user configuration, system properties, local virtual environments ({@code .venv}),
 * and system fallback paths.
 * </p>
 */
public class PythonScriptManager
{
    private static final Logger LOG = LoggerFactory.getLogger(PythonScriptManager.class);

    private static final String SCRIPT_NAME = "scraper.py";
    private static final String SCRIPT_RESOURCE_PATH = "/python/" + SCRIPT_NAME;
    private static File scriptFile;

    /**
     * Extracts the embedded {@code scraper.py} script from JAR resources to the working directory.
     * <p>
     * Performs SHA-256 hash validation between the embedded JAR resource and any existing local target file.
     * If the local file is missing or contains a version mismatch, it is atomically updated/extracted.
     * </p>
     *
     * @return {@code true} if the script is extracted, up to date, and ready for execution; {@code false} if extraction fails
     */
    public static synchronized boolean initScript()
    {
        try (InputStream resourceStream = PythonScriptManager.class.getResourceAsStream(SCRIPT_RESOURCE_PATH)) 
        {
            if (resourceStream == null) 
            {
                LOG.error("Could not find [{}] inside JAR resources!", SCRIPT_RESOURCE_PATH);
                return false;
            }

            byte[] resourceBytes = resourceStream.readAllBytes();
            String resourceHash = calculateSha256(resourceBytes);

            File targetFile = new File(System.getProperty("user.dir"), SCRIPT_NAME);

            if (targetFile.exists()) 
            {
                byte[] localBytes = Files.readAllBytes(targetFile.toPath());
                String localHash = calculateSha256(localBytes);

                if (resourceHash.equalsIgnoreCase(localHash)) 
                {
                    LOG.debug("{} is up to date (SHA-256 match).", SCRIPT_NAME);
                    scriptFile = targetFile;
                    return true;
                }
                LOG.info("{} version mismatch detected. Updating local copy from JAR...", SCRIPT_NAME);
            } 
            else 
            {
                LOG.info("Extracting {} from JAR to [{}]", SCRIPT_NAME, targetFile.getAbsolutePath());
            }

            Files.copy(
                PythonScriptManager.class.getResourceAsStream(SCRIPT_RESOURCE_PATH), 
                targetFile.toPath(), 
                StandardCopyOption.REPLACE_EXISTING
            );

            scriptFile = targetFile;
            return true;
        } 
        catch (Exception e) 
        {
            LOG.error("Failed to extract {} from JAR: {}", SCRIPT_NAME, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Retrieves the {@link File} pointer to the extracted {@code scraper.py} script on disk.
     * <p>
     * Lazily invokes {@link #initScript()} if the file reference is null or does not exist on disk.
     * </p>
     *
     * @return a {@link File} pointing to {@code scraper.py}, or {@code null} if script extraction failed
     */
    public static synchronized File getScriptFile()
    {
        if (scriptFile == null || !scriptFile.exists())
        {
            if (!initScript())
            {
                return null;
            }
        }
        return scriptFile;
    }

    /**
     * Computes a lower-case hexadecimal SHA-256 checksum string for a byte array payload.
     *
     * @param data the byte array input to digest
     * @return the 64-character hexadecimal SHA-256 hash string
     * @throws NoSuchAlgorithmException if the SHA-256 MessageDigest algorithm is unavailable in the JVM environment
     */
    private static String calculateSha256(byte[] data) throws NoSuchAlgorithmException 
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) 
        {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) 
            {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * Resolves the path to the Python interpreter executable using a prioritized fallback chain.
     * <p>
     * <b>Resolution Priority:</b>
     * <ol>
     *   <li>Explicitly provided configuration path parameter (if non-blank).</li>
     *   <li>System property override ({@code -Dbot.pythonpath=...}).</li>
     *   <li>Local virtual environment binary relative to working directory ({@code .venv/bin/python} or {@code .venv\Scripts\python.exe}).</li>
     *   <li>OS system executable fallback ({@code "python3"} on Unix/macOS or {@code "python"} on Windows).</li>
     * </ol>
     * </p>
     *
     * @param configPythonPath a custom Python executable path from configuration, or {@code null}/blank to use fallbacks
     * @return the resolved absolute file path or system command string for executing Python
     */
    public static String getPythonExecutablePath(String configPythonPath) 
    {
        if (configPythonPath != null && !configPythonPath.isBlank()) 
        {
            LOG.debug("Using configured Python path: {}", configPythonPath);
            return configPythonPath;
        }

        String sysPropertyPath = System.getProperty("bot.pythonpath");
        if (sysPropertyPath != null && !sysPropertyPath.isBlank()) 
        {
            LOG.debug("Using system property Python path: {}", sysPropertyPath);
            return sysPropertyPath;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        String venvRelPath = isWindows ? ".venv\\Scripts\\python.exe" : ".venv/bin/python";
        File venvFile = new File(System.getProperty("user.dir"), venvRelPath);

        if (venvFile.exists() && venvFile.isFile()) 
        {
            LOG.debug("Found virtual environment Python binary at: {}", venvFile.getAbsolutePath());
            return venvFile.getAbsolutePath();
        }

        String systemFallback = isWindows ? "python" : "python3";
        LOG.warn(".venv Python binary not found at [{}]. Falling back to system executable: '{}'", 
                 venvFile.getAbsolutePath(), systemFallback);

        return systemFallback;
    }
    
    /**
     * Overload for {@link #getPythonExecutablePath(String)} without an explicit configuration path.
     *
     * @return the resolved absolute file path or system command string for executing Python
     */
    public static String getPythonExecutablePath() 
    {
        return getPythonExecutablePath(null);
    }
}