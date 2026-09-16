package io.autoptu.core.event;

import java.util.Locale;

/**
 * Semantic occurrence for a completed authoritative non-move action.
 *
 * <p>The action key is language-neutral runtime data. Reaction registries may classify it, while
 * adapters only render the resulting event and never infer PTU legality from it.</p>
 */
public record ActionResolvedEvent(
        String actorId,
        String actionKey,
        String qualifier
) implements BattleEvent {
    public ActionResolvedEvent {
        actorId = safe(actorId);
        actionKey = normalize(actionKey);
        qualifier = normalize(qualifier);
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (actionKey.isBlank()) throw new IllegalArgumentException("actionKey is required");
    }

    public ActionResolvedEvent(String actorId, String actionKey) {
        this(actorId, actionKey, "");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.RULE_EFFECT;
    }

    @Override
    public String stableKey() {
        return String.join("|", "action_resolved", actorId, actionKey, qualifier);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
