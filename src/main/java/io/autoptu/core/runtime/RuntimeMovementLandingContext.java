package io.autoptu.core.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds movement-landing hook input exclusively from server-owned battle state and canonical
 * rule content. Movement producers provide only the combatant identity; adapters never decide
 * team affiliation, HP, landing position, active tile hazards, or Naturewalk applicability.
 */
final class RuntimeMovementLandingContext {
    private RuntimeMovementLandingContext() {}

    static MovementLandingHookRegistry.LandingContext resolve(
            BattleRuntimeState state,
            String combatantId,
            CombatantRuleContent ruleContent
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ruleContent, "ruleContent");
        RuntimeCombatantState combatant = state.requireCombatant(combatantId);

        String actorName = combatant.profileIdentity().name();
        if (actorName.isBlank()) actorName = combatant.combatantId();

        Set<String> naturewalkTerrains = new LinkedHashSet<>(ruleContent.effectiveNaturewalkLabels());
        TileEntryTrapResolution.EntryContext entryContext = new TileEntryTrapResolution.EntryContext(
                combatant.combatantId(),
                actorName,
                state.teamId(combatant.combatantId()),
                combatant.hp(),
                combatant.position(),
                naturewalkTerrains
        );
        List<TileEntryTrapResolution.TrapLayer> tileTraps = state.tileTrapsAt(combatant.position());
        return new MovementLandingHookRegistry.LandingContext(entryContext, tileTraps);
    }
}
