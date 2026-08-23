package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.StatusAbilityPreventionResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Optional;

/** Built-in target-owned ability gates for canonical status application. */
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
}
