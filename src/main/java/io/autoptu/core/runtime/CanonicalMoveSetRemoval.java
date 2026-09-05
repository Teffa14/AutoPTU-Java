package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure server-core contract for removing canonical moves by normalized identity.
 *
 * <p>Python lifecycle rules such as Psionic Sponge remove every move whose name matches
 * one of the temporary borrowed-move markers, using trim + case-fold comparison. This
 * resolver freezes that behavior without granting lifecycle/content hooks direct access
 * to the mutable battle-state collections.</p>
 */
public final class CanonicalMoveSetRemoval {
    private CanonicalMoveSetRemoval() {}

    public record Result(List<MoveOption> kept, List<MoveOption> removed) {
        public Result {
            kept = List.copyOf(kept == null ? List.of() : kept);
            removed = List.copyOf(removed == null ? List.of() : removed);
        }
    }

    public static Result resolve(Collection<MoveOption> moves, Collection<String> moveIdentities) {
        List<MoveOption> source = moves == null ? List.of() : List.copyOf(moves);
        Set<String> removalKeys = normalizedKeys(moveIdentities);
        if (source.isEmpty() || removalKeys.isEmpty()) {
            return new Result(source, List.of());
        }

        ArrayList<MoveOption> kept = new ArrayList<>();
        ArrayList<MoveOption> removed = new ArrayList<>();
        for (MoveOption move : source) {
            if (move == null) continue;
            if (removalKeys.contains(normalize(move.moveId()))) {
                removed.add(move);
            } else {
                kept.add(move);
            }
        }
        return new Result(kept, removed);
    }

    private static Set<String> normalizedKeys(Collection<String> identities) {
        if (identities == null || identities.isEmpty()) return Set.of();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String identity : identities) {
            String key = normalize(identity);
            if (!key.isBlank()) keys.add(key);
        }
        return Set.copyOf(keys);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
