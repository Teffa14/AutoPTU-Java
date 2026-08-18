package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AutobattlerActionSpace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Minecraft-facing action-space boundary backed by authoritative runtime state.
 *
 * External adapters provide move options, semantically permitted target IDs, and
 * rendering/environment projections only. Current positions, PTU footprint sizes,
 * movement capabilities, and action economy are read from {@link BattleRuntimeState}.
 */
public final class RuntimeAutobattlerActionSpace {
    private RuntimeAutobattlerActionSpace() {
    }

    public static List<BattleChoice> legalChoices(
            BattleRuntimeState state,
            String actorId,
            List<MoveOption> moves,
            List<String> targetCombatantIds,
            Set<GridCoord> lineOfSightBlockers,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        List<TargetCandidate> targetCandidates = authoritativeTargets(state, targetCombatantIds);
        return AutobattlerActionSpace.legalChoices(
                actor.combatantId(),
                state.geometry(actorId).sizeLabel(),
                state.grid(),
                actor.movementProfile(),
                actor.actionBudget(),
                moves,
                targetCandidates,
                lineOfSightBlockers,
                movementPenalty,
                canFit
        );
    }

    private static List<TargetCandidate> authoritativeTargets(
            BattleRuntimeState state,
            List<String> targetCombatantIds
    ) {
        if (targetCombatantIds == null || targetCombatantIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        for (String targetId : targetCombatantIds) {
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("target combatant id is required");
            }
            uniqueIds.add(targetId);
        }

        List<TargetCandidate> candidates = new ArrayList<>(uniqueIds.size());
        for (String targetId : uniqueIds) {
            RuntimeCombatantState target = state.requireCombatant(targetId);
            candidates.add(new TargetCandidate(
                    target.combatantId(),
                    target.position(),
                    state.geometry(targetId).sizeLabel()
            ));
        }
        return List.copyOf(candidates);
    }
}
