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
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatantPhaseEffectDispatcherTest {
    @Test
    void dispatchesStatusAbilityPerkInOracleOrderAndLastPendingSkipWins() {
        List<CombatantPhaseEffectFamily> observed = new ArrayList<>();
        CombatantPhaseEffectDispatcher dispatcher = CombatantPhaseEffectDispatcher.builder()
                .family(CombatantPhaseEffectFamily.PERK, context -> {
                    observed.add(CombatantPhaseEffectFamily.PERK);
                    return LifecycleHookResult.pendingStatusSkip(
                            new PendingStatusSkipRequest("Flinch", context.phase(), "perk")
                    );
                })
                .family(CombatantPhaseEffectFamily.ABILITY, context -> {
                    observed.add(CombatantPhaseEffectFamily.ABILITY);
                    return LifecycleHookResult.pendingStatusSkip(
                            new PendingStatusSkipRequest("Confused", context.phase(), "ability")
                    );
                })
                .family(CombatantPhaseEffectFamily.STATUS, context -> {
                    observed.add(CombatantPhaseEffectFamily.STATUS);
                    return LifecycleHookResult.pendingStatusSkip(
                            new PendingStatusSkipRequest("Paralyzed", context.phase(), "status")
                    );
                })
                .build();

        LifecycleHookResult result = dispatcher.apply(context());

        assertEquals(List.of(
                CombatantPhaseEffectFamily.STATUS,
                CombatantPhaseEffectFamily.ABILITY,
                CombatantPhaseEffectFamily.PERK
        ), observed);
        assertEquals("Flinch", result.pendingStatusSkip().status());
        assertEquals("perk", result.pendingStatusSkip().reason());
    }

    @Test
    void rejectsDuplicateFamilyRegistration() {
        CombatantPhaseEffectDispatcher.Builder builder = CombatantPhaseEffectDispatcher.builder()
                .family(CombatantPhaseEffectFamily.STATUS, context -> LifecycleHookResult.empty());
        assertThrows(IllegalArgumentException.class, () ->
                builder.family(CombatantPhaseEffectFamily.STATUS, context -> LifecycleHookResult.empty())
        );
    }

    private static LifecycleHookContext context() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 1), 20, 20, new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor)
        );
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                new io.autoptu.core.runtime.RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                2,
                "actor",
                TurnPhase.COMMAND
        );
    }
}
