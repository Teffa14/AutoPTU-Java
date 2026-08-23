package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.rules.Targeting;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Locale;

/** Built-in pre-mutation Combat Stage blockers frozen from the pinned Python oracle. */
public final class BuiltinCombatStagePreventionHooks {
    private BuiltinCombatStagePreventionHooks() {}

    public static CombatStagePreventionHookRegistry registry() {
        return CombatStagePreventionHookRegistry.builder()
                .register("ability.flower-veil", HookSource.ABILITY, 10,
                        BuiltinCombatStagePreventionHooks::flowerVeilBlocksExternalDrop)
                .build();
    }

    private static CombatStagePreventionResult flowerVeilBlocksExternalDrop(CombatStagePreventionContext context) {
        if (context.requestedDelta() >= 0 || context.targetId().equals(context.attackerId())) {
            return CombatStagePreventionResult.allow();
        }
        RuntimeCombatantState target = context.target();
        boolean grass = target.types().stream().anyMatch(type -> "grass".equals(normalize(type)));
        if (!grass) return CombatStagePreventionResult.allow();

        String holderId = null;
        String abilityName = null;
        for (String candidateId : context.state().combatantIds()) {
            RuntimeCombatantState candidate = context.state().requireCombatant(candidateId);
            if (candidate.hp() <= 0 || !context.state().isActive(candidateId)) continue;
            if (!AbilityIdentityResolution.matchesRegistration(candidate.abilities(), "Flower Veil")) continue;

            boolean errata = AbilityIdentityResolution.matchesExact(candidate.abilities(), "Flower Veil [Errata]");
            int radius = errata ? 5 : 10;
            if (candidate.position() == null || target.position() == null) {
                holderId = candidateId;
                abilityName = errata ? "Flower Veil [Errata]" : "Flower Veil";
                break;
            }
            int distance = Targeting.footprintDistance(
                    candidate.position(), context.state().geometry(candidateId).sizeLabel(),
                    target.position(), "Medium"
            );
            if (distance <= radius) {
                holderId = candidateId;
                abilityName = errata ? "Flower Veil [Errata]" : "Flower Veil";
                break;
            }
        }
        if (holderId == null) return CombatStagePreventionResult.allow();

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                abilityName,
                holderId,
                context.targetId(),
                context.moveId(),
                "combat_stage_block",
                0,
                target.hp()
        );
        return CombatStagePreventionResult.block(List.of(event));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
