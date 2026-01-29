/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.utils;

import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class OtherUtil
{
    public final static String NEW_VERSION_AVAILABLE = "There is a new version of JMusicBot available!\n"
                    + "Current version: %s\n"
                    + "New Version: %s\n\n"
                    + "Please visit https://github.com/arif-banai/MusicBot/releases/latest to get the latest release.";
    private final static String WINDOWS_INVALID_PATH = "c:\\windows\\system32\\";
    
    /**
     * gets a Path from a String
     * also fixes the windows tendency to try to start in system32
     * any time the bot tries to access this path, it will instead start in the location of the jar file
     * 
     * @param path the string path
     * @return the Path object
     */
    public static Path getPath(String path)
    {
        Path result = Paths.get(path);
        // special logic to prevent trying to access system32
        if(result.toAbsolutePath().toString().toLowerCase().startsWith(WINDOWS_INVALID_PATH))
        {
            try
            {
                result = Paths.get(new File(JMusicBot.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath() + File.separator + path);
            }
            catch(URISyntaxException ignored) {}
        }
        return result;
    }
    
    /**
     * Loads a resource from the jar as a string
     * 
     * @param clazz class to use for loading the resource
     * @param name name of resource
     * @return string containing the contents of the resource
     */
    public static String loadResource(Class<?> clazz, String name)
    {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(name), StandardCharsets.UTF_8)))
        {
            StringBuilder sb = new StringBuilder();
            reader.lines().forEach(line -> sb.append("\r\n").append(line));
            return sb.toString().trim();
        }
        catch(IOException ignored)
        {
            return null;
        }
    }
    
    /**
     * Loads image data from a URL
     * 
     * @param url url of image
     * @return inputstream of url
     */
    public static InputStream imageFromUrl(String url)
    {
        if(url==null)
            return null;
        try 
        {
            URL u = new URL(url);
            URLConnection urlConnection = u.openConnection();
            urlConnection.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/49.0.2623.112 Safari/537.36");
            return urlConnection.getInputStream();
        }
        catch(IOException | IllegalArgumentException ignore) {}
        return null;
    }
    
    /**
     * Parses an activity from a string
     * 
     * @param game the game, including the action such as 'playing' or 'watching'
     * @return the parsed activity
     */
    public static Activity parseGame(String game)
    {
        if(game==null || game.trim().isEmpty() || game.trim().equalsIgnoreCase("default"))
            return null;
        String lower = game.toLowerCase();
        if(lower.startsWith("playing"))
            return Activity.playing(makeNonEmpty(game.substring(7).trim()));
        if(lower.startsWith("listening to"))
            return Activity.listening(makeNonEmpty(game.substring(12).trim()));
        if(lower.startsWith("listening"))
            return Activity.listening(makeNonEmpty(game.substring(9).trim()));
        if(lower.startsWith("watching"))
            return Activity.watching(makeNonEmpty(game.substring(8).trim()));
        if(lower.startsWith("streaming"))
        {
            String[] parts = game.substring(9).trim().split("\\s+", 2);
            if(parts.length == 2)
            {
                return Activity.streaming(makeNonEmpty(parts[1]), "https://twitch.tv/"+parts[0]);
            }
        }
        return Activity.playing(game);
    }
   
    public static String makeNonEmpty(String str)
    {
        return str == null || str.isEmpty() ? "\u200B" : str;
    }
    
    public static OnlineStatus parseStatus(String status)
    {
        if(status==null || status.trim().isEmpty())
            return OnlineStatus.ONLINE;
        OnlineStatus st = OnlineStatus.fromKey(status);
        return st == null ? OnlineStatus.ONLINE : st;
    }
    
    public static void checkJavaVersion(UserInteraction userInteraction)
    {
        if(!System.getProperty("java.vm.name").contains("64"))
            userInteraction.alert(Level.WARNING, "Java Version", 
                    "It appears that you may not be using a supported Java version. Please use 64-bit java.");
    }
    
    public static String getCurrentVersion()
    {
        if(JMusicBot.class.getPackage()!=null && JMusicBot.class.getPackage().getImplementationVersion()!=null)
            return JMusicBot.class.getPackage().getImplementationVersion();
        else
            return "UNKNOWN";
    }
    
    public static String getLatestVersion()
    {
        return getLatestVersion("https://api.github.com/repos/arif-banai/MusicBot");
    }
    
    /**
     * Gets the latest non-prerelease version from GitHub releases API.
     * This method is public to allow testing with mock servers.
     * 
     * @param baseUrl the base URL for the GitHub API (e.g., "https://api.github.com/repos/arif-banai/MusicBot")
     * @return the latest non-prerelease version tag (without 'v' prefix), or null if not found
     */
    public static String getLatestVersion(String baseUrl)
    {
        try
        {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build();
            // First, try to get the latest release
            Response response = client.newCall(new Request.Builder().get()
                    .url(baseUrl + "/releases/latest").build())
                    .execute();
            ResponseBody body = response.body();
            if(body != null)
            {
                try(Reader reader = body.charStream())
                {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode obj = objectMapper.readTree(reader);
                    if(obj != null && obj.has("tag_name"))
                    {
                        // Check if this is a pre-release
                        boolean isPrerelease = obj.has("prerelease") && obj.get("prerelease").asBoolean();
                        
                        if(!isPrerelease)
                        {
                            // Not a pre-release, use it
                            String tag = obj.get("tag_name").asText();
                            if(tag.startsWith("v"))
                                tag = tag.substring(1);
                            return tag;
                        }
                    }
                }
                finally
                {
                    response.close();
                }
            }
            
            // If the latest release was a pre-release, fetch recent releases and find the latest non-prerelease
            // Limit to first 10 releases (sorted by date, newest first) - should be more than enough
            Response allReleasesResponse = client.newCall(new Request.Builder().get()
                    .url(baseUrl + "/releases?per_page=10").build())
                    .execute();
            ResponseBody allReleasesBody = allReleasesResponse.body();
            if(allReleasesBody != null)
            {
                try(Reader reader = allReleasesBody.charStream())
                {
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode releases = objectMapper.readTree(reader);
                    if(releases != null && releases.isArray())
                    {
                        // Find the first non-prerelease release
                        for(JsonNode release : releases)
                        {
                            if(release.has("prerelease") && release.get("prerelease").asBoolean())
                                continue; // Skip pre-releases
                            
                            if(release.has("tag_name"))
                            {
                                String tag = release.get("tag_name").asText();
                                if(tag.startsWith("v"))
                                    tag = tag.substring(1);
                                return tag;
                            }
                        }
                    }
                }
                finally
                {
                    allReleasesResponse.close();
                }
            }
            return null;
        }
        catch(IOException | NullPointerException ex)
        {
            return null;
        }
    }

    public static void checkVersion(UserInteraction userInteraction)
    {
        // Get current version number
        String version = getCurrentVersion();

        // Check for new version
        String latestVersion = getLatestVersion();

        if(latestVersion != null && isNewerVersion(version, latestVersion))
        {
            userInteraction.alert(Level.WARNING, "JMusicBot Version", String.format(NEW_VERSION_AVAILABLE, version, latestVersion));
        }
    }

    public static boolean isNewerVersion(String current, String latest)
    {
        if (current.equalsIgnoreCase("UNKNOWN"))
            return true;

        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++)
        {
            int curr = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("\\D", "")) : 0;
            int late = i < latestParts.length ? Integer.parseInt(latestParts[i].replaceAll("\\D", "")) : 0;

            if (late > curr) return true;
            if (late < curr) return false;
        }
        return false;
    }

    /**
     * Checks if the bot JMusicBot is being run on is supported & returns the reason if it is not.
     * @return A string with the reason, or null if it is supported.
     */
    public static String getUnsupportedBotReason(JDA jda) 
    {
        if (jda.getSelfUser().getFlags().contains(User.UserFlag.VERIFIED_BOT))
            return "The bot is verified. Using JMusicBot in a verified bot is not supported.";

        ApplicationInfo info = jda.retrieveApplicationInfo().complete();
        if (info.isBotPublic())
            return "\"Public Bot\" is enabled. Using JMusicBot as a public bot is not supported. Please disable it in the "
                    + "Developer Dashboard at https://discord.com/developers/applications/" + jda.getSelfUser().getId() + "/bot ."
                    + "You may also need to disable all Installation Contexts at https://discord.com/developers/applications/" 
                    + jda.getSelfUser().getId() + "/installation .";

        return null;
    }
}
