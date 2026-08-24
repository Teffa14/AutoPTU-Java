package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.AppliedActionResult;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.ReactionMovementApplication;
import io.autoptu.core.runtime.ReactionPushApplication;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Built-in PRE-damage reactions backed by canonical battle state. */
public final class BuiltinPreDamageReactionHooks {
    private BuiltinPreDamageReactionHooks() {
    }

    public static PreDamageReactionHookRegistry registry() {
        return PreDamageReactionHookRegistry.builder()
                .register("perception-area-escape", HookSource.ABILITY, 90,
                        BuiltinPreDamageReactionHooks::perception)
                .register("perception-errata-area-escape", HookSource.ABILITY, 95,
                        BuiltinPreDamageReactionHooks::perceptionErrata)
                .register("parry-melee-avoid", HookSource.ABILITY, 97,
                        BuiltinPreDamageReactionHooks::parry)
                .register("telepathy-area-escape", HookSource.ABILITY, 100,
                        BuiltinPreDamageReactionHooks::telepathy)
                .register("sway-melee-redirect", HookSource.ABILITY, 110,
                        BuiltinPreDamageReactionHooks::sway)
                .build();
    }

    private static PreDamageReactionResult perception(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Perception")) return current;
        if (!defender.temporaryEffects().has("perception_ready")) return current;

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Perception", true))) {
            return current;
        }
        defender.temporaryEffects().removeFirst("perception_ready");

        GridCoord origin = defender.position();
        if (context.threatenedTiles().isEmpty() || !context.threatenedTiles().contains(origin)) {
            return current;
        }

        for (TemporaryEffectEntry entry : defender.temporaryEffects().getAll("perception_used")) {
            Object expiresRound = entry.payload().get("expires_round");
            if (expiresRound != null && state.currentRound() > intLike(expiresRound)) {
                defender.temporaryEffects().removeFirst("perception_used");
                continue;
            }
            return current;
        }

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Perception [Errata]", true))) {
            return current;
        }

        AppliedActionResult movement = ReactionMovementApplication.escapeThreatenedArea(
                state,
                context.defenderId(),
                context.threatenedTiles(),
                null
        );
        if (movement.events().isEmpty() || defender.position().equals(origin)) {
            return current;
        }

        defender.temporaryEffects().add(
                "perception_used",
                Map.of("expires_round", state.currentRound() + 1)
        );
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Perception",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "shift",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    private static PreDamageReactionResult perceptionErrata(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesExact(defender.abilities(), "Perception [Errata]")) return current;
        if (context.attackerId().equals(context.defenderId())) return current;
        if (!state.teamId(context.attackerId()).equals(state.teamId(context.defenderId()))) return current;

        GridCoord origin = defender.position();
        if (context.threatenedTiles().isEmpty() || !context.threatenedTiles().contains(origin)) {
            return current;
        }

        AppliedActionResult movement = ReactionMovementApplication.escapeThreatenedArea(
                state,
                context.defenderId(),
                context.threatenedTiles(),
                1
        );
        if (movement.events().isEmpty() || defender.position().equals(origin)) {
            return current;
        }

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Perception [Errata]",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "disengage",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    /** Python Parry parity for melee PRE-damage avoidance. */
    private static PreDamageReactionResult parry(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Parry")) return current;
        if (!defender.temporaryEffects().has("parry_ready")) return current;

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Parry", true))) {
            return current;
        }
        defender.temporaryEffects().removeFirst("parry_ready");

        if (!context.effectiveTargetKind().equals("melee")) return current;

        for (TemporaryEffectEntry entry : defender.temporaryEffects().getAll("parry_used")) {
            Object round = entry.payload().get("round");
            if (round != null && intLike(round) == state.currentRound()) return current;
            defender.temporaryEffects().removeFirst("parry_used");
        }
        defender.temporaryEffects().add("parry_used", Map.of("round", state.currentRound()));

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Parry",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "avoid",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    private static PreDamageReactionResult telepathy(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Telepathy")) return current;
        if (!state.teamId(context.attackerId()).equals(state.teamId(context.defenderId()))) return current;

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Telepathy", true))) {
            return current;
        }

        GridCoord origin = defender.position();
        if (context.threatenedTiles().isEmpty() || !context.threatenedTiles().contains(origin)) {
            return current;
        }

        AppliedActionResult movement = ReactionMovementApplication.escapeThreatenedArea(
                state,
                context.defenderId(),
                context.threatenedTiles(),
                null
        );
        if (movement.events().isEmpty() || defender.position().equals(origin)) {
            return current;
        }

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Telepathy",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "shift",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    /** Python Sway parity for a once-per-scene melee redirect plus adjacent push. */
    private static PreDamageReactionResult sway(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Sway")) return current;
        if (attacker.temporaryEffects().has("sway_redirect")) return current;
        if (!context.effectiveTargetKind().equals("melee")) return current;

        TemporaryEffectEntry usedEntry = defender.temporaryEffects().getAll("sway_used").stream()
                .findFirst()
                .orElse(null);
        int usedCount = usedEntry == null ? 0 : intLike(usedEntry.payload().getOrDefault("count", 0));
        if (usedCount >= 1) return current;
        if (!defender.actionBudget().hasActionAvailable(ActionType.STANDARD)) return current;
        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Sway", true))) {
            return current;
        }

        defender.actionBudget().markAction(ActionType.STANDARD, "Sway");
        if (usedEntry != null) defender.temporaryEffects().removeFirst("sway_used");
        defender.temporaryEffects().add("sway_used", Map.of("count", usedCount + 1));
        attacker.temporaryEffects().add("sway_redirect", Map.of("expires_round", state.currentRound()));

        ArrayList<BattleEvent> events = new ArrayList<>();
        events.add(new RuleEffectEvent(
                "ability",
                "Sway",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "redirect",
                0.0,
                defender.hp()
        ));
        try {
            PreDamageFollowUpMoveResult redirected = context.resolveFollowUpMove(
                    PreDamageFollowUpMoveRequest.originalMove(
                            context.attackerId(),
                            context.attackerId(),
                            attacker.position()
                    )
            );
            events.addAll(redirected.events());
        } catch (RuntimeException ignored) {
            // Python swallows nested move-resolution errors and continues into push/cancel logic.
        } finally {
            attacker.temporaryEffects().removeAll("sway_redirect");
        }

        ReactionPushApplication.pushToFirstOpenAdjacent(
                state,
                context.defenderId(),
                context.attackerId()
        ).ifPresent(destination -> events.add(new RuleEffectEvent(
                "ability",
                "Sway",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "push",
                0.0,
                attacker.hp()
        )));
        return current.cancelHit(events);
    }

    private static int intLike(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value).strip());
    }
}
