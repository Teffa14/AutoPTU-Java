package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativePokemonCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RuntimeHardenedInitiativeAuthorityTest {
    @Test
    void ignoresForgedHardenedBonusAndDerivesNormalBonusFromCanonicalState() {
        RuntimeCombatantState actor = combatant("actor", 10);
        actor.temporaryEffects().add("hardened");
        BattleRuntimeState state = stateWithTrainer(actor, List.of(), Map.of("intimidate", 8));
        state.injuryHistory().setCurrentInjuries("actor", 3);
        state.syncCurrentRoundFromLifecycle(4);

        RuntimeInitiativePokemonContext forged = new RuntimeInitiativePokemonContext(
                0, false, false, 999, false, false
        );
        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(state, "actor", forged);

        assertEquals(15, candidate.baseEntry().total());
    }

    @Test
    void pressOnAndIntimidateExpertDoubleCanonicalHardenedBonus() {
        RuntimeCombatantState actor = combatant("actor", 10);
        actor.temporaryEffects().add("hardened");
        actor.temporaryEffects().add("press_on_active");
        BattleRuntimeState state = stateWithTrainer(actor, List.of("Press On!"), Map.of("Intimidate", 6));
        state.injuryHistory().setCurrentInjuries("actor", 3);
        state.syncCurrentRoundFromLifecycle(4);

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                RuntimeInitiativePokemonContext.neutral()
        );

        assertEquals(20, candidate.baseEntry().total());
        assertEquals(6, state.requireTrainerForCombatant("actor").skillRank("INTIMIDATE"));
    }

    @Test
    void expiredHardenedEffectDoesNotContributeInitiative() {
        RuntimeCombatantState actor = combatant("actor", 10);
        actor.temporaryEffects().add("hardened", Map.of("expires_round", 3));
        BattleRuntimeState state = stateWithTrainer(actor, List.of("Press On!"), Map.of("intimidate", 8));
        state.injuryHistory().setCurrentInjuries("actor", 5);
        state.syncCurrentRoundFromLifecycle(4);

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                new RuntimeInitiativePokemonContext(0, false, false, 500, false, false)
        );

        assertEquals(10, candidate.baseEntry().total());
    }

    private static BattleRuntimeState stateWithTrainer(
            RuntimeCombatantState actor,
            List<String> features,
            Map<String, Integer> skills
    ) {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );
        state.putTrainer(new TrainerRuntimeState("trainer", features, 3, 0, skills));
        state.bindController("actor", "trainer");
        return state;
    }

    private static RuntimeCombatantState combatant(String id, int speed) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget(),
                stats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
