package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative pre-entry state plan for a successful combatant switch.
 *
 * <p>This contract freezes the Python {@code _apply_switch()} state prefix after legality has
 * produced a {@link CombatantSwitchTransitionPlan}: recall bookkeeping is applied to the outgoing
 * combatant, stale ball-entry markers are cleared from the replacement, and new release/joined
 * markers are materialized before any send-out Feature or ability hook runs.</p>
 *
 * <p>Field presence and active-affiliation mutation remain deliberately outside this class until
 * the core owns a first-class off-field placement store. Minecraft/Cobblemon adapters must never
 * apply these mutations themselves.</p>
 */
public record CombatantSwitchEntryStatePlan(
        CombatantSwitchTransitionPlan transition,
        List<TemporaryEffectMutation> temporaryEffectMutations
) {
    public static final String RECALLED = "recalled";
    public static final String RELEASED_FROM_BALL = "released_from_ball";
    public static final String JOINED_ROUND = "joined_round";

    public CombatantSwitchEntryStatePlan {
        if (transition == null) throw new IllegalArgumentException("switch transition is required");
        temporaryEffectMutations = temporaryEffectMutations == null
                ? List.of()
                : List.copyOf(temporaryEffectMutations);
    }

    public static CombatantSwitchEntryStatePlan resolve(
            BattleRuntimeState state,
            CombatantSwitchTransitionPlan transition
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (transition == null) throw new IllegalArgumentException("switch transition is required");
        state.requireCombatant(transition.outgoingId());
        state.requireCombatant(transition.replacementId());

        int round = state.currentRound();
        ArrayList<TemporaryEffectMutation> mutations = new ArrayList<>();
        mutations.add(TemporaryEffectMutation.add(
                transition.outgoingId(), RECALLED, Map.of("round", round)
        ));
        mutations.add(TemporaryEffectMutation.removeAll(
                transition.replacementId(), RECALLED
        ));
        mutations.add(TemporaryEffectMutation.removeAll(
                transition.replacementId(), RELEASED_FROM_BALL
        ));
        mutations.add(TemporaryEffectMutation.add(
                transition.replacementId(), RELEASED_FROM_BALL, Map.of("round", round)
        ));
        if (round > 0) {
            mutations.add(TemporaryEffectMutation.add(
                    transition.replacementId(), JOINED_ROUND, Map.of("round", round)
            ));
        }
        return new CombatantSwitchEntryStatePlan(transition, mutations);
    }

    /**
     * Applies only the temporary-effect prefix frozen by this plan.
     *
     * <p>The future switch executor will compose this with field-presence mutation and then dispatch
     * send-out/COMBATANT_ENTRY hooks. Keeping this method narrow prevents callers from mistaking the
     * current slice for complete switch execution.</p>
     */
    void applyTemporaryEffects(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        for (TemporaryEffectMutation mutation : temporaryEffectMutations) {
            TemporaryEffectStore store = state.requireCombatant(mutation.combatantId()).temporaryEffects();
            if (mutation.operation() == TemporaryEffectOperation.REMOVE_ALL) {
                store.removeAll(mutation.effectName());
            } else {
                store.add(mutation.effectName(), mutation.payload());
            }
        }
    }

    public enum TemporaryEffectOperation {
        REMOVE_ALL,
        ADD
    }

    public record TemporaryEffectMutation(
            String combatantId,
            TemporaryEffectOperation operation,
            String effectName,
            Map<String, Object> payload
    ) {
        public TemporaryEffectMutation {
            if (combatantId == null || combatantId.isBlank()) {
                throw new IllegalArgumentException("combatantId is required");
            }
            if (operation == null) throw new IllegalArgumentException("operation is required");
            if (effectName == null || effectName.isBlank()) {
                throw new IllegalArgumentException("effectName is required");
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            if (payload != null) copy.putAll(payload);
            payload = Map.copyOf(copy);
        }

        static TemporaryEffectMutation removeAll(String combatantId, String effectName) {
            return new TemporaryEffectMutation(
                    combatantId, TemporaryEffectOperation.REMOVE_ALL, effectName, Map.of()
            );
        }

        static TemporaryEffectMutation add(String combatantId, String effectName, Map<String, ?> payload) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            if (payload != null) copy.putAll(payload);
            return new TemporaryEffectMutation(
                    combatantId, TemporaryEffectOperation.ADD, effectName, copy
            );
        }
    }
}
