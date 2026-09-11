package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-authoritative dispatcher for the ordered post-entry switch families frozen from Python.
 *
 * <p>The dispatcher owns only staging and ordered publication. Rule behavior stays inside
 * registered reusable handlers. Missing stages remain explicit {@link StageStatus#PENDING}
 * results so an incomplete Java port cannot silently claim parity.</p>
 */
public final class CombatantSwitchPostEntryDispatcher {
    private final Map<CombatantSwitchPostEntryPlan.Stage, StageHandler> handlers;

    public CombatantSwitchPostEntryDispatcher(Map<CombatantSwitchPostEntryPlan.Stage, StageHandler> handlers) {
        EnumMap<CombatantSwitchPostEntryPlan.Stage, StageHandler> copy =
                new EnumMap<>(CombatantSwitchPostEntryPlan.Stage.class);
        if (handlers != null) {
            handlers.forEach((stage, handler) -> {
                if (stage == null) throw new IllegalArgumentException("post-entry stage is required");
                if (handler == null) throw new IllegalArgumentException("post-entry handler is required");
                copy.put(stage, handler);
            });
        }
        this.handlers = Map.copyOf(copy);
    }

    public static CombatantSwitchPostEntryDispatcher empty() {
        return new CombatantSwitchPostEntryDispatcher(Map.of());
    }

    public CombatantSwitchPostEntryDispatcher withHandler(
            CombatantSwitchPostEntryPlan.Stage stage,
            StageHandler handler
    ) {
        if (stage == null) throw new IllegalArgumentException("post-entry stage is required");
        if (handler == null) throw new IllegalArgumentException("post-entry handler is required");
        EnumMap<CombatantSwitchPostEntryPlan.Stage, StageHandler> next =
                new EnumMap<>(CombatantSwitchPostEntryPlan.Stage.class);
        next.putAll(handlers);
        next.put(stage, handler);
        return new CombatantSwitchPostEntryDispatcher(next);
    }

    public DispatchResult dispatch(
            CombatantSwitchPostEntryPlan plan,
            DispatchContext context,
            Consumer<BattleEvent> eventSink
    ) {
        if (plan == null) throw new IllegalArgumentException("post-entry plan is required");
        if (context == null) throw new IllegalArgumentException("post-entry context is required");
        if (eventSink == null) throw new IllegalArgumentException("event sink is required");
        context.state().requireCombatant(context.replacementId());

        ArrayList<StageResult> stages = new ArrayList<>();
        ArrayList<BattleEvent> orderedEvents = new ArrayList<>();
        for (CombatantSwitchPostEntryPlan.Stage stage : plan.stages()) {
            StageHandler handler = handlers.get(stage);
            if (handler == null) {
                stages.add(new StageResult(stage, StageStatus.PENDING, List.of()));
                continue;
            }

            List<? extends BattleEvent> emitted = handler.handle(context);
            List<BattleEvent> stageEvents = emitted == null ? List.of() : List.copyOf(emitted);
            for (BattleEvent event : stageEvents) {
                if (event == null) throw new IllegalStateException("post-entry handler emitted null event");
                eventSink.accept(event);
                orderedEvents.add(event);
            }
            stages.add(new StageResult(stage, StageStatus.EXECUTED, stageEvents));
        }
        return new DispatchResult(stages, orderedEvents);
    }

    @FunctionalInterface
    public interface StageHandler {
        List<? extends BattleEvent> handle(DispatchContext context);
    }

    public record DispatchContext(
            BattleRuntimeState state,
            String replacementId,
            String phase,
            int round,
            boolean allowReplacementTurn,
            boolean allowImmediate
    ) {
        /** Compatibility boundary for callers that do not grant a replacement turn. */
        public DispatchContext(BattleRuntimeState state, String replacementId, String phase, int round) {
            this(state, replacementId, phase, round, false, false);
        }

        public DispatchContext {
            if (state == null) throw new IllegalArgumentException("battle state is required");
            if (replacementId == null || replacementId.isBlank()) {
                throw new IllegalArgumentException("replacementId is required");
            }
            if (phase == null || phase.isBlank()) throw new IllegalArgumentException("phase is required");
            if (round < 0) throw new IllegalArgumentException("round cannot be negative");
            replacementId = replacementId.strip();
            phase = phase.strip();
        }
    }

    public enum StageStatus {
        EXECUTED,
        PENDING
    }

    public record StageResult(
            CombatantSwitchPostEntryPlan.Stage stage,
            StageStatus status,
            List<BattleEvent> events
    ) {
        public StageResult {
            if (stage == null) throw new IllegalArgumentException("stage is required");
            if (status == null) throw new IllegalArgumentException("status is required");
            events = events == null ? List.of() : List.copyOf(events);
            if (status == StageStatus.PENDING && !events.isEmpty()) {
                throw new IllegalArgumentException("pending stage cannot publish events");
            }
        }
    }

    public record DispatchResult(
            List<StageResult> stages,
            List<BattleEvent> orderedEvents
    ) {
        public DispatchResult {
            stages = stages == null ? List.of() : List.copyOf(stages);
            orderedEvents = orderedEvents == null ? List.of() : List.copyOf(orderedEvents);
        }
    }
}
