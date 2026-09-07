package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python-compatible invocation contract for TrainerFeatureDispatcher at round start.
 *
 * The concrete Trainer Feature catalog/effect executor remains server-owned and plugs into
 * ROUND_START_EFFECTS. This value freezes the trigger and payload shape so consumers do not
 * invent actor/target arguments or a different lifecycle payload.
 */
public record RoundStartTrainerFeatureDispatchContract(
        String trigger,
        Map<String, Object> payload,
        boolean actorArgumentPresent,
        boolean targetArgumentPresent
) {
    public RoundStartTrainerFeatureDispatchContract {
        if (trigger == null || trigger.isBlank()) throw new IllegalArgumentException("trigger is required");
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    }

    public static RoundStartTrainerFeatureDispatchContract forRound(int round) {
        if (round < 1) throw new IllegalArgumentException("round must be positive");
        return new RoundStartTrainerFeatureDispatchContract(
                "round_start",
                Map.of("round", round),
                false,
                false
        );
    }
}
