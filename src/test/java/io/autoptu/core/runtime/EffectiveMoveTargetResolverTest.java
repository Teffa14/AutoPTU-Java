package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveMoveTargetResolverTest {
    @Test
    void selectsByFootprintOverlapAndPrioritizesLiveExplicitTarget() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 3);
        RuntimeCombatantState large = combatant("large", 1, 3);
        RuntimeCombatantState preferred = combatant("preferred", 3, 3);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(attacker, large, preferred),
                Map.of(),
                Map.of(),
                Map.of("large", new CombatantGeometryState("Large")),
                Map.of(),
                Map.of()
        );
        MoveOption burst = new MoveOption(
                "burst",
                new MoveSpec("Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1"),
                ActionType.STANDARD,
                false
        );

        EffectiveMoveTargetResolution result = EffectiveMoveTargetResolver.resolve(
                state,
                "attacker",
                burst,
                new GridCoord(3, 3),
                "preferred"
        );

        assertEquals(List.of("preferred", "large"), result.targetIds());
        assertTrue(result.affectedTiles().contains(new GridCoord(2, 3)));
        assertTrue(result.affectedTiles().contains(new GridCoord(3, 3)));
    }

    @Test
    void lineOfSightUsesServerOwnedBlockers() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0);
        RuntimeCombatantState target = combatant("target", 3, 0);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(new GridCoord(1, 0)), Map.of()),
                List.of(attacker, target)
        );
        MoveOption ranged = new MoveOption(
                "ranged",
                new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6"),
                ActionType.STANDARD,
                true
        );

        EffectiveMoveTargetResolution result = EffectiveMoveTargetResolver.resolve(
                state,
                "attacker",
                ranged,
                new GridCoord(3, 0),
                "target"
        );

        assertTrue(result.targetIds().isEmpty());
    }

    @Test
    void stalePreferredIdDoesNotSuppressCombatantAtStoredAnchor() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 2);
        RuntimeCombatantState replacement = combatant("replacement", 3, 2);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(attacker, replacement)
        );
        MoveOption ranged = new MoveOption(
                "future-hit",
                new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6"),
                ActionType.STANDARD,
                false
        );

        EffectiveMoveTargetResolution result = EffectiveMoveTargetResolver.resolve(
                state,
                "attacker",
                ranged,
                new GridCoord(3, 2),
                "missing-target"
        );

        assertEquals(List.of("replacement"), result.targetIds());
    }

    @Test
    void excludesNonPositiveHpButDoesNotInventAnActiveStateFilter() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 2);
        RuntimeCombatantState inactive = combatant("inactive", 3, 2);
        RuntimeCombatantState fainted = combatant("fainted", 3, 2, 0);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(attacker, inactive, fainted),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("alpha"),
                        "inactive", new CombatantAffiliationState("beta", false),
                        "fainted", CombatantAffiliationState.active("beta")
                )
        );
        MoveOption ranged = new MoveOption(
                "future-hit",
                new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6"),
                ActionType.STANDARD,
                false
        );

        EffectiveMoveTargetResolution result = EffectiveMoveTargetResolver.resolve(
                state,
                "attacker",
                ranged,
                new GridCoord(3, 2),
                null
        );

        assertEquals(List.of("inactive"), result.targetIds());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return combatant(id, x, y, 30);
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 5),
                hp,
                30,
                new ActionBudget()
        );
    }
}
