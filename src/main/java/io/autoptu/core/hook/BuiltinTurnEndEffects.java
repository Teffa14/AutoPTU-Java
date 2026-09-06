package io.autoptu.core.hook;

import io.autoptu.core.event.BorrowMoveEndedEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.runtime.RuntimeCanonicalMoveSetRemoval;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.RuntimeOrdinaryDamageIngress;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TickValueResolution;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Built-in TURN_END rules frozen from the pinned Python oracle. */
public final class BuiltinTurnEndEffects {
    private static final SelectiveTemporaryEffectCleanupLifecycleHook ADAPTIVE_GEOGRAPHY_CLEANUP =
            new SelectiveTemporaryEffectCleanupLifecycleHook(
                    SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR,
                    List.of(new SelectiveTemporaryEffectCleanupLifecycleHook.Selector(
                            "terrain_alias",
                            Map.of("feature", "Adaptive Geography")
                    ))
            );

    private BuiltinTurnEndEffects() {}

    public static TurnEndEffectRegistry registry() {
        return TurnEndEffectRegistry.builder()
                .register(
                        "adaptive-geography-terrain-alias-cleanup",
                        TurnEndEffectRegistry.Scope.ACTOR,
                        10,
                        (context, combatantId) -> ADAPTIVE_GEOGRAPHY_CLEANUP.apply(context)
                )
                .register(
                        "psionic-sponge-borrowed-move-cleanup",
                        TurnEndEffectRegistry.Scope.ACTOR,
                        20,
                        BuiltinTurnEndEffects::clearPsionicSpongeMoves
                )
                .register(
                        "psionic-overload-telekinesis-tick",
                        TurnEndEffectRegistry.Scope.ALL_COMBATANTS,
                        30,
                        BuiltinTurnEndEffects::applyPsionicOverloadTick
                )
                .build();
    }

    private static LifecycleHookResult clearPsionicSpongeMoves(
            LifecycleHookContext context,
            String combatantId
    ) {
        RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
        List<TemporaryEffectEntry> borrowedEntries = combatant.temporaryEffects().getAll("psionic_sponge_move");
        if (borrowedEntries.isEmpty()) {
            return LifecycleHookResult.empty();
        }

        TreeSet<String> borrowedMoveNames = new TreeSet<>();
        for (TemporaryEffectEntry entry : borrowedEntries) {
            Object rawName = entry.payload().get("name");
            String normalized = rawName == null ? "" : String.valueOf(rawName).strip().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                borrowedMoveNames.add(normalized);
            }
        }

        if (!borrowedMoveNames.isEmpty()) {
            RuntimeCanonicalMoveSetRemoval.apply(context.state(), combatantId, borrowedMoveNames);
        }
        combatant.temporaryEffects().removeAllEntries("psionic_sponge_move");

        if (borrowedMoveNames.isEmpty()) {
            return LifecycleHookResult.empty();
        }
        return LifecycleHookResult.events(List.of(
                new BorrowMoveEndedEvent(combatantId, List.copyOf(borrowedMoveNames))
        ));
    }

    private static LifecycleHookResult applyPsionicOverloadTick(
            LifecycleHookContext context,
            String combatantId
    ) {
        RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
        if (combatant.hp() <= 0) {
            return LifecycleHookResult.empty();
        }

        List<TemporaryEffectEntry> entries = combatant.temporaryEffects()
                .getAll("psionic_overload_telekinesis");
        if (entries.isEmpty()) {
            return LifecycleHookResult.empty();
        }

        if (!context.state().hasStatus(combatantId, "Lifted")) {
            combatant.temporaryEffects().removeAllEntries("psionic_overload_telekinesis");
            return LifecycleHookResult.empty();
        }

        String sourceId = text(entries.get(0).payload().get("source_id"));
        if (sourceId.isBlank()) {
            sourceId = text(entries.get(0).payload().get("source"));
        }
        if (sourceId.isBlank()) {
            throw new IllegalStateException("Psionic Overload telekinesis binding requires source_id");
        }

        int tick = TickValueResolution.resolve(combatant.maxHp());
        RuntimeOrdinaryDamageIngress.Result damage = RuntimeOrdinaryDamageIngress.apply(
                context.state(),
                combatantId,
                tick
        );

        return LifecycleHookResult.events(List.of(new TrainerFeatureEvent(
                sourceId,
                "Psionic Overload",
                "telekinesis_tick",
                Map.of(
                        "target", combatantId,
                        "amount", tick,
                        "targetHp", damage.hpAfter()
                )
        )));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
