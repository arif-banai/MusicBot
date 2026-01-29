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
package com.jagrosh.jmusicbot.testutil.commands;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;

import static org.mockito.Mockito.spy;

/**
 * Test implementation of MusicSlashCommand for testing validation logic.
 * Exposes protected methods and fields for test configuration and verification.
 */
public class TestMusicSlashCommand extends MusicSlashCommand
{
    private boolean doCommandCalled = false;
    private SlashCommandEvent lastEvent = null;

    public TestMusicSlashCommand(Bot bot)
    {
        super(bot);
        this.name = "testcommand";
        this.help = "Test command for unit testing";
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        this.doCommandCalled = true;
        this.lastEvent = event;
    }

    // ==================== Expose Protected Methods ====================

    /**
     * Exposes the protected execute method for testing.
     */
    public void testExecute(SlashCommandEvent event)
    {
        execute(event);
    }

    // ==================== Configuration Setters ====================

    /**
     * Sets whether music must be playing for this command.
     */
    public void setBePlaying(boolean value)
    {
        this.bePlaying = value;
    }

    /**
     * Sets whether the user must be listening in voice for this command.
     */
    public void setBeListening(boolean value)
    {
        this.beListening = value;
    }

    // ==================== Test Verification ====================

    /**
     * Returns true if doCommand was called.
     */
    public boolean wasDoCommandCalled()
    {
        return doCommandCalled;
    }

    /**
     * Returns the event passed to doCommand, or null if not called.
     */
    public SlashCommandEvent getLastEvent()
    {
        return lastEvent;
    }

    /**
     * Resets the test state (call between tests if reusing).
     */
    public void reset()
    {
        this.doCommandCalled = false;
        this.lastEvent = null;
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a new test command.
     */
    public static TestMusicSlashCommand create(Bot bot)
    {
        return new TestMusicSlashCommand(bot);
    }

    /**
     * Creates a spied test command for Mockito verification.
     */
    public static TestMusicSlashCommand createSpied(Bot bot)
    {
        return spy(new TestMusicSlashCommand(bot));
    }

    /**
     * Creates a test command configured for basic validation (no special requirements).
     */
    public static TestMusicSlashCommand createBasic(Bot bot)
    {
        TestMusicSlashCommand cmd = new TestMusicSlashCommand(bot);
        cmd.setBePlaying(false);
        cmd.setBeListening(false);
        return cmd;
    }

    /**
     * Creates a test command configured to require music playing.
     */
    public static TestMusicSlashCommand createRequiresPlaying(Bot bot)
    {
        TestMusicSlashCommand cmd = new TestMusicSlashCommand(bot);
        cmd.setBePlaying(true);
        cmd.setBeListening(false);
        return cmd;
    }

    /**
     * Creates a test command configured to require user listening.
     */
    public static TestMusicSlashCommand createRequiresListening(Bot bot)
    {
        TestMusicSlashCommand cmd = new TestMusicSlashCommand(bot);
        cmd.setBePlaying(false);
        cmd.setBeListening(true);
        return cmd;
    }

    /**
     * Creates a test command configured to require both playing and listening.
     */
    public static TestMusicSlashCommand createRequiresPlayingAndListening(Bot bot)
    {
        TestMusicSlashCommand cmd = new TestMusicSlashCommand(bot);
        cmd.setBePlaying(true);
        cmd.setBeListening(true);
        return cmd;
    }
}
