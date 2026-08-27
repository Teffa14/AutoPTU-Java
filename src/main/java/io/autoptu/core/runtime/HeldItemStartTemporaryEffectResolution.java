package io.autoptu.core.runtime;

import java.util.List;
import java.util.Map;

/**
 * Applies the declarative held-item START temporary-effect families shared by many items.
 *
 * This is intentionally item-agnostic. Canonical item metadata is parsed elsewhere; this
 * boundary only reproduces the reusable Python materialization rules and exact duplicate
 * guards for base stat modifiers/scalars plus accuracy/evasion bonuses.
 */
public final class HeldItemStartTemporaryEffectResolution {
    private HeldItemStartTemporaryEffectResolution() {}

    public record StatAmount(String stat, int amount) {}
    public record StatScalar(String stat, double multiplier) {}
    public record Input(
            String itemName,
            List<StatAmount> baseStatChanges,
            List<StatScalar> baseStatScalars,
            Integer accuracyBonus,
            Integer statusEvasionBonus,
            Integer allEvasionBonus
    ) {
        public Input {
            if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("itemName is required");
            baseStatChanges = baseStatChanges == null ? List.of() : List.copyOf(baseStatChanges);
            baseStatScalars = baseStatScalars == null ? List.of() : List.copyOf(baseStatScalars);
        }
    }

    public static void apply(TemporaryEffectStore store, Input input) {
        if (store == null) throw new IllegalArgumentException("store is required");
        if (input == null) throw new IllegalArgumentException("input is required");

        for (StatAmount modifier : input.baseStatChanges()) {
            if (!has(store, "stat_modifier", Map.of("stat", modifier.stat(), "source", input.itemName()))) {
                store.add("stat_modifier", Map.of(
                        "stat", modifier.stat(),
                        "amount", modifier.amount(),
                        "source", input.itemName()
                ));
            }
        }
        for (StatScalar scalar : input.baseStatScalars()) {
            if (!has(store, "stat_scalar", Map.of("stat", scalar.stat(), "source", input.itemName()))) {
                store.add("stat_scalar", Map.of(
                        "stat", scalar.stat(),
                        "multiplier", scalar.multiplier(),
                        "source", input.itemName()
                ));
            }
        }
        if (input.accuracyBonus() != null
                && !has(store, "accuracy_bonus", mapWithNullableType(input.accuracyBonus(), input.itemName()))) {
            store.add("accuracy_bonus", mapWithNullableType(input.accuracyBonus(), input.itemName()));
        }
        if (input.statusEvasionBonus() != null
                && !has(store, "evasion_bonus", Map.of(
                        "scope", "status", "amount", input.statusEvasionBonus(), "source", input.itemName()))) {
            store.add("evasion_bonus", Map.of(
                    "scope", "status", "amount", input.statusEvasionBonus(), "source", input.itemName()));
        }
        if (input.allEvasionBonus() != null
                && !has(store, "evasion_bonus", Map.of(
                        "scope", "all", "amount", input.allEvasionBonus(), "source", input.itemName()))) {
            store.add("evasion_bonus", Map.of(
                    "scope", "all", "amount", input.allEvasionBonus(), "source", input.itemName()));
        }
    }

    private static Map<String, Object> mapWithNullableType(int amount, String source) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("amount", amount);
        payload.put("type", null);
        payload.put("source", source);
        return payload;
    }

    private static boolean has(TemporaryEffectStore store, String family, Map<String, ?> match) {
        for (TemporaryEffectEntry entry : store.getAll(family)) {
            boolean allMatch = true;
            for (Map.Entry<String, ?> expected : match.entrySet()) {
                if (!java.util.Objects.equals(entry.payload().get(expected.getKey()), expected.getValue())) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }
}
