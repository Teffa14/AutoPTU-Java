package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativeOrderAssemblyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInitiativeOrderAssemblyTest {
    @Test
    void assemblesTrainerAndPokemonEntriesFromCanonicalRuntimeState() {
        RuntimeCombatantState pokemon = combatant("pokemon", 10);
        pokemon.temporaryEffects().add(
                "initiative_penalty",
                Map.of("amount", -3, "expires_round", 1)
        );

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(pokemon)
        );
        state.syncCurrentRoundFromLifecycle(2);
        state.putTrainer(new TrainerRuntimeState(
                "trainer",
                List.of(),
                3,
                0,
                Map.of(),
                30,
                "team-a"
        ));
        state.bindController("pokemon", "trainer");

        InitiativeOrderAssemblyResult assembly = RuntimeInitiativeOrderAssembly.fromState(
                state,
                false,
                false
        );

        assertEquals(List.of("trainer", "pokemon"), assembly.orderedActorIds());
        assertEquals(List.of("initiative_penalty"), assembly.temporaryEffectFamiliesToClear().get("pokemon"));
        assertTrue(pokemon.temporaryEffects().has("initiative_penalty"));
        assertEquals(List.of(), state.initiativeProgress().orderedActorIds());
    }

    @Test
    void trickRoomOrderingIsAppliedOnlyByTheCoreAssemblyBoundary() {
        RuntimeCombatantState pokemon = combatant("pokemon", 10);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(pokemon)
        );
        state.putTrainer(new TrainerRuntimeState(
                "trainer",
                List.of(),
                3,
                0,
                Map.of(),
                30,
                "team-a"
        ));
        state.bindController("pokemon", "trainer");

        assertEquals(
                List.of("pokemon", "trainer"),
                RuntimeInitiativeOrderAssembly.fromState(state, true, false).orderedActorIds()
        );
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
