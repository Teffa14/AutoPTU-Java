package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Locale;

/** First built-in status prevention hooks ported from the Python oracle. */
public final class BuiltinStatusApplicationHooks {
    private BuiltinStatusApplicationHooks() {
    }

    public static StatusApplicationHookRegistry registry() {
        return StatusApplicationHookRegistry.builder()
                .register("inner-focus-flinch", HookSource.ABILITY, 100, BuiltinStatusApplicationHooks::innerFocus)
                .build();
    }

    private static StatusApplicationHookResult innerFocus(StatusApplicationContext context) {
        String status = context.status().name().toLowerCase(Locale.ROOT);
        if (!status.equals("flinch") && !status.equals("flinched")) {
            return StatusApplicationHookResult.allow();
        }
        RuntimeCombatantState target = context.state().requireCombatant(context.targetId());
        if (!AbilityIdentityResolution.matchesRegistration(target.abilities(), "Inner Focus")) {
            return StatusApplicationHookResult.allow();
        }
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Inner Focus",
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
