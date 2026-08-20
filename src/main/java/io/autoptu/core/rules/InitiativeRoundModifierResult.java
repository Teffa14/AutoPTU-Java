package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;

import java.util.List;
import java.util.Objects;

/** Result of applying round-scoped initiative modifiers to one canonical entry. */
public record InitiativeRoundModifierResult(
        InitiativeEntry entry,
        List<String> temporaryEffectsToClear
) {
    public InitiativeRoundModifierResult {
        entry = Objects.requireNonNull(entry, "entry");
        temporaryEffectsToClear = temporaryEffectsToClear == null
                ? List.of()
                : List.copyOf(temporaryEffectsToClear);
    }
}
