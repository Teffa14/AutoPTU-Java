package io.autoptu.core.runtime;

import io.autoptu.core.rules.ForcedMovementStepConstraintResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Projects authoritative temporary-effect state into generic forced-movement constraints. */
final class RuntimeForcedMovementStepConstraintProjection {
    private RuntimeForcedMovementStepConstraintProjection() {}

    static List<ForcedMovementStepConstraintResolution.Constraint> constraints(
            BattleRuntimeState state,
            String combatantId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        RuntimeCombatantState combatant = state.requireCombatant(combatantId);
        TemporaryEffectStore store = combatant.temporaryEffects();
        Set<String> supported = ForcedMovementStepConstraintResolution.temporaryEffectNames();
        ArrayList<ForcedMovementStepConstraintResolution.TemporaryEffect> projected = new ArrayList<>();

        for (TemporaryEffectEntry entry : store.entriesInInsertionOrder()) {
            if (entry == null || !supported.contains(entry.name())) continue;
            Integer expiresRound = integer(entry.payload().get("expires_round"));
            if (expiresRound != null && state.currentRound() > expiresRound) {
                store.removeEntry(entry);
                continue;
            }
            ForcedMovementStepConstraintResolution.projectTemporaryEffect(entry.name(), entry.payload())
                    .ifPresent(projected::add);
        }
        return ForcedMovementStepConstraintResolution.resolve(projected, state.currentRound());
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
