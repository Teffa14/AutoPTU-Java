package io.autoptu.core.event;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Semantic snapshot emitted after Python-compatible round-start state mutation and
 * initiative rebuild, but before round-start Trainer Features and abilities resolve.
 */
public record RoundStartedEvent(
        int round,
        List<InitiativeSnapshot> initiative,
        String weather,
        List<CombatantSnapshot> initialStates
) implements BattleEvent {
    public RoundStartedEvent {
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        initiative = initiative == null ? List.of() : List.copyOf(initiative);
        weather = weather == null ? "" : weather;
        initialStates = initialStates == null ? List.of() : List.copyOf(initialStates);
    }

    public static RoundStartedEvent fromState(BattleRuntimeState state, int round) {
        Objects.requireNonNull(state, "state");
        if (!state.initiativeProgress().hasDetailedOrder()) {
            throw new IllegalStateException("round_start requires a detailed authoritative initiative snapshot");
        }

        List<InitiativeSnapshot> initiative = state.initiativeProgress().orderedEntries().stream()
                .map(InitiativeSnapshot::fromEntry)
                .toList();

        ArrayList<CombatantSnapshot> combatants = new ArrayList<>();
        for (String actorId : state.combatantIds()) {
            RuntimeCombatantState actor = state.requireCombatant(actorId);
            combatants.add(new CombatantSnapshot(
                    actorId,
                    actor.hp(),
                    actor.maxHp(),
                    List.copyOf(state.statuses(actorId)),
                    actor.abilities(),
                    state.isActive(actorId)
            ));
        }

        return new RoundStartedEvent(
                round,
                initiative,
                state.environment().weather(),
                combatants
        );
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.ROUND_START;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(),
                Integer.toString(round),
                weather,
                initiative.toString(),
                initialStates.toString()
        );
    }

    /** Exact language-neutral projection of Python InitiativeEntry.to_dict(). */
    public record InitiativeSnapshot(
            String actor,
            String controller,
            int speed,
            int trainerModifier,
            int roll,
            int total
    ) {
        public InitiativeSnapshot {
            actor = actor == null ? "" : actor.strip();
            controller = controller == null ? "" : controller.strip();
            if (actor.isBlank()) throw new IllegalArgumentException("initiative actor is required");
        }

        static InitiativeSnapshot fromEntry(InitiativeEntry entry) {
            return new InitiativeSnapshot(
                    entry.actorId(),
                    entry.trainerId(),
                    entry.speed(),
                    entry.trainerModifier(),
                    entry.roll(),
                    entry.total()
            );
        }
    }

    /** Exact language-neutral projection of Python round_start initial_states entries. */
    public record CombatantSnapshot(
            String actor,
            int hp,
            int maxHp,
            List<String> statuses,
            List<String> abilities,
            boolean active
    ) {
        public CombatantSnapshot {
            actor = actor == null ? "" : actor.strip();
            if (actor.isBlank()) throw new IllegalArgumentException("combatant actor is required");
            if (maxHp <= 0) throw new IllegalArgumentException("maxHp must be positive");
            if (hp < 0 || hp > maxHp) throw new IllegalArgumentException("hp must be between 0 and maxHp");
            statuses = statuses == null ? List.of() : List.copyOf(statuses);
            abilities = abilities == null ? List.of() : List.copyOf(abilities);
        }
    }
}
