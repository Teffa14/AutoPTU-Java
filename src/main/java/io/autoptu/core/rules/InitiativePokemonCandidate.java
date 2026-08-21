package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;

/**
 * Language-neutral Pokemon input for assembling a round initiative order.
 *
 * The base entry is already produced by the parity-tested Pokemon initiative resolvers.
 * This contract owns only the state inspected by Python _build_initiative_order() after
 * that entry exists: active/fainted filtering, Parental Bond child exclusion, round
 * temporary effects, and ability identities used by round-scoped modifiers.
 */
public record InitiativePokemonCandidate(
        InitiativeEntry baseEntry,
        boolean active,
        boolean fainted,
        boolean parentalBondChild,
        List<TemporaryEffectEntry> temporaryEffects,
        List<String> abilities
) {
    public InitiativePokemonCandidate {
        temporaryEffects = temporaryEffects == null ? List.of() : List.copyOf(temporaryEffects);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
    }

    public String actorId() {
        return baseEntry == null ? "" : baseEntry.actorId();
    }
}
