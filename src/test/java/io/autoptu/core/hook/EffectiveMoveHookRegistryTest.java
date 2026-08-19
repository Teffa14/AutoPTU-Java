package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
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

class EffectiveMoveHookRegistryTest {
    @Test
    void composesProfilesAndEventsInExplicitOrder() {
        EffectiveMoveHookRegistry registry = EffectiveMoveHookRegistry.builder()
                .register("later", HookSource.ITEM, 20, context -> new EffectiveMoveHookResult(
                        withDb(context.effectiveProfile(), context.effectiveProfile().damageBase() + 3),
                        List.of(event("later"))))
                .register("earlier", HookSource.ABILITY, 10, context -> new EffectiveMoveHookResult(
                        withDb(context.effectiveProfile(), context.effectiveProfile().damageBase() + 2),
                        List.of(event("earlier"))))
                .build();

        EffectiveMoveHookResult result = registry.resolve(context());

        assertEquals(11, result.profile().damageBase());
        assertEquals(List.of("earlier", "later"), result.events().stream()
                .map(RuleEffectEvent.class::cast)
                .map(RuleEffectEvent::effect)
                .toList());
    }

    @Test
    void rejectsDuplicateRegistrationWithinSource() {
        EffectiveMoveHookRegistry.Builder builder = EffectiveMoveHookRegistry.builder()
                .register("same", HookSource.ABILITY, 10, context -> EffectiveMoveHookResult.unchanged(context.effectiveProfile()));
        assertThrows(IllegalArgumentException.class, () -> builder.register(
                "same", HookSource.ABILITY, 20,
                context -> EffectiveMoveHookResult.unchanged(context.effectiveProfile())));
    }

    private static EffectiveMoveHookContext context() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 1));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor, target));
        MoveOption move = MoveOption.standard(
                "water-pulse",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, 6, 20, "special", "Water"));
        MoveCombatProfile profile = move.requireCombatProfile();
        return new EffectiveMoveHookContext(state, "actor", "target", actor, target, move, profile, profile);
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 3), 20, 20, new ActionBudget());
    }

    private static MoveCombatProfile withDb(MoveCombatProfile profile, int db) {
        return new MoveCombatProfile(profile.ac(), db, profile.critRange(), profile.damageCategory(), profile.moveType());
    }

    private static RuleEffectEvent event(String effect) {
        return new RuleEffectEvent("system", "test", "actor", "target", "water-pulse", effect, 0, 20);
    }
}
