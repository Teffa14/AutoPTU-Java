package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AutobattlerActionSpace;
import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Minecraft-facing action-space boundary backed by authoritative runtime state.
 *
 * External adapters provide move options and rendering/environment projections only.
 * Current positions, PTU footprint sizes, battle affiliation, active/fainted state,
 * movement capabilities, and action economy are read from {@link BattleRuntimeState}.
 */
public final class RuntimeAutobattlerActionSpace {
    private RuntimeAutobattlerActionSpace() {
    }

    /**
     * Preferred server-authoritative decision boundary.
     *
     * Python AI semantics are preserved here: Blessing targets active allies; other
     * combatant-targeting moves use active opponents. Fainted/inactive combatants are
     * removed before the AI sees a choice.
     */
    public static List<BattleChoice> legalChoices(
            BattleRuntimeState state,
            String actorId,
            List<MoveOption> moves,
            Set<GridCoord> lineOfSightBlockers,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        return legalChoicesInternal(
                state,
                actorId,
                moves,
                null,
                lineOfSightBlockers,
                movementPenalty,
                canFit
        );
    }

    /**
     * Transitional compatibility overload for older callers that already prefiltered
     * target IDs. Supplied IDs can only restrict the server-derived set; they cannot
     * add an ally, inactive combatant, fainted combatant, or unknown combatant.
     */
    public static List<BattleChoice> legalChoices(
            BattleRuntimeState state,
            String actorId,
            List<MoveOption> moves,
            List<String> targetCombatantIds,
            Set<GridCoord> lineOfSightBlockers,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        Set<String> permittedIds = validateTargetIds(state, targetCombatantIds);
        return legalChoicesInternal(
                state,
                actorId,
                moves,
                permittedIds,
                lineOfSightBlockers,
                movementPenalty,
                canFit
        );
    }

    private static List<BattleChoice> legalChoicesInternal(
            BattleRuntimeState state,
            String actorId,
            List<MoveOption> moves,
            Set<String> permittedIds,
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
        if (!state.isTargetableCombatant(actorId)) {
            return List.of();
        }

        List<BattleChoice> choices = new ArrayList<>();
        choices.addAll(AutobattlerActionSpace.legalShiftChoices(
                actor.combatantId(),
                state.grid(),
                actor.movementProfile(),
                actor.actionBudget(),
                movementPenalty,
                canFit
        ));

        List<MoveOption> safeMoves = moves == null ? List.of() : moves;
        for (MoveOption move : safeMoves) {
            if (move == null) {
                continue;
            }
            choices.addAll(AutobattlerActionSpace.legalMoveChoices(
                    actor.combatantId(),
                    state.geometry(actorId).sizeLabel(),
                    state.grid(),
                    actor.position(),
                    actor.actionBudget(),
                    List.of(move),
                    authoritativeTargets(state, actorId, move, permittedIds),
                    lineOfSightBlockers
            ));
        }

        choices.sort(Comparator.comparing(BattleChoice::stableKey));
        return List.copyOf(choices);
    }

    private static List<TargetCandidate> authoritativeTargets(
            BattleRuntimeState state,
            String actorId,
            MoveOption move,
            Set<String> permittedIds
    ) {
        if (!Targeting.moveRequiresTarget(move.spec())) {
            return List.of();
        }

        String actorTeam = state.teamId(actorId);
        boolean blessing = Targeting.normalizedTargetKind(move.spec()).equals("blessing");
        List<TargetCandidate> candidates = new ArrayList<>();
        for (String targetId : state.combatantIds()) {
            if (permittedIds != null && !permittedIds.contains(targetId)) {
                continue;
            }
            if (!state.isTargetableCombatant(targetId)) {
                continue;
            }

            boolean allied = state.teamId(targetId).equals(actorTeam);
            if (blessing != allied) {
                continue;
            }

            RuntimeCombatantState target = state.requireCombatant(targetId);
            candidates.add(new TargetCandidate(
                    target.combatantId(),
                    target.position(),
                    state.geometry(targetId).sizeLabel()
            ));
        }
        return List.copyOf(candidates);
    }

    private static Set<String> validateTargetIds(
            BattleRuntimeState state,
            List<String> targetCombatantIds
    ) {
        if (targetCombatantIds == null || targetCombatantIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        for (String targetId : targetCombatantIds) {
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("target combatant id is required");
            }
            state.requireCombatant(targetId);
            uniqueIds.add(targetId);
        }
        return Set.copyOf(uniqueIds);
    }
}
