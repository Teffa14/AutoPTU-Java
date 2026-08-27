package io.autoptu.core.hook;

import io.autoptu.core.model.TurnPhase;

import java.util.List;
import java.util.Objects;

/**
 * Cross-system phase envelope frozen from Python StatusController.run_phase_effects().
 *
 * This policy intentionally owns ordering only. Concrete held-item, food and
 * combatant phase hooks remain separate registries so Minecraft/Cobblemon never
 * becomes the coordinator for PTU phase effects.
 */
public final class StatusControllerPhaseOrderingPolicy {
    private StatusControllerPhaseOrderingPolicy() {}

    public enum Step {
        HELD_ITEM_START,
        FOOD_REGEN,
        FOOD_BUFF_START,
        COMBATANT_PHASE_EFFECTS,
        HELD_ITEM_END
    }

    public static List<Step> sequence(TurnPhase phase) {
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case START -> List.of(
                    Step.HELD_ITEM_START,
                    Step.FOOD_REGEN,
                    Step.FOOD_BUFF_START,
                    Step.COMBATANT_PHASE_EFFECTS
            );
            case END -> List.of(
                    Step.COMBATANT_PHASE_EFFECTS,
                    Step.HELD_ITEM_END
            );
            default -> List.of(Step.COMBATANT_PHASE_EFFECTS);
        };
    }
}
