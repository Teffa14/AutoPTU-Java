package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStageStat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned state mutation shared by Transform-like moves and abilities.
 *
 * <p>The resolver deliberately owns only the reusable transformation payload: copying the
 * complete seven-stage PTU Combat Stage snapshot when the target has at least one modified
 * stage, then replacing the actor's temporary copied ability. Target selection, RNG choice,
 * once-per-entry guards, action economy, and semantic events remain the responsibility of
 * the calling move/ability hook.</p>
 */
public final class TransformationStateResolver {
    public static final String ENTRAINED_ABILITY = "entrained_ability";

    private TransformationStateResolver() {
    }

    public static Result apply(
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            String chosenAbility,
            String source
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }

        Map<CombatStageStat, Integer> targetStages = target.combatStages().fullSnapshot();
        boolean copiedStages = targetStages.values().stream().anyMatch(value -> value != 0);
        if (copiedStages) {
            for (CombatStageStat stat : CombatStageStat.values()) {
                actor.combatStages().set(stat, targetStages.getOrDefault(stat, 0));
            }
        }

        actor.temporaryEffects().removeAll(ENTRAINED_ABILITY);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("ability", chosenAbility);
        payload.put("source", source.strip());
        actor.temporaryEffects().add(ENTRAINED_ABILITY, payload);

        return new Result(copiedStages, chosenAbility);
    }

    public record Result(boolean copiedStages, String abilityAssigned) {
    }
}
