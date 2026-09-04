package io.autoptu.core.runtime;

import io.autoptu.core.hook.DamageModifierHookRegistry;
import io.autoptu.core.hook.EffectiveMoveHookRegistry;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

final class BattleRuntimeDependenciesTest {
    @Test
    void preservesInjectedMoveAndDamageHookRegistries() {
        CombatantRuleContentRegistry content = CombatantRuleContentRegistry.empty();
        StatusApplicationHookRegistry status = StatusApplicationHookRegistry.builder().build();
        MovementLandingHookRegistry landing = new MovementLandingHookRegistry();
        EffectiveMoveHookRegistry effectiveMove = EffectiveMoveHookRegistry.builder().build();
        DamageModifierHookRegistry damageModifier = DamageModifierHookRegistry.builder().build();
        PreDamageReactionHookRegistry preDamage = PreDamageReactionHookRegistry.builder().build();
        PostDamageHookRegistry postDamage = PostDamageHookRegistry.builder().build();

        BattleRuntimeDependencies dependencies = new BattleRuntimeDependencies(
                content,
                status,
                landing,
                effectiveMove,
                damageModifier,
                preDamage,
                postDamage
        );

        assertSame(content, dependencies.combatantRuleContent());
        assertSame(status, dependencies.statusApplicationHooks());
        assertSame(landing, dependencies.movementLandingHooks());
        assertSame(effectiveMove, dependencies.effectiveMoveHooks());
        assertSame(damageModifier, dependencies.damageModifierHooks());
        assertSame(preDamage, dependencies.preDamageReactionHooks());
        assertSame(postDamage, dependencies.postDamageHooks());
    }
}
