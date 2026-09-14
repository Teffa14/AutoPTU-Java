package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.rules.Targeting;

import java.util.List;
import java.util.Optional;

/**
 * Pure server-authoritative matcher from resolved battle events to declarative reaction triggers.
 *
 * <p>This layer only discovers a trigger. It does not decide ownership or eligibility, commit
 * reaction usage, consume action resources, select targets, roll RNG, or execute the reaction.</p>
 */
public final class RuntimeReactionTriggerMatcher {
    private final RuntimeReactionTriggerRegistry registry;

    public RuntimeReactionTriggerMatcher(RuntimeReactionTriggerRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("reaction trigger registry is required");
        }
        this.registry = registry;
    }

    public static RuntimeReactionTriggerMatcher builtin() {
        return new RuntimeReactionTriggerMatcher(RuntimeReactionTriggerRegistry.builtin());
    }

    /**
     * Matches a completed authoritative Shift against reaction trigger definitions.
     *
     * <p>The pinned Python oracle says Attack of Opportunity triggers when a foe "Shifts out of a
     * Square adjacent to you". The relevant adjacency is therefore the Shift origin. The
     * destination is deliberately not required to end outside adjacency.</p>
     */
    public Optional<TriggerMatch> matchShift(
            String reactionKey,
            String reactorId,
            BattleRuntimeState battleState,
            ShiftResolvedEvent event
    ) {
        if (battleState == null) {
            throw new IllegalArgumentException("battleState is required");
        }
        if (event == null) {
            throw new IllegalArgumentException("shift event is required");
        }
        RuntimeCombatantState reactor = battleState.requireCombatant(reactorId);
        RuntimeCombatantState triggeringActor = battleState.requireCombatant(event.actorId());

        List<RuntimeReactionTriggerRegistry.TriggerDefinition> definitions =
                registry.resolve(reactionKey).orElse(List.of());
        RuntimeReactionTriggerRegistry.TriggerDefinition shiftDefinition = definitions.stream()
                .filter(definition -> definition.kind()
                        == RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY)
                .findFirst()
                .orElse(null);
        if (shiftDefinition == null) {
            return Optional.empty();
        }

        if (battleState.teamId(reactorId).equals(battleState.teamId(event.actorId()))) {
            return Optional.empty();
        }

        String reactorSize = battleState.geometry(reactorId).sizeLabel();
        String triggeringActorSize = battleState.geometry(event.actorId()).sizeLabel();
        int originDistance = Targeting.footprintDistance(
                reactor.position(),
                reactorSize,
                event.origin(),
                triggeringActorSize
        );
        if (originDistance != 1) {
            return Optional.empty();
        }

        return Optional.of(new TriggerMatch(
                reactorId,
                triggeringActor.combatantId(),
                shiftDefinition
        ));
    }

    public record TriggerMatch(
            String reactorId,
            String triggeringActorId,
            RuntimeReactionTriggerRegistry.TriggerDefinition trigger
    ) {
        public TriggerMatch {
            if (reactorId == null || reactorId.isBlank()) {
                throw new IllegalArgumentException("reactorId is required");
            }
            if (triggeringActorId == null || triggeringActorId.isBlank()) {
                throw new IllegalArgumentException("triggeringActorId is required");
            }
            if (trigger == null) {
                throw new IllegalArgumentException("trigger definition is required");
            }
        }
    }
}
