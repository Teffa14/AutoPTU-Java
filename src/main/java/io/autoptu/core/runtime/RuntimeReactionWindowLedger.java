package io.autoptu.core.runtime;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Battle-local ledger for reaction opportunities that have already been surfaced.
 *
 * <p>Identity comes from {@link RuntimeReactionWindow#windowKey()}, which binds the
 * authoritative event occurrence, reactor, reaction key, trigger family, actor, and round.
 * The linked set preserves first-discovery order for deterministic diagnostics. This ledger
 * does not commit reaction usage, spend actions, choose targets, or execute reactions.</p>
 */
public final class RuntimeReactionWindowLedger {
    private final Set<String> discoveredWindowKeys = new LinkedHashSet<>();

    /**
     * Claims one discovered window. Returns true only for its first battle-local discovery.
     */
    public boolean claim(RuntimeReactionWindow window) {
        Objects.requireNonNull(window, "window");
        return discoveredWindowKeys.add(window.windowKey());
    }

    public boolean hasSeen(RuntimeReactionWindow window) {
        Objects.requireNonNull(window, "window");
        return discoveredWindowKeys.contains(window.windowKey());
    }

    public int size() {
        return discoveredWindowKeys.size();
    }
}
