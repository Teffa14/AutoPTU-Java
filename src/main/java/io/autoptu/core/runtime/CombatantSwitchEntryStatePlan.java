package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative pre-entry state plan for a successful combatant switch.
 *
 * <p>This contract freezes the Python {@code _apply_switch()} temporary-effect prefix after legality
 * has produced a {@link CombatantSwitchTransitionPlan}. The pinned oracle preserves any pre-existing
 * {@code recalled}/{@code released_from_ball} markers and appends a new release marker plus a
 * round-scoped {@code joined_round} marker before send-out Feature or ability hooks run.</p>
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
                transition.replacementId(), RELEASED_FROM_BALL, Map.of("round", round)
        ));
        if (round > 0) {
            mutations.add(TemporaryEffectMutation.add(
                    transition.replacementId(), JOINED_ROUND, Map.of("round", round)
            ));
        }
        return new CombatantSwitchEntryStatePlan(transition, mutations);
    }

    /** Applies only the temporary-effect prefix frozen by this plan. */
    void applyTemporaryEffects(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        for (TemporaryEffectMutation mutation : temporaryEffectMutations) {
            state.requireCombatant(mutation.combatantId())
                    .temporaryEffects()
                    .add(mutation.effectName(), mutation.payload());
        }
    }

    public record TemporaryEffectMutation(
            String combatantId,
            String effectName,
            Map<String, Object> payload
    ) {
        public TemporaryEffectMutation {
            if (combatantId == null || combatantId.isBlank()) {
                throw new IllegalArgumentException("combatantId is required");
            }
            if (effectName == null || effectName.isBlank()) {
                throw new IllegalArgumentException("effectName is required");
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            if (payload != null) copy.putAll(payload);
            payload = Map.copyOf(copy);
        }

        static TemporaryEffectMutation add(String combatantId, String effectName, Map<String, ?> payload) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            if (payload != null) copy.putAll(payload);
            return new TemporaryEffectMutation(combatantId, effectName, copy);
        }
    }
}
