package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves Python-compatible terrain context labels from authoritative battle state.
 *
 * <p>The runtime owns active field terrain, legacy terrain name, combatant position,
 * movement-grid tile type, and temporary terrain aliases. Minecraft/Cobblemon may
 * render those facts but does not provide the resolved labels used by PTU rules.</p>
 */
final class TerrainContextLabelResolver {
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final List<String> TERRAIN_ADJACENCY_CYCLE = List.of(
            "grassland", "forest", "wetlands", "ocean", "tundra",
            "mountain", "cave", "urban", "desert"
    );

    private TerrainContextLabelResolver() {}

    static List<String> resolve(BattleRuntimeState state, String combatantId) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState actor = state.requireCombatant(combatantId);

        String terrainName = normalizeTerrainName(activeTerrainName(state.environment()));
        String tileType = normalizeTerrainName(state.grid().tileType(actor.position()));
        String base = terrainName.isEmpty() ? tileType : terrainName;

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (!base.isEmpty()) labels.add(base);

        for (TemporaryEffectEntry entry : actor.temporaryEffects().getAll("terrain_alias")) {
            Object rawTerrain = entry.payload().get("terrain");
            String alias = normalizeTerrainName(rawTerrain);
            if (!alias.isEmpty()) labels.add(alias);
        }

        if (!tileType.isEmpty()) labels.add(tileType);
        return List.copyOf(new ArrayList<>(labels));
    }

    private static String activeTerrainName(BattleEnvironmentState environment) {
        return environment.terrainEffect()
                .map(FieldEffectEntry::name)
                .orElseGet(environment::terrainName);
    }

    static String normalizeTerrainName(Object value) {
        String source = value == null ? "" : String.valueOf(value);
        String token = NON_ALPHANUMERIC.matcher(source.strip().toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .strip();
        if (token.isEmpty()) return "";
        for (String terrain : TERRAIN_ADJACENCY_CYCLE) {
            if (token.equals(terrain) || token.contains(terrain)) return terrain;
        }
        return token;
    }
}
