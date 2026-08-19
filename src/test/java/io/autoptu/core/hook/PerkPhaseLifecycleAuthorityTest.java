package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundInjuryHistoryState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TrainerRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerkPhaseLifecycleAuthorityTest {
    @Test
    void preferredLifecycleHookReadsCanonicalTrainerFeatures() {
        BattleRuntimeState state = state();
        state.putTrainer(new TrainerRuntimeState("trainer", List.of("Defense Mastery"), 1));
        state.bindController("actor", "trainer");
        ArrayList<String> observed = new ArrayList<>();

        PerkPhaseEffectRegistry registry = PerkPhaseEffectRegistry.builder()
                .register("global", null, TurnPhase.END, 10, (context, perk) -> {
                    observed.add("global");
                    return LifecycleHookResult.empty();
                })
                .register("defense-mastery", "Defense Mastery", TurnPhase.END, 20, (context, perk) -> {
                    observed.add(perk);
                    return LifecycleHookResult.empty();
                })
                .register("attack-link", "Attack Link", TurnPhase.END, 30, (context, perk) -> {
                    observed.add(perk);
                    return LifecycleHookResult.empty();
                })
                .build();

        new PerkPhaseLifecycleHook(registry).apply(context(state));

        assertEquals(List.of("global", "Defense Mastery"), observed);
    }

    @Test
    void missingCanonicalTrainerFailsClosedForNamedPerksButKeepsGlobalHooks() {
        BattleRuntimeState state = state();
        ArrayList<String> observed = new ArrayList<>();
        PerkPhaseEffectRegistry registry = PerkPhaseEffectRegistry.builder()
                .register("global", null, TurnPhase.END, 10, (context, perk) -> {
                    observed.add("global");
                    return LifecycleHookResult.empty();
                })
                .register("forged", "Defense Mastery", TurnPhase.END, 20, (context, perk) -> {
                    observed.add(perk);
                    return LifecycleHookResult.empty();
                })
                .build();

        new PerkPhaseLifecycleHook(registry).apply(context(state));

        assertEquals(List.of("global"), observed);
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 1), 20, 20, new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor)
        );
    }

    private static LifecycleHookContext context(BattleRuntimeState state) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                2,
                "actor",
                TurnPhase.END
        );
    }
}
