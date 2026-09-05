package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalAbilityPhaseEffectHookTest {
    @Test
    void traversesAbilityOwnersInStableBattleOrderWithoutOuterActor() {
        List<String> observed = new ArrayList<>();
        AbilityPhaseEffectRegistry registry = AbilityPhaseEffectRegistry.builder()
                .register("ability.global.end", "Global Test", TurnPhase.END, 100, (context, ability) -> {
                    observed.add(context.actorId());
                    return LifecycleHookResult.empty();
                })
                .build();
        GlobalAbilityPhaseEffectHook hook = new GlobalAbilityPhaseEffectHook(registry);

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(
                        combatant("first", List.of("Global Test")),
                        combatant("ignored", List.of()),
                        combatant("third", List.of("Global Test"))
                )
        );
        LifecycleHookContext globalContext = new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                2,
                "",
                TurnPhase.END
        );

        hook.apply(globalContext);

        assertEquals(List.of("first", "third"), observed);
    }

    @Test
    void preservesRegistryPhaseFilteringAcrossGlobalTraversal() {
        List<String> observed = new ArrayList<>();
        AbilityPhaseEffectRegistry registry = AbilityPhaseEffectRegistry.builder()
                .register("ability.global.end", "Global Test", TurnPhase.END, 100, (context, ability) -> {
                    observed.add(context.actorId());
                    return LifecycleHookResult.empty();
                })
                .build();
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatant("actor", List.of("Global Test")))
        );
        LifecycleHookContext commandContext = new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                2,
                "",
                TurnPhase.COMMAND
        );

        new GlobalAbilityPhaseEffectHook(registry).apply(commandContext);

        assertEquals(List.of(), observed);
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 1),
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
                List.of(),
                List.of(),
                abilities
        );
    }
}
