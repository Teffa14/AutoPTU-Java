package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntryTrapResolutionTest {
    private static final TileEntryTrapResolution.EntryContext ENTRY =
            new TileEntryTrapResolution.EntryContext("target", "blue", 37, new GridCoord(4, 3), Set.of());

    @Test
    void enemyTrapTriggersWithProvenanceAndConsumesWholeTrapKey() {
        var result = TileEntryTrapResolution.resolve(ENTRY, List.of(
                new TileEntryTrapResolution.TrapLayer(
                        "abrasion_trap", 3, "source", "red", Set.of("mountain", "cave"), "Abrasion Trap")
        ));

        assertTrue(result.blocks().isEmpty());
        assertEquals(1, result.triggers().size());
        var trigger = result.triggers().getFirst();
        assertEquals("target", trigger.actorId());
        assertEquals("abrasion_trap", trigger.trapKey());
        assertEquals("Abrasion Trap", trigger.trapName());
        assertEquals("source", trigger.sourceId());
        assertEquals(Set.of("mountain", "cave"), trigger.terrains());
        assertEquals(new GridCoord(4, 3), trigger.coordinate());
        assertEquals(37, trigger.targetHp());
        assertEquals(Set.of("abrasion_trap"), result.consumedTrapKeys());
    }

    @Test
    void sameTeamSourceDoesNotTriggerBlockOrConsume() {
        var result = TileEntryTrapResolution.resolve(ENTRY, List.of(
                new TileEntryTrapResolution.TrapLayer("dust_trap", 1, "ally", "blue", Set.of("desert"), "Dust Trap")
        ));
        assertTrue(result.triggers().isEmpty());
        assertTrue(result.blocks().isEmpty());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }

    @Test
    void matchingNaturewalkEmitsBlockWithoutConsumption() {
        var context = new TileEntryTrapResolution.EntryContext(
                "target", "blue", 37, new GridCoord(4, 3), Set.of("forest"));
        var result = TileEntryTrapResolution.resolve(context, List.of(
                new TileEntryTrapResolution.TrapLayer("tangle_trap", 1, "source", "red", Set.of("forest", "wetlands"), "Tangle Trap")
        ));

        assertTrue(result.triggers().isEmpty());
        assertEquals(1, result.blocks().size());
        var block = result.blocks().getFirst();
        assertEquals("target", block.actorId());
        assertEquals("tangle_trap", block.trapKey());
        assertEquals("Naturewalk ignores the trap's terrain-linked effects.", block.description());
        assertEquals(37, block.targetHp());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }

    @Test
    void nonpositiveLayerTrapsAreIgnored() {
        var result = TileEntryTrapResolution.resolve(ENTRY, List.of(
                new TileEntryTrapResolution.TrapLayer("zero_trap", 0, "source", "red", Set.of("ocean"), "Zero Trap"),
                new TileEntryTrapResolution.TrapLayer("stale_trap", -2, "source", "red", Set.of("ocean"), "Stale Trap")
        ));
        assertTrue(result.triggers().isEmpty());
        assertTrue(result.blocks().isEmpty());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }
}
