package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable post-entry family for abilities that reset combat stages on nearby allied combatants.
 *
 * <p>The family owns relation/range selection, state mutation, one-shot marker handling and
 * semantic event materialization. Individual abilities provide names, range and description as
 * data. Minecraft/Cobblemon may render the resulting events but never selects targets or mutates
 * combat stages.</p>
 */
public final class AbilityCombatStageResetPostEntryHandler
        implements CombatantSwitchPostEntryDispatcher.StageHandler {
    private final String abilityName;
    private final String temporaryEffectName;
    private final int maxDistance;
    private final String description;

    public AbilityCombatStageResetPostEntryHandler(
            String abilityName,
            String temporaryEffectName,
            int maxDistance,
            String description
    ) {
        if (abilityName == null || abilityName.isBlank()) {
            throw new IllegalArgumentException("abilityName is required");
        }
        if (temporaryEffectName == null || temporaryEffectName.isBlank()) {
            throw new IllegalArgumentException("temporaryEffectName is required");
        }
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance cannot be negative");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        this.abilityName = abilityName.strip();
        this.temporaryEffectName = temporaryEffectName.strip();
        this.maxDistance = maxDistance;
        this.description = description.strip();
    }

    @Override
    public List<? extends BattleEvent> handle(CombatantSwitchPostEntryDispatcher.DispatchContext context) {
        if (context == null) throw new IllegalArgumentException("post-entry context is required");
        BattleRuntimeState state = context.state();
        RuntimeCombatantState source = state.requireCombatant(context.replacementId());
        if (!EffectiveAbilityResolver.hasExact(source, abilityName)
                || source.temporaryEffects().has(temporaryEffectName)) {
            return List.of();
        }

        String sourceTeam = state.teamId(context.replacementId());
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (String targetId : state.combatantIds()) {
            if (!state.teamId(targetId).equals(sourceTeam)) continue;
            if (!state.isActive(targetId)) continue;
            RuntimeCombatantState target = state.requireCombatant(targetId);
            if (combatantDistance(state, context.replacementId(), targetId) > maxDistance) continue;
            if (!hasNonZeroCombatStage(target)) continue;

            for (CombatStageStat stat : CombatStageStat.values()) {
                target.combatStages().set(stat, 0);
            }
            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            details.put("target", targetId);
            details.put("move", abilityName);
            details.put("description", description);
            details.put("targetHp", target.hp());
            details.put("phase", context.phase());
            details.put("round", context.round());
            events.add(new AbilityEvent(context.replacementId(), abilityName, "reset_cs", details));
        }
        source.temporaryEffects().add(temporaryEffectName);
        return List.copyOf(events);
    }

    private static boolean hasNonZeroCombatStage(RuntimeCombatantState combatant) {
        for (int value : combatant.combatStages().fullSnapshot().values()) {
            if (value != 0) return true;
        }
        return false;
    }

    private static int combatantDistance(BattleRuntimeState state, String firstId, String secondId) {
        Set<GridCoord> first = Targeting.footprintTiles(
                state.requireCombatant(firstId).position(),
                state.geometry(firstId).sizeLabel()
        );
        Set<GridCoord> second = Targeting.footprintTiles(
                state.requireCombatant(secondId).position(),
                state.geometry(secondId).sizeLabel()
        );
        int best = Integer.MAX_VALUE;
        for (GridCoord left : first) {
            for (GridCoord right : second) {
                best = Math.min(best, Math.max(
                        Math.abs(left.x() - right.x()),
                        Math.abs(left.y() - right.y())
                ));
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }
}
