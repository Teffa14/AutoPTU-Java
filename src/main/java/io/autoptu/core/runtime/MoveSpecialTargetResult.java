package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

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

    List<BattleEvent> events() {
        return actionResult.events();
    }
}
