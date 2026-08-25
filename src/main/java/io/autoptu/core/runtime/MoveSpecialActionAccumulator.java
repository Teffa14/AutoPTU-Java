package io.autoptu.core.runtime;

import io.autoptu.core.hook.MoveSpecialEndActionResolution;
import io.autoptu.core.hook.MoveSpecialHookRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-owned aggregation for Python move-special END_ACTION state.
 *
 * <p>The pinned Python oracle initializes {@code last_result} to {@code {"hit": false}}
 * and {@code total_damage_dealt} to zero, replaces {@code last_result} with each target's
 * final shared move-special result, sums the applied damage across targets, and dispatches
 * END_ACTION once after target processing. This state deliberately remains package-private
 * so Minecraft/Cobblemon adapters cannot synthesize action-wide PTU results.</p>
 */
final class MoveSpecialActionAccumulator {
    private final LinkedHashMap<String, Object> lastResult = new LinkedHashMap<>();
    private int totalDamageDealt;

    MoveSpecialActionAccumulator() {
        lastResult.put("hit", false);
    }

    void recordTarget(Map<String, ?> resultSnapshot, int damageDealt) {
        if (damageDealt < 0) throw new IllegalArgumentException("damageDealt must be non-negative");
        lastResult.clear();
        if (resultSnapshot != null) resultSnapshot.forEach(lastResult::put);
        totalDamageDealt = Math.addExact(totalDamageDealt, damageDealt);
    }

    void recordTarget(RuntimeMoveSpecialPostDamageApplication.Result targetResult) {
        Objects.requireNonNull(targetResult, "targetResult");
        recordTarget(targetResult.resultSnapshot(), targetResult.damageDealt());
    }

    Map<String, Object> lastResultSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(lastResult));
    }

    int totalDamageDealt() {
        return totalDamageDealt;
    }

    MoveSpecialEndActionResolution.Result finish(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String moveName,
            String moveCategory
    ) {
        return MoveSpecialEndActionResolution.resolve(
                Objects.requireNonNull(registry, "registry"),
                Objects.requireNonNull(state, "state"),
                attackerId,
                moveName,
                moveCategory,
                lastResult,
                totalDamageDealt
        );
    }
}
