package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.ArrayList;
import java.util.List;

/** Effective move profile plus ordered semantic events emitted by pre-damage hooks. */
public record EffectiveMoveHookResult(
        MoveCombatProfile profile,
        List<BattleEvent> events
) {
    public EffectiveMoveHookResult {
        if (profile == null) throw new IllegalArgumentException("profile is required");
        if (events == null || events.isEmpty()) {
            events = List.of();
        } else {
            ArrayList<BattleEvent> copied = new ArrayList<>(events.size());
            for (BattleEvent event : events) {
                if (event == null) throw new IllegalArgumentException("effective move hook event cannot be null");
                copied.add(event);
            }
            events = List.copyOf(copied);
        }
    }

    public static EffectiveMoveHookResult unchanged(MoveCombatProfile profile) {
        return new EffectiveMoveHookResult(profile, List.of());
    }
}
