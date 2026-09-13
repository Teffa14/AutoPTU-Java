package io.autoptu.core.hook;

import java.util.Objects;

/** Minimal language-neutral input for triggered action discovery. */
public record ActionWindowContext(ActionWindow window, String actingCombatantId, String triggerKey) {
    public ActionWindowContext {
        window = Objects.requireNonNull(window, "window");
        if (actingCombatantId == null || actingCombatantId.isBlank()) {
            throw new IllegalArgumentException("acting combatant id is required");
        }
        actingCombatantId = actingCombatantId.strip();
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new IllegalArgumentException("trigger key is required");
        }
        triggerKey = triggerKey.strip();
    }
}
