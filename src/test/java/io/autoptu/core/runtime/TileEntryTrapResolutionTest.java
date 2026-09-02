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
    void sameTeamSourceDoesNotTriggerOrConsume() {
        var result = TileEntryTrapResolution.resolve(ENTRY, List.of(
                new TileEntryTrapResolution.TrapLayer("dust_trap", 1, "ally", "blue", Set.of("desert"), "Dust Trap")
        ));
        assertTrue(result.triggers().isEmpty());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }

    @Test
    void matchingNaturewalkDoesNotTriggerOrConsume() {
        var context = new TileEntryTrapResolution.EntryContext(
                "target", "blue", 37, new GridCoord(4, 3), Set.of("forest"));
        var result = TileEntryTrapResolution.resolve(context, List.of(
                new TileEntryTrapResolution.TrapLayer("tangle_trap", 1, "source", "red", Set.of("forest", "wetlands"), "Tangle Trap")
        ));
        assertTrue(result.triggers().isEmpty());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }

    @Test
    void zeroLayerTrapIsIgnored() {
        var result = TileEntryTrapResolution.resolve(ENTRY, List.of(
                new TileEntryTrapResolution.TrapLayer("slick_trap", 0, "source", "red", Set.of("ocean"), "Slick Trap")
        ));
        assertTrue(result.triggers().isEmpty());
        assertTrue(result.consumedTrapKeys().isEmpty());
    }
}
