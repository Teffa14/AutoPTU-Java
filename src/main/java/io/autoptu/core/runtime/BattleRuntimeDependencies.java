package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinDamageModifierHooks;
import io.autoptu.core.hook.BuiltinEffectiveMoveHooks;
import io.autoptu.core.hook.BuiltinPostDamageHooks;
import io.autoptu.core.hook.BuiltinPreDamageReactionHooks;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.hook.DamageModifierHookRegistry;
import io.autoptu.core.hook.EffectiveMoveHookRegistry;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookRegistry;

/**
 * Immutable composition snapshot for authoritative runtime rule dependencies.
 *
 * <p>This object carries canonical PTU rule data and reusable hook registries into runtime
 * orchestration without storing adapter-owned state in {@link BattleRuntimeState} or growing
 * method signatures for each rule family. Rule conclusions remain in their dedicated resolvers;
 * this boundary only composes the authoritative PTU implementation used by every producer.</p>
 */
public record BattleRuntimeDependencies(
        CombatantRuleContentRegistry combatantRuleContent,
        StatusApplicationHookRegistry statusApplicationHooks,
        MovementLandingHookRegistry movementLandingHooks,
        EffectiveMoveHookRegistry effectiveMoveHooks,
        DamageModifierHookRegistry damageModifierHooks,
        PreDamageReactionHookRegistry preDamageReactionHooks,
        PostDamageHookRegistry postDamageHooks,
        MoveSpecialHookRegistryFactory moveSpecialHookRegistryFactory
) {
    public BattleRuntimeDependencies {
        if (combatantRuleContent == null) throw new IllegalArgumentException("combatant rule content registry is required");
        if (statusApplicationHooks == null) throw new IllegalArgumentException("status application hook registry is required");
        if (movementLandingHooks == null) throw new IllegalArgumentException("movement landing hook registry is required");
        if (effectiveMoveHooks == null) throw new IllegalArgumentException("effective move hook registry is required");
        if (damageModifierHooks == null) throw new IllegalArgumentException("damage modifier hook registry is required");
        if (preDamageReactionHooks == null) throw new IllegalArgumentException("pre-damage reaction hook registry is required");
        if (postDamageHooks == null) throw new IllegalArgumentException("post-damage hook registry is required");
        if (moveSpecialHookRegistryFactory == null) throw new IllegalArgumentException("move-special hook registry factory is required");
    }

    /** Compatibility constructor for callers that compose move/damage registries explicitly. */
    public BattleRuntimeDependencies(
            CombatantRuleContentRegistry combatantRuleContent,
            StatusApplicationHookRegistry statusApplicationHooks,
            MovementLandingHookRegistry movementLandingHooks,
            EffectiveMoveHookRegistry effectiveMoveHooks,
            DamageModifierHookRegistry damageModifierHooks,
            PreDamageReactionHookRegistry preDamageReactionHooks,
            PostDamageHookRegistry postDamageHooks
    ) {
        this(
                combatantRuleContent,
                statusApplicationHooks,
                movementLandingHooks,
                effectiveMoveHooks,
                damageModifierHooks,
                preDamageReactionHooks,
                postDamageHooks,
                MoveSpecialHookRegistryFactory.standard()
        );
    }

    /** Compatibility constructor for landing/status composition tests and callers. */
    public BattleRuntimeDependencies(
            CombatantRuleContentRegistry combatantRuleContent,
            StatusApplicationHookRegistry statusApplicationHooks,
            MovementLandingHookRegistry movementLandingHooks
    ) {
        this(
                combatantRuleContent,
                statusApplicationHooks,
                movementLandingHooks,
                BuiltinEffectiveMoveHooks.standardRegistry(),
                BuiltinDamageModifierHooks.standardRegistry(),
                BuiltinPreDamageReactionHooks.registry(),
                BuiltinPostDamageHooks.standardRegistry(),
                MoveSpecialHookRegistryFactory.standard()
        );
    }

    /** Compatibility constructor for callers that supply canonical content and status hooks. */
    public BattleRuntimeDependencies(
            CombatantRuleContentRegistry combatantRuleContent,
            StatusApplicationHookRegistry statusApplicationHooks
    ) {
        this(combatantRuleContent, statusApplicationHooks, MovementLandingHookRegistry.standard());
    }

    /** Compatibility constructor for callers that only supply canonical combatant content. */
    public BattleRuntimeDependencies(CombatantRuleContentRegistry combatantRuleContent) {
        this(combatantRuleContent, BuiltinStatusApplicationHooks.registry(), MovementLandingHookRegistry.standard());
    }

    public static BattleRuntimeDependencies empty() {
        return new BattleRuntimeDependencies(
                CombatantRuleContentRegistry.empty(),
                BuiltinStatusApplicationHooks.registry(),
                MovementLandingHookRegistry.standard()
        );
    }
}
