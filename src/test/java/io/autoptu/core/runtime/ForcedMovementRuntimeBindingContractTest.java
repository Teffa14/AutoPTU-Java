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

/**
 * Freezes the current negative runtime-binding contract for move-caused forced movement.
 *
 * <p>The pinned Python oracle exposes the deterministic forced_movement_instruction helper but
 * does not currently call it from production battle runtime. Java may keep the reusable
 * RuntimeForcedMovementMoveApplication boundary, but no production coordinator may invoke it
 * until Python exposes an ordering contract that can be ported explicitly.</p>
 */
class ForcedMovementRuntimeBindingContractTest {
    private static final Pattern JAVA_RUNTIME_CALL = Pattern.compile(
            "\\bRuntimeForcedMovementMoveApplication\\s*\\.\\s*apply\\s*\\("
    );

    @Test
    void pythonAndJavaProductionRemainUnboundUntilOracleDefinesOrdering() throws IOException {
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
                "forced movement is not runtime-bound in the pinned Python oracle; "
                        + "inspect Python ordering before adding Java production callsites"
        );
    }

    private static void assertPinnedPythonFixtureIfAvailable() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_RUNTIME_BINDING_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 2, "forced movement runtime binding fixture is incomplete");
        assertEquals("symbol\truntime_call_count", lines.get(0));
        assertEquals("forced_movement_instruction\t0", lines.get(1));
        assertEquals(2, lines.size(), "pinned oracle unexpectedly contains runtime callsites");
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
