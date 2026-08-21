package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativeOrderAssemblyResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitiativeAssemblyInstallerTest {
    @Test
    void installsCanonicalOrderAndAppliesPythonAssemblyCleanupRequests() {
        RuntimeCombatantState first = combatant("first");
        RuntimeCombatantState second = combatant("second");
        first.temporaryEffects().add("initiative_penalty", Map.of("amount", -4, "expires_round", 3));
        first.temporaryEffects().add("initiative_penalty", Map.of("amount", -2, "expires_round", 3));
        first.temporaryEffects().add("persistent_marker", Map.of("source", "test"));

        BattleRuntimeState state = state(first, second);
        state.initiativeProgress().replaceOrderFromLifecycle(List.of("first"));
        state.initiativeProgress().setCursorFromLifecycle(0);

        LinkedHashMap<String, List<String>> cleanup = new LinkedHashMap<>();
        cleanup.put("first", List.of("initiative_penalty"));
        InitiativeOrderAssemblyResult assembly = new InitiativeOrderAssemblyResult(
                List.of(
                        entry("second", 20),
                        entry("first", 10)
                ),
                cleanup
        );

        List<String> installed = InitiativeAssemblyInstaller.install(state, assembly);

        assertEquals(List.of("second", "first"), installed);
        assertEquals(installed, state.initiativeProgress().orderedActorIds());
        assertEquals(-1, state.initiativeProgress().cursor());
        assertFalse(first.temporaryEffects().has("initiative_penalty"));
        assertTrue(first.temporaryEffects().has("persistent_marker"));
    }

    @Test
    void installsCanonicalTrainerAndPokemonSlotsTogether() {
        RuntimeCombatantState pokemon = combatant("pokemon");
        BattleRuntimeState state = state(pokemon);
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 5));

        InitiativeOrderAssemblyResult assembly = new InitiativeOrderAssemblyResult(
                List.of(
                        new InitiativeEntry("trainer", "trainer", 30, 0, 0, 30),
                        new InitiativeEntry("pokemon", "trainer", 20, 0, 0, 20)
                ),
                Map.of()
        );

        assertEquals(
                List.of("trainer", "pokemon"),
                InitiativeAssemblyInstaller.install(state, assembly)
        );
        assertEquals(List.of("trainer", "pokemon"), state.initiativeProgress().orderedActorIds());
        assertEquals(-1, state.initiativeProgress().cursor());
    }

    @Test
    void invalidOrderFailsBeforeAnyCleanupMutation() {
        RuntimeCombatantState actor = combatant("actor");
        actor.temporaryEffects().add("initiative_penalty", Map.of("amount", -3));
        BattleRuntimeState state = state(actor);

        InitiativeOrderAssemblyResult assembly = new InitiativeOrderAssemblyResult(
                List.of(entry("forged-client-id", 99)),
                Map.of("actor", List.of("initiative_penalty"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> InitiativeAssemblyInstaller.install(state, assembly)
        );
        assertTrue(actor.temporaryEffects().has("initiative_penalty"));
        assertEquals(List.of(), state.initiativeProgress().orderedActorIds());
        assertEquals(-1, state.initiativeProgress().cursor());
    }

    @Test
    void invalidCleanupIdentityFailsBeforeEarlierCleanupCanMutateState() {
        RuntimeCombatantState actor = combatant("actor");
        actor.temporaryEffects().add("initiative_penalty", Map.of("amount", -3));
        BattleRuntimeState state = state(actor);

        LinkedHashMap<String, List<String>> cleanup = new LinkedHashMap<>();
        cleanup.put("actor", List.of("initiative_penalty"));
        cleanup.put("unknown", List.of("rocket_initiative"));
        InitiativeOrderAssemblyResult assembly = new InitiativeOrderAssemblyResult(
                List.of(entry("actor", 10)),
                cleanup
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> InitiativeAssemblyInstaller.install(state, assembly)
        );
        assertTrue(actor.temporaryEffects().has("initiative_penalty"));
        assertEquals(List.of(), state.initiativeProgress().orderedActorIds());
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(combatants)
        );
    }

    private static RuntimeCombatantState combatant(String id) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget()
        );
    }

    private static InitiativeEntry entry(String actorId, int total) {
        return new InitiativeEntry(actorId, "trainer", total, 0, 0, total);
    }
}
