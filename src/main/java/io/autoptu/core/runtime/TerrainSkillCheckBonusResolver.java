package io.autoptu.core.runtime;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves PTU terrain skill-check bonuses from server-owned facts.
 *
 * <p>This mirrors the reusable portion of Python BattleState._terrain_skill_check_bonus
 * and _matches_naturewalk_terrain without accepting an already-computed adapter bonus.</p>
 */
final class TerrainSkillCheckBonusResolver {
    private static final Set<String> ELIGIBLE_SKILLS = Set.of(
            "athletics", "acrobatics", "stealth", "perception", "survival"
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int SURVIVALIST_NATUREWALK_BONUS = 2;

    private TerrainSkillCheckBonusResolver() {}

    static int resolve(
            String skillName,
            boolean hasSurvivalist,
            Collection<String> naturewalkLabels,
            Collection<String> terrainContextLabels
    ) {
        String skill = normalize(skillName);
        if (!ELIGIBLE_SKILLS.contains(skill) || !hasSurvivalist) return 0;
        return matchesNaturewalkTerrain(naturewalkLabels, terrainContextLabels)
                ? SURVIVALIST_NATUREWALK_BONUS
                : 0;
    }

    static boolean matchesNaturewalkTerrain(
            Collection<String> naturewalkLabels,
            Collection<String> terrainContextLabels
    ) {
        if (naturewalkLabels == null || naturewalkLabels.isEmpty()) return false;
        String combined = combinedTerrainContext(terrainContextLabels);
        if (combined.isEmpty()) return false;

        for (String entry : naturewalkLabels) {
            String label = normalize(entry);
            if (label.isEmpty()) continue;
            if (combined.contains(label)) return true;

            for (String token : NON_ALPHANUMERIC.split(label)) {
                if (!token.isEmpty() && combined.contains(token)) return true;
            }
            if (label.endsWith("land") && label.length() > 4) {
                String stem = label.substring(0, label.length() - 4);
                if (!stem.isEmpty() && combined.contains(stem)) return true;
            }
        }
        return false;
    }

    private static String combinedTerrainContext(Collection<String> labels) {
        if (labels == null || labels.isEmpty()) return "";
        StringBuilder combined = new StringBuilder();
        for (String entry : labels) {
            String label = normalize(entry);
            if (label.isEmpty()) continue;
            if (!combined.isEmpty()) combined.append(' ');
            combined.append(label);
        }
        return combined.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
