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
package com.jagrosh.jmusicbot.unit.ytdlp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jagrosh.jmusicbot.ytdlp.ProcessRunner.ProcessResult;
import com.jagrosh.jmusicbot.ytdlp.SystemProcessRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link SystemProcessRunner#applyUtf8Environment(Map)} without starting any
 * process (real or otherwise): it's a pure function over a plain {@code Map}, so it
 * can be tested directly. This guards against a yt-dlp (Python) child process on
 * Windows defaulting its piped stdout to the console's ANSI code page, which can
 * corrupt or drop non-ASCII characters in the printed path before it's decoded.
 */
@DisplayName("SystemProcessRunner Tests")
class SystemProcessRunnerTest {

    @Test
    @DisplayName("applyUtf8Environment() sets PYTHONUTF8 and PYTHONIOENCODING")
    void applyUtf8Environment_setsPythonEncodingVars() {
        Map<String, String> environment = new HashMap<>();

        SystemProcessRunner.applyUtf8Environment(environment);

        assertEquals("1", environment.get("PYTHONUTF8"));
        assertEquals("utf-8", environment.get("PYTHONIOENCODING"));
    }

    @Test
    @DisplayName("applyUtf8Environment() overrides a pre-existing, differently-cased value")
    void applyUtf8Environment_overridesExistingValue() {
        Map<String, String> environment = new HashMap<>();
        environment.put("PYTHONIOENCODING", "cp1252");
        environment.put("PYTHONUTF8", "0");

        SystemProcessRunner.applyUtf8Environment(environment);

        assertEquals("1", environment.get("PYTHONUTF8"));
        assertEquals("utf-8", environment.get("PYTHONIOENCODING"));
    }

    @Test
    @DisplayName("applyUtf8Environment() leaves other environment entries untouched")
    void applyUtf8Environment_leavesOtherEntriesUntouched() {
        Map<String, String> environment = new HashMap<>();
        environment.put("PATH", "/usr/bin");

        SystemProcessRunner.applyUtf8Environment(environment);

        assertEquals("/usr/bin", environment.get("PATH"));
        assertEquals(3, environment.size());
    }

    /** Starts a grandchild that inherits (and keeps open) its stdout, prints the grandchild's PID, and exits. */
    private static final String PARENT = """
            public class Parent {
                public static void main(String[] args) throws Exception {
                    Process grandchild = new ProcessBuilder(args[0], args[1]).inheritIO().start();
                    System.out.println("grandchild=" + grandchild.pid());
                    System.out.flush();
                }
            }
            """;

    /** Holds the inherited pipe for a while; ends on its own in case the test can't kill it. */
    private static final String GRANDCHILD = """
            public class Grandchild {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(30_000);
                }
            }
            """;

    @Test
    @DisplayName("run() returns after a normal exit even if a process it started still holds the output pipe")
    void normalExit_grandchildHoldingPipe_returnsAfterGrace(@TempDir Path dir) throws Exception {
        Path parent = Files.writeString(dir.resolve("Parent.java"), PARENT);
        Path grandchild = Files.writeString(dir.resolve("Grandchild.java"), GRANDCHILD);
        String java = ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());

        long started = System.nanoTime();
        ProcessResult result = new SystemProcessRunner(Duration.ofMillis(500))
                .run(List.of(java, parent.toString(), java, grandchild.toString()), Duration.ofSeconds(60));
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        try {
            assertFalse(result.timedOut());
            assertEquals(0, result.exitCode());
            assertTrue(took.compareTo(Duration.ofSeconds(20)) < 0, "run() waited for the grandchild: " + took);
        } finally {
            killGrandchild(result.stdout());
        }
    }

    private static void killGrandchild(String stdout) {
        for (String line : stdout.lines().toList()) {
            if (line.startsWith("grandchild=")) {
                long pid = Long.parseLong(line.substring("grandchild=".length()).strip());
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }
}
