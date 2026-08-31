package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes Python forced-movement runtime placement before Java binds that ordering. */
class ForcedMovementRuntimeBindingContractTest {
    private static final Pattern JAVA_RUNTIME_CALL = Pattern.compile(
            "\\bRuntimeForcedMovementMoveApplication\\s*\\.\\s*apply\\s*\\("
    );

    @Test
    void freezesPythonRuntimeAndToolingCallsitesWhileJavaOrderingRemainsUnbound() throws IOException {
        assertPinnedPythonFixtureIfAvailable();

        Path sourceRoot = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(sourceRoot), "Java production source root must be available");

        List<String> callsites = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("RuntimeForcedMovementMoveApplication.java"))
                    .forEach(path -> collectCallsites(sourceRoot, path, callsites));
        }

        assertEquals(
                List.of(),
                callsites,
                "Java forced movement ordering must remain unbound until the pinned battle_state order is frozen"
        );
    }

    private static void assertPinnedPythonFixtureIfAvailable() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_RUNTIME_BINDING_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 3, "forced movement callsite fixture is incomplete");
        assertEquals(
                "role\tpath\tline\tenclosing\tstatement\tblock\tprevious\tnext",
                lines.get(0)
        );

        List<String[]> rows = lines.stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\\t", -1))
                .peek(fields -> assertEquals(8, fields.length, "forced movement fixture row shape changed"))
                .toList();

        List<String[]> runtimeRows = rows.stream().filter(fields -> fields[0].equals("runtime")).toList();
        List<String[]> toolingRows = rows.stream().filter(fields -> fields[0].equals("tooling")).toList();
        assertEquals(1, runtimeRows.size(), "pinned oracle runtime forced-movement callsite changed");
        assertEquals(1, toolingRows.size(), "pinned oracle tooling forced-movement callsite changed");

        String[] runtime = runtimeRows.get(0);
        assertEquals("auto_ptu/rules/battle_state.py", runtime[1]);
        assertFalse(runtime[3].isBlank(), "runtime callsite must remain inside a named function");
        assertTrue(runtime[4].contains("forced_movement_instruction"));
        assertFalse(runtime[5].isBlank(), "runtime callsite block identity must be frozen");
        assertFalse(runtime[6].isBlank(), "statement before forced movement must be frozen");
        assertFalse(runtime[7].isBlank(), "statement after forced movement must be frozen");

        String[] tooling = toolingRows.get(0);
        assertEquals("auto_ptu/tools/generate_attack_log.py", tooling[1]);
    }

    private static void collectCallsites(Path sourceRoot, Path path, List<String> callsites) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                Matcher matcher = JAVA_RUNTIME_CALL.matcher(lines.get(index));
                if (matcher.find()) {
                    callsites.add(sourceRoot.relativize(path).toString().replace('\\', '/') + ":" + (index + 1));
                }
            }
        } catch (IOException exception) {
            throw new RuntimeException("failed to inspect Java production source: " + path, exception);
        }
    }
}
