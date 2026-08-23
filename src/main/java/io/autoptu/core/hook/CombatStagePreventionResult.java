package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Result of one pre-mutation combat-stage prevention hook. */
public record CombatStagePreventionResult(boolean blocked, List<BattleEvent> events) {
    public CombatStagePreventionResult {
        if (events == null || events.isEmpty()) {
            events = List.of();
        } else {
            ArrayList<BattleEvent> copy = new ArrayList<>(events.size());
            for (BattleEvent event : events) {
                if (event == null) throw new IllegalArgumentException("combat-stage prevention event cannot be null");
                copy.add(event);
            }
            events = List.copyOf(copy);
        }
    }

    public static CombatStagePreventionResult allow() { return new CombatStagePreventionResult(false, List.of()); }
    public static CombatStagePreventionResult block(List<BattleEvent> events) { return new CombatStagePreventionResult(true, events); }
}
