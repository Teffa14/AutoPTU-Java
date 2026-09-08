package io.autoptu.core.runtime;

import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects the generic server-owned battle snapshot into Arena Trap's language-neutral
 * targeting contract.
 *
 * <p>This class intentionally contains no Arena Trap eligibility rules. Types and abilities
 * come from {@link RuntimeCombatantState}, affiliations and activity come from
 * {@link BattleRuntimeState}, PTU capabilities come from {@link CombatantRuleContentRegistry},
 * movement magnitudes come from {@link MovementProfile}, and distance uses the same Chebyshev
 * grid contract as core targeting. Minecraft/Cobblemon adapters never provide a pre-resolved
 * immunity or target list.</p>
 */
public final class ArenaTrapRuntimeCandidateProjection {
    private ArenaTrapRuntimeCandidateProjection() {}

    public static List<ArenaTrapTargetingContract.Candidate> candidatesForHolder(
            BattleRuntimeState state,
            CombatantRuleContentRegistry ruleContent,
            String holderId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (ruleContent == null) throw new IllegalArgumentException("combatant rule content is required");
        RuntimeCombatantState holder = state.requireCombatant(holderId);

        ArrayList<ArenaTrapTargetingContract.Candidate> candidates = new ArrayList<>();
        for (String actorId : state.combatantIds()) {
            RuntimeCombatantState actor = state.requireCombatant(actorId);
            MovementProfile movement = actor.movementProfile();
            candidates.add(new ArenaTrapTargetingContract.Candidate(
                    actorId,
                    state.teamId(actorId),
                    state.isActive(actorId),
                    actor.hp() <= 0,
                    Targeting.chebyshevDistance(holder.position(), actor.position()),
                    actor.types(),
                    actor.abilities(),
                    ruleContent.require(actorId).capabilities(),
                    movement.skySpeed(),
                    movement.burrowSpeed()
            ));
        }
        return List.copyOf(candidates);
    }
}
