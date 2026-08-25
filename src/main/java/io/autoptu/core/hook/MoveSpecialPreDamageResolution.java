package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.runtime.BattleRuntimeState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-facing bridge for Python move-special PRE_DAMAGE dispatch.
 *
 * <p>The pinned Python engine passes one mutable result mapping through move-special handlers.
 * This bridge seeds the fields consumed by the surrounding attack pipeline, dispatches the
 * generic registry, and returns typed values without exposing mutation authority to adapters.</p>
 */
public final class MoveSpecialPreDamageResolution {
    private MoveSpecialPreDamageResolution() {}

    /** Compatibility path for callers that do not yet transport the authoritative accuracy roll. */
    public static Result resolve(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            boolean hit,
            boolean crit,
            int damage,
            double typeMultiplier
    ) {
        return resolveInternal(
                registry, state, attackerId, defenderId, moveName, moveCategory,
                hit, crit, damage, typeMultiplier, null);
    }

    /**
     * Python-parity path. The accuracy d20 is carried in the shared result mapping as {@code roll}
     * so later PRE/POST move-special handlers and {@code _effect_roll()} observe the same roll that
     * resolved the attack. The value remains owned by the battle core.
     */
    public static Result resolve(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            boolean hit,
            boolean crit,
            int damage,
            double typeMultiplier,
            int roll
    ) {
        return resolveInternal(
                registry, state, attackerId, defenderId, moveName, moveCategory,
                hit, crit, damage, typeMultiplier, roll);
    }

    private static Result resolveInternal(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            boolean hit,
            boolean crit,
            int damage,
            double typeMultiplier,
            Integer roll
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");

        LinkedHashMap<String, Object> initial = new LinkedHashMap<>();
        initial.put("hit", hit);
        initial.put("crit", crit);
        initial.put("damage", damage);
        initial.put("type_multiplier", typeMultiplier);
        if (roll != null) initial.put("roll", roll);
        MoveSpecialResultState mutable = new MoveSpecialResultState(initial);

        List<BattleEvent> events = registry.dispatch(new MoveSpecialHookContext(
                state,
                attackerId,
                defenderId,
                moveName,
                moveCategory,
                mutable,
                hit,
                MoveSpecialPhase.PRE_DAMAGE
        ));

        return new Result(
                mutable.hit(),
                pythonTruthy(mutable.get("crit")),
                requireInt(mutable, "damage"),
                requireDouble(mutable, "type_multiplier"),
                events,
                mutable.snapshot()
        );
    }

    private static int requireInt(MoveSpecialResultState state, String key) {
        Object value = state.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("move-special result " + key + " must remain numeric");
        }
        return number.intValue();
    }

    private static double requireDouble(MoveSpecialResultState state, String key) {
        Object value = state.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("move-special result " + key + " must remain numeric");
        }
        return number.doubleValue();
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0.0d;
        if (value instanceof CharSequence sequence) return !sequence.isEmpty();
        return true;
    }

    public record Result(
            boolean hit,
            boolean crit,
            int damage,
            double typeMultiplier,
            List<BattleEvent> events,
            Map<String, Object> resultSnapshot
    ) {
        public Result {
            events = List.copyOf(events == null ? List.of() : events);
            resultSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(
                    resultSnapshot == null ? Map.of() : resultSnapshot));
        }
    }
}
