package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes the current Python callsite inventory before Java binds forced movement into runtime order. */
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
                "Java forced movement ordering must remain unbound until the pinned battle_state callsite order is frozen"
        );
    }

    private static void assertPinnedPythonFixtureIfAvailable() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_RUNTIME_BINDING_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 3, "forced movement callsite fixture is incomplete");
        assertEquals("role\tpath\tline\tenclosing\tstatement", lines.get(0));

        long runtime = lines.stream().skip(1).filter(line -> line.startsWith("runtime\t")).count();
        long tooling = lines.stream().skip(1).filter(line -> line.startsWith("tooling\t")).count();
        assertEquals(1, runtime, "pinned oracle runtime forced-movement callsite changed");
        assertEquals(1, tooling, "pinned oracle tooling forced-movement callsite changed");
        assertTrue(
                lines.stream().anyMatch(line -> line.startsWith("runtime\tauto_ptu/rules/battle_state.py\t")),
                "runtime callsite must remain in battle_state.py"
        );
        assertTrue(
                lines.stream().anyMatch(line -> line.startsWith("tooling\tauto_ptu/tools/generate_attack_log.py\t")),
                "tooling callsite must remain in generate_attack_log.py"
        );
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
