package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoritativeInitiativeRoundRebuilderTest {
    @Test
    void rolloverBuildsInstallsAndExecutesCanonicalMixedInitiativeOrder() {
        RuntimeCombatantState pokemon = combatant("pokemon", 10);
        pokemon.temporaryEffects().add(
                "initiative_penalty",
                Map.of("amount", -3, "expires_round", 1)
        );
        pokemon.actionBudget().markAction(ActionType.STANDARD, "stale action from prior turn");

        BattleRuntimeState state = state(pokemon);
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

        BattleRoundController rounds = new BattleRoundController(state, 1);
        InitiativeTurnAdvanceResult result = rounds.advanceInitiativeTurnWithRollover(
                InitiativeRoundRebuilder.authoritative()
        );

        assertEquals(2, rounds.round());
        assertEquals(2, state.currentRound());
        assertEquals(List.of("trainer", "pokemon"), state.initiativeProgress().orderedActorIds());
        assertEquals(0, state.initiativeProgress().cursor());
        assertFalse(pokemon.temporaryEffects().has("initiative_penalty"));
        assertTrue(result.hasActor());
        assertEquals("trainer", result.actorId());
        assertEquals("trainer", rounds.turnState().currentActorId());
    }

    @Test
    void authoritativeRebuildRejectsRoundMismatchBeforeMutation() {
        RuntimeCombatantState pokemon = combatant("pokemon", 10);
        pokemon.temporaryEffects().add(
                "initiative_penalty",
                Map.of("amount", -3, "expires_round", 0)
        );
        BattleRuntimeState state = state(pokemon);

        assertThrows(
                IllegalStateException.class,
                () -> InitiativeRoundRebuilder.authoritative().rebuildOrder(state, 1)
        );

        assertTrue(pokemon.temporaryEffects().has("initiative_penalty"));
        assertEquals(List.of(), state.initiativeProgress().orderedActorIds());
        assertEquals(-1, state.initiativeProgress().cursor());
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
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
