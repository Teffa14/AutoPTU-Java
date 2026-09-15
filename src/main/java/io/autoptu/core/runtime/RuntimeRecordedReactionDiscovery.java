package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Projects one recorded authoritative action into reaction-window discovery without
 * discarding battle-local event occurrence identity.
 *
 * <p>This boundary consumes only {@link RecordedActionResult}. It does not accept raw
 * semantic Shift payloads, mint event identities, execute reactions, or mutate battle
 * state. Unsupported event families are ignored until their trigger contracts are
 * explicitly ported.</p>
 */
public final class RuntimeRecordedReactionDiscovery {
    private final RuntimeReactionWindowResolver resolver;

    public RuntimeRecordedReactionDiscovery(BattleRuntimeState battleState) {
        this(new RuntimeReactionWindowResolver(Objects.requireNonNull(battleState, "battleState")));
    }

    RuntimeRecordedReactionDiscovery(RuntimeReactionWindowResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Discovers Shift-triggered reaction windows from the authoritative occurrences
     * already assigned to one completed action.
     */
    public RuntimeReactionWindowResolver.Resolution discoverShiftWindows(
            String reactionKey,
            RecordedActionResult recorded,
            ReactionEligibilityPolicy policy
    ) {
        Objects.requireNonNull(recorded, "recorded action result");
        Objects.requireNonNull(policy, "policy");

        ArrayList<RuntimeReactionWindowResolver.Candidate> eligible = new ArrayList<>();
        ArrayList<RuntimeReactionWindowResolver.UnresolvedCandidate> unresolved = new ArrayList<>();
        for (BattleEventOccurrence occurrence : recorded.occurrences()) {
            if (!(occurrence.event() instanceof ShiftResolvedEvent)) {
                continue;
            }
            RuntimeReactionWindowResolver.Resolution resolution = resolver.discoverShiftWindows(
                    reactionKey,
                    occurrence,
                    policy
            );
            eligible.addAll(resolution.eligible());
            unresolved.addAll(resolution.unresolved());
        }
        return new RuntimeReactionWindowResolver.Resolution(
                List.copyOf(eligible),
                List.copyOf(unresolved)
        );
    }
}
