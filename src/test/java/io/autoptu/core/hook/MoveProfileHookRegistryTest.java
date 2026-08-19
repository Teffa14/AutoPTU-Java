package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoveProfileHookRegistryTest {
    @Test
    void appliesHooksInStableDeclaredOrder() {
        MoveProfileHookRegistry registry = MoveProfileHookRegistry.builder()
                .register("later", HookSource.ITEM, 200, context -> MoveProfileHookResult.unchanged(withDb(context.profile(), context.profile().damageBase() * 2)))
                .register("first", HookSource.ABILITY, 100, context -> MoveProfileHookResult.unchanged(withDb(context.profile(), context.profile().damageBase() + 3)))
                .register("same-order", HookSource.STATUS, 100, context -> MoveProfileHookResult.unchanged(withDb(context.profile(), context.profile().damageBase() + 1)))
                .build();

        MoveProfileHookResult result = registry.resolve(context(4));

        assertEquals(16, result.profile().damageBase());
        assertEquals(List.of("ability:first", "status:same-order", "item:later"),
                registry.registrations().stream().map(MoveProfileHookRegistry.Registration::key).toList());
    }

    @Test
    void duplicateSourceAndIdFailsClosed() {
        MoveProfileHookRegistry.Builder builder = MoveProfileHookRegistry.builder()
                .register("same", HookSource.ABILITY, 10, context -> MoveProfileHookResult.unchanged(context.profile()));

        assertThrows(IllegalArgumentException.class, () -> builder.register(
                "same", HookSource.ABILITY, 20, context -> MoveProfileHookResult.unchanged(context.profile())));
    }

    @Test
    void nullHookResultFailsClosed() {
        MoveProfileHookRegistry registry = MoveProfileHookRegistry.builder()
                .register("bad", HookSource.SYSTEM, 10, context -> null)
                .build();

        assertThrows(IllegalStateException.class, () -> registry.resolve(context(4)));
    }

    private static MoveCombatProfile withDb(MoveCombatProfile profile, int db) {
        return new MoveCombatProfile(profile.ac(), db, profile.critRange(), profile.damageCategory(), profile.moveType());
    }

    private static MoveProfileHookContext context(int db) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 1));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor, target));
        MoveOption move = MoveOption.standard(
                "test-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, db, 20, "special", "Water"));
        return new MoveProfileHookContext(state, "actor", "target", actor, target, move, move.requireCombatProfile());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 3), 20, 20, new ActionBudget());
    }
}
