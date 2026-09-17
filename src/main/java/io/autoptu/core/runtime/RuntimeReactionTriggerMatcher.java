package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.rules.Targeting;

import java.util.List;
import java.util.Optional;

/** Pure server-authoritative matcher from resolved battle events to declarative reaction triggers. */
public final class RuntimeReactionTriggerMatcher {
    private final RuntimeReactionTriggerRegistry registry;

    public RuntimeReactionTriggerMatcher(RuntimeReactionTriggerRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("reaction trigger registry is required");
        this.registry = registry;
    }

    public static RuntimeReactionTriggerMatcher builtin() {
        return new RuntimeReactionTriggerMatcher(RuntimeReactionTriggerRegistry.builtin());
    }

    public Optional<TriggerMatch> matchShift(
            String reactionKey, String reactorId, BattleRuntimeState battleState, ShiftResolvedEvent event
    ) {
        if (battleState == null) throw new IllegalArgumentException("battleState is required");
        if (event == null) throw new IllegalArgumentException("shift event is required");
        RuntimeCombatantState reactor = battleState.requireCombatant(reactorId);
        RuntimeCombatantState triggeringActor = battleState.requireCombatant(event.actorId());
        RuntimeReactionTriggerRegistry.TriggerDefinition shiftDefinition = definition(
                reactionKey, RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY, null
        ).orElse(null);
        if (shiftDefinition == null) return Optional.empty();
        if (battleState.teamId(reactorId).equals(battleState.teamId(event.actorId()))) return Optional.empty();
        int originDistance = Targeting.footprintDistance(
                reactor.position(), battleState.geometry(reactorId).sizeLabel(),
                event.origin(), battleState.geometry(event.actorId()).sizeLabel()
        );
        if (originDistance != 1) return Optional.empty();
        return Optional.of(new TriggerMatch(reactorId, triggeringActor.combatantId(), shiftDefinition));
    }

    /** Matches a completed adjacent action by resolving its trigger family from registry data. */
    public Optional<TriggerMatch> matchAdjacentOccurrence(
            String reactionKey,
            String reactorId,
            BattleRuntimeState battleState,
            ActionResolvedEvent event
    ) {
        if (event == null) throw new IllegalArgumentException("action event is required");
        RuntimeReactionTriggerRegistry.TriggerDefinition trigger = registry.resolveOccurrence(
                reactionKey, event.actionKey(), event.qualifier()
        ).orElse(null);
        if (trigger == null || trigger.kind() == RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY) {
            return Optional.empty();
        }
        if (trigger.kind() == RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET
                && hasTargetAdjacentToActor(battleState, event)) {
            return Optional.empty();
        }
        return matchAdjacentAction(
                reactionKey, reactorId, battleState, event.actorId(), trigger.kind(), event.qualifier()
        );
    }

    private boolean hasTargetAdjacentToActor(BattleRuntimeState battleState, ActionResolvedEvent event) {
        RuntimeCombatantState actor = battleState.requireCombatant(event.actorId());
        for (String targetId : event.targetIds()) {
            RuntimeCombatantState target = battleState.requireCombatant(targetId);
            int distance = Targeting.footprintDistance(
                    actor.position(), battleState.geometry(event.actorId()).sizeLabel(),
                    target.position(), battleState.geometry(targetId).sizeLabel()
            );
            if (distance == 1) return true;
        }
        return false;
    }

    public Optional<TriggerMatch> matchAdjacentAction(
            String reactionKey,
            String reactorId,
            BattleRuntimeState battleState,
            String triggeringActorId,
            RuntimeReactionTriggerRegistry.TriggerKind triggerKind,
            String qualifier
    ) {
        if (battleState == null) throw new IllegalArgumentException("battleState is required");
        if (triggeringActorId == null || triggeringActorId.isBlank()) {
            throw new IllegalArgumentException("triggeringActorId is required");
        }
        if (triggerKind == null) throw new IllegalArgumentException("triggerKind is required");
        RuntimeCombatantState reactor = battleState.requireCombatant(reactorId);
        RuntimeCombatantState triggeringActor = battleState.requireCombatant(triggeringActorId);
        RuntimeReactionTriggerRegistry.TriggerDefinition triggerDefinition = definition(
                reactionKey, triggerKind, qualifier
        ).orElse(null);
        if (triggerDefinition == null) return Optional.empty();
        if (battleState.teamId(reactorId).equals(battleState.teamId(triggeringActorId))) return Optional.empty();
        int distance = Targeting.footprintDistance(
                reactor.position(), battleState.geometry(reactorId).sizeLabel(),
                triggeringActor.position(), battleState.geometry(triggeringActorId).sizeLabel()
        );
        if (distance != 1) return Optional.empty();
        return Optional.of(new TriggerMatch(reactorId, triggeringActorId, triggerDefinition));
    }

    private Optional<RuntimeReactionTriggerRegistry.TriggerDefinition> definition(
            String reactionKey, RuntimeReactionTriggerRegistry.TriggerKind kind, String qualifier
    ) {
        String normalizedQualifier = qualifier == null || qualifier.isBlank()
                ? ""
                : MoveReactionOwnershipSource.normalizeKey(qualifier);
        List<RuntimeReactionTriggerRegistry.TriggerDefinition> definitions = registry.resolve(reactionKey).orElse(List.of());
        return definitions.stream()
                .filter(candidate -> candidate.kind() == kind)
                .filter(candidate -> candidate.qualifiers().isEmpty()
                        || candidate.qualifiers().contains(normalizedQualifier))
                .findFirst();
    }

    public record TriggerMatch(
            String reactorId,
            String triggeringActorId,
            RuntimeReactionTriggerRegistry.TriggerDefinition trigger
    ) {
        public TriggerMatch {
            if (reactorId == null || reactorId.isBlank()) throw new IllegalArgumentException("reactorId is required");
            if (triggeringActorId == null || triggeringActorId.isBlank()) {
                throw new IllegalArgumentException("triggeringActorId is required");
            }
            if (trigger == null) throw new IllegalArgumentException("trigger definition is required");
        }
    }
}
