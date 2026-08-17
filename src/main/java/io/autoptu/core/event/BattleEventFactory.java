package io.autoptu.core.event;

import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.DamageResult;

/** Converts resolved core rule outputs into renderer-safe semantic events. */
public final class BattleEventFactory {
    private BattleEventFactory() {
    }

    public static MoveResolvedEvent moveResolved(
            String source,
            String attackerId,
            String targetId,
            String moveId,
            AccuracyResult accuracy,
            DamageResult damage,
            int targetHp
    ) {
        if (accuracy == null) {
            throw new IllegalArgumentException("accuracy is required");
        }
        if (accuracy.hit() && damage == null) {
            throw new IllegalArgumentException("damage is required for a hit");
        }
        int resolvedDamage = accuracy.hit() ? damage.damage() : 0;
        return new MoveResolvedEvent(
                source,
                attackerId,
                targetId,
                moveId,
                accuracy.hit(),
                accuracy.crit(),
                resolvedDamage,
                targetHp
        );
    }
}
