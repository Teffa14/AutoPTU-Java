package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.model.MoveCombatProfile;

/**
 * Composition boundary for move-specific hook registries whose contents depend on the
 * authoritative effective move profile.
 */
@FunctionalInterface
public interface MoveSpecialHookRegistryFactory {
    MoveSpecialHookRegistry create(
            MoveOption move,
            MoveCombatProfile effectiveProfile,
            StatusApplicationHookRegistry statusApplicationHooks
    );

    static MoveSpecialHookRegistryFactory standard() {
        return RuntimeMoveSpecialHooks::standardRegistry;
    }
}
