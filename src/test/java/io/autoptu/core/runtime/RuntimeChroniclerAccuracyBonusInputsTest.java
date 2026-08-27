package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
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

class RuntimeChroniclerAccuracyBonusInputsTest {
    @Test
    void canonicalTrainerStateOverridesSpoofedExternalChroniclerBonus() {
        BattleRuntimeState state = state();
        RuntimeCombatantState attacker = state.requireCombatant("attacker");
        attacker.temporaryEffects().add("targeted_profiling", Map.of("target_id", "defender"));
        attacker.temporaryEffects().add("targeted_profiling", Map.of(
                "target_id", "defender",
                "source_controller", "chronicler-b"
        ));

        MoveOption move = MoveOption.standard(
                "Tackle",
                new MoveSpec("Melee", "Melee", 1, null, null, null, "Melee"),
                new MoveCombatProfile(2, 4, 20, "Physical", "Normal")
        );
        TemporaryAccuracyBonusResolution.Input input = RuntimeTemporaryAccuracyBonusInputs.fromState(
                state,
                "attacker",
                "defender",
                move,
                new RuntimeTemporaryAccuracyBonusInputs.ContextBonuses(null, 99)
        );

        assertEquals(4, RuntimeChroniclerAccuracyBonusInputs.resolve(state, "attacker", "defender"));
        assertEquals(4, TemporaryAccuracyBonusResolution.resolve(input));
    }

    @Test
    void expiresProfilingAndFailsClosedForUnknownSourceTrainer() {
        BattleRuntimeState state = state();
        state.syncCurrentRoundFromLifecycle(4);
        RuntimeCombatantState attacker = state.requireCombatant("attacker");
        attacker.temporaryEffects().add("targeted_profiling", Map.of(
                "target_id", "defender",
                "expires_round", 3
        ));
        attacker.temporaryEffects().add("targeted_profiling", Map.of(
                "target_id", "defender",
                "source_controller", "missing-trainer"
        ));

        assertEquals(0, RuntimeChroniclerAccuracyBonusInputs.resolve(state, "attacker", "defender"));
        assertEquals(1, attacker.temporaryEffects().count("targeted_profiling"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState attacker = combatant(
                "attacker", new GridCoord(0, 0), new CombatantProfileIdentity("Sparky", "Pikachu"));
        RuntimeCombatantState defender = combatant(
                "defender", new GridCoord(1, 0), new CombatantProfileIdentity("Ribbon", "Eevee"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(attacker, defender)
        );

        state.putTrainer(trainer(
                "chronicler-a",
                "Researcher A",
                new ChroniclerProfileMetadata(Set.of("Profile"), Map.of("profile", List.of("Eevee")))
        ));
        state.putTrainer(trainer(
                "chronicler-b",
                "Researcher B",
                new ChroniclerProfileMetadata(Set.of("Profile"), Map.of("profile", List.of("Gym Leader")))
        ));
        state.putTrainer(trainer(
                "target-trainer",
                "Gym Leader",
                ChroniclerProfileMetadata.empty()
        ));
        state.bindController("attacker", "chronicler-a");
        state.bindController("defender", "target-trainer");
        return state;
    }

    private static TrainerRuntimeState trainer(
            String id,
            String name,
            ChroniclerProfileMetadata metadata
    ) {
        return new TrainerRuntimeState(
                id,
                List.of(),
                0,
                0,
                Map.of(),
                null,
                "",
                Map.of(),
                Map.of(),
                name,
                metadata
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            CombatantProfileIdentity identity
    ) {
        CombatantStatProfile stats = stats();
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
                List.of(),
                identity
        );
    }

    private static CombatantStatProfile stats() {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) bases.put(stat, 5);
        return new CombatantStatProfile(bases, Map.of(), Map.of(), Set.of());
    }
}
