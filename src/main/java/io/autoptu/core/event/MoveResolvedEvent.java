package io.autoptu.core.event;

import java.util.Locale;

/**
 * Semantic result of one resolved move.
 *
 * IDs are core-stable identifiers. A Minecraft adapter may map them to current
 * Cobblemon/Minecraft entities without feeding presentation state back into rules.
 */
public record MoveResolvedEvent(
        String source,
        String attackerId,
        String targetId,
        String moveId,
        boolean hit,
        boolean crit,
        int damage,
        int targetHp
) implements BattleEvent {
    public MoveResolvedEvent {
        source = source == null ? "" : source.strip().toLowerCase(Locale.ROOT);
        attackerId = requireId(attackerId, "attackerId");
        targetId = requireId(targetId, "targetId");
        moveId = requireId(moveId, "moveId");
        damage = Math.max(0, damage);
        targetHp = Math.max(0, targetHp);
        if (!hit) {
            crit = false;
            damage = 0;
        }
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.MOVE_RESOLVED;
    }

    @Override
    public String stableKey() {
        return kind().value()
                + "|" + source
                + "|" + attackerId
                + "|" + targetId
                + "|" + moveId
                + "|" + hit
                + "|" + crit
                + "|" + damage
                + "|" + targetHp;
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
