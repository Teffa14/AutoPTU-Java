package io.autoptu.core.runtime;

import io.autoptu.core.rules.TrainerFeatureTargetResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative effect registry for generic Trainer Feature payloads.
 *
 * <p>This is the execution seam corresponding to Python
 * {@code TrainerFeatureDispatcher._apply_effect()}. Target selection is delegated to
 * {@link TrainerFeatureTargetResolution}; concrete handlers mutate only canonical
 * {@link BattleRuntimeState}. Minecraft/Cobblemon adapters never provide resolved
 * targets, HP deltas, or effect outcomes.
 */
public final class TrainerFeatureEffectRegistry {
    @FunctionalInterface
    public interface EffectHandler {
        EffectResult apply(EffectContext context, Map<String, ?> effect);
    }

    public record EffectContext(
            BattleRuntimeState state,
            String trainerId,
            String actorId,
            Map<String, ?> feature,
            Map<String, ?> payload
    ) {
        public EffectContext {
            state = Objects.requireNonNull(state, "state");
            trainerId = normalizeId(trainerId, "trainerId");
            actorId = actorId == null ? "" : actorId.strip();
            feature = feature == null ? Map.of() : Map.copyOf(feature);
            payload = payload == null ? Map.of() : Map.copyOf(payload);
            state.requireTrainer(trainerId);
        }
    }

    public record EffectResult(
            boolean applied,
            String effectType,
            List<String> targets,
            Map<String, Object> details
    ) {
        public EffectResult {
            effectType = effectType == null ? "" : effectType.strip().toLowerCase(Locale.ROOT);
            targets = targets == null ? List.of() : List.copyOf(targets);
            details = immutableDetails(details);
        }
    }

    private final LinkedHashMap<String, EffectHandler> handlers = new LinkedHashMap<>();

    public TrainerFeatureEffectRegistry() {
        register("heal", TrainerFeatureEffectRegistry::applyHeal);
        register("heal_active", TrainerFeatureEffectRegistry::applyHeal);
    }

    public void register(String effectType, EffectHandler handler) {
        String token = normalize(effectType);
        if (token.isBlank()) throw new IllegalArgumentException("effectType is required");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(token, handler) != null) {
            throw new IllegalArgumentException("duplicate Trainer Feature effect handler: " + token);
        }
    }

    /** Mirrors Python _apply_effect() for one normalized effect payload. */
    public EffectResult apply(EffectContext context, Map<String, ?> effect) {
        Objects.requireNonNull(context, "context");
        Map<String, ?> safeEffect = effect == null ? Map.of() : effect;
        String effectType = normalize(safeEffect.get("type"));
        if (effectType.isBlank()) {
            return new EffectResult(true, "log_only", List.of(), Map.of());
        }
        EffectHandler handler = handlers.get(effectType);
        if (handler == null) {
            // Python deliberately treats unknown effect types as applied scaffolding.
            return new EffectResult(true, effectType, List.of(), Map.of("unhandled", true));
        }
        return handler.apply(context, safeEffect);
    }

    private static EffectResult applyHeal(EffectContext context, Map<String, ?> effect) {
        int amount = intLike(effect.get("amount"), 0);
        if (amount <= 0) {
            return new EffectResult(false, normalize(effect.get("type")), List.of(), Map.of());
        }

        List<String> targetIds = resolveTargets(context, effect);
        if (targetIds.isEmpty()) {
            return new EffectResult(false, normalize(effect.get("type")), List.of(), Map.of());
        }

        ArrayList<String> changed = new ArrayList<>();
        for (String targetId : targetIds) {
            RuntimeCombatantState target = context.state().requireCombatant(targetId);
            int before = target.hp();
            target.setHp(before + amount);
            if (target.hp() != before) changed.add(targetId);
        }
        return new EffectResult(!changed.isEmpty(), "heal", changed, Map.of("amount", amount));
    }

    private static List<String> resolveTargets(EffectContext context, Map<String, ?> effect) {
        LinkedHashMap<String, Object> rules = new LinkedHashMap<>();
        copyMap(context.feature().get("target_rules"), rules);
        copyMap(effect.get("target_rules"), rules);

        ArrayList<TrainerFeatureTargetResolution.CombatantView> combatants = new ArrayList<>();
        for (String combatantId : context.state().combatantIds()) {
            RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
            String controllerId = context.state().hasCanonicalTrainer(combatantId)
                    ? context.state().controllerId(combatantId)
                    : "";
            combatants.add(new TrainerFeatureTargetResolution.CombatantView(
                    combatantId,
                    controllerId,
                    context.state().isActive(combatantId),
                    combatant.hp() <= 0,
                    context.state().statuses(combatantId)
            ));
        }
        return TrainerFeatureTargetResolution.resolve(
                rules,
                new TrainerFeatureTargetResolution.Context(
                        context.trainerId(), context.actorId(), context.payload(), combatants
                )
        );
    }

    private static void copyMap(Object raw, Map<String, Object> destination) {
        if (!(raw instanceof Map<?, ?> source)) return;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) destination.put(key, entry.getValue());
        }
    }

    /** Python _int_like semantics used by Trainer Feature effect payloads. */
    private static int intLike(Object value, int fallback) {
        if (value == null || "".equals(value)) return fallback;
        try {
            if (value instanceof Boolean bool) return bool ? 1 : 0;
            if (value instanceof Number number) return number.intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException first) {
            try {
                return (int) Double.parseDouble(String.valueOf(value));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }

    private static Map<String, Object> immutableDetails(Map<String, ?> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
