package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Language-neutral Arena Trap effect plan between target eligibility and runtime mutation.
 *
 * <p>The pinned Python oracle applies Slowed for one round, records the ability holder as
 * source/source_id, and emits one ordered Arena Trap slowed event per eligible target. This
 * plan freezes that effect shape without coupling the targeting contract to status storage or
 * Minecraft/Cobblemon adapters.</p>
 */
public final class ArenaTrapEffectPlan {
    public static final String STATUS = "Slowed";
    public static final int DURATION_ROUNDS = 1;
    public static final String ABILITY = "Arena Trap";
    public static final String ACTION = "slowed";
    public static final String DESCRIPTION = "Arena Trap slows nearby foes.";

    private ArenaTrapEffectPlan() {}

    public record Effect(
            String targetId,
            String status,
            int durationRounds,
            String source,
            String sourceId,
            String ability,
            String action,
            String description
    ) {
        public Effect {
            targetId = required(targetId, "targetId");
            status = required(status, "status");
            source = required(source, "source");
            sourceId = required(sourceId, "sourceId");
            ability = required(ability, "ability");
            action = required(action, "action");
            description = required(description, "description");
            if (durationRounds <= 0) throw new IllegalArgumentException("durationRounds must be positive");
        }
    }

    public static List<Effect> forTargets(String holderId, List<String> targetIds) {
        String sourceId = required(holderId, "holderId");
        ArrayList<Effect> effects = new ArrayList<>();
        for (String targetId : targetIds == null ? List.<String>of() : targetIds) {
            effects.add(new Effect(
                    targetId,
                    STATUS,
                    DURATION_ROUNDS,
                    ABILITY,
                    sourceId,
                    ABILITY,
                    ACTION,
                    DESCRIPTION
            ));
        }
        return List.copyOf(effects);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
