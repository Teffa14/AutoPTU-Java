package io.autoptu.core.runtime;

/**
 * One authoritative content-domain source of reaction ownership.
 *
 * <p>Sources return UNKNOWN when their domain is not materialized in the battle snapshot.
 * This prevents a partial snapshot from silently denying a reaction that may be owned by
 * another content domain.</p>
 */
public interface ReactionOwnershipSource {
    String sourceId();

    Resolution resolve(BattleRuntimeState battleState, String combatantId, String reactionKey);

    enum Resolution {
        OWNED,
        NOT_OWNED,
        UNKNOWN
    }
}
