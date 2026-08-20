package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Pure parity boundary for round-scoped modifiers applied by Python
 * BattleState._build_initiative_order() after _initiative_entry_for_pokemon().
 *
 * This resolver owns Rocket Initiative, initiative_penalty expiry/application,
 * and the Inner Focus [Errata] exception. It reports temporary-effect families
 * that Python clears while still evaluating the original snapshot.
 */
public final class InitiativeRoundModifierResolution {
    private static final String ROCKET_INITIATIVE = "rocket_initiative";
    private static final String INITIATIVE_PENALTY = "initiative_penalty";

    private InitiativeRoundModifierResolution() {
    }

    public static InitiativeRoundModifierResult resolve(
            InitiativeEntry baseEntry,
            int currentRound,
            List<TemporaryEffectEntry> temporaryEffects,
            List<String> abilities
    ) {
        Objects.requireNonNull(baseEntry, "baseEntry");
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }

        int total = baseEntry.total();
        LinkedHashSet<String> clear = new LinkedHashSet<>();
        List<TemporaryEffectEntry> snapshot = temporaryEffects == null
                ? List.of()
                : List.copyOf(temporaryEffects);

        for (TemporaryEffectEntry effect : snapshot) {
            if (effect == null || !ROCKET_INITIATIVE.equals(effect.name())) {
                continue;
            }
            Object rawRound = effect.payload().get("round");
            if (rawRound != null && pythonInt(rawRound) < currentRound) {
                clear.add(ROCKET_INITIATIVE);
                continue;
            }
            if (pythonEqualsInt(rawRound, currentRound)) {
                total += 1000;
            }
        }

        boolean innerFocusErrata = AbilityIdentityResolution.matchesExact(
                abilities,
                "Inner Focus [Errata]"
        );
        for (TemporaryEffectEntry effect : snapshot) {
            if (effect == null || !INITIATIVE_PENALTY.equals(effect.name())) {
                continue;
            }
            Object rawExpiry = effect.payload().get("expires_round");
            if (rawExpiry != null && currentRound > pythonInt(rawExpiry)) {
                clear.add(INITIATIVE_PENALTY);
                continue;
            }

            int amount = pythonInt(pythonOrZero(effect.payload().get("amount")));
            if (amount < 0 && innerFocusErrata) {
                Object sourceId = effect.payload().get("source_id");
                if (!Objects.equals(sourceId, baseEntry.actorId())) {
                    continue;
                }
            }
            total += amount;
        }

        InitiativeEntry adjusted = new InitiativeEntry(
                baseEntry.actorId(),
                baseEntry.trainerId(),
                baseEntry.speed(),
                baseEntry.trainerModifier(),
                baseEntry.roll(),
                total
        );
        return new InitiativeRoundModifierResult(adjusted, new ArrayList<>(clear));
    }

    private static Object pythonOrZero(Object value) {
        if (value == null) return 0;
        if (value instanceof Boolean bool) return bool ? value : 0;
        if (value instanceof Number number) return number.doubleValue() == 0.0 ? 0 : value;
        if (value instanceof String text) return text.isEmpty() ? 0 : value;
        return value;
    }

    /** Scalar subset of Python int(value) used by the pinned temporary-effect payloads. */
    private static int pythonInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer integer) return integer;
        if (value instanceof Long number) return number.intValue();
        if (value instanceof Double number) return number.intValue();
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid Python int payload: " + value, error);
            }
        }
        throw new IllegalArgumentException("unsupported Python int payload: " + value.getClass().getName());
    }

    private static boolean pythonEqualsInt(Object value, int expected) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return (bool ? 1 : 0) == expected;
        if (value instanceof Number number) return number.doubleValue() == expected;
        return false;
    }
}
