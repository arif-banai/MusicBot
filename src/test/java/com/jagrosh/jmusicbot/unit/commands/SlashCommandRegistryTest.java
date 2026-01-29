package com.jagrosh.jmusicbot.unit.commands;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jmusicbot.commands.SlashCommandRegistry;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SlashCommandRegistry} to verify:
 * 1. First run (no hash file) => registers commands and creates hash file
 * 2. Hash unchanged => skips registration
 * 3. Hash changed => re-registers and updates hash file
 */
@SuppressWarnings("unchecked") // ArgumentCaptor with generics requires unchecked casts
public class SlashCommandRegistryTest {

    private static final String HASH_FILE_NAME = ".slashcommands.hash";

    @TempDir
    Path tempDir;

    @Mock
    private JDA jda;

    @Mock
    private CommandClient commandClient;

    @Mock
    private CommandListUpdateAction commandListUpdateAction;

    @Mock
    private SlashCommand slashCommand;

    @Mock
    private SlashCommandData slashCommandData;

    private MockedStatic<OtherUtil> otherUtilMock;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock OtherUtil.getPath to redirect to temp directory
        otherUtilMock = mockStatic(OtherUtil.class);
        otherUtilMock.when(() -> OtherUtil.getPath(HASH_FILE_NAME))
                .thenReturn(tempDir.resolve(HASH_FILE_NAME));

        // Setup JDA mock chain
        when(jda.updateCommands()).thenReturn(commandListUpdateAction);
        when(commandListUpdateAction.addCommands(anyCollection())).thenReturn(commandListUpdateAction);

        // Setup SlashCommand mock
        when(slashCommand.buildCommandData()).thenReturn(slashCommandData);
        when(slashCommandData.getName()).thenReturn("testcommand");
        when(slashCommandData.getDescription()).thenReturn("A test command");
        when(slashCommandData.getOptions()).thenReturn(Collections.emptyList());
        when(slashCommandData.getSubcommands()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (otherUtilMock != null) {
            otherUtilMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testFirstRun_NoHashFile_RegistersCommandsAndCreatesHashFile() {
        // Given: No hash file exists and we have commands to register
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        // Capture the success callback
        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), errorCaptor.capture());

        // When: Register commands
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Then: JDA.updateCommands() should be called
        verify(jda).updateCommands();
        verify(commandListUpdateAction).addCommands(anyCollection());
        verify(commandListUpdateAction).queue(any(), any());

        // Simulate successful registration callback
        List<Command> registeredCommands = Collections.emptyList(); // Mock result
        successCaptor.getValue().accept(registeredCommands);

        // Verify hash file was created
        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        assertTrue(Files.exists(hashFile), "Hash file should be created after registration");
    }

    @Test
    void testHashUnchanged_SkipsRegistration() throws IOException {
        // Given: Hash file exists with current command hash
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        // First, register to create the hash file
        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), any());

        SlashCommandRegistry.registerIfChanged(jda, commandClient);
        successCaptor.getValue().accept(Collections.emptyList()); // Trigger hash save

        // Verify hash file exists
        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        assertTrue(Files.exists(hashFile), "Hash file should exist");
        String savedHash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();
        assertFalse(savedHash.isEmpty(), "Hash should not be empty");

        // Reset mocks for second call
        reset(jda, commandListUpdateAction);
        when(jda.updateCommands()).thenReturn(commandListUpdateAction);
        when(commandListUpdateAction.addCommands(anyCollection())).thenReturn(commandListUpdateAction);

        // When: Register again with same commands
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Then: JDA.updateCommands() should NOT be called (commands unchanged)
        verify(jda, never()).updateCommands();
        verify(commandListUpdateAction, never()).queue(any(), any());
    }

    @Test
    void testHashChanged_ReregistersAndUpdatesHashFile() throws IOException {
        // Given: Hash file exists with old command hash
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        // First, register to create the hash file
        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), any());

        SlashCommandRegistry.registerIfChanged(jda, commandClient);
        successCaptor.getValue().accept(Collections.emptyList()); // Trigger hash save

        // Verify hash file exists and capture the old hash
        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        String oldHash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();

        // Now change the command (different name = different hash)
        SlashCommand modifiedCommand = mock(SlashCommand.class);
        SlashCommandData modifiedData = mock(SlashCommandData.class);
        when(modifiedCommand.buildCommandData()).thenReturn(modifiedData);
        when(modifiedData.getName()).thenReturn("modifiedcommand"); // Different name
        when(modifiedData.getDescription()).thenReturn("A modified command");
        when(modifiedData.getOptions()).thenReturn(Collections.emptyList());
        when(modifiedData.getSubcommands()).thenReturn(Collections.emptyList());
        when(commandClient.getSlashCommands()).thenReturn(List.of(modifiedCommand));

        // Reset mocks for second call
        reset(jda, commandListUpdateAction);
        when(jda.updateCommands()).thenReturn(commandListUpdateAction);
        when(commandListUpdateAction.addCommands(anyCollection())).thenReturn(commandListUpdateAction);

        ArgumentCaptor<Consumer<List<Command>>> secondSuccessCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(secondSuccessCaptor.capture(), any());

        // When: Register again with modified commands
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Then: JDA.updateCommands() SHOULD be called (commands changed)
        verify(jda).updateCommands();
        verify(commandListUpdateAction).addCommands(anyCollection());
        verify(commandListUpdateAction).queue(any(), any());

        // Simulate successful registration
        secondSuccessCaptor.getValue().accept(Collections.emptyList());

        // Verify hash file was updated with new hash
        String newHash = Files.readString(hashFile, StandardCharsets.UTF_8).trim();
        assertNotEquals(oldHash, newHash, "Hash should be different after command change");
    }

    @Test
    void testEmptyCommands_DoesNotRegister() {
        // Given: No commands to register
        when(commandClient.getSlashCommands()).thenReturn(Collections.emptyList());

        // When: Register
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Then: Nothing should happen
        verify(jda, never()).updateCommands();
    }

    @Test
    void testNullCommands_DoesNotRegister() {
        // Given: Null command list
        when(commandClient.getSlashCommands()).thenReturn(null);

        // When: Register
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Then: Nothing should happen
        verify(jda, never()).updateCommands();
    }

    @Test
    void testClearHash_RemovesHashFile() throws IOException {
        // Given: Hash file exists
        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        Files.writeString(hashFile, "somehash", StandardCharsets.UTF_8);
        assertTrue(Files.exists(hashFile), "Hash file should exist before clearing");

        // When: Clear hash
        SlashCommandRegistry.clearHash();

        // Then: Hash file should be deleted
        assertFalse(Files.exists(hashFile), "Hash file should be deleted after clearing");
    }

    @Test
    void testForceRegister_AlwaysRegisters() throws IOException {
        // Given: Hash file exists with current command hash
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        // First, do a normal registration
        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), any());

        SlashCommandRegistry.registerIfChanged(jda, commandClient);
        successCaptor.getValue().accept(Collections.emptyList());

        // Reset mocks
        reset(jda, commandListUpdateAction);
        when(jda.updateCommands()).thenReturn(commandListUpdateAction);
        when(commandListUpdateAction.addCommands(anyCollection())).thenReturn(commandListUpdateAction);

        ArgumentCaptor<Consumer<List<Command>>> forceSuccessCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(forceSuccessCaptor.capture(), any());

        // When: Force register (even though hash hasn't changed)
        SlashCommandRegistry.forceRegister(jda, commandClient);

        // Then: JDA.updateCommands() SHOULD be called (forced)
        verify(jda).updateCommands();
        verify(commandListUpdateAction).addCommands(anyCollection());
        verify(commandListUpdateAction).queue(any(), any());
    }

    @Test
    void testRegistrationFailure_DoesNotSaveHash() throws IOException {
        // Given: No hash file exists
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        // Capture the error callback
        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), errorCaptor.capture());

        // When: Register commands
        SlashCommandRegistry.registerIfChanged(jda, commandClient);

        // Simulate failed registration callback
        errorCaptor.getValue().accept(new RuntimeException("Discord API error"));

        // Then: Hash file should NOT be created
        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        assertFalse(Files.exists(hashFile), "Hash file should not be created after failed registration");
    }

    @Test
    void testHashCalculation_IncludesAllCommandDetails() throws IOException {
        // Given: A command with specific details
        when(commandClient.getSlashCommands()).thenReturn(List.of(slashCommand));

        ArgumentCaptor<Consumer<List<Command>>> successCaptor = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor.capture(), any());

        SlashCommandRegistry.registerIfChanged(jda, commandClient);
        successCaptor.getValue().accept(Collections.emptyList());

        Path hashFile = tempDir.resolve(HASH_FILE_NAME);
        String hash1 = Files.readString(hashFile, StandardCharsets.UTF_8).trim();

        // Now change the description (different description = different hash)
        reset(jda, commandListUpdateAction, slashCommandData);
        when(jda.updateCommands()).thenReturn(commandListUpdateAction);
        when(commandListUpdateAction.addCommands(anyCollection())).thenReturn(commandListUpdateAction);
        when(slashCommand.buildCommandData()).thenReturn(slashCommandData);
        when(slashCommandData.getName()).thenReturn("testcommand"); // Same name
        when(slashCommandData.getDescription()).thenReturn("Different description"); // Different description
        when(slashCommandData.getOptions()).thenReturn(Collections.emptyList());
        when(slashCommandData.getSubcommands()).thenReturn(Collections.emptyList());

        ArgumentCaptor<Consumer<List<Command>>> successCaptor2 = ArgumentCaptor.forClass(Consumer.class);
        doNothing().when(commandListUpdateAction).queue(successCaptor2.capture(), any());

        SlashCommandRegistry.registerIfChanged(jda, commandClient);
        successCaptor2.getValue().accept(Collections.emptyList());

        String hash2 = Files.readString(hashFile, StandardCharsets.UTF_8).trim();

        // Hashes should be different because description changed
        assertNotEquals(hash1, hash2, "Hash should change when command description changes");
    }
}
