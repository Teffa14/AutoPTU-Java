package io.autoptu.core.runtime;

/**
 * Canonical server-owned Trainer Feature state relevant to status-skip resolution.
 * Minecraft/Cobblemon may display these choices, but cannot supply bypass decisions.
 */
public record StatusSkipFeatureState(
        String signatureModification,
        String signatureMove,
        boolean duelistsManualIgnoreStatus
) {
    public static final StatusSkipFeatureState NONE = new StatusSkipFeatureState("", "", false);

    public StatusSkipFeatureState {
        signatureModification = signatureModification == null ? "" : signatureModification.strip();
        signatureMove = signatureMove == null ? "" : signatureMove.strip();
    }
}
