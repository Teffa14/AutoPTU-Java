package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovementLandingHookRegistryTest {
    @Test
    void standardRegistryKeepsOnlyObservableTrapConsequencesInTrapOrder() {
        GridCoord coordinate = new GridCoord(3, 2);
        var context = new MovementLandingHookRegistry.LandingContext(
                new TileEntryTrapResolution.Context(
                        "target",
                        "Target",
                        "blue",
                        42,
                        Set.of("forest"),
                        coordinate
                ),
                List.of(
                        trap("allied", 1, "ally", "blue", Set.of(), "Allied"),
                        trap("naturewalk", 1, "enemy-a", "red", Set.of("forest"), "Naturewalk Trap"),
                        trap("trigger", 2, "enemy-b", "red", Set.of("cave"), "Trigger Trap")
                )
        );

        List<MovementLandingHookRegistry.ResolvedHook> resolved =
                MovementLandingHookRegistry.standard().resolve(context);

        assertEquals(2, resolved.size());
        assertEquals(List.of("tile_traps", "tile_traps"), resolved.stream().map(MovementLandingHookRegistry.ResolvedHook::hookKey).toList());
        assertEquals(
                List.of(
                        MovementLandingHookRegistry.HookFamily.TILE_TRAP,
                        MovementLandingHookRegistry.HookFamily.TILE_TRAP
                ),
                resolved.stream().map(MovementLandingHookRegistry.ResolvedHook::family).toList()
        );

        var blocked = assertInstanceOf(
                MovementLandingHookRegistry.TileTrapConsequence.class,
                resolved.get(0).consequence()
        );
        var blockedResult = assertInstanceOf(TileEntryTrapResolution.Block.class, blocked.resolution());
        assertEquals("naturewalk", blockedResult.trapKey());
        assertEquals(List.of(TileEntryTrapResolution.TraceStep.EMIT_TRAP_EVENT), blockedResult.trace());

        var triggered = assertInstanceOf(
                MovementLandingHookRegistry.TileTrapConsequence.class,
                resolved.get(1).consequence()
        );
        var triggeredResult = assertInstanceOf(TileEntryTrapResolution.Trigger.class, triggered.resolution());
        assertEquals("trigger", triggeredResult.trapKey());
        assertEquals(
                List.of(
                        TileEntryTrapResolution.TraceStep.APPLY_STATUS,
                        TileEntryTrapResolution.TraceStep.EMIT_TRAP_EVENT,
                        TileEntryTrapResolution.TraceStep.CONSUME_TRAP
                ),
                triggeredResult.trace()
        );
    }

    @Test
    void registeredHookOrderIsDeterministicAndDuplicateKeysAreRejected() {
        var registry = new MovementLandingHookRegistry();
        registry.register(
                MovementLandingHookRegistry.HookFamily.TILE_TRAP,
                "first",
                context -> List.of(new Marker("one"))
        );
        registry.register(
                MovementLandingHookRegistry.HookFamily.TILE_TRAP,
                "second",
                context -> List.of(new Marker("two"))
        );

        var context = new MovementLandingHookRegistry.LandingContext(
                new TileEntryTrapResolution.Context(
                        "target",
                        "Target",
                        "blue",
                        42,
                        Set.of(),
                        new GridCoord(0, 0)
                ),
                List.of()
        );

        assertEquals(
                List.of("first", "second"),
                registry.resolve(context).stream().map(MovementLandingHookRegistry.ResolvedHook::hookKey).toList()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        MovementLandingHookRegistry.HookFamily.TILE_TRAP,
                        " FIRST ",
                        ignored -> List.of()
                )
        );
    }

    private static TileEntryTrapResolution.TrapLayer trap(
            String key,
            int layers,
            String sourceId,
            String sourceTeam,
            Set<String> terrains,
            String name
    ) {
        return new TileEntryTrapResolution.TrapLayer(
                key,
                layers,
                sourceId,
                sourceTeam,
                terrains,
                name
        );
    }

    private record Marker(String value) implements MovementLandingHookRegistry.LandingConsequence {
    }
}
