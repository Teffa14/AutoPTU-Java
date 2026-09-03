package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Server-authoritative executor for ordered consequences produced by movement landing hooks.
 *
 * <p>The hook registry remains pure. This executor is the mutation boundary: status applications
 * pass through the shared status-prevention pipeline, semantic events are emitted in contract order,
 * and hazards are consumed only after their observable trigger event.</p>
 */
final class MovementLandingConsequenceExecutor {
    private MovementLandingConsequenceExecutor() {}

    enum SemanticEventKind {
        TRAP_BLOCK,
        TRAP_TRIGGER
    }

    record SemanticEvent(
            SemanticEventKind kind,
            String actorId,
            String trapKey,
            String trapName,
            String sourceId,
            String description,
            int targetHp,
            GridCoord coordinate,
            Set<String> terrains
    ) {
        SemanticEvent {
            Objects.requireNonNull(kind, "kind");
            actorId = safe(actorId);
            trapKey = safe(trapKey);
            trapName = safe(trapName);
            sourceId = safe(sourceId);
            description = safe(description);
            terrains = Set.copyOf(terrains == null ? Set.of() : terrains);
            if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            if (trapKey.isBlank()) throw new IllegalArgumentException("trapKey is required");
            if (targetHp < 0) throw new IllegalArgumentException("targetHp cannot be negative");
            if (kind == SemanticEventKind.TRAP_TRIGGER && coordinate == null) {
                throw new IllegalArgumentException("trap trigger coordinate is required");
            }
        }
    }

    record ExecutionResult(
            List<StatusApplicationResult> statusApplications,
            List<BattleEvent> statusHookEvents,
            List<SemanticEvent> semanticEvents,
            List<String> consumedTrapKeys
    ) {
        ExecutionResult {
            statusApplications = List.copyOf(statusApplications == null ? List.of() : statusApplications);
            statusHookEvents = List.copyOf(statusHookEvents == null ? List.of() : statusHookEvents);
            semanticEvents = List.copyOf(semanticEvents == null ? List.of() : semanticEvents);
            consumedTrapKeys = List.copyOf(consumedTrapKeys == null ? List.of() : consumedTrapKeys);
        }
    }

    static ExecutionResult execute(
            BattleRuntimeState state,
            StatusApplicationHookRegistry statusHooks,
            List<MovementLandingHookRegistry.ResolvedHook> resolvedHooks,
            Consumer<SemanticEvent> semanticEventSink
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(statusHooks, "statusHooks");
        Objects.requireNonNull(semanticEventSink, "semanticEventSink");

        ArrayList<StatusApplicationResult> statusApplications = new ArrayList<>();
        ArrayList<BattleEvent> statusHookEvents = new ArrayList<>();
        ArrayList<SemanticEvent> semanticEvents = new ArrayList<>();
        ArrayList<String> consumedTrapKeys = new ArrayList<>();

        for (MovementLandingHookRegistry.ResolvedHook resolvedHook
                : resolvedHooks == null ? List.<MovementLandingHookRegistry.ResolvedHook>of() : resolvedHooks) {
            if (resolvedHook == null) continue;
            if (resolvedHook.family() != MovementLandingHookRegistry.HookFamily.TILE_TRAP) {
                throw new IllegalStateException("unsupported movement landing hook family: " + resolvedHook.family());
            }
            MovementLandingHookRegistry.TileTrapConsequence consequence =
                    (MovementLandingHookRegistry.TileTrapConsequence) resolvedHook.consequence();
            TileEntryTrapResolution.Result trapResult = consequence.resolution();

            for (TileEntryTrapResolution.Block block : trapResult.blocks()) {
                SemanticEvent event = new SemanticEvent(
                        SemanticEventKind.TRAP_BLOCK,
                        block.actorId(),
                        block.trapKey(),
                        "",
                        "",
                        block.description(),
                        block.targetHp(),
                        null,
                        Set.of()
                );
                semanticEventSink.accept(event);
                semanticEvents.add(event);
            }

            for (TileEntryTrapResolution.Trigger trigger : trapResult.triggers()) {
                for (TileEntryTrapResolution.EffectStep effectStep : trigger.effectOrder()) {
                    switch (effectStep) {
                        case APPLY_STATUS -> {
                            TileEntryTrapResolution.StatusApplication instruction = trigger.statusApplication();
                            StatusEntry statusEntry = new StatusEntry(
                                    instruction.status(),
                                    statusPayload(instruction)
                            );
                            StatusApplicationResult application = StatusApplicationResolution.apply(
                                    state,
                                    statusHooks,
                                    statusSourceActorId(state, trigger.sourceId()),
                                    instruction.targetId(),
                                    statusEntry,
                                    "terrain",
                                    trigger.trapName(),
                                    ""
                            );
                            statusApplications.add(application);
                            statusHookEvents.addAll(application.events());
                        }
                        case EMIT_TRAP_EVENT -> {
                            SemanticEvent event = new SemanticEvent(
                                    SemanticEventKind.TRAP_TRIGGER,
                                    trigger.actorId(),
                                    trigger.trapKey(),
                                    trigger.trapName(),
                                    trigger.sourceId(),
                                    trigger.description(),
                                    trigger.targetHp(),
                                    trigger.coordinate(),
                                    trigger.terrains()
                            );
                            semanticEventSink.accept(event);
                            semanticEvents.add(event);
                        }
                        case CONSUME_TRAP -> {
                            if (state.consumeTileTrapFromRuntime(trigger.coordinate(), trigger.trapKey())) {
                                consumedTrapKeys.add(trigger.trapKey());
                            }
                        }
                    }
                }
            }
        }

        return new ExecutionResult(statusApplications, statusHookEvents, semanticEvents, consumedTrapKeys);
    }

    private static String statusSourceActorId(BattleRuntimeState state, String sourceId) {
        String candidate = safe(sourceId);
        if (candidate.isBlank()) return "";
        return state.combatants().containsKey(candidate) ? candidate : "";
    }

    private static Map<String, Object> statusPayload(TileEntryTrapResolution.StatusApplication instruction) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("remaining", instruction.remaining());
        payload.put("move_name", safe(instruction.moveName()));
        payload.put("move_type", safe(instruction.moveType()));
        payload.put("move_category", safe(instruction.moveCategory()));
        payload.put("effect", safe(instruction.effect()));
        payload.put("description", safe(instruction.description()));
        return payload;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
