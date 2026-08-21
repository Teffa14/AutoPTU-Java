package io.autoptu.core.runtime;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.InitiativeOrderAssemblyResult;

import java.util.List;
import java.util.Map;

/**
 * Commits a parity-tested initiative assembly result into authoritative runtime state.
 *
 * The assembly layer may discover round-scoped temporary effects that Python removes
 * while rebuilding initiative. This boundary validates the complete result before any
 * state mutation, applies those cleanup requests to server-owned temporary effects, and
 * installs the canonical initiative order with the cursor reset to -1.
 *
 * Trainer initiative slots remain intentionally unsupported by the current turn runner.
 * They fail closed here instead of being silently skipped as if they were Pokemon turns.
 */
public final class InitiativeAssemblyInstaller {
    private InitiativeAssemblyInstaller() {
    }

    public static List<String> install(
            BattleRuntimeState state,
            InitiativeOrderAssemblyResult assembly
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (assembly == null) {
            throw new IllegalArgumentException("initiative assembly is required");
        }

        List<String> orderedActorIds = assembly.orderedActorIds();

        // Validate the full mutation set first. A malformed rebuild must not partially
        // clear temporary effects before failing to install its order.
        for (InitiativeEntry entry : assembly.orderedEntries()) {
            String actorId = entry.actorId();
            if (!state.combatants().containsKey(actorId)) {
                throw new IllegalArgumentException(
                        "initiative entry is not an executable Pokemon combatant: " + actorId
                );
            }
        }
        for (Map.Entry<String, List<String>> cleanup : assembly.temporaryEffectFamiliesToClear().entrySet()) {
            RuntimeCombatantState actor = state.requireCombatant(cleanup.getKey());
            for (String family : cleanup.getValue()) {
                if (family == null || family.isBlank()) {
                    throw new IllegalArgumentException("initiative cleanup family is required");
                }
            }
            // Deliberately touch the actor during validation so unknown identities fail
            // before any later cleanup is applied.
            actor.combatantId();
        }

        for (Map.Entry<String, List<String>> cleanup : assembly.temporaryEffectFamiliesToClear().entrySet()) {
            TemporaryEffectStore temporaryEffects = state.requireCombatant(cleanup.getKey()).temporaryEffects();
            for (String family : cleanup.getValue()) {
                temporaryEffects.removeAll(family);
            }
        }

        state.initiativeProgress().replaceOrderFromLifecycle(orderedActorIds);
        return orderedActorIds;
    }
}
