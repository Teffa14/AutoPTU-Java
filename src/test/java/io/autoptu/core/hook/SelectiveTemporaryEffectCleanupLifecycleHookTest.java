package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundDamageHistoryState;
import io.autoptu.core.runtime.RoundInjuryHistoryState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectiveTemporaryEffectCleanupLifecycleHookTest {
    @Test
    void actorCleanupRemovesOnlyMetadataMatchesInsideFamily() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography", "terrain", "forest"));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "Other Feature", "terrain", "cave"));
        actor.temporaryEffects().add("persistent_fixture", Map.of("feature", "Adaptive Geography"));

        hook(SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR).apply(context(state(actor), "actor"));

        assertEquals(1, actor.temporaryEffects().getAll("terrain_alias").size());
        assertEquals("Other Feature", actor.temporaryEffects().getAll("terrain_alias").getFirst().payload().get("feature"));
        assertEquals(1, actor.temporaryEffects().getAll("persistent_fixture").size());
    }

    @Test
    void stringMetadataMatchingMirrorsPythonTrimAndCaseFold() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "  ADAPTIVE geography  ", "terrain", "forest"));

        hook(SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR).apply(context(state(actor), "actor"));

        assertEquals(0, actor.temporaryEffects().count("terrain_alias"));
    }

    @Test
    void actorScopeDoesNotTouchOtherCombatants() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography"));
        other.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography"));

        hook(SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR).apply(context(state(actor, other), "actor"));

        assertEquals(0, actor.temporaryEffects().count("terrain_alias"));
        assertEquals(1, other.temporaryEffects().count("terrain_alias"));
    }

    @Test
    void allCombatantsScopeTraversesEntireRoster() {
        RuntimeCombatantState first = combatant("first", new GridCoord(0, 0));
        RuntimeCombatantState second = combatant("second", new GridCoord(1, 0));
        first.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography"));
        second.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography"));

        hook(SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ALL_COMBATANTS)
                .apply(context(state(first, second), ""));

        assertEquals(0, first.temporaryEffects().count("terrain_alias"));
        assertEquals(0, second.temporaryEffects().count("terrain_alias"));
    }

    @Test
    void actorScopeRequiresActorIdentity() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> hook(SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR)
                        .apply(context(state(actor), ""))
        );
    }

    private static SelectiveTemporaryEffectCleanupLifecycleHook hook(
            SelectiveTemporaryEffectCleanupLifecycleHook.Scope scope
    ) {
        return new SelectiveTemporaryEffectCleanupLifecycleHook(
                scope,
                List.of(new SelectiveTemporaryEffectCleanupLifecycleHook.Selector(
                        "terrain_alias",
                        Map.of("feature", "Adaptive Geography")
                ))
        );
    }

    private static LifecycleHookContext context(BattleRuntimeState state, String actorId) {
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.TURN_END,
                2,
                2,
                actorId,
                TurnPhase.END
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatants),
                Map.of()
        );
    }
}
