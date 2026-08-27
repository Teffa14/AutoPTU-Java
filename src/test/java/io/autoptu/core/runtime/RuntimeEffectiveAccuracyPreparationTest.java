package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeEffectiveAccuracyPreparationTest {
    @Test
    void preparationUsesDynamicIntrinsicAndRuntimeAccuracyInsteadOfLegacyInput() {
        RuntimeCombatantState actor = combatant(
                "actor",
                new GridCoord(1, 1),
                stats(2),
                List.of("Compound Eyes")
        );
        actor.setAccuracyStage(1);
        RuntimeCombatantState target = combatant(
                "target",
                new GridCoord(2, 1),
                stats(0),
                List.of()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target)
        );
        MoveOption move = move();

        RuntimeAuthoritativeMovePreparation.Prepared prepared = RuntimeAuthoritativeMovePreparation.prepare(
                state,
                choice(),
                move,
                legacyInput(-6),
                false,
                false
        );

        // Python: mutable stage 1 + intrinsic accuracy_cs 2 + Compound Eyes 3 = +6.
        assertEquals(6, prepared.input().accuracyStage());
    }

    @Test
    void preparationClampsAfterAllServerOwnedAccuracyContributions() {
        RuntimeCombatantState actor = combatant(
                "actor",
                new GridCoord(1, 1),
                stats(2),
                List.of("Compound Eyes", "Keen Eye")
        );
        actor.setAccuracyStage(5);
        RuntimeCombatantState target = combatant(
                "target",
                new GridCoord(2, 1),
                stats(0),
                List.of()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target)
        );

        RuntimeAuthoritativeMovePreparation.Prepared prepared = RuntimeAuthoritativeMovePreparation.prepare(
                state,
                choice(),
                move(),
                legacyInput(0),
                false,
                false
        );

        assertEquals(6, prepared.input().accuracyStage());
    }

    private static MoveOption move() {
        return MoveOption.standard(
                "Tackle",
                new MoveSpec("Melee", "Melee", 1, null, null, null, "Melee"),
                new MoveCombatProfile(2, 4, 20, "Physical", "Normal")
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "Tackle",
                ChoiceTargetMode.COMBATANT,
                "target",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveResolutionInput legacyInput(int accuracyStage) {
        return new MoveResolutionInput(
                2,
                0,
                accuracyStage,
                20,
                false,
                false,
                false,
                4,
                5,
                5,
                false,
                1.0,
                List.of()
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            CombatantStatProfile stats,
            List<String> abilities
    ) {
        EvasionProfile evasion = new EvasionProfile(stats, 0, 0, 0, false, false);
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 5),
                100,
                100,
                new ActionBudget(),
                stats,
                evasion,
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

    private static CombatantStatProfile stats(int intrinsicAccuracyCs) {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) bases.put(stat, 5);
        return new CombatantStatProfile(
                bases,
                Map.of(),
                Map.of(),
                Set.of(),
                intrinsicAccuracyCs
        );
    }
}
