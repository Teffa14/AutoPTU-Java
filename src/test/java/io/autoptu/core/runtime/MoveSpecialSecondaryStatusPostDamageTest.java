package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
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

class MoveSpecialSecondaryStatusPostDamageTest {
    @Test
    void derivesEffectRollFromAuthoritativeStateBeforeApplyingStatus() {
        BattleRuntimeState state = state(List.of("Serene Grace"), List.of());
        MoveOption move = move("flamethrower", "Fire", "Burns the target on 17+");

        MoveSpecialSecondaryStatusPostDamage.Result result =
                MoveSpecialSecondaryStatusPostDamage.resolveAndApply(
                        state,
                        BuiltinStatusApplicationHooks.registry(),
                        "source",
                        "target",
                        move,
                        Map.of("roll", 15)
                );

        assertEquals(17, result.effectRoll());
        assertEquals(1, result.appliedCount());
        assertTrue(state.hasStatus("target", "burned"));
    }

    @Test
    void canonicalStatusPreventionStillOwnsFinalMutation() {
        BattleRuntimeState state = state(List.of(), List.of("Immunity"));
        MoveOption move = move("poison-jab", "Poison", "Poisons the target on 17+");

        MoveSpecialSecondaryStatusPostDamage.Result result =
                MoveSpecialSecondaryStatusPostDamage.resolveAndApply(
                        state,
                        BuiltinStatusApplicationHooks.registry(),
                        "source",
                        "target",
                        move,
                        Map.of("roll", 18)
                );

        assertEquals(18, result.effectRoll());
        assertEquals(0, result.appliedCount());
        assertFalse(state.hasStatus("target", "poisoned"));
        assertEquals("Immunity", ((RuleEffectEvent) result.events().getFirst()).sourceName());
    }

    @Test
    void failsClosedWhenSharedResultDoesNotCarryAccuracyRoll() {
        BattleRuntimeState state = state(List.of(), List.of());
        MoveOption move = move("flamethrower", "Fire", "Burns the target on 17+");

        assertThrows(
                IllegalStateException.class,
                () -> MoveSpecialSecondaryStatusPostDamage.resolveAndApply(
                        state,
                        BuiltinStatusApplicationHooks.registry(),
                        "source",
                        "target",
                        move,
                        Map.of("hit", true)
                )
        );
    }

    private static MoveOption move(String moveId, String type, String effectsText) {
        MoveSpec spec = new MoveSpec(
                "Ranged",
                "Ranged",
                6,
                6,
                null,
                null,
                "6",
                List.of(),
                effectsText
        );
        return new MoveOption(
                moveId,
                spec,
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 6, 20, "special", type)
        );
    }

    private static BattleRuntimeState state(List<String> sourceAbilities, List<String> targetAbilities) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(
                        combatant("source", sourceAbilities),
                        combatant("target", targetAbilities)
                )
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
