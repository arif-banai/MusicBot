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

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.entities.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spy implementation of MusicService.OutputAdapter for testing.
 * Captures all method calls for verification in tests.
 * 
 * Usage:
 * <pre>
 * OutputAdapterSpy spy = new OutputAdapterSpy();
 * musicService.play(guild, member, "query", textChannel, spy);
 * spy.assertSuccessMessageContains("Added");
 * </pre>
 */
public class OutputAdapterSpy implements MusicService.OutputAdapter
{
    // Captured calls
    private final List<String> successMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();
    private final List<String> warningMessages = new ArrayList<>();
    private final List<String> editedMessages = new ArrayList<>();
    private final List<AudioHandler> nowPlayingEdits = new ArrayList<>();
    private final List<AudioHandler> noMusicEdits = new ArrayList<>();
    private int helpShownCount = 0;

    // Callbacks for async operations
    private Consumer<Message> lastEditCallback;

    @Override
    public void replySuccess(String content)
    {
        successMessages.add(content);
    }

    @Override
    public void replyError(String content)
    {
        errorMessages.add(content);
    }

    @Override
    public void replyWarning(String content)
    {
        warningMessages.add(content);
    }

    @Override
    public void editMessage(String content)
    {
        editedMessages.add(content);
    }

    @Override
    public void editMessage(String content, Consumer<Message> onSuccess)
    {
        editedMessages.add(content);
        lastEditCallback = onSuccess;
    }

    @Override
    public void editNowPlaying(AudioHandler handler)
    {
        nowPlayingEdits.add(handler);
    }

    @Override
    public void editNoMusic(AudioHandler handler)
    {
        noMusicEdits.add(handler);
    }

    @Override
    public void onShowHelp()
    {
        helpShownCount++;
    }

    // ==================== Assertion Methods ====================

    /**
     * Asserts that a success message was sent with the exact content.
     */
    public void assertSuccessMessage(String expected)
    {
        assertTrue(successMessages.contains(expected),
                "Expected success message '" + expected + "' but got: " + successMessages);
    }

    /**
     * Asserts that at least one success message contains the given substring.
     */
    public void assertSuccessMessageContains(String substring)
    {
        boolean found = successMessages.stream().anyMatch(m -> m.contains(substring));
        assertTrue(found,
                "Expected success message containing '" + substring + "' but got: " + successMessages);
    }

    /**
     * Asserts that an error message was sent with the exact content.
     */
    public void assertErrorMessage(String expected)
    {
        assertTrue(errorMessages.contains(expected),
                "Expected error message '" + expected + "' but got: " + errorMessages);
    }

    /**
     * Asserts that at least one error message contains the given substring.
     */
    public void assertErrorMessageContains(String substring)
    {
        boolean found = errorMessages.stream().anyMatch(m -> m.contains(substring));
        assertTrue(found,
                "Expected error message containing '" + substring + "' but got: " + errorMessages);
    }

    /**
     * Asserts that a warning message was sent with the exact content.
     */
    public void assertWarningMessage(String expected)
    {
        assertTrue(warningMessages.contains(expected),
                "Expected warning message '" + expected + "' but got: " + warningMessages);
    }

    /**
     * Asserts that at least one warning message contains the given substring.
     */
    public void assertWarningMessageContains(String substring)
    {
        boolean found = warningMessages.stream().anyMatch(m -> m.contains(substring));
        assertTrue(found,
                "Expected warning message containing '" + substring + "' but got: " + warningMessages);
    }

    /**
     * Asserts that help was shown.
     */
    public void assertHelpShown()
    {
        assertTrue(helpShownCount > 0, "Expected help to be shown but it was not");
    }

    /**
     * Asserts that help was not shown.
     */
    public void assertHelpNotShown()
    {
        assertEquals(0, helpShownCount, "Expected help not to be shown but it was");
    }

    /**
     * Asserts that no messages were sent at all.
     */
    public void assertNoMessages()
    {
        assertTrue(successMessages.isEmpty(), "Expected no success messages but got: " + successMessages);
        assertTrue(errorMessages.isEmpty(), "Expected no error messages but got: " + errorMessages);
        assertTrue(warningMessages.isEmpty(), "Expected no warning messages but got: " + warningMessages);
    }

    /**
     * Asserts that no error messages were sent.
     */
    public void assertNoErrors()
    {
        assertTrue(errorMessages.isEmpty(), "Expected no error messages but got: " + errorMessages);
    }

    /**
     * Asserts that no success messages were sent.
     */
    public void assertNoSuccess()
    {
        assertTrue(successMessages.isEmpty(), "Expected no success messages but got: " + successMessages);
    }

    /**
     * Asserts that no warning messages were sent.
     */
    public void assertNoWarnings()
    {
        assertTrue(warningMessages.isEmpty(), "Expected no warning messages but got: " + warningMessages);
    }

    /**
     * Asserts that editNowPlaying was called.
     */
    public void assertNowPlayingEdited()
    {
        assertFalse(nowPlayingEdits.isEmpty(), "Expected editNowPlaying to be called but it was not");
    }

    /**
     * Asserts that editNoMusic was called.
     */
    public void assertNoMusicEdited()
    {
        assertFalse(noMusicEdits.isEmpty(), "Expected editNoMusic to be called but it was not");
    }

    /**
     * Asserts that a message was edited with the exact content.
     */
    public void assertMessageEdited(String expected)
    {
        assertTrue(editedMessages.contains(expected),
                "Expected edited message '" + expected + "' but got: " + editedMessages);
    }

    /**
     * Asserts that at least one edited message contains the given substring.
     */
    public void assertMessageEditedContains(String substring)
    {
        boolean found = editedMessages.stream().anyMatch(m -> m.contains(substring));
        assertTrue(found,
                "Expected edited message containing '" + substring + "' but got: " + editedMessages);
    }

    /**
     * Asserts that exactly one success message was sent.
     */
    public void assertSingleSuccessMessage()
    {
        assertEquals(1, successMessages.size(),
                "Expected exactly 1 success message but got " + successMessages.size() + ": " + successMessages);
    }

    /**
     * Asserts that exactly one error message was sent.
     */
    public void assertSingleErrorMessage()
    {
        assertEquals(1, errorMessages.size(),
                "Expected exactly 1 error message but got " + errorMessages.size() + ": " + errorMessages);
    }

    // ==================== Getters for Advanced Assertions ====================

    public List<String> getSuccessMessages()
    {
        return new ArrayList<>(successMessages);
    }

    public List<String> getErrorMessages()
    {
        return new ArrayList<>(errorMessages);
    }

    public List<String> getWarningMessages()
    {
        return new ArrayList<>(warningMessages);
    }

    public List<String> getEditedMessages()
    {
        return new ArrayList<>(editedMessages);
    }

    public List<AudioHandler> getNowPlayingEdits()
    {
        return new ArrayList<>(nowPlayingEdits);
    }

    public List<AudioHandler> getNoMusicEdits()
    {
        return new ArrayList<>(noMusicEdits);
    }

    public int getHelpShownCount()
    {
        return helpShownCount;
    }

    public Consumer<Message> getLastEditCallback()
    {
        return lastEditCallback;
    }

    /**
     * Gets the last success message sent, or null if none.
     */
    public String getLastSuccessMessage()
    {
        return successMessages.isEmpty() ? null : successMessages.get(successMessages.size() - 1);
    }

    /**
     * Gets the last error message sent, or null if none.
     */
    public String getLastErrorMessage()
    {
        return errorMessages.isEmpty() ? null : errorMessages.get(errorMessages.size() - 1);
    }

    /**
     * Gets the last warning message sent, or null if none.
     */
    public String getLastWarningMessage()
    {
        return warningMessages.isEmpty() ? null : warningMessages.get(warningMessages.size() - 1);
    }

    /**
     * Resets all captured data.
     */
    public void reset()
    {
        successMessages.clear();
        errorMessages.clear();
        warningMessages.clear();
        editedMessages.clear();
        nowPlayingEdits.clear();
        noMusicEdits.clear();
        helpShownCount = 0;
        lastEditCallback = null;
    }

    /**
     * Returns a summary of all captured calls for debugging.
     */
    @Override
    public String toString()
    {
        return "OutputAdapterSpy{" +
                "success=" + successMessages +
                ", error=" + errorMessages +
                ", warning=" + warningMessages +
                ", edited=" + editedMessages +
                ", nowPlayingEdits=" + nowPlayingEdits.size() +
                ", noMusicEdits=" + noMusicEdits.size() +
                ", helpShown=" + helpShownCount +
                '}';
    }
}
