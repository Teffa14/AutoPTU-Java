package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.AttackModifier;
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

class DamageModifierHookRegistryTest {
    @Test
    void resolvesByExplicitOrderAndPreservesRegistrationOrderForTies() {
        DamageModifierHookRegistry registry = DamageModifierHookRegistry.builder()
                .register("item-second", HookSource.ITEM, 20,
                        context -> DamageModifierHookResult.modifiersOnly(List.of(AttackModifier.flat("item-second", 2))))
                .register("status-first", HookSource.STATUS, 10,
                        context -> DamageModifierHookResult.modifiersOnly(List.of(AttackModifier.flat("status-first", 1))))
                .register("ability-third", HookSource.ABILITY, 20,
                        context -> DamageModifierHookResult.modifiersOnly(List.of(AttackModifier.flat("ability-third", 3))))
                .build();

        List<String> slugs = registry.resolve(context()).modifiers().stream()
                .map(AttackModifier::slug)
                .toList();

        assertEquals(List.of("status-first", "item-second", "ability-third"), slugs);
    }

    @Test
    void semanticEventsFollowTheSameHookOrder() {
        DamageModifierHookRegistry registry = DamageModifierHookRegistry.builder()
                .register("late", HookSource.ITEM, 20,
                        context -> DamageModifierHookResult.of(
                                List.of(),
                                List.of(event("late"))
                        ))
                .register("early", HookSource.STATUS, 10,
                        context -> DamageModifierHookResult.of(
                                List.of(),
                                List.of(event("early"))
                        ))
                .build();

        List<String> effects = registry.resolve(context()).events().stream()
                .map(event -> ((RuleEffectEvent) event).effect())
                .toList();

        assertEquals(List.of("early", "late"), effects);
    }

    @Test
    void sourceCategoryIsMetadataNotAnOrderingRule() {
        DamageModifierHookRegistry registry = DamageModifierHookRegistry.builder()
                .register("feature", HookSource.TRAINER_FEATURE, 5, context -> DamageModifierHookResult.empty())
                .register("ability", HookSource.ABILITY, 5, context -> DamageModifierHookResult.empty())
                .build();

        assertEquals(HookSource.TRAINER_FEATURE, registry.registrations().get(0).source());
        assertEquals(HookSource.ABILITY, registry.registrations().get(1).source());
    }

    @Test
    void rejectsDuplicateRegistrationWithinSameSource() {
        DamageModifierHookRegistry.Builder builder = DamageModifierHookRegistry.builder()
                .register("same", HookSource.ABILITY, 10, context -> DamageModifierHookResult.empty());

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("same", HookSource.ABILITY, 20, context -> DamageModifierHookResult.empty()));
    }

    @Test
    void contextReadsCanonicalStatusesFromBattleState() {
        assertEquals(Set.of("burned"), context().actorStatuses());
    }

    @Test
    void rejectsNullHookResultsInsteadOfSilentlyDroppingRules() {
        DamageModifierHookRegistry registry = DamageModifierHookRegistry.builder()
                .register("broken", HookSource.SYSTEM, 1, context -> null)
                .build();

        assertThrows(IllegalStateException.class, () -> registry.resolve(context()));
    }

    private static DamageModifierHookContext context() {
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
                List.of(actor, target),
                Map.of("actor", Set.of("Burned"))
        );
        MoveOption move = MoveOption.standard(
                "tackle",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "physical", "Normal")
        );
        return new DamageModifierHookContext(
                state,
                "actor",
                "target",
                actor,
                target,
                move,
                move.requireCombatProfile()
        );
    }

    private static RuleEffectEvent event(String effect) {
        return new RuleEffectEvent("system", "test", "actor", "target", "tackle", effect, 0, 20);
    }
}
