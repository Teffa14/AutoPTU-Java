package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;

/**
 * Pure parity boundary for Speed multipliers applied inside Python
 * BattleState._initiative_entry_for_pokemon().
 *
 * This resolver owns only the initiative-time Slush Rush, Surge Surfer and
 * Chlorophyll [Errata] checks. Weather, terrain, grounded state, HP and ability
 * identity are authoritative semantic inputs; Minecraft/Cobblemon must not
 * compute the resulting Speed.
 */
public final class InitiativeSpeedAbilityResolution {
    private InitiativeSpeedAbilityResolution() {
    }

    public static int resolve(
            int baseSpeed,
            Integer currentHp,
            int maxHp,
            String weather,
            String terrainName,
            boolean grounded,
            List<String> abilities
    ) {
        int speed = baseSpeed;
        boolean lowHp = maxHp > 0 && currentHp != null && ((long) currentHp * 2L <= maxHp);
        String normalizedWeather = normalize(weather);
        String normalizedTerrain = normalize(terrainName);

        if (AbilityIdentityResolution.matchesRegistration(abilities, "Slush Rush")) {
            boolean hailing = normalizedWeather.contains("hail") || normalizedWeather.contains("hailing");
            if (hailing || lowHp) {
                speed *= 2;
            }
        }

        if (AbilityIdentityResolution.matchesRegistration(abilities, "Surge Surfer")) {
            boolean electricTerrain = normalizedTerrain.startsWith("electric") && grounded;
            if (electricTerrain || lowHp) {
                speed *= 2;
            }
        }

        if (AbilityIdentityResolution.matchesExact(abilities, "Chlorophyll [Errata]")) {
            boolean sunny = normalizedWeather.contains("sun")
                    || normalizedWeather.contains("sunny")
                    || normalizedWeather.contains("harsh sunlight");
            if (sunny || lowHp) {
                speed *= 2;
            }
        }

        return speed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
