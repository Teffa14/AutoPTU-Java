package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.hook.MoveSpecialSecondaryStatusResolution;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialSecondaryStatusApplicationTest {
    @Test
    void appliesParsedBurnThroughCanonicalStatusBoundary() {
        BattleRuntimeState state = state(List.of());
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve("Burns the target on 17+", 18);

        MoveSpecialSecondaryStatusApplication.Result result = MoveSpecialSecondaryStatusApplication.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", "Flamethrower", "flamethrower", requests
        );

        assertEquals(1, result.appliedCount());
        assertTrue(state.hasStatus("target", "burned"));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void immunityBlocksParsedPoisonWithoutSecondStatusPath() {
        BattleRuntimeState state = state(List.of("Immunity"));
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve("Poisons the target on 17+", 18);

        MoveSpecialSecondaryStatusApplication.Result result = MoveSpecialSecondaryStatusApplication.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", "Poison Jab", "poison-jab", requests
        );

        assertEquals(0, result.appliedCount());
        assertFalse(state.hasStatus("target", "poisoned"));
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Immunity", event.sourceName());
        assertEquals("status_block", event.effect());
    }

    @Test
    void safeguardBlocksParsedSleepAndPreservesProtection() {
        BattleRuntimeState state = state(List.of());
        StatusEntry safeguard = new StatusEntry("Safeguard", Map.of("remaining", 2));
        state.replaceStatusEntries("target", List.of(safeguard));
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve("The target falls asleep.", 1);

        MoveSpecialSecondaryStatusApplication.Result result = MoveSpecialSecondaryStatusApplication.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", "Sleep Powder", "sleep-powder", requests
        );

        assertEquals(0, result.appliedCount());
        assertFalse(state.hasStatus("target", "sleep"));
        assertEquals(safeguard, state.statusEntry("target", "safeguard").orElseThrow());
        assertEquals("safeguard_block", ((RuleEffectEvent) result.events().getFirst()).effect());
    }

    @Test
    void flinchCarriesRemainingAndAppliedRoundIntoCanonicalEntry() {
        BattleRuntimeState state = state(List.of());
        state.syncCurrentRoundFromLifecycle(6);
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve("Flinches the target on 15+", 16);

        MoveSpecialSecondaryStatusApplication.Result result = MoveSpecialSecondaryStatusApplication.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", "Air Slash", "air-slash", requests
        );

        assertEquals(1, result.appliedCount());
        StatusEntry flinch = state.statusEntry("target", "flinched").orElseThrow();
        assertEquals(1, flinch.intPayload("remaining").orElseThrow());
        assertEquals(6, flinch.intPayload("applied_round").orElseThrow());
    }

    @Test
    void innerFocusBlocksParsedFlinchBeforeCanonicalMutation() {
        BattleRuntimeState state = state(List.of("Inner Focus"));
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve("Flinches the target on 15+", 16);

        MoveSpecialSecondaryStatusApplication.Result result = MoveSpecialSecondaryStatusApplication.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", "Air Slash", "air-slash", requests
        );

        assertEquals(0, result.appliedCount());
        assertFalse(state.hasStatus("target", "flinched"));
        assertEquals("Inner Focus", ((RuleEffectEvent) result.events().getFirst()).sourceName());
    }

    private static BattleRuntimeState state(List<String> targetAbilities) {
        RuntimeCombatantState source = combatant("source", List.of());
        RuntimeCombatantState target = combatant("target", targetAbilities);
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(source, target)
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal"),
                List.of(),
                abilities
        );
    }
}
