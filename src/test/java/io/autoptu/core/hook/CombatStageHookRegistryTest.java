package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
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

class CombatStageHookRegistryTest {
    @Test
    void resolvesByExplicitOrderAndPreservesRegistrationOrderForTies() {
        CombatStageHookRegistry registry = CombatStageHookRegistry.builder()
                .register("late-one", HookSource.ITEM, CombatStageHookPhase.POST_APPLY, 20,
                        context -> event("late-one"))
                .register("early", HookSource.STATUS, CombatStageHookPhase.POST_APPLY, 10,
                        context -> event("early"))
                .register("late-two", HookSource.ABILITY, CombatStageHookPhase.POST_APPLY, 20,
                        context -> event("late-two"))
                .build();

        List<String> effects = registry.apply(CombatStageHookPhase.POST_APPLY, context()).events().stream()
                .map(RuleEffectEvent.class::cast)
                .map(RuleEffectEvent::effect)
                .toList();

        assertEquals(List.of("early", "late-one", "late-two"), effects);
    }

    @Test
    void sourceCategoryIsMetadataNotOrdering() {
        CombatStageHookRegistry registry = CombatStageHookRegistry.builder()
                .register("feature", HookSource.TRAINER_FEATURE, CombatStageHookPhase.POST_APPLY, 5,
                        context -> CombatStageHookResult.empty())
                .register("ability", HookSource.ABILITY, CombatStageHookPhase.POST_APPLY, 5,
                        context -> CombatStageHookResult.empty())
                .build();

        assertEquals(HookSource.TRAINER_FEATURE, registry.registrations().get(0).source());
        assertEquals(HookSource.ABILITY, registry.registrations().get(1).source());
    }

    @Test
    void rejectsDuplicateRegistrationWithinSameSourceAndPhase() {
        CombatStageHookRegistry.Builder builder = CombatStageHookRegistry.builder()
                .register("same", HookSource.ABILITY, CombatStageHookPhase.POST_APPLY, 10,
                        context -> CombatStageHookResult.empty());

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("same", HookSource.ABILITY, CombatStageHookPhase.POST_APPLY, 20,
                        context -> CombatStageHookResult.empty()));
    }

    @Test
    void rejectsNullHookResults() {
        CombatStageHookRegistry registry = CombatStageHookRegistry.builder()
                .register("broken", HookSource.SYSTEM, CombatStageHookPhase.POST_APPLY, 1, context -> null)
                .build();

        assertThrows(IllegalStateException.class,
                () -> registry.apply(CombatStageHookPhase.POST_APPLY, context()));
    }

    private static CombatStageHookContext context() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget()
        );
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor, target)
        );
        return new CombatStageHookContext(
                state,
                "actor",
                "target",
                "test-move",
                CombatStat.ATK,
                1,
                1,
                "test"
        );
    }

    private static CombatStageHookResult event(String effect) {
        return CombatStageHookResult.events(List.of(
                new RuleEffectEvent("system", "test", "actor", "target", "test-move", effect, 0, 20)
        ));
    }
}
