package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeAreaMoveTargetingTest {
    @Test
    void expandsTargetsFromCanonicalTileChoiceAndRuntimeGeometry() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2);
        RuntimeCombatantState first = combatant("first", 3, 2);
        RuntimeCombatantState second = combatant("second", 3, 3);
        BattleRuntimeState state = state(actor, first, second, burst);
        MoveChoice choice = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        EffectiveMoveTargetResolution result = RuntimeAreaMoveTargeting.resolve(state, choice);

        assertEquals(new GridCoord(3, 2), result.anchor());
        assertEquals(List.of("first", "second"), result.targetIds());
    }

    @Test
    void rejectsAnchorThatIsNoLongerLegalInCurrentState() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2);
        RuntimeCombatantState target = combatant("target", 3, 2);
        BattleRuntimeState state = state(actor, target, burst);
        MoveChoice stale = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(7, 7), ActionType.STANDARD);

        assertThrows(IllegalArgumentException.class, () -> RuntimeAreaMoveTargeting.resolve(state, stale));
    }

    @Test
    void rejectsMoveThatIsNotInCanonicalMoveset() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2);
        RuntimeCombatantState target = combatant("target", 3, 2);
        BattleRuntimeState state = state(actor, target, burst);
        MoveChoice spoofed = new MoveChoice(
                "actor", "not-owned", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        assertThrows(IllegalArgumentException.class, () -> RuntimeAreaMoveTargeting.resolve(state, spoofed));
    }

    @Test
    void rejectsExhaustedMoveFrequencyBeforeTargetExpansion() {
        MoveOption burst = new MoveOption(
                "burst",
                new MoveSpec("Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1"),
                ActionType.STANDARD,
                false,
                null,
                "Scene x1"
        );
        RuntimeCombatantState actor = combatant("actor", 0, 2);
        RuntimeCombatantState target = combatant("target", 3, 2);
        BattleRuntimeState state = state(actor, target, burst);
        actor.moveFrequencyUsage().recordUse(burst);
        MoveChoice choice = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        assertThrows(IllegalArgumentException.class, () -> RuntimeAreaMoveTargeting.resolve(state, choice));
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveOption move
    ) {
        return state(actor, target, null, move);
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            RuntimeCombatantState first,
            RuntimeCombatantState second,
            MoveOption move
    ) {
        List<RuntimeCombatantState> combatants = second == null
                ? List.of(actor, first)
                : List.of(actor, first, second);
        LinkedHashMap<String, CombatantAffiliationState> affiliation = new LinkedHashMap<>();
        affiliation.put(actor.combatantId(), CombatantAffiliationState.active("alpha"));
        affiliation.put(first.combatantId(), CombatantAffiliationState.active("beta"));
        if (second != null) {
            affiliation.put(second.combatantId(), CombatantAffiliationState.active("beta"));
        }
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliation,
                Map.of(actor.combatantId(), List.of(move))
        );
    }

    private static MoveOption burstMove() {
        return new MoveOption(
                "burst",
                new MoveSpec("Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1"),
                ActionType.STANDARD,
                false
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 5),
                30,
                30,
                new ActionBudget()
        );
    }
}
