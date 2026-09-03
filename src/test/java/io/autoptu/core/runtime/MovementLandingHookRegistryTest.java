package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovementLandingHookRegistryTest {
    @Test
    void standardRegistryKeepsOnlyObservableTrapConsequencesInTrapOrder() {
        GridCoord coordinate = new GridCoord(3, 2);
        var context = new MovementLandingHookRegistry.LandingContext(
                new TileEntryTrapResolution.EntryContext(
                        "target",
                        "Target",
                        "blue",
                        42,
                        coordinate,
                        Set.of("forest")
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
        assertEquals(
                List.of("tile_traps", "tile_traps"),
                resolved.stream().map(MovementLandingHookRegistry.ResolvedHook::hookKey).toList()
        );
        assertEquals(
                List.of(
                        MovementLandingHookRegistry.HookFamily.TILE_TRAP,
                        MovementLandingHookRegistry.HookFamily.TILE_TRAP
                ),
                resolved.stream().map(MovementLandingHookRegistry.ResolvedHook::family).toList()
        );

        var blocked = (MovementLandingHookRegistry.TileTrapConsequence) resolved.get(0).consequence();
        assertEquals(0, blocked.resolution().triggers().size());
        assertEquals(1, blocked.resolution().blocks().size());
        assertEquals("naturewalk", blocked.resolution().blocks().get(0).trapKey());
        assertEquals(Set.of(), blocked.resolution().consumedTrapKeys());

        var triggered = (MovementLandingHookRegistry.TileTrapConsequence) resolved.get(1).consequence();
        assertEquals(1, triggered.resolution().triggers().size());
        assertEquals(0, triggered.resolution().blocks().size());
        var trigger = triggered.resolution().triggers().get(0);
        assertEquals("trigger", trigger.trapKey());
        assertEquals(
                List.of(
                        TileEntryTrapResolution.EffectStep.APPLY_STATUS,
                        TileEntryTrapResolution.EffectStep.EMIT_TRAP_EVENT,
                        TileEntryTrapResolution.EffectStep.CONSUME_TRAP
                ),
                trigger.effectOrder()
        );
        assertEquals(Set.of("trigger"), triggered.resolution().consumedTrapKeys());
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
                new TileEntryTrapResolution.EntryContext(
                        "target",
                        "Target",
                        "blue",
                        42,
                        new GridCoord(0, 0),
                        Set.of()
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
