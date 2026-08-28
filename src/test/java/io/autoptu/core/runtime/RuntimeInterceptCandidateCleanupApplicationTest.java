package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeInterceptCandidateCleanupApplicationTest {
    @Test
    void appliesNoInterceptRemovalCountWithFirstFamilySemantics() {
        RuntimeCombatantState attacker = combatant("attacker", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        attacker.temporaryEffects().add("no_intercept", Map.of("expires_round", 2));
        attacker.temporaryEffects().add("no_intercept", Map.of("expires_round", 7));
        attacker.temporaryEffects().add("no_intercept", Map.of("expires_round", 1));
        BattleRuntimeState state = battle(attacker, target);
        state.syncCurrentRoundFromLifecycle(5);

        InterceptCandidateDiscoveryResolution.Result discovery = new InterceptCandidateDiscoveryResolution.Result(
                true,
                1,
                List.of(),
                Map.of()
        );

        RuntimeInterceptCandidateCleanupApplication.Result cleanup =
                RuntimeInterceptCandidateCleanupApplication.apply(state, "attacker", discovery);

        assertEquals(1, cleanup.attackerNoInterceptRemoved());
        assertEquals(
                List.of(7, 1),
                attacker.temporaryEffects().getAll("no_intercept").stream()
                        .map(entry -> ((Number) entry.payload().get("expires_round")).intValue())
                        .toList()
        );
    }

    @Test
    void sentinelExpiryTriggerRemovesFirstFamilyOccurrenceLikePython() {
        RuntimeCombatantState attacker = combatant("attacker", 1, 1);
        RuntimeCombatantState sentinel = combatant("sentinel", 2, 2);
        sentinel.temporaryEffects().add("sentinel_stance", Map.of("expires_round", 9));
        sentinel.temporaryEffects().add("sentinel_stance", Map.of("expires_round", 2));
        sentinel.temporaryEffects().add("sentinel_stance", Map.of("expires_round", 8));
        sentinel.temporaryEffects().add("sentinel_stance", Map.of("expires_round", 4));
        BattleRuntimeState state = battle(attacker, sentinel);
        state.syncCurrentRoundFromLifecycle(5);

        InterceptCandidateDiscoveryResolution.Result discovery = new InterceptCandidateDiscoveryResolution.Result(
                false,
                0,
                List.of(),
                Map.of("sentinel", 2)
        );

        RuntimeInterceptCandidateCleanupApplication.Result cleanup =
                RuntimeInterceptCandidateCleanupApplication.apply(state, "attacker", discovery);

        assertEquals(Map.of("sentinel", 2), cleanup.sentinelRemovedByCombatant());
        assertEquals(
                List.of(8, 4),
                sentinel.temporaryEffects().getAll("sentinel_stance").stream()
                        .map(entry -> ((Number) entry.payload().get("expires_round")).intValue())
                        .toList()
        );
    }

    @Test
    void removalCountCannotRemoveMoreOccurrencesThanExist() {
        RuntimeCombatantState attacker = combatant("attacker", 1, 1);
        attacker.temporaryEffects().add("no_intercept", Map.of("expires_round", 5));
        BattleRuntimeState state = battle(attacker);
        state.syncCurrentRoundFromLifecycle(5);

        InterceptCandidateDiscoveryResolution.Result discovery = new InterceptCandidateDiscoveryResolution.Result(
                false,
                3,
                List.of(),
                Map.of()
        );

        RuntimeInterceptCandidateCleanupApplication.Result cleanup =
                RuntimeInterceptCandidateCleanupApplication.apply(state, "attacker", discovery);

        assertEquals(1, cleanup.attackerNoInterceptRemoved());
        assertEquals(0, attacker.temporaryEffects().count("no_intercept"));
    }

    private static BattleRuntimeState battle(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(combatants)
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
