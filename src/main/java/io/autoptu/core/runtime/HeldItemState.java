package io.autoptu.core.runtime;

import java.util.Locale;

/**
 * Stable server-authoritative identity for one held item in battle state.
 *
 * The stable item ID is separate from the display/catalog name so duplicate
 * copies of the same item can later be consumed or replaced independently.
 */
public record HeldItemState(String itemId, String name) {
    public HeldItemState {
        itemId = safe(itemId);
        name = safe(name);
        if (itemId.isBlank()) throw new IllegalArgumentException("itemId is required");
        if (name.isBlank()) throw new IllegalArgumentException("item name is required");
    }

    public String normalizedName() {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
