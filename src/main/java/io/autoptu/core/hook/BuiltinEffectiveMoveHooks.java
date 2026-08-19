package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.rules.AbilityIdentityResolution;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parity-backed pure transformations of effective move metadata. */
public final class BuiltinEffectiveMoveHooks {
    private static final Set<String> MEGA_LAUNCHER_MOVES = Set.of(
            "aura sphere",
            "dark pulse",
            "dragon pulse",
            "water pulse"
    );

    private static final EffectiveMoveHookRegistry STANDARD = EffectiveMoveHookRegistry.builder()
            .register(
                    "mega-launcher-db",
                    HookSource.ABILITY,
                    100,
                    context -> megaLauncher(context, "Mega Launcher", 2)
            )
            .register(
                    "mega-launcher-errata-db",
                    HookSource.ABILITY,
                    110,
                    context -> megaLauncher(context, "Mega Launcher [Errata]", 3)
            )
            .build();

    private BuiltinEffectiveMoveHooks() {
    }

    public static EffectiveMoveHookRegistry standardRegistry() {
        return STANDARD;
    }

    private static EffectiveMoveHookResult megaLauncher(
            EffectiveMoveHookContext context,
            String abilityName,
            int bonus
    ) {
        if (!AbilityIdentityResolution.matchesRegistration(context.actor().abilities(), abilityName)) {
            return EffectiveMoveHookResult.unchanged(context.effectiveProfile());
        }
        if (!MEGA_LAUNCHER_MOVES.contains(normalizeMoveName(context.move().moveId()))) {
            return EffectiveMoveHookResult.unchanged(context.effectiveProfile());
        }

        MoveCombatProfile current = context.effectiveProfile();
        MoveCombatProfile modified = new MoveCombatProfile(
                current.ac(),
                Math.min(20, current.damageBase() + bonus),
                current.critRange(),
                current.damageCategory(),
                current.moveType()
        );
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                abilityName,
                context.actorId(),
                context.targetId(),
                context.move().moveId(),
                "db_bonus",
                bonus,
                context.actor().hp()
        );
        return new EffectiveMoveHookResult(modified, List.of(event));
    }

    private static String normalizeMoveName(String moveId) {
        if (moveId == null) return "";
        return moveId.strip().toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ')
                .replaceAll("\\s+", " ");
    }
}
