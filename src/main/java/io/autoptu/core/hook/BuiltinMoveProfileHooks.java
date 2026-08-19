package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.AbilityState;

import java.util.List;
import java.util.Set;

/** Default authoritative effective-move hooks that are already parity-backed. */
public final class BuiltinMoveProfileHooks {
    private static final Set<String> PULSE_MOVES = Set.of(
            "aura sphere", "dark pulse", "dragon pulse", "water pulse"
    );

    private static final MoveProfileHookRegistry STANDARD = MoveProfileHookRegistry.builder()
            .register(
                    "mega-launcher-pulse-db",
                    HookSource.ABILITY,
                    100,
                    BuiltinMoveProfileHooks::megaLauncherPulseBoost
            )
            .build();

    private BuiltinMoveProfileHooks() {
    }

    public static MoveProfileHookRegistry standardRegistry() {
        return STANDARD;
    }

    private static MoveProfileHookResult megaLauncherPulseBoost(MoveProfileHookContext context) {
        String moveName = context.move().moveId().strip().toLowerCase(java.util.Locale.ROOT);
        if (!PULSE_MOVES.contains(moveName)) {
            return MoveProfileHookResult.unchanged(context.profile());
        }

        AbilityState ability = findAbility(context.actorAbilities(), "mega launcher [errata]");
        int bonus = 3;
        if (ability == null) {
            ability = findAbility(context.actorAbilities(), "mega launcher");
            bonus = 2;
        }
        if (ability == null) {
            return MoveProfileHookResult.unchanged(context.profile());
        }

        MoveCombatProfile current = context.profile();
        int nextDb = Math.min(20, current.damageBase() + bonus);
        MoveCombatProfile next = new MoveCombatProfile(
                current.ac(), nextDb, current.critRange(), current.damageCategory(), current.moveType()
        );
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                ability.name(),
                context.actorId(),
                context.targetId(),
                context.move().moveId(),
                "db_bonus",
                bonus,
                context.target().hp()
        );
        return MoveProfileHookResult.of(next, List.of(event));
    }

    private static AbilityState findAbility(List<AbilityState> abilities, String normalizedName) {
        for (AbilityState ability : abilities) {
            if (normalizedName.equals(ability.normalizedName())) {
                return ability;
            }
        }
        return null;
    }
}
