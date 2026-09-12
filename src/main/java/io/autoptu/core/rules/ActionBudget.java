package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * BattleState-independent action budget with explicit profile semantics.
 *
 * <p>The default profile mirrors the pinned Python oracle. Other supported rule
 * profiles must be injected explicitly.</p>
 */
public final class ActionBudget {
    private final ActionEconomyProfile profile;
    private final EnumMap<ActionType, String> consumed = new EnumMap<>(ActionType.class);
    private final EnumMap<ActionType, Integer> extras = new EnumMap<>(ActionType.class);
    private ActionType standardConversion;
    private boolean regularShiftUsedForMovement;

    public ActionBudget() {
        this(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
    }

    public ActionBudget(ActionEconomyProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        this.profile = profile;
    }

    public ActionEconomyProfile profile() {
        return profile;
    }

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
        profile.markBaseAction(consumed, actionType, detail);
    }

    public boolean hasActionAvailable(ActionType actionType) {
        requireType(actionType);
        return profile.hasBaseActionAvailable(consumed, actionType);
    }

    public boolean hasCapacity(ActionType actionType) {
        return hasCapacity(actionType, false);
    }

    /**
     * Returns resource capacity for the selected profile. The movement flag only
     * affects Shift conversion rules; ordinary compatibility callers remain unchanged.
     */
    public boolean hasCapacity(ActionType actionType, boolean movement) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return true;
        }
        if (hasActionAvailable(actionType)) {
            return true;
        }
        if (profile.extraActionCanSatisfy(actionType) && extraCount(actionType) > 0) {
            return true;
        }
        if (!profile.allowsStandardConversion(actionType)
                || !hasActionAvailable(ActionType.STANDARD)) {
            return false;
        }
        return actionType != ActionType.SHIFT
                || !movement
                || !profile.blocksConvertedMovementAfterRegularMovement()
                || !regularShiftUsedForMovement;
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
     * Consumes a non-movement action using the selected profile.
     */
    public boolean consume(ActionType actionType, String detail) {
        return consume(actionType, detail, false);
    }

    /**
     * Consumes a Shift specifically for movement. This is currently used only by
     * explicit profile conformance tests; production movement callers remain on
     * the Python-compatibility path until a later bounded slice.
     */
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
        if (profile.extraActionCanSatisfy(actionType) && consumeExtra(actionType)) {
            return true;
        }
        if (!profile.allowsStandardConversion(actionType)
                || !hasActionAvailable(ActionType.STANDARD)) {
            return false;
        }
        if (actionType == ActionType.SHIFT
                && movement
                && profile.blocksConvertedMovementAfterRegularMovement()
                && regularShiftUsedForMovement) {
            return false;
        }
        consumeStandardConversion(actionType, detail);
        return true;
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
