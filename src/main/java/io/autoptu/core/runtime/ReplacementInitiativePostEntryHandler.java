package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.InitiativePokemonCandidate;

import java.util.List;

/**
 * Reusable switch-entry family that hands a replacement into the canonical initiative mutation.
 *
 * <p>Caller policy is carried by the switch context. Candidate construction and initiative state
 * remain server-authoritative and are derived from {@link BattleRuntimeState}; adapters never
 * provide initiative arithmetic or ordering.</p>
 */
public final class ReplacementInitiativePostEntryHandler
        implements CombatantSwitchPostEntryDispatcher.StageHandler {

    @Override
    public List<? extends BattleEvent> handle(CombatantSwitchPostEntryDispatcher.DispatchContext context) {
        if (!context.allowReplacementTurn()) {
            return List.of();
        }

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                context.state(),
                context.replacementId()
        );
        InitiativeEntry entry = candidate.baseEntry();
        ReplacementInitiativeInsertion.apply(
                context.state().initiativeProgress(),
                entry,
                context.allowImmediate()
        );
        return List.of();
    }
}
