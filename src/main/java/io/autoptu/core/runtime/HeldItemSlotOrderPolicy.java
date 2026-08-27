package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Python-compatible held-item processing order for phase/item hooks.
 *
 * <p>The Python oracle sorts held-item rows by their slot index in descending order
 * before applying START effects. This policy keeps that ordering rule reusable and
 * independent from any Minecraft/Cobblemon inventory representation.</p>
 */
public final class HeldItemSlotOrderPolicy {
    private HeldItemSlotOrderPolicy() {}

    public static <T> List<T> descendingBySlot(
            Collection<T> entries,
            ToIntFunction<T> slotIndex
    ) {
        Objects.requireNonNull(slotIndex, "slot index function");
        ArrayList<T> ordered = new ArrayList<>();
        if (entries != null) {
            for (T entry : entries) {
                if (entry != null) ordered.add(entry);
            }
        }
        ordered.sort(Comparator.comparingInt(slotIndex).reversed());
        return List.copyOf(ordered);
    }
}
