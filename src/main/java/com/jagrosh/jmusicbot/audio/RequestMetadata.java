/*
 * Copyright 2021 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.audio;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.User;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class RequestMetadata
{
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static final RequestMetadata EMPTY = new RequestMetadata(null, null, 0L);
    
    public final UserInfo user;
    public final RequestInfo requestInfo;
    public final long channelId;
    
    @JsonCreator
    public RequestMetadata(
        @JsonProperty("user") UserInfo user,
        @JsonProperty("requestInfo") RequestInfo requestInfo,
        @JsonProperty("channelId") Long channelId)
    {
        this.user = user;
        this.requestInfo = requestInfo;
        this.channelId = channelId != null ? channelId : 0L;
    }
    
    public RequestMetadata(User user, RequestInfo requestInfo, long channelId)
    {
        this.user = user == null
            ? null
            : new UserInfo(user.getIdLong(), user.getName(), user.getDiscriminator(), user.getEffectiveAvatarUrl());
        this.requestInfo = requestInfo;
        this.channelId = channelId;
    }

    public RequestMetadata(User user, RequestInfo requestInfo)
    {
        this(user, requestInfo, 0L);
    }
    
    public long getOwner()
    {
        return user == null ? 0L : user.id;
    }
    
    /**
     * Returns JSON representation of this object.
     * This ensures compatibility with YouTube source extensions that expect JSON userData.
     */
    @Override
    public String toString()
    {
        try
        {
            return objectMapper.writeValueAsString(this);
        }
        catch (JsonProcessingException e)
        {
            // Fallback to a simple JSON-like representation if serialization fails
            return "{\"user\":" + (user != null ? user.toString() : "null") + 
                   ",\"requestInfo\":" + (requestInfo != null ? requestInfo.toString() : "null") + "}";
        }
    }

    public static RequestMetadata fromResultHandler(AudioTrack track, CommandEvent event)
    {
        return new RequestMetadata(event.getAuthor(), new RequestInfo(event.getArgs(), track.getInfo().uri), event.getChannel().getIdLong());
    }
    
    public static class RequestInfo
    {
        public final String query, url;
        public final long startTimestamp;

        @JsonCreator
        public RequestInfo(
            @JsonProperty("query") String query,
            @JsonProperty("url") String url,
            @JsonProperty("startTimestamp") Long startTimestamp)
        {
            this.url = url;
            this.query = query;
            // If startTimestamp is null or 0 and we have a query, try to extract it from the query
            if (startTimestamp == null || startTimestamp == 0)
            {
                this.startTimestamp = query != null ? tryGetTimestamp(query) : 0;
            }
            else
            {
                this.startTimestamp = startTimestamp;
            }
        }

        public RequestInfo(String query, String url)
        {
            this(query, url, null);
        }
        
        @Override
        public String toString()
        {
            try
            {
                return objectMapper.writeValueAsString(this);
            }
            catch (JsonProcessingException e)
            {
                return "{\"query\":\"" + (query != null ? query.replace("\"", "\\\"") : "") + 
                       "\",\"url\":\"" + (url != null ? url.replace("\"", "\\\"") : "") + 
                       "\",\"startTimestamp\":" + startTimestamp + "}";
            }
        }

        private static final Pattern youtubeTimestampPattern = Pattern.compile("youtu(?:\\.be|be\\..+)/.*\\?.*(?!.*list=)t=([\\dhms]+)");
        private static long tryGetTimestamp(String url)
        {
            Matcher matcher = youtubeTimestampPattern.matcher(url);
            return matcher.find() ? TimeUtil.parseUnitTime(matcher.group(1)) : 0;
        }
    }
    
    public static class UserInfo
    {
        public final long id;
        public final String username, discrim, avatar;
        
        @JsonCreator
        public UserInfo(
            @JsonProperty("id") long id,
            @JsonProperty("username") String username,
            @JsonProperty("discrim") String discrim,
            @JsonProperty("avatar") String avatar)
        {
            this.id = id;
            this.username = username;
            this.discrim = discrim;
            this.avatar = avatar;
        }
        
        @Override
        public String toString()
        {
            try
            {
                return objectMapper.writeValueAsString(this);
            }
            catch (JsonProcessingException e)
            {
                return "{\"id\":" + id + 
                       ",\"username\":\"" + (username != null ? username.replace("\"", "\\\"") : "") + 
                       "\",\"discrim\":\"" + (discrim != null ? discrim.replace("\"", "\\\"") : "") + 
                       "\",\"avatar\":\"" + (avatar != null ? avatar.replace("\"", "\\\"") : "") + "\"}";
            }
        }
    }
}
