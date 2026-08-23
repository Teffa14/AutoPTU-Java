package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.rules.TrainerFeatureTargetResolution;
import io.autoptu.core.rules.TrainerFeatureTrainerTargetResolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-authoritative effect registry for generic Trainer Feature payloads.
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
        register("grant_temp_hp", TrainerFeatureEffectRegistry::applyGrantTempHp);
        register("raise_cs", TrainerFeatureEffectRegistry::applyRaiseCs);
        register("grant_ap", TrainerFeatureEffectRegistry::applyGrantAp);
        register("apply_status", TrainerFeatureEffectRegistry::applyStatus);
        register("remove_status", TrainerFeatureEffectRegistry::removeStatus);
    }

    public void register(String effectType, EffectHandler handler) {
        String token = normalize(effectType);
        if (token.isBlank()) throw new IllegalArgumentException("effectType is required");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(token, handler) != null) {
            throw new IllegalArgumentException("duplicate Trainer Feature effect handler: " + token);
        }
    }

    public EffectResult apply(EffectContext context, Map<String, ?> effect) {
        Objects.requireNonNull(context, "context");
        Map<String, ?> safeEffect = effect == null ? Map.of() : effect;
        String effectType = normalize(safeEffect.get("type"));
        if (effectType.isBlank()) return new EffectResult(true, "log_only", List.of(), Map.of());
        EffectHandler handler = handlers.get(effectType);
        if (handler == null) return new EffectResult(true, effectType, List.of(), Map.of("unhandled", true));
        return handler.apply(context, safeEffect);
    }

    private static EffectResult applyHeal(EffectContext context, Map<String, ?> effect) {
        int amount = intLike(effect.get("amount"), 0);
        if (amount <= 0) return new EffectResult(false, normalize(effect.get("type")), List.of(), Map.of());
        List<String> targetIds = resolveTargets(context, effect);
        if (targetIds.isEmpty()) return new EffectResult(false, normalize(effect.get("type")), List.of(), Map.of());
        ArrayList<String> changed = new ArrayList<>();
        for (String targetId : targetIds) {
            RuntimeCombatantState target = context.state().requireCombatant(targetId);
            int before = target.hp();
            target.setHp(before + amount);
            if (target.hp() != before) changed.add(targetId);
        }
        return new EffectResult(!changed.isEmpty(), "heal", changed, Map.of("amount", amount));
    }

    private static EffectResult applyGrantTempHp(EffectContext context, Map<String, ?> effect) {
        int amount = intLike(effect.get("amount"), 0);
        if (amount <= 0) return new EffectResult(false, "grant_temp_hp", List.of(), Map.of());
        List<String> targetIds = resolveTargets(context, effect);
        if (targetIds.isEmpty()) return new EffectResult(false, "grant_temp_hp", List.of(), Map.of());
        ArrayList<String> changed = new ArrayList<>();
        for (String targetId : targetIds) {
            if (TempHpResolution.grant(context.state(), targetId, amount) > 0) changed.add(targetId);
        }
        return new EffectResult(!changed.isEmpty(), "grant_temp_hp", changed, Map.of("amount", amount));
    }

    private static EffectResult applyGrantAp(EffectContext context, Map<String, ?> effect) {
        int amount = intLike(effect.containsKey("amount") ? effect.get("amount") : 1, 0);
        if (amount <= 0) return new EffectResult(false, "grant_ap", List.of(), Map.of());
        List<String> trainerIds = TrainerFeatureTrainerTargetResolution.resolve(
                context.trainerId(), context.state().trainerIds(), effect
        );
        ArrayList<String> changed = new ArrayList<>();
        for (String trainerId : trainerIds) {
            context.state().requireTrainer(trainerId).restoreAp(amount);
            changed.add(trainerId);
        }
        return new EffectResult(
                !changed.isEmpty(),
                "grant_ap",
                List.of(),
                Map.of("amount", amount, "trainers", List.copyOf(changed))
        );
    }

    private static EffectResult applyRaiseCs(EffectContext context, Map<String, ?> effect) {
        LinkedHashMap<String, Integer> changes = new LinkedHashMap<>();
        Object rawStats = effect.get("stats");
        if (rawStats instanceof Map<?, ?> stats) {
            for (Map.Entry<?, ?> entry : stats.entrySet()) {
                String stat = normalizeStat(entry.getKey());
                int amount = intLike(entry.getValue(), 0);
                if (!stat.isBlank() && amount != 0) changes.put(stat, amount);
            }
        } else {
            String stat = normalizeStat(effect.get("stat"));
            int amount = intLike(effect.get("amount"), 0);
            if (!stat.isBlank() && amount != 0) changes.put(stat, amount);
        }
        if (changes.isEmpty()) return new EffectResult(false, "raise_cs", List.of(), Map.of());
        List<String> targetIds = resolveTargets(context, effect);
        if (targetIds.isEmpty()) return new EffectResult(false, "raise_cs", List.of(), Map.of());

        ArrayList<String> changedTargets = new ArrayList<>();
        for (String targetId : targetIds) {
            RuntimeCombatantState target = context.state().requireCombatant(targetId);
            boolean changed = false;
            for (Map.Entry<String, Integer> change : changes.entrySet()) {
                if ("accuracy".equals(change.getKey())) {
                    int before = target.accuracyStage();
                    target.adjustAccuracyStage(change.getValue());
                    changed |= target.accuracyStage() != before;
                } else {
                    CombatStat stat = switch (change.getKey()) {
                        case "atk" -> CombatStat.ATK;
                        case "def" -> CombatStat.DEF;
                        case "spatk" -> CombatStat.SPATK;
                        case "spdef" -> CombatStat.SPDEF;
                        case "spd" -> CombatStat.SPD;
                        default -> null;
                    };
                    if (stat != null) {
                        int before = target.combatStages().get(stat);
                        target.combatStages().adjust(stat, change.getValue());
                        changed |= target.combatStages().get(stat) != before;
                    }
                }
            }
            if (changed) changedTargets.add(targetId);
        }
        return new EffectResult(!changedTargets.isEmpty(), "raise_cs", changedTargets, Map.of("stats", changes));
    }

    private static EffectResult applyStatus(EffectContext context, Map<String, ?> effect) {
        Object rawStatus = effect.containsKey("status") ? effect.get("status") : (effect.containsKey("name") ? effect.get("name") : "");
        String statusName = String.valueOf(rawStatus).strip();
        int duration = intLike(effect.containsKey("duration") ? effect.get("duration") : effect.get("remaining"), 0);
        boolean stack = boolLike(effect.get("stack"), false);
        List<String> targetIds = resolveTargets(context, effect);
        Map<String, Object> details = Map.of("status", statusName, "duration", duration);
        if (statusName.isBlank() || targetIds.isEmpty()) {
            return new EffectResult(false, "apply_status", List.of(), details);
        }

        ArrayList<String> changed = new ArrayList<>();
        for (String targetId : targetIds) {
            var existing = context.state().statusEntry(targetId, statusName);
            if (existing.isPresent() && !stack) {
                if (duration > 0) {
                    StatusEntry entry = existing.get();
                    int current = entry.intPayload("remaining").orElse(entry.intPayload("duration").orElse(0));
                    if (current < duration) {
                        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(entry.payload());
                        payload.put("remaining", duration);
                        payload.put("duration", duration);
                        context.state().putStatus(targetId, new StatusEntry(statusName, payload));
                        changed.add(targetId);
                    }
                }
                continue;
            }

            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "trainer_feature:" + featureIdentifier(context.feature()));
            if (duration > 0) {
                payload.put("remaining", duration);
                payload.put("duration", duration);
            }
            StatusEntry entry = new StatusEntry(statusName, payload);
            if (stack && existing.isPresent()) {
                ArrayList<StatusEntry> entries = new ArrayList<>(context.state().statusEntries(targetId));
                entries.add(entry);
                context.state().replaceStatusEntries(targetId, entries);
            } else {
                context.state().putStatus(targetId, entry);
            }
            changed.add(targetId);
        }
        return new EffectResult(!changed.isEmpty(), "apply_status", changed, details);
    }

    private static EffectResult removeStatus(EffectContext context, Map<String, ?> effect) {
        Object rawStatuses = effect.get("statuses");
        if (!pythonTruthy(rawStatuses)) rawStatuses = effect.get("status");
        List<String> statuses = normalizeTokens(rawStatuses);
        boolean removeAll = boolLike(effect.get("all"), false);
        List<String> targetIds = resolveTargets(context, effect);
        if ((statuses.isEmpty() && !removeAll) || targetIds.isEmpty()) {
            return new EffectResult(false, "remove_status", List.of(), Map.of("removed", List.of()));
        }

        ArrayList<String> changed = new ArrayList<>();
        ArrayList<String> removedNames = new ArrayList<>();
        for (String targetId : targetIds) {
            boolean localRemoved = false;
            if (removeAll) {
                int count = context.state().statusEntries(targetId).size();
                if (count > 0) {
                    context.state().replaceStatusEntries(targetId, List.of());
                    localRemoved = true;
                    removedNames.add("all");
                }
            } else {
                for (String status : statuses) {
                    while (context.state().removeStatus(targetId, status)) {
                        localRemoved = true;
                        removedNames.add(status);
                    }
                }
            }
            if (localRemoved) changed.add(targetId);
        }
        return new EffectResult(!changed.isEmpty(), "remove_status", changed, Map.of("removed", List.copyOf(removedNames)));
    }

    private static List<String> resolveTargets(EffectContext context, Map<String, ?> effect) {
        LinkedHashMap<String, Object> rules = new LinkedHashMap<>();
        copyMap(context.feature().get("target_rules"), rules);
        copyMap(effect.get("target_rules"), rules);
        ArrayList<TrainerFeatureTargetResolution.CombatantView> combatants = new ArrayList<>();
        for (String combatantId : context.state().combatantIds()) {
            RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
            String controllerId = context.state().hasCanonicalTrainer(combatantId)
                    ? context.state().controllerId(combatantId) : "";
            combatants.add(new TrainerFeatureTargetResolution.CombatantView(
                    combatantId, controllerId, context.state().isActive(combatantId),
                    combatant.hp() <= 0, context.state().statuses(combatantId)
            ));
        }
        return TrainerFeatureTargetResolution.resolve(
                rules,
                new TrainerFeatureTargetResolution.Context(
                        context.trainerId(), context.actorId(), context.payload(), combatants
                )
        );
    }

    private static String normalizeStat(Object raw) {
        return switch (normalize(raw)) {
            case "atk", "attack" -> "atk";
            case "def", "defense" -> "def";
            case "spa", "spatk", "special-attack", "special_attack" -> "spatk";
            case "spd", "speed" -> "spd";
            case "spdef", "special-defense", "special_defense" -> "spdef";
            case "acc", "accuracy" -> "accuracy";
            default -> "";
        };
    }

    private static String featureIdentifier(Map<String, ?> feature) {
        Object raw = feature.get("feature_id");
        if (!pythonTruthy(raw)) raw = feature.get("id");
        if (!pythonTruthy(raw)) raw = feature.get("name");
        String token = normalize(raw).replace(" ", "-");
        return token.isBlank() ? "feature" : token;
    }

    private static List<String> normalizeTokens(Object raw) {
        if (raw == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                String token = normalize(value);
                if (!token.isBlank()) out.add(token);
            }
        } else if (raw instanceof Object[] values) {
            for (Object value : values) {
                String token = normalize(value);
                if (!token.isBlank()) out.add(token);
            }
        } else {
            String token = normalize(raw);
            if (!token.isBlank()) out.add(token);
        }
        return List.copyOf(out);
    }

    private static boolean boolLike(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String token = normalize(value);
        if (Set.of("1", "true", "yes", "y", "on").contains(token)) return true;
        if (Set.of("0", "false", "no", "n", "off").contains(token)) return false;
        return fallback;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0.0;
        if (value instanceof CharSequence chars) return !chars.isEmpty();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private static void copyMap(Object raw, Map<String, Object> destination) {
        if (!(raw instanceof Map<?, ?> source)) return;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) destination.put(key, entry.getValue());
        }
    }

    private static int intLike(Object value, int fallback) {
        if (value == null || "".equals(value)) return fallback;
        try {
            if (value instanceof Boolean bool) return bool ? 1 : 0;
            if (value instanceof Number number) return number.intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException first) {
            try { return (int) Double.parseDouble(String.valueOf(value)); }
            catch (RuntimeException ignored) { return fallback; }
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