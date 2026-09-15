package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Projects recorded authoritative actions into reaction-window discovery without
 * discarding battle-local event occurrence identity.
 *
 * <p>This boundary consumes only {@link RecordedActionResult}. Eligible windows are claimed
 * in a battle-local ledger before they leave discovery, so replaying the same occurrence
 * cannot surface the same reactor/reaction opportunity twice. Unresolved ownership is not
 * claimed because later evidence may make that candidate eligible. This class does not mint
 * event identities, commit reaction usage, execute reactions, or mutate PTU battle state.</p>
 */
public final class RuntimeRecordedReactionDiscovery {
    private final RuntimeReactionWindowResolver resolver;
    private final RuntimeReactionWindowLedger ledger;

    public RuntimeRecordedReactionDiscovery(BattleRuntimeState battleState) {
        this(
                new RuntimeReactionWindowResolver(Objects.requireNonNull(battleState, "battleState")),
                new RuntimeReactionWindowLedger()
        );
    }

    RuntimeRecordedReactionDiscovery(
            RuntimeReactionWindowResolver resolver,
            RuntimeReactionWindowLedger ledger
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * Discovers Shift-triggered reaction windows from authoritative occurrences and returns
     * each eligible occurrence/reactor/reaction identity at most once for this discovery owner.
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
            for (RuntimeReactionWindowResolver.Candidate candidate : resolution.eligible()) {
                if (ledger.claim(candidate.window())) {
                    eligible.add(candidate);
                }
            }
            unresolved.addAll(resolution.unresolved());
        }
        return new RuntimeReactionWindowResolver.Resolution(
                List.copyOf(eligible),
                List.copyOf(unresolved)
        );
    }

    int discoveredWindowCount() {
        return ledger.size();
    }
}
