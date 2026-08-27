package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds Intercept candidate discovery input exclusively from server-owned battle/content state. */
public final class RuntimeInterceptCandidateDiscoveryFactory {
    private RuntimeInterceptCandidateDiscoveryFactory() {}

    public static InterceptCandidateDiscoveryResolution.Input build(
            BattleRuntimeState state,
            String attackerId,
            String targetId,
            String interceptKind,
            Map<String, CombatantRuleContent> contentByCombatant
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState target = state.requireCombatant(targetId);
        Map<String, CombatantRuleContent> content = contentByCombatant == null ? Map.of() : Map.copyOf(contentByCombatant);
        CombatantRuleContent targetContent = content.getOrDefault(targetId, CombatantRuleContent.empty());

        List<InterceptCandidateDiscoveryResolution.NoInterceptEntry> noIntercept = new ArrayList<>();
        for (TemporaryEffectEntry entry : attacker.temporaryEffects().getAll("no_intercept")) {
            noIntercept.add(new InterceptCandidateDiscoveryResolution.NoInterceptEntry(integer(entry.payload().get("expires_round"))));
        }

        List<InterceptCandidateDiscoveryResolution.CombatantInput> combatants = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            CombatantRuleContent ruleContent = content.getOrDefault(combatantId, CombatantRuleContent.empty());
            boolean sameController = !ruleContent.controllerId().isBlank()
                    && ruleContent.controllerId().equals(targetContent.controllerId());
            boolean coaching = combatant.temporaryEffects().has("coaching_intercept");
            InterceptEligibilityResolution.Result eligibility = InterceptEligibilityResolution.resolve(
                    new InterceptEligibilityResolution.Input(
                            ruleContent.loyalty(),
                            sameController,
                            coaching,
                            combatant.hp() <= 0,
                            state.hasStatus(combatantId, "Paralyzed"),
                            state.hasStatus(combatantId, "Stuck"),
                            state.hasStatus(combatantId, "Tripped"),
                            state.hasStatus(combatantId, "Sleep"),
                            state.hasStatus(combatantId, "Flinch"),
                            state.hasStatus(combatantId, "Trapped")
                    )
            );

            List<InterceptCandidateDiscoveryResolution.ReadyEntry> ready = new ArrayList<>();
            for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll("intercept_ready")) {
                ready.add(new InterceptCandidateDiscoveryResolution.ReadyEntry(
                        string(entry.payload().get("ally")),
                        string(entry.payload().get("intercept_kind")),
                        string(entry.payload().get("source"))
                ));
            }

            List<InterceptCandidateDiscoveryResolution.SentinelEntry> sentinel = new ArrayList<>();
            for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll("sentinel_stance")) {
                sentinel.add(new InterceptCandidateDiscoveryResolution.SentinelEntry(integer(entry.payload().get("expires_round"))));
            }

            combatants.add(new InterceptCandidateDiscoveryResolution.CombatantInput(
                    combatantId,
                    combatant.hp() > 0,
                    state.teamId(combatantId).equals(state.teamId(targetId)),
                    combatant.hasAbilityExact("Weaponize"),
                    ruleContent.hasCapability("Living Weapon"),
                    !ruleContent.controllerId().isBlank() && ruleContent.controllerId().equals(targetId),
                    eligibility.allowed(),
                    eligibility.blockReason() != InterceptEligibilityResolution.BlockReason.LOYALTY,
                    ready,
                    sentinel,
                    combatant.actionBudget().hasActionAvailable(ActionType.SHIFT),
                    combatant.actionBudget().extraCount(ActionType.SHIFT)
            ));
        }

        return new InterceptCandidateDiscoveryResolution.Input(
                targetId,
                interceptKind,
                state.currentRound(),
                noIntercept,
                combatants
        );
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Integer.parseInt(text.strip()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
