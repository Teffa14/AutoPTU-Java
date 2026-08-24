package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinDamageModifierHooks;
import io.autoptu.core.hook.BuiltinEffectiveMoveHooks;
import io.autoptu.core.hook.DamageModifierHookContext;
import io.autoptu.core.hook.DamageModifierHookResult;
import io.autoptu.core.hook.EffectiveMoveHookContext;
import io.autoptu.core.hook.EffectiveMoveHookResult;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.rules.EvasionResolution;
import io.autoptu.core.rules.PtuTables;
import io.autoptu.core.rules.StabResolution;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusEvasionResolution;
import io.autoptu.core.rules.StatusStatResolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable authoritative preparation for a concrete attacker/target pairing.
 *
 * <p>This is the state-derivation portion of direct move resolution: effective move metadata,
 * status-adjusted stats/evasion, STAB, type effectiveness and server-owned damage modifiers.
 * It deliberately does not spend actions, consume frequency, roll RNG or mutate HP.</p>
 */
final class RuntimeAuthoritativeMovePreparation {
    private RuntimeAuthoritativeMovePreparation() {
    }

    static Prepared prepare(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (legacyInput == null) throw new IllegalArgumentException("legacyInput is required");

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        MoveCombatProfile metadata = move.requireCombatProfile();

        EffectiveMoveHookResult effectiveMoveHooks = BuiltinEffectiveMoveHooks.standardRegistry().resolve(
                new EffectiveMoveHookContext(
                        state,
                        choice.actorId(),
                        choice.targetId(),
                        actor,
                        target,
                        move,
                        metadata,
                        metadata
                )
        );
        MoveCombatProfile effectiveMetadata = effectiveMoveHooks.profile();

        CombatantStatProfile actorStats = StatusStatResolution.apply(
                actor.effectiveStatProfile(), state.statuses(choice.actorId()));
        CombatantStatProfile targetStats = StatusStatResolution.apply(
                target.effectiveStatProfile(), state.statuses(choice.targetId()));
        EvasionProfile authoritativeEvasion = StatusEvasionResolution.apply(
                target.requireEvasionProfile(), state.statuses(choice.targetId()));
        int evasion = EvasionResolution.resolve(authoritativeEvasion, effectiveMetadata.damageCategory());
        int attackValue = StatResolution.offensive(
                actorStats, effectiveMetadata.damageCategory(), ignorePositiveAttackStage);
        int defenseValue = StatResolution.defensive(
                targetStats, effectiveMetadata.damageCategory(), ignorePositiveDefenseStage);
        boolean meleeNoGuard = isMelee(move) && (actor.noGuard() || target.noGuard());
        int effectiveDb = authoritativeStabDamageBase(move, effectiveMetadata, actor);
        double typeMultiplier = authoritativeTypeMultiplier(
                effectiveMetadata, target, legacyInput.typeMultiplier());
        MoveResolutionInput stateBoundInput = new MoveResolutionInput(
                effectiveMetadata.ac(),
                evasion,
                actor.accuracyStage(),
                effectiveMetadata.critRange(),
                meleeNoGuard,
                target.blur(),
                actor.probabilityControl(),
                effectiveDb,
                attackValue,
                defenseValue,
                actor.sniper(),
                typeMultiplier,
                List.of()
        );

        ArrayList<AttackModifier> resolvedModifiers = new ArrayList<>();
        for (AttackModifier modifier : actor.damageModifiers()) {
            if (modifier != null && !"burned".equalsIgnoreCase(modifier.slug())) {
                resolvedModifiers.add(modifier);
            }
        }
        DamageModifierHookResult damageHooks = BuiltinDamageModifierHooks.standardRegistry().resolve(
                new DamageModifierHookContext(
                        state,
                        choice.actorId(),
                        choice.targetId(),
                        actor,
                        target,
                        move,
                        effectiveMetadata
                )
        );
        resolvedModifiers.addAll(damageHooks.modifiers());
        stateBoundInput = withModifiers(stateBoundInput, resolvedModifiers);

        ArrayList<BattleEvent> events = new ArrayList<>();
        events.addAll(effectiveMoveHooks.events());
        events.addAll(damageHooks.events());
        return new Prepared(stateBoundInput, List.copyOf(events), effectiveMetadata);
    }

    private static MoveResolutionInput withModifiers(
            MoveResolutionInput input,
            List<AttackModifier> modifiers
    ) {
        return new MoveResolutionInput(
                input.moveAc(), input.evasion(), input.accuracyStage(), input.critRange(),
                input.meleeNoGuard(), input.blurApplies(), input.rerollOnMiss(), input.effectiveDb(),
                input.attackValue(), input.defenseValue(), input.sniper(), input.typeMultiplier(), modifiers
        );
    }

    private static int authoritativeStabDamageBase(
            MoveOption move,
            MoveCombatProfile metadata,
            RuntimeCombatantState actor
    ) {
        if (metadata.moveType() == null || actor.types().isEmpty()) return metadata.damageBase();
        return StabResolution.resolve(
                metadata.damageBase(), move.moveId(), metadata.moveType(), actor.types());
    }

    private static double authoritativeTypeMultiplier(
            MoveCombatProfile metadata,
            RuntimeCombatantState target,
            double legacyMultiplier
    ) {
        if (metadata.moveType() == null || target.types().isEmpty()) return legacyMultiplier;
        return PtuTables.typeMultiplier(metadata.moveType(), target.types());
    }

    private static boolean isMelee(MoveOption move) {
        String targetKind = move.spec().targetKind();
        return targetKind != null && targetKind.trim().equalsIgnoreCase("melee");
    }

    record Prepared(
            MoveResolutionInput input,
            List<BattleEvent> preResolutionEvents,
            MoveCombatProfile effectiveMetadata
    ) {
        Prepared {
            if (input == null) throw new IllegalArgumentException("input is required");
            preResolutionEvents = preResolutionEvents == null ? List.of() : List.copyOf(preResolutionEvents);
            if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        }
    }
}
