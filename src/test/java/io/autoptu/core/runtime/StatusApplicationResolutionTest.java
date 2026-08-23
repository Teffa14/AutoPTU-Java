package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookResult;
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

class StatusApplicationResolutionTest {
    @Test
    void innerFocusBlocksFlinchBeforeCanonicalMutation() {
        BattleRuntimeState state = state(List.of("Inner Focus"));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Flinched", Map.of("applied_round", 4)),
                "move", "Fake Out", "fake-out"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "flinched"));
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Inner Focus", event.sourceName());
        assertEquals("status_block", event.effect());
        assertEquals("target", event.actorId());
    }

    @Test
    void immunityBlocksPoisonBeforeCanonicalMutation() {
        BattleRuntimeState state = state(List.of("Immunity"));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Badly Poisoned"), "move", "Toxic", "toxic"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "badly poisoned"));
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Immunity", event.sourceName());
        assertEquals("status_block", event.effect());
    }

    @Test
    void insomniaAndVitalSpiritBlockSleep() {
        for (String ability : List.of("Insomnia", "Vital Spirit")) {
            BattleRuntimeState state = state(List.of(ability));
            StatusApplicationResult result = StatusApplicationResolution.apply(
                    state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                    new StatusEntry("Asleep"), "move", "Hypnosis", "hypnosis"
            );

            assertFalse(result.applied(), ability);
            assertFalse(state.hasStatus("target", "asleep"), ability);
            RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
            assertEquals(ability, event.sourceName());
        }
    }

    @Test
    void canonicalSuppressionDisablesAbilityPrevention() {
        BattleRuntimeState state = state(List.of("Immunity"));
        RuntimeCombatantState target = state.requireCombatant("target");
        assertFalse(target.abilitiesSuppressed());
        target.setAbilitiesSuppressedFromRuntime(true);

        StatusEntry poisoned = new StatusEntry("Poisoned");
        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", poisoned,
                "move", "Poison Powder", "poison-powder"
        );

        assertTrue(target.abilitiesSuppressed());
        assertTrue(result.applied());
        assertEquals(poisoned, state.statusEntry("target", "poisoned").orElseThrow());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void safeguardBlocksWithoutConsumingCanonicalStatus() {
        BattleRuntimeState state = state(List.of());
        StatusEntry safeguard = new StatusEntry("Safeguard", Map.of("remaining", 2));
        state.replaceStatusEntries("target", List.of(safeguard));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Burned"), "move", "Will-O-Wisp", "will-o-wisp"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "burned"));
        assertEquals(safeguard, state.statusEntry("target", "safeguard").orElseThrow());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("status", event.sourceKind());
        assertEquals("Safeguard", event.sourceName());
        assertEquals("safeguard_block", event.effect());
        assertEquals("target", event.actorId());
    }

    @Test
    void safeguardUsesPythonIntLikeRemainingAndOnlyPositiveValuesBlock() {
        BattleRuntimeState active = state(List.of());
        active.replaceStatusEntries("target", List.of(new StatusEntry("Safeguard", Map.of("remaining", "2"))));
        StatusApplicationResult blocked = StatusApplicationResolution.apply(
                active, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Burned"), "move", "Will-O-Wisp", "will-o-wisp"
        );
        assertFalse(blocked.applied());

        BattleRuntimeState spent = state(List.of());
        spent.replaceStatusEntries("target", List.of(new StatusEntry("Safeguard", Map.of("remaining", 0))));
        StatusApplicationResult applied = StatusApplicationResolution.apply(
                spent, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Burned"), "move", "Will-O-Wisp", "will-o-wisp"
        );
        assertTrue(applied.applied());
        assertTrue(spent.hasStatus("target", "burned"));
    }

    @Test
    void infiltratorBypassesSafeguardEvenWhenSourceAbilitiesAreSuppressed() {
        BattleRuntimeState state = state(List.of("Infiltrator"), List.of());
        state.replaceStatusEntries("target", List.of(new StatusEntry("Safeguard", Map.of("remaining", 1))));
        state.requireCombatant("source").setAbilitiesSuppressedFromRuntime(true);

        StatusEntry burned = new StatusEntry("Burned");
        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", burned,
                "move", "Will-O-Wisp", "will-o-wisp"
        );

        assertTrue(result.applied());
        assertEquals(burned, state.statusEntry("target", "burned").orElseThrow());
        assertTrue(result.events().isEmpty());
        assertEquals(1, state.statusEntry("target", "safeguard").orElseThrow().intPayload("remaining").orElseThrow());
    }

    @Test
    void targetAbilityPreventionRunsBeforeSafeguard() {
        BattleRuntimeState state = state(List.of("Immunity"));
        state.replaceStatusEntries("target", List.of(new StatusEntry("Safeguard", Map.of("remaining", 1))));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Poisoned"), "move", "Poison Powder", "poison-powder"
        );

        assertFalse(result.applied());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Immunity", event.sourceName());
        assertEquals(new StatusEntry("Safeguard", Map.of("remaining", 1)),
                state.statusEntry("target", "safeguard").orElseThrow());
    }

    @Test
    void unrelatedStatusStillAppliesWithInnerFocus() {
        BattleRuntimeState state = state(List.of("Inner Focus"));
        StatusEntry burned = new StatusEntry("Burned", Map.of("source", "ember"));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", burned,
                "move", "Ember", "ember"
        );

        assertTrue(result.applied());
        assertEquals(burned, state.statusEntry("target", "burned").orElseThrow());
    }

    @Test
    void registryIsOrderedAndStopsAfterFirstBlock() {
        BattleRuntimeState state = state(List.of());
        StatusApplicationHookRegistry hooks = StatusApplicationHookRegistry.builder()
                .register("first", HookSource.SYSTEM, 10, context -> StatusApplicationHookResult.allow())
                .register("block", HookSource.SYSTEM, 20, context -> StatusApplicationHookResult.block(List.of()))
                .register("must-not-run", HookSource.SYSTEM, 30, context -> {
                    throw new AssertionError("hook after block executed");
                })
                .build();

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, hooks, "source", "target", new StatusEntry("Burned"), "move", "Ember", "ember"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "burned"));
    }

    @Test
    void unknownTargetFailsBeforeHooksOrMutation() {
        BattleRuntimeState state = state(List.of());
        assertThrows(IllegalArgumentException.class, () -> StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "intruder",
                new StatusEntry("Burned"), "move", "Ember", "ember"
        ));
    }

    private static BattleRuntimeState state(List<String> targetAbilities) {
        return state(List.of(), targetAbilities);
    }

    private static BattleRuntimeState state(List<String> sourceAbilities, List<String> targetAbilities) {
        RuntimeCombatantState source = combatant("source", sourceAbilities);
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
