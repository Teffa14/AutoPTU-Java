package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * BattleState-independent action budget modeled after TrainerState/PokemonState.
 *
 * Python currently tracks each ActionType as its own consumed bucket. Extra actions
 * are modeled separately and are consumed only after the base bucket is unavailable.
 */
public final class ActionBudget {
    private final EnumMap<ActionType, String> consumed = new EnumMap<>(ActionType.class);
    private final EnumMap<ActionType, Integer> extras = new EnumMap<>(ActionType.class);

    public void reset() {
        consumed.clear();
        extras.clear();
    }

    public void resetConsumedActions() {
        consumed.clear();
    }

    public void markAction(ActionType actionType, String detail) {
        requireType(actionType);
        consumed.put(actionType, detail == null ? "" : detail);
    }

    public boolean hasActionAvailable(ActionType actionType) {
        requireType(actionType);
        return !consumed.containsKey(actionType);
    }

    public Optional<String> consumedDetail(ActionType actionType) {
        requireType(actionType);
        return Optional.ofNullable(consumed.get(actionType));
    }

    public void grantExtra(ActionType actionType) {
        grantExtra(actionType, 1);
    }

    public void grantExtra(ActionType actionType, int count) {
        requireType(actionType);
        if (count <= 0) {
            return;
        }
        extras.merge(actionType, count, Integer::sum);
    }

    public int extraCount(ActionType actionType) {
        requireType(actionType);
        return extras.getOrDefault(actionType, 0);
    }

    public boolean consumeExtra(ActionType actionType) {
        requireType(actionType);
        int count = extras.getOrDefault(actionType, 0);
        if (count <= 0) {
            return false;
        }
        if (count == 1) {
            extras.remove(actionType);
        } else {
            extras.put(actionType, count - 1);
        }
        return true;
    }

    /**
     * Model ActionResolver's normal non-free consumption rule.
     * Returns false only when both the base bucket and extra bucket are exhausted.
     */
    public boolean consume(ActionType actionType, String detail) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return true;
        }
        if (hasActionAvailable(actionType)) {
            markAction(actionType, detail);
            return true;
        }
        return consumeExtra(actionType);
    }

    public Map<ActionType, String> consumedActions() {
        return Map.copyOf(consumed);
    }

    private static void requireType(ActionType actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
    }
}
