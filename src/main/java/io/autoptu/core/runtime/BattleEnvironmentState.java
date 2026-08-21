package io.autoptu.core.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical battle-environment and spatial-relationship inputs consumed by PTU rules.
 *
 * Minecraft/Cobblemon may be used to materialize an initial snapshot, but rules read
 * this server-owned state rather than live world/entity claims. Grounded defaults to
 * true for legacy/headless fixtures that have not materialized an explicit value yet.
 * Mounted pairs preserve Python BattleState insertion order as riderId -> mountId.
 * Initiative ordering modes live here so Trick Room and League ordering cannot be
 * supplied ad hoc by the renderer/controller when a round is rebuilt.
 */
public final class BattleEnvironmentState {
    private final String weather;
    private final String terrainName;
    private final Set<String> tailwindTeams;
    private final Map<String, Boolean> groundedByCombatant;
    private final Map<String, String> mountedPairs;
    private final boolean trickRoomOrdering;
    private final boolean leagueBattleOrdering;

    public BattleEnvironmentState(
            String weather,
            String terrainName,
            Collection<String> tailwindTeams,
            Map<String, Boolean> groundedByCombatant
    ) {
        this(weather, terrainName, tailwindTeams, groundedByCombatant, Map.of(), false, false);
    }

    public BattleEnvironmentState(
            String weather,
            String terrainName,
            Collection<String> tailwindTeams,
            Map<String, Boolean> groundedByCombatant,
            Map<String, String> mountedPairs
    ) {
        this(weather, terrainName, tailwindTeams, groundedByCombatant, mountedPairs, false, false);
    }

    public BattleEnvironmentState(
            String weather,
            String terrainName,
            Collection<String> tailwindTeams,
            Map<String, Boolean> groundedByCombatant,
            Map<String, String> mountedPairs,
            boolean trickRoomOrdering,
            boolean leagueBattleOrdering
    ) {
        this.weather = normalizeText(weather);
        this.terrainName = normalizeText(terrainName);

        LinkedHashSet<String> teams = new LinkedHashSet<>();
        if (tailwindTeams != null) {
            for (String teamId : tailwindTeams) {
                String canonical = normalizeText(teamId);
                if (!canonical.isEmpty()) {
                    teams.add(canonical);
                }
            }
        }
        this.tailwindTeams = Collections.unmodifiableSet(teams);

        LinkedHashMap<String, Boolean> grounded = new LinkedHashMap<>();
        if (groundedByCombatant != null) {
            for (Map.Entry<String, Boolean> entry : groundedByCombatant.entrySet()) {
                String combatantId = normalizeText(entry.getKey());
                if (combatantId.isEmpty()) {
                    throw new IllegalArgumentException("grounded combatantId is required");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("grounded value is required for combatant: " + combatantId);
                }
                grounded.put(combatantId, entry.getValue());
            }
        }
        this.groundedByCombatant = Collections.unmodifiableMap(grounded);

        LinkedHashMap<String, String> pairs = new LinkedHashMap<>();
        if (mountedPairs != null) {
            for (Map.Entry<String, String> entry : mountedPairs.entrySet()) {
                String riderId = normalizeText(entry.getKey());
                String mountId = normalizeText(entry.getValue());
                if (riderId.isEmpty()) {
                    throw new IllegalArgumentException("mounted riderId is required");
                }
                if (mountId.isEmpty()) {
                    throw new IllegalArgumentException("mounted mountId is required for rider: " + riderId);
                }
                pairs.put(riderId, mountId);
            }
        }
        this.mountedPairs = Collections.unmodifiableMap(pairs);
        this.trickRoomOrdering = trickRoomOrdering;
        this.leagueBattleOrdering = leagueBattleOrdering;
    }

    public static BattleEnvironmentState neutral() {
        return new BattleEnvironmentState("", "", Set.of(), Map.of(), Map.of(), false, false);
    }

    public String weather() {
        return weather;
    }

    public String terrainName() {
        return terrainName;
    }

    public Set<String> tailwindTeams() {
        return tailwindTeams;
    }

    public boolean tailwindActive(String teamId) {
        String canonical = normalizeText(teamId);
        return !canonical.isEmpty() && tailwindTeams.contains(canonical);
    }

    public Map<String, Boolean> groundedByCombatant() {
        return groundedByCombatant;
    }

    public Set<String> groundedCombatantIds() {
        return groundedByCombatant.keySet();
    }

    public boolean grounded(String combatantId) {
        String canonical = normalizeText(combatantId);
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        return groundedByCombatant.getOrDefault(canonical, true);
    }

    /** Canonical riderId -> mountId relationships in deterministic Python-compatible order. */
    public Map<String, String> mountedPairs() {
        return mountedPairs;
    }

    /** True when the authoritative field state currently reverses initiative for Trick Room. */
    public boolean trickRoomOrdering() {
        return trickRoomOrdering;
    }

    /** True when the authoritative battle format uses League trainer-first initiative ordering. */
    public boolean leagueBattleOrdering() {
        return leagueBattleOrdering;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.strip();
    }
}
