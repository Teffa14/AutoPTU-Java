package io.autoptu.core.event;

import java.util.List;
import java.util.Locale;

/**
 * Semantic occurrence for a completed authoritative non-move action.
 *
 * <p>The action key and target ids are language-neutral runtime data. Reaction registries may
 * classify the occurrence, while adapters only render the resulting event and never infer PTU
 * legality from it.</p>
 */
public record ActionResolvedEvent(
        String actorId,
        String actionKey,
        String qualifier,
        List<String> targetIds
) implements BattleEvent {
    public ActionResolvedEvent {
        actorId = safe(actorId);
        actionKey = normalize(actionKey);
        qualifier = normalize(qualifier);
        targetIds = targetIds == null ? List.of() : targetIds.stream().map(ActionResolvedEvent::safe).toList();
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (actionKey.isBlank()) throw new IllegalArgumentException("actionKey is required");
        if (targetIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("target ids must not be blank");
        }
    }

    public ActionResolvedEvent(String actorId, String actionKey) {
        this(actorId, actionKey, "", List.of());
    }

    public ActionResolvedEvent(String actorId, String actionKey, String qualifier) {
        this(actorId, actionKey, qualifier, List.of());
    }

    public static ActionResolvedEvent targeted(String actorId, String actionKey, List<String> targetIds) {
        return new ActionResolvedEvent(actorId, actionKey, "", targetIds);
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.RULE_EFFECT;
    }

    @Override
    public String stableKey() {
        String legacyKey = String.join("|", "action_resolved", actorId, actionKey, qualifier);
        if (targetIds.isEmpty()) {
            return legacyKey;
        }
        return legacyKey + "|" + String.join(",", targetIds);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
