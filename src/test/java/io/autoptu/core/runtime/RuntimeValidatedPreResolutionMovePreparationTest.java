package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeValidatedPreResolutionMovePreparationTest {
    @Test
    void validatesDeclaredTargetOnceThenPreparesReplacementWithoutSecondDeclarationCheck() {
        BattleRuntimeState state = state();
        PreResolutionTargetHookRegistry registry = PreResolutionTargetHookRegistry.builder()
                .register("intercept", HookSource.REACTION, 10, (context, current) ->
                        current.replaceTarget("interceptor", List.of(new RuleEffectEvent(
                                "reaction", "Intercept", "attacker", "interceptor", "Tackle",
                                "target_redirect", 0.0, 10
                        ))))
                .build();
        MoveChoice declared = new MoveChoice(
                "attacker", "Tackle", ChoiceTargetMode.COMBATANT, "protected",
                new GridCoord(5, 1), ActionType.STANDARD
        );

        RuntimePreResolutionMovePreparation.Result result = RuntimeValidatedPreResolutionMovePreparation.prepare(
                state,
                declared,
                physicalMove(),
                "Medium",
                "Medium",
                Set.of(),
                legacyInput(),
                registry,
                false,
                false
        );

        assertEquals("interceptor", result.effectiveChoice().targetId());
        assertEquals(new GridCoord(8, 8), result.effectiveChoice().targetAnchor());
        assertEquals(30, result.input().defenseValue());
        assertEquals(6, result.input().evasion());
        assertEquals("Intercept", ((RuleEffectEvent) result.preResolutionEvents().getFirst()).sourceName());
        assertTrue(state.requireCombatant("attacker").actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(40, state.requireCombatant("protected").hp());
        assertEquals(40, state.requireCombatant("interceptor").hp());
    }

    @Test
    void rejectsStaleDeclarationBeforeAnyTargetHookRuns() {
        BattleRuntimeState state = state();
        state.requireCombatant("attacker").actionBudget().markAction(ActionType.STANDARD, "already spent");
        AtomicInteger hookCalls = new AtomicInteger();
        PreResolutionTargetHookRegistry registry = PreResolutionTargetHookRegistry.builder()
                .register("intercept", HookSource.REACTION, 10, (context, current) -> {
                    hookCalls.incrementAndGet();
                    return current.replaceTarget("interceptor", List.of());
                })
                .build();
        MoveChoice declared = new MoveChoice(
                "attacker", "Tackle", ChoiceTargetMode.COMBATANT, "protected",
                new GridCoord(5, 1), ActionType.STANDARD
        );

        assertThrows(IllegalArgumentException.class, () -> RuntimeValidatedPreResolutionMovePreparation.prepare(
                state,
                declared,
                physicalMove(),
                "Medium",
                "Medium",
                Set.of(),
                legacyInput(),
                registry,
                false,
                false
        ));
        assertEquals(0, hookCalls.get());
    }

    @Test
    void adaptersCannotInvokeValidatedPreparationBoundaryPublicly() throws Exception {
        Method method = RuntimeValidatedPreResolutionMovePreparation.class.getDeclaredMethod(
                "prepare",
                BattleRuntimeState.class,
                MoveChoice.class,
                MoveOption.class,
                String.class,
                String.class,
                Set.class,
                MoveResolutionInput.class,
                PreResolutionTargetHookRegistry.class,
                boolean.class,
                boolean.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState attacker = combatant("attacker", 1, 1, stats(20, 10));
        RuntimeCombatantState protectedTarget = combatant("protected", 5, 1, stats(10, 5));
        RuntimeCombatantState interceptor = combatant("interceptor", 8, 8, stats(10, 30));
        return new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(attacker, protectedTarget, interceptor),
                123L
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, CombatantStatProfile stats) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                40,
                40,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false)
        );
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, attack,
                        CombatStat.SPDEF, defense,
                        CombatStat.SPD, 10
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveOption physicalMove() {
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged");
        return MoveOption.standard("Tackle", spec, new MoveCombatProfile(2, 6, 20, "physical"));
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                19, 19, 0, 20, false, false, false,
                1, 1, 99, false, 1.0, List.of()
        );
    }
}
