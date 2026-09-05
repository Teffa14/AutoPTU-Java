package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Package-private per-target move-special transport for action-wide aggregation.
 *
 * <p>The public Minecraft-facing result remains {@link AppliedActionResult}. This record keeps
 * the shared Python result mapping and applied damage inside the runtime package so multi-target
 * declarations can feed END_ACTION without exposing mutable PTU bookkeeping to adapters.</p>
 */
record MoveSpecialTargetResult(
        AppliedActionResult actionResult,
        Map<String, Object> resultSnapshot,
        int damageDealt
) {
    MoveSpecialTargetResult {
        actionResult = Objects.requireNonNull(actionResult, "actionResult");
        resultSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(
                resultSnapshot == null ? Map.of() : resultSnapshot));
        if (damageDealt < 0) throw new IllegalArgumentException("damageDealt must be non-negative");
    }

    static MoveSpecialTargetResult from(RuntimeMoveSpecialPostDamageApplication.Result result) {
        Objects.requireNonNull(result, "result");
        return new MoveSpecialTargetResult(result.actionResult(), result.resultSnapshot(), result.damageDealt());
    }

    static MoveSpecialTargetResult fromAppliedOutcome(
            AppliedActionResult actionResult,
            Map<String, ?> resultSnapshot,
            boolean hit,
            int targetHpBefore,
            RuntimeCombatantState target
    ) {
        Objects.requireNonNull(actionResult, "actionResult");
        Objects.requireNonNull(target, "target");
        if (targetHpBefore < 0) throw new IllegalArgumentException("targetHpBefore must be non-negative");

        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        if (resultSnapshot != null) resultSnapshot.forEach(snapshot::put);
        int appliedDamage = hit ? Math.max(0, targetHpBefore - target.hp()) : 0;
        return new MoveSpecialTargetResult(actionResult, snapshot, appliedDamage);
    }

    /**
     * Replaces only the adapter-facing event result while preserving the post-damage snapshot
     * and applied-damage bookkeeping needed by action-wide END_ACTION finalization.
     */
    MoveSpecialTargetResult withActionResult(AppliedActionResult nextActionResult) {
        return new MoveSpecialTargetResult(nextActionResult, resultSnapshot, damageDealt);
    }

    /**
     * Prepends already-resolved semantic events without losing the post-damage transport.
     * This mirrors BattleRuntime's current pre-resolution / PRE_DAMAGE / post-result ordering
     * while keeping END_ACTION inputs attached to the target result.
     */
    MoveSpecialTargetResult prependEvents(List<? extends BattleEvent> before) {
        if (before == null || before.isEmpty()) return this;
        ArrayList<BattleEvent> events = new ArrayList<>(before.size() + actionResult.events().size());
        for (BattleEvent event : before) {
            if (event == null) throw new IllegalArgumentException("events cannot contain null");
            events.add(event);
        }
        events.addAll(actionResult.events());
        return withActionResult(new AppliedActionResult(events));
    }

    /**
     * Appends already-resolved semantic events, such as post-hit forced movement, while
     * preserving the snapshot and applied damage required by action-wide finalization.
     */
    MoveSpecialTargetResult appendEvents(List<? extends BattleEvent> after) {
        if (after == null || after.isEmpty()) return this;
        ArrayList<BattleEvent> events = new ArrayList<>(actionResult.events().size() + after.size());
        events.addAll(actionResult.events());
        for (BattleEvent event : after) {
            if (event == null) throw new IllegalArgumentException("events cannot contain null");
            events.add(event);
        }
        return withActionResult(new AppliedActionResult(events));
    }

    List<BattleEvent> events() {
        return actionResult.events();
    }
}
