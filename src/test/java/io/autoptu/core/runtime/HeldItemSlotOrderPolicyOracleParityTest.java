package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemSlotOrderPolicyOracleParityTest {
    private record SlotEntry(int slot, String id) {}

    @Test
    void slotOrderContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.held.item.start.slot.order.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("start_sorts_held_items"));
        assertEquals(1, fixture.get("start_orders_by_slot_index"));
        assertEquals(1, fixture.get("start_orders_slots_descending"));
        assertEquals(1, fixture.get("start_preserves_slot_identity_in_loop"));
        assertEquals(1, fixture.get("sorting_precedes_item_processing"));
    }

    @Test
    void ordersHigherSlotsFirstAndPreservesStableEqualSlotOrder() {
        List<SlotEntry> ordered = HeldItemSlotOrderPolicy.descendingBySlot(
                List.of(
                        new SlotEntry(1, "one-a"),
                        new SlotEntry(3, "three"),
                        new SlotEntry(1, "one-b"),
                        new SlotEntry(2, "two")
                ),
                SlotEntry::slot
        );

        assertEquals(List.of("three", "two", "one-a", "one-b"), ordered.stream().map(SlotEntry::id).toList());
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
