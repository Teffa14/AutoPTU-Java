package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates reaction ownership across registered authoritative content domains.
 *
 * <p>An owned result wins immediately. If any registered source is UNKNOWN and none
 * reports ownership, the aggregate remains UNKNOWN rather than silently denying the
 * reaction from a partial battle snapshot.</p>
 */
public final class RuntimeReactionOwnershipResolver {
    private final List<ReactionOwnershipSource> sources;

    public RuntimeReactionOwnershipResolver() {
        this(List.of(new MoveReactionOwnershipSource()));
    }

    public RuntimeReactionOwnershipResolver(List<? extends ReactionOwnershipSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("at least one reaction ownership source is required");
        }
        ArrayList<ReactionOwnershipSource> copy = new ArrayList<>(sources.size());
        for (ReactionOwnershipSource source : sources) {
            copy.add(Objects.requireNonNull(source, "reaction ownership source"));
        }
        this.sources = List.copyOf(copy);
    }

    public Result resolve(BattleRuntimeState battleState, String combatantId, String reactionKey) {
        Objects.requireNonNull(battleState, "battleState");
        if (combatantId == null || combatantId.isBlank()) throw new IllegalArgumentException("combatantId is required");
        if (reactionKey == null || reactionKey.isBlank()) throw new IllegalArgumentException("reactionKey is required");

        ArrayList<String> ownedBy = new ArrayList<>();
        boolean sawUnknown = false;
        for (ReactionOwnershipSource source : sources) {
            ReactionOwnershipSource.Resolution resolution = source.resolve(battleState, combatantId, reactionKey);
            if (resolution == ReactionOwnershipSource.Resolution.OWNED) {
                ownedBy.add(source.sourceId());
            } else if (resolution == ReactionOwnershipSource.Resolution.UNKNOWN) {
                sawUnknown = true;
            }
        }
        if (!ownedBy.isEmpty()) return new Result(Status.OWNED, List.copyOf(ownedBy));
        if (sawUnknown) return new Result(Status.UNKNOWN, List.of());
        return new Result(Status.NOT_OWNED, List.of());
    }

    public enum Status {
        OWNED,
        NOT_OWNED,
        UNKNOWN
    }

    public record Result(Status status, List<String> sourceIds) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
            if (status != Status.OWNED && !sourceIds.isEmpty()) {
                throw new IllegalArgumentException("only OWNED results may report source ids");
            }
        }

        public boolean ownsReaction() {
            return status == Status.OWNED;
        }
    }
}
