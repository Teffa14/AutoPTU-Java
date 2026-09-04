package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPhase;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.Objects;

/** Standard server-authoritative move-special families that are live in runtime resolution. */
final class RuntimeMoveSpecialHooks {
    private RuntimeMoveSpecialHooks() {}

    static MoveSpecialHookRegistry standardRegistry(
            MoveOption move,
            MoveCombatProfile effectiveProfile,
            StatusApplicationHookRegistry statusApplicationHooks
    ) {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(effectiveProfile, "effectiveProfile");
        Objects.requireNonNull(statusApplicationHooks, "statusApplicationHooks");

        return MoveSpecialHookRegistry.builder()
                .registerGlobal(
                        "generic-secondary-status-from-effects-text",
                        MoveSpecialPhase.POST_DAMAGE,
                        context -> MoveSpecialSecondaryStatusPostDamage.resolveAndApply(
                                context.state(),
                                statusApplicationHooks,
                                context.attackerId(),
                                context.defenderId(),
                                move,
                                effectiveProfile,
                                context.result().snapshot()
                        ).events()
                )
                .build();
    }
}
