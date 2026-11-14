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

import java.util.HashMap;
import java.util.Map;

import com.jagrosh.jmusicbot.ytdlp.SystemProcessRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
