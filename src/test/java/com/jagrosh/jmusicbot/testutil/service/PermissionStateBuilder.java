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
package com.jagrosh.jmusicbot.testutil.service;

import com.jagrosh.jmusicbot.audio.RequestMetadata;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;

import java.util.Collections;

import static com.jagrosh.jmusicbot.testutil.TestConstants.*;
import static org.mockito.Mockito.*;

/**
 * Builder for constructing permission-related test scenarios.
 * Handles DJ permissions, track ownership, and voice channel permissions.
 * 
 * Usage:
 * <pre>
 * PermissionStateBuilder.with(fixture)
 *     .asTrackOwner()
 *     .withoutDJRole()
 *     .build();
 * </pre>
 */
public class PermissionStateBuilder
{
    private final ServiceTestFixture fixture;

    private PermissionStateBuilder(ServiceTestFixture fixture)
    {
        this.fixture = fixture;
    }

    /**
     * Creates a new permission state builder with the given fixture.
     */
    public static PermissionStateBuilder with(ServiceTestFixture fixture)
    {
        return new PermissionStateBuilder(fixture);
    }

    /**
     * Returns the configured fixture.
     */
    public ServiceTestFixture build()
    {
        return fixture;
    }

    // ==================== Bot Owner Permissions ====================

    /**
     * Configures the user as the bot owner.
     */
    public PermissionStateBuilder asBotOwner()
    {
        when(fixture.getConfig().getOwnerId()).thenReturn(USER_ID);
        return this;
    }

    /**
     * Configures the user as NOT the bot owner.
     */
    public PermissionStateBuilder notBotOwner()
    {
        when(fixture.getConfig().getOwnerId()).thenReturn(OWNER_ID);
        return this;
    }

    // ==================== DJ Role Permissions ====================

    /**
     * Configures the user to have the DJ role.
     */
    public PermissionStateBuilder withDJRole()
    {
        Role djRole = mock(Role.class);
        when(djRole.getIdLong()).thenReturn(DJ_ROLE_ID);
        when(fixture.getSettings().getRole(fixture.getGuild())).thenReturn(djRole);
        when(fixture.getMember().getRoles()).thenReturn(Collections.singletonList(djRole));
        return this;
    }

    /**
     * Configures the user to NOT have the DJ role.
     */
    public PermissionStateBuilder withoutDJRole()
    {
        Role djRole = mock(Role.class);
        when(djRole.getIdLong()).thenReturn(DJ_ROLE_ID);
        when(fixture.getSettings().getRole(fixture.getGuild())).thenReturn(djRole);
        when(fixture.getMember().getRoles()).thenReturn(Collections.emptyList());
        return this;
    }

    /**
     * Configures no DJ role to be set in the guild.
     */
    public PermissionStateBuilder noDJRoleConfigured()
    {
        when(fixture.getSettings().getRole(fixture.getGuild())).thenReturn(null);
        return this;
    }

    // ==================== Server Permissions ====================

    /**
     * Configures the user to have MANAGE_SERVER permission.
     */
    public PermissionStateBuilder withManageServer()
    {
        when(fixture.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);
        return this;
    }

    /**
     * Configures the user to NOT have MANAGE_SERVER permission.
     */
    public PermissionStateBuilder withoutManageServer()
    {
        when(fixture.getMember().hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);
        return this;
    }

    // ==================== Track Ownership ====================

    /**
     * Configures the user as the owner of the currently playing track.
     */
    public PermissionStateBuilder asTrackOwner()
    {
        if (fixture.getCurrentTrack() != null)
        {
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(USER_ID);
            when(fixture.getCurrentTrack().getUserData(RequestMetadata.class)).thenReturn(metadata);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);
        }
        return this;
    }

    /**
     * Configures the user as NOT the owner of the currently playing track.
     */
    public PermissionStateBuilder notTrackOwner()
    {
        if (fixture.getCurrentTrack() != null)
        {
            RequestMetadata metadata = mock(RequestMetadata.class);
            when(metadata.getOwner()).thenReturn(999999999L); // Different user
            when(fixture.getCurrentTrack().getUserData(RequestMetadata.class)).thenReturn(metadata);
            when(fixture.getAudioHandler().getRequestMetadata()).thenReturn(metadata);
        }
        return this;
    }

    // ==================== Combined Permission Scenarios ====================

    /**
     * Configures the user to have full DJ permissions (as bot owner).
     */
    public PermissionStateBuilder fullDJPermissions()
    {
        return asBotOwner();
    }

    /**
     * Configures the user to have NO DJ permissions at all.
     */
    public PermissionStateBuilder noDJPermissions()
    {
        return notBotOwner()
                .withoutDJRole()
                .withoutManageServer();
    }

    /**
     * Configures the user to have DJ permissions via role only.
     */
    public PermissionStateBuilder djViaRole()
    {
        return notBotOwner()
                .withDJRole()
                .withoutManageServer();
    }

    /**
     * Configures the user to have DJ permissions via MANAGE_SERVER.
     */
    public PermissionStateBuilder djViaManageServer()
    {
        return notBotOwner()
                .withoutDJRole()
                .withManageServer();
    }

    /**
     * Configures a "regular user" scenario:
     * - Not bot owner
     * - No DJ role
     * - No MANAGE_SERVER
     * - Is the track owner
     */
    public PermissionStateBuilder regularUserOwnsTrack()
    {
        return noDJPermissions().asTrackOwner();
    }

    /**
     * Configures a "regular user" scenario where they don't own the track:
     * - Not bot owner
     * - No DJ role
     * - No MANAGE_SERVER
     * - Does NOT own the current track
     */
    public PermissionStateBuilder regularUserNotTrackOwner()
    {
        return noDJPermissions().notTrackOwner();
    }

    // ==================== Voice Channel Permissions ====================

    /**
     * Configures the user to be alone in the voice channel (effectively DJ).
     */
    public PermissionStateBuilder aloneInVoice()
    {
        fixture.withUserInVoiceChannel();
        // When user is alone, they effectively have DJ permissions for their track
        return this;
    }

    /**
     * Configures multiple users in the voice channel.
     */
    public PermissionStateBuilder multipleUsersInVoice()
    {
        fixture.withBothInVoiceChannel();
        // Configure the voice channel to report multiple members
        when(fixture.getVoiceChannel().getMembers()).thenReturn(
                java.util.Arrays.asList(fixture.getMember(), fixture.getSelfMember()));
        return this;
    }
}
