package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMultiTargetSecondaryStatusIntegrationTest {
    @Test
    void areaMoveRunsCanonicalSecondaryStatusPipelinePerTargetWithSingleResourceSpend() {
        MoveOption burst = new MoveOption(
                "poison-burst",
                new MoveSpec(
                        "Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1",
                        List.of(), "Poisons the target on 1+"
                ),
                ActionType.STANDARD,
                false,
                new MoveCombatProfile(null, 6, 20, "special", "Poison"),
                "Scene x1"
        );
        RuntimeCombatantState actor = combatant("actor", 0, 2, List.of());
        RuntimeCombatantState first = combatant("first", 3, 2, List.of());
        RuntimeCombatantState immune = combatant("immune", 3, 3, List.of("Immunity"));
        LinkedHashMap<String, CombatantAffiliationState> affiliation = new LinkedHashMap<>();
        affiliation.put("actor", CombatantAffiliationState.active("alpha"));
        affiliation.put("first", CombatantAffiliationState.active("beta"));
        affiliation.put("immune", CombatantAffiliationState.active("beta"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, first, immune),
                Map.of(), Map.of(), Map.of(), affiliation,
                Map.of("actor", List.of(burst))
        );
        MoveChoice choice = new MoveChoice(
                "actor", "poison-burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(),
                false,
                false
        );

        assertEquals(List.of("first", "immune"), result.targetIds());
        assertTrue(state.hasStatus("first", "poisoned"));
        assertFalse(state.hasStatus("immune", "poisoned"));
        assertTrue(result.events().stream()
                .filter(RuleEffectEvent.class::isInstance)
                .map(RuleEffectEvent.class::cast)
                .anyMatch(event -> event.sourceName().equals("Immunity")
                        && event.actorId().equals("immune")
                        && event.effect().equals("status_block")));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses("poison-burst"));
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, List<String> abilities) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, 20,
                        CombatStat.DEF, 10,
                        CombatStat.SPATK, 20,
                        CombatStat.SPDEF, 10,
                        CombatStat.SPD, 10
                ),
                Map.of(), Map.of(), Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 5),
                100,
                100,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false),
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                null, 99, 6, 20, false, false, false,
                1, 1, 1, false, 1.0, List.of()
        );
    }
}
