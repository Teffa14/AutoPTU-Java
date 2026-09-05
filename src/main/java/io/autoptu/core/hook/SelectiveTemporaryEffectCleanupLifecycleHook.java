package io.autoptu.core.hook;

import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative lifecycle cleanup for selected entries inside one temporary-effect family.
 *
 * Python stores multiple metadata-bearing dictionaries under the same logical effect family.
 * Some lifecycle rules remove only entries whose metadata identifies a particular source
 * (for example Adaptive Geography's terrain_alias entry). This hook preserves unrelated
 * entries in the same family and keeps selection data language-neutral and server-owned.
 */
public final class SelectiveTemporaryEffectCleanupLifecycleHook implements LifecycleHook {
    public enum Scope {
        ACTOR,
        ALL_COMBATANTS
    }

    /**
     * Match one normalized effect family and an AND-set of payload fields.
     * String values compare after trim + case-fold to mirror Python's common metadata checks;
     * non-string scalar values use ordinary equality.
     */
    public record Selector(String effectName, Map<String, Object> payloadEquals) {
        public Selector {
            if (effectName == null || effectName.isBlank()) {
                throw new IllegalArgumentException("effectName is required");
            }
            effectName = effectName.strip().toLowerCase(Locale.ROOT);
            LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
            if (payloadEquals != null) {
                for (Map.Entry<String, Object> entry : payloadEquals.entrySet()) {
                    String key = entry.getKey();
                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("payload selector keys must be non-blank");
                    }
                    copied.put(key, entry.getValue());
                }
            }
            payloadEquals = Map.copyOf(copied);
        }

        boolean matches(TemporaryEffectEntry entry) {
            for (Map.Entry<String, Object> expected : payloadEquals.entrySet()) {
                if (!scalarEquals(entry.payload().get(expected.getKey()), expected.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean scalarEquals(Object actual, Object expected) {
            if (actual instanceof String actualString && expected instanceof String expectedString) {
                return actualString.strip().equalsIgnoreCase(expectedString.strip());
            }
            return Objects.equals(actual, expected);
        }
    }

    private final Scope scope;
    private final List<Selector> selectors;

    public SelectiveTemporaryEffectCleanupLifecycleHook(Scope scope, List<Selector> selectors) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (selectors == null || selectors.isEmpty()) {
            throw new IllegalArgumentException("selectors must not be empty");
        }
        this.selectors = List.copyOf(selectors.stream()
                .map(selector -> Objects.requireNonNull(selector, "selector"))
                .toList());
    }

    public Scope scope() {
        return scope;
    }

    public List<Selector> selectors() {
        return selectors;
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (scope == Scope.ALL_COMBATANTS) {
            for (String combatantId : context.state().combatantIds()) {
                cleanup(context.state().requireCombatant(combatantId));
            }
        } else {
            if (context.actorId() == null || context.actorId().isBlank()) {
                throw new IllegalArgumentException("ACTOR cleanup requires actorId");
            }
            cleanup(context.state().requireCombatant(context.actorId()));
        }
        return LifecycleHookResult.empty();
    }

    private void cleanup(RuntimeCombatantState combatant) {
        for (Selector selector : selectors) {
            combatant.temporaryEffects().removeIf(selector.effectName(), selector::matches);
        }
    }
}
