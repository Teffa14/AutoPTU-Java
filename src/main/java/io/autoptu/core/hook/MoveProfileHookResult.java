package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Effective move profile plus ordered semantic playback events emitted by profile hooks. */
public record MoveProfileHookResult(MoveCombatProfile profile, List<BattleEvent> events) {
    public MoveProfileHookResult {
        profile = Objects.requireNonNull(profile, "profile");
        ArrayList<BattleEvent> copied = new ArrayList<>();
        if (events != null) {
            for (BattleEvent event : events) {
                if (event == null) throw new IllegalArgumentException("events cannot contain null");
                copied.add(event);
            }
        }
        events = List.copyOf(copied);
    }

    public static MoveProfileHookResult unchanged(MoveCombatProfile profile) {
        return new MoveProfileHookResult(profile, List.of());
    }

    public static MoveProfileHookResult of(MoveCombatProfile profile, List<? extends BattleEvent> events) {
        return new MoveProfileHookResult(profile, events == null ? List.of() : List.copyOf(events));
    }
}
