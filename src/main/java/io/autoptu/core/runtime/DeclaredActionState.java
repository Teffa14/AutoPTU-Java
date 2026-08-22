package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned snapshot of actions declared during the current round.
 *
 * Python currently treats declared_actions as battle-scoped opaque records and clears
 * the entire list at ROUND_START. The Java port intentionally preserves record payloads
 * without interpreting their gameplay meaning until those declaration producers/consumers
 * are ported. Values are recursively copied into immutable Java collections so adapters
 * cannot mutate canonical battle state through retained references.
 */
public final class DeclaredActionState {
    private final ArrayList<Map<String, Object>> declarations = new ArrayList<>();

    public List<Map<String, Object>> snapshot() {
        return List.copyOf(declarations);
    }

    void addFromRuntime(Map<String, ?> declaration) {
        if (declaration == null) {
            throw new IllegalArgumentException("declared action is required");
        }
        declarations.add(copyMap(declaration));
    }

    void replaceFromRuntime(Collection<? extends Map<String, ?>> entries) {
        ArrayList<Map<String, Object>> copied = new ArrayList<>();
        if (entries != null) {
            for (Map<String, ?> entry : entries) {
                if (entry == null) {
                    continue;
                }
                copied.add(copyMap(entry));
            }
        }
        declarations.clear();
        declarations.addAll(copied);
    }

    void clearFromLifecycle() {
        declarations.clear();
    }

    private static Map<String, Object> copyMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("declared action keys cannot be null");
            }
            copied.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copied);
    }

    private static Object copyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(copyValue(entry.getKey()), copyValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> copied = new ArrayList<>();
            for (Object item : collection) {
                copied.add(copyValue(item));
            }
            return Collections.unmodifiableList(copied);
        }
        throw new IllegalArgumentException(
                "declared action payloads must use scalar/map/list values, got: " + value.getClass().getName()
        );
    }
}
