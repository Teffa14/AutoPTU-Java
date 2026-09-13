package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BattleState-independent action budget with explicit profile semantics.
 *
 * <p>The default profile mirrors the pinned Python oracle. Other supported rule
 * profiles must be injected explicitly.</p>
 */
public final class ActionBudget {
    private static final String LEGACY_EXTRA_GRANT = "legacy";

    private final ActionEconomyProfile profile;
    private final EnumMap<ActionType, String> consumed = new EnumMap<>(ActionType.class);
    private final EnumMap<ActionType, LinkedHashMap<String, Integer>> extras = new EnumMap<>(ActionType.class);
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
        grantExtra(actionType, LEGACY_EXTRA_GRANT, count);
    }

    /**
     * Grants an extra action owned by a named rule source such as a Feature, ability,
     * item, or temporary effect. Grant insertion order is preserved so generic extra
     * spending remains deterministic while callers can still inspect provenance.
     */
    public void grantExtra(ActionType actionType, String grantName, int count) {
        requireType(actionType);
        requireGrantName(grantName);
        if (count <= 0) {
            return;
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
        requireGrantName(grantName);
        Map<String, Integer> grants = extras.get(actionType);
        return grants == null ? 0 : grants.getOrDefault(grantName, 0);
    }

    /** Read-only deterministic provenance snapshot for one action type. */
    public Map<String, Integer> extraGrants(ActionType actionType) {
        requireType(actionType);
        LinkedHashMap<String, Integer> grants = extras.get(actionType);
        if (grants == null || grants.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(grants));
    }

    public boolean consumeExtra(ActionType actionType) {
        return consumeExtraGrant(actionType).isPresent();
    }

    /**
     * Consumes one extra action from the earliest still-live named grant and returns
     * that source identity. This keeps legacy aggregate spending deterministic while
     * making future semantic events and hook accounting able to retain provenance.
     */
    public Optional<String> consumeExtraGrant(ActionType actionType) {
        requireType(actionType);
        LinkedHashMap<String, Integer> grants = extras.get(actionType);
        if (grants == null || grants.isEmpty()) {
            return Optional.empty();
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
        return Optional.of(grantName);
    }

    /**
     * Consumes a non-movement action using the selected profile.
     * Existing callers retain the legacy boolean contract.
     */
    public boolean consume(ActionType actionType, String detail) {
        return consumeDetailed(actionType, detail).consumed();
    }

    /**
     * Consumes a Shift for actual movement using the selected profile's movement-aware
     * conversion restrictions. Authoritative movement execution must use this path.
     */
    public boolean consumeMovement(String detail) {
        return consumeMovementDetailed(detail).consumed();
    }

    /**
     * Consumes a non-movement action and reports which resource source paid for it.
     * This preserves named extra-action provenance for generic hook/event callers.
     */
    public ActionSpendResult consumeDetailed(ActionType actionType, String detail) {
        return consumeDetailed(actionType, detail, false);
    }

    /**
     * Movement-aware detailed spend boundary. Shift movement callers use this when
     * semantic events need to retain the exact resource source.
     */
    public ActionSpendResult consumeMovementDetailed(String detail) {
        return consumeDetailed(ActionType.SHIFT, detail, true);
    }

    private ActionSpendResult consumeDetailed(ActionType actionType, String detail, boolean movement) {
        requireType(actionType);
        if (actionType == ActionType.FREE) {
            return ActionSpendResult.free();
        }
        if (hasActionAvailable(actionType)) {
            markAction(actionType, detail);
            if (actionType == ActionType.SHIFT && movement) {
                regularShiftUsedForMovement = true;
            }
            return ActionSpendResult.base();
        }
        if (profile.extraActionCanSatisfy(actionType)) {
            Optional<String> extraGrant = consumeExtraGrant(actionType);
            if (extraGrant.isPresent()) {
                return ActionSpendResult.extra(extraGrant.orElseThrow());
            }
        }
        if (!profile.allowsStandardConversion(actionType)
                || !hasActionAvailable(ActionType.STANDARD)) {
            return ActionSpendResult.unavailable();
        }
        if (actionType == ActionType.SHIFT
                && movement
                && profile.blocksConvertedMovementAfterRegularMovement()
                && regularShiftUsedForMovement) {
            return ActionSpendResult.unavailable();
        }
        consumeStandardConversion(actionType, detail);
        return ActionSpendResult.standardConversion();
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

    private static void requireGrantName(String grantName) {
        if (grantName == null || grantName.isBlank()) {
            throw new IllegalArgumentException("grantName is required");
        }
    }
}
