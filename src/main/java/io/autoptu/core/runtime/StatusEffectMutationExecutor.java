package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies planned status effects to authoritative state and emits their ordered semantic events. */
public final class StatusEffectMutationExecutor {
    private StatusEffectMutationExecutor() {}

    public static List<BattleEvent> apply(
            BattleRuntimeState state,
            List<StatusEffectInstruction> instructions
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (StatusEffectInstruction instruction : instructions == null
                ? List.<StatusEffectInstruction>of()
                : instructions) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("remaining", instruction.durationRounds());
            payload.put("source", instruction.source());
            payload.put("source_id", instruction.sourceId());
            state.putStatus(instruction.targetId(), new StatusEntry(instruction.status(), payload));

            events.add(new AbilityEvent(
                    instruction.sourceId(),
                    instruction.eventAbility(),
                    instruction.eventEffect(),
                    Map.of(
                            "target", instruction.targetId(),
                            "description", instruction.eventDescription()
                    )
            ));
        }
        return List.copyOf(events);
    }
}
