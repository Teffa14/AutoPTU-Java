package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.rules.StatusAbilityPreventionResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.StatusEntry;

import java.util.List;
import java.util.Optional;

/** Built-in ability/status gates for canonical status application. */
public final class BuiltinStatusApplicationHooks {
    private BuiltinStatusApplicationHooks() {
    }

    public static StatusApplicationHookRegistry registry() {
        return StatusApplicationHookRegistry.builder()
                .register(
                        "target-ability-status-prevention",
                        HookSource.ABILITY,
                        100,
                        BuiltinStatusApplicationHooks::targetAbilityPrevention
                )
                .register(
                        "spatial-ability-status-prevention",
                        HookSource.ABILITY,
                        150,
                        BuiltinStatusApplicationHooks::spatialAbilityPrevention
                )
                .register(
                        "safeguard-status-prevention",
                        HookSource.STATUS,
                        200,
                        BuiltinStatusApplicationHooks::safeguardPrevention
                )
                .build();
    }

    private static StatusApplicationHookResult targetAbilityPrevention(StatusApplicationContext context) {
        RuntimeCombatantState target = context.state().requireCombatant(context.targetId());
        Optional<String> blockingAbility = StatusAbilityPreventionResolution.blockingAbility(
                target.abilities(),
                context.status().name(),
                target.abilitiesSuppressed()
        );
        if (blockingAbility.isEmpty()) {
            return StatusApplicationHookResult.allow();
        }

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                blockingAbility.orElseThrow(),
                context.targetId(),
                context.targetId(),
                context.moveId(),
                "status_block",
                0.0,
                target.hp()
        );
        return StatusApplicationHookResult.block(List.of(event));
    }

    private static StatusApplicationHookResult spatialAbilityPrevention(StatusApplicationContext context) {
        Optional<SpatialStatusAbilityPreventionResolution.Blocker> blocker =
                SpatialStatusAbilityPreventionResolution.findBlocker(
                        context.state(),
                        context.targetId(),
                        context.status().name()
                );
        if (blocker.isEmpty()) return StatusApplicationHookResult.allow();

        SpatialStatusAbilityPreventionResolution.Blocker resolved = blocker.orElseThrow();
        RuntimeCombatantState target = context.state().requireCombatant(context.targetId());
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                resolved.abilityName(),
                resolved.combatantId(),
                context.targetId(),
                context.moveId(),
                "status_block",
                0.0,
                target.hp()
        );
        return StatusApplicationHookResult.block(List.of(event));
    }

    /**
     * Python status-application parity for Safeguard.
     *
     * The pinned oracle scans the target's ordered status list for the first Safeguard
     * whose int(remaining) is positive. Infiltrator on the source bypasses the block.
     * This boundary does not consume or remove Safeguard, so the canonical status entry
     * remains unchanged when the attempted status is rejected.
     */
    private static StatusApplicationHookResult safeguardPrevention(StatusApplicationContext context) {
        Optional<StatusEntry> safeguard = context.state().statusEntries(context.targetId()).stream()
                .filter(entry -> entry.name().equals("safeguard"))
                .filter(BuiltinStatusApplicationHooks::hasPositiveRemaining)
                .findFirst();
        if (safeguard.isEmpty()) {
            return StatusApplicationHookResult.allow();
        }

        if (!context.sourceActorId().isBlank()) {
            RuntimeCombatantState source = context.state().requireCombatant(context.sourceActorId());
            if (AbilityIdentityResolution.matchesRegistration(source.abilities(), "Infiltrator")) {
                return StatusApplicationHookResult.allow();
            }
        }

        RuntimeCombatantState target = context.state().requireCombatant(context.targetId());
        RuleEffectEvent event = new RuleEffectEvent(
                "status",
                "Safeguard",
                context.targetId(),
                context.targetId(),
                context.moveId(),
                "safeguard_block",
                0.0,
                target.hp()
        );
        return StatusApplicationHookResult.block(List.of(event));
    }

    private static boolean hasPositiveRemaining(StatusEntry entry) {
        Object value = entry.payload().get("remaining");
        if (value == null) return false;
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Integer integerValue) return integerValue > 0;
        if (value instanceof Long longValue) return longValue > 0;
        if (value instanceof Double doubleValue) {
            if (!Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException("Safeguard remaining must be a finite number");
            }
            return ((long) doubleValue.doubleValue()) > 0;
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.strip()) > 0;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Safeguard remaining must be int-like", exception);
            }
        }
        throw new IllegalArgumentException("unsupported Safeguard remaining payload: " + value.getClass().getSimpleName());
    }
}
