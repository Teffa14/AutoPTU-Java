package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BattleState-independent PTU action budget.
 *
 * <p>A normal turn owns one Standard, one Shift, and one Swift resource. A Full
 * Action atomically spends the Standard and Shift resources. Standard may instead
 * be converted to one additional Swift or one additional non-movement Shift; it
 * may pay for movement only when the regular Shift was not already used to move.
 * Free Actions are intentionally not numerically capped here. Trigger, Priority,
 * and Interrupt lifecycle restrictions belong to their dedicated controllers.</p>
 */
public final class ActionBudget {
    private static final String LEGACY_GRANT = "legacy";

    private final EnumMap<ActionType, String> consumed = new EnumMap<>(ActionType.class);
    private final EnumMap<ActionType, LinkedHashMap<String, Integer>> extras = new EnumMap<>(ActionType.class);
    private ActionType standardConversion;
    private boolean regularShiftUsedForMovement;

    public void reset() {
        consumed.clear();
        extras.clear();
        standardConversion = null;
        regularShiftUsedForMovement = false;
    }

    public void resetConsumedActions() {
        consumed.clear();
        standardConversion = null;
        regularShiftUsedForMovement = false;
    }

    public void markAction(ActionType actionType, String detail) {
        requireType(actionType);
        String safeDetail = detail == null ? "" : detail;
        if (actionType == ActionType.FULL) {
            consumed.put(ActionType.STANDARD, safeDetail);
            consumed.put(ActionType.SHIFT, safeDetail);
            consumed.put(ActionType.FULL, safeDetail);
            return;
        }
        consumed.put(actionType, safeDetail);
    }

    public boolean hasActionAvailable(ActionType actionType) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return true;
        }
        if (actionType == ActionType.FULL) {
            return !consumed.containsKey(ActionType.STANDARD)
                    && !consumed.containsKey(ActionType.SHIFT);
        }
        return !consumed.containsKey(actionType);
    }

    /** Returns rule-aware capacity for a non-movement use of the requested action. */
    public boolean hasCapacity(ActionType actionType) {
        return hasCapacity(actionType, false);
    }

    /**
     * Returns rule-aware capacity, including an explicit Standard-to-Shift
     * conversion check when {@code movement} is true.
     */
    public boolean hasCapacity(ActionType actionType, boolean movement) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return true;
        }
        if (hasActionAvailable(actionType) || extraCount(actionType) > 0) {
            return true;
        }
        if (actionType == ActionType.SWIFT) {
            return hasActionAvailable(ActionType.STANDARD);
        }
        if (actionType == ActionType.SHIFT) {
            return hasActionAvailable(ActionType.STANDARD)
                    && (!movement || !regularShiftUsedForMovement);
        }
        return false;
    }

    public Optional<String> consumedDetail(ActionType actionType) {
        requireType(actionType);
        return Optional.ofNullable(consumed.get(actionType));
    }

    public void grantExtra(ActionType actionType) {
        grantExtra(actionType, 1);
    }

    public void grantExtra(ActionType actionType, int count) {
        grantExtra(actionType, LEGACY_GRANT, count);
    }

    /** Grants a rule-owned extra action without turning it into another base resource. */
    public void grantExtra(ActionType actionType, String grantName, int count) {
        requireType(actionType);
        if (count <= 0) {
            return;
        }
        if (grantName == null || grantName.isBlank()) {
            throw new IllegalArgumentException("grantName is required");
        }
        extras.computeIfAbsent(actionType, ignored -> new LinkedHashMap<>())
                .merge(grantName, count, Integer::sum);
    }

    public int extraCount(ActionType actionType) {
        requireType(actionType);
        Map<String, Integer> grants = extras.get(actionType);
        if (grants == null) {
            return 0;
        }
        return grants.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int extraCount(ActionType actionType, String grantName) {
        requireType(actionType);
        if (grantName == null || grantName.isBlank()) {
            throw new IllegalArgumentException("grantName is required");
        }
        Map<String, Integer> grants = extras.get(actionType);
        return grants == null ? 0 : grants.getOrDefault(grantName, 0);
    }

    public boolean consumeExtra(ActionType actionType) {
        requireType(actionType);
        LinkedHashMap<String, Integer> grants = extras.get(actionType);
        if (grants == null || grants.isEmpty()) {
            return false;
        }
        String grantName = grants.keySet().iterator().next();
        int count = grants.get(grantName);
        if (count == 1) {
            grants.remove(grantName);
        } else {
            grants.put(grantName, count - 1);
        }
        if (grants.isEmpty()) {
            extras.remove(actionType);
        }
        return true;
    }

    /**
     * Spends a non-movement action using base, named-extra, then conversion capacity.
     */
    public boolean consume(ActionType actionType, String detail) {
        return consume(actionType, detail, false);
    }

    /** Spends a Shift for actual movement under PTU's conversion restriction. */
    public boolean consumeMovement(String detail) {
        return consume(ActionType.SHIFT, detail, true);
    }

    private boolean consume(ActionType actionType, String detail, boolean movement) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return true;
        }
        if (hasActionAvailable(actionType)) {
            markAction(actionType, detail);
            if (actionType == ActionType.SHIFT && movement) {
                regularShiftUsedForMovement = true;
            }
            return true;
        }
        if (consumeExtra(actionType)) {
            return true;
        }
        if (actionType == ActionType.SWIFT && hasActionAvailable(ActionType.STANDARD)) {
            consumeStandardConversion(ActionType.SWIFT, detail);
            return true;
        }
        if (actionType == ActionType.SHIFT
                && hasActionAvailable(ActionType.STANDARD)
                && (!movement || !regularShiftUsedForMovement)) {
            consumeStandardConversion(ActionType.SHIFT, detail);
            return true;
        }
        return false;
    }

    public Optional<ActionType> standardConversion() {
        return Optional.ofNullable(standardConversion);
    }

    public boolean regularShiftUsedForMovement() {
        return regularShiftUsedForMovement;
    }

    public Map<ActionType, String> consumedActions() {
        return Map.copyOf(consumed);
    }

    private void consumeStandardConversion(ActionType target, String detail) {
        standardConversion = target;
        String safeDetail = detail == null ? "" : detail;
        consumed.put(ActionType.STANDARD, "converted-to-" + target.value() + ":" + safeDetail);
    }

    private static void requireType(ActionType actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
    }
}
