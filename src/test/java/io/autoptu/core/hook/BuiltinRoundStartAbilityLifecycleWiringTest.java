package io.autoptu.core.hook;

import io.autoptu.core.runtime.CombatantRuleContentRegistry;
import io.autoptu.core.runtime.HeldItemRuleCatalog;
import io.autoptu.core.runtime.RoundStartAbilityLifecycleHook;
import io.autoptu.core.runtime.TrainerFeatureEffectRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BuiltinRoundStartAbilityLifecycleWiringTest {
    @Test
    void roundStartAbilitiesRunAfterTrainerFeaturesWithCanonicalContentInjection() {
        CombatantRuleContentRegistry ruleContent = new CombatantRuleContentRegistry(Map.of());
        LifecycleHookRegistry registry = BuiltinLifecycleHooks.registry(
                new HeldItemRuleCatalog(Map.of()),
                List.of(),
                new TrainerFeatureEffectRegistry(),
                ruleContent
        );

        List<LifecycleHookRegistry.Registration> effects = registry.registrations().stream()
                .filter(entry -> entry.point() == LifecycleHookPoint.ROUND_START_EFFECTS)
                .toList();

        assertEquals(List.of("round-trainer-feature-dispatch", "round-ability-dispatch"),
                effects.stream().map(LifecycleHookRegistry.Registration::id).toList());
        assertEquals(HookSource.TRAINER_FEATURE, effects.get(0).source());
        assertEquals(HookSource.ABILITY, effects.get(1).source());
        assertEquals(100, effects.get(0).priority());
        assertEquals(110, effects.get(1).priority());
        assertInstanceOf(RoundStartAbilityLifecycleHook.class, effects.get(1).hook());
    }
}
