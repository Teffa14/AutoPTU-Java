package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered initiative entries plus authoritative temporary-effect cleanup requests. */
public record InitiativeOrderAssemblyResult(
        List<InitiativeEntry> orderedEntries,
        Map<String, List<String>> temporaryEffectFamiliesToClear
) {
    public InitiativeOrderAssemblyResult {
        orderedEntries = orderedEntries == null ? List.of() : List.copyOf(orderedEntries);
        LinkedHashMap<String, List<String>> copied = new LinkedHashMap<>();
        if (temporaryEffectFamiliesToClear != null) {
            temporaryEffectFamiliesToClear.forEach((actorId, families) -> {
                if (actorId == null || actorId.isBlank()) {
                    throw new IllegalArgumentException("cleanup actorId is required");
                }
                List<String> safeFamilies = families == null ? List.of() : List.copyOf(families);
                if (!safeFamilies.isEmpty()) {
                    copied.put(actorId.strip(), safeFamilies);
                }
            });
        }
        temporaryEffectFamiliesToClear = Collections.unmodifiableMap(copied);
    }

    public List<String> orderedActorIds() {
        return orderedEntries.stream().map(InitiativeEntry::actorId).toList();
    }
}
