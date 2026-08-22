package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureTargetResolutionOracleParityTest {
    @Test
    void targetScopesAndFiltersMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-targets.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, String> expected = readFixture(fixturePath);

        List<TrainerFeatureTargetResolution.CombatantView> combatants = List.of(
                mon("ally_active", "t1", true, false, "Burned"),
                mon("ally_inactive", "t1", false, false, "Sleep"),
                mon("ally_fainted", "t1", true, true, "Poisoned"),
                mon("enemy_active", "t2", true, false, "Burned", "Poisoned"),
                mon("enemy_inactive", "t2", false, false)
        );

        LinkedHashMap<String, Case> cases = new LinkedHashMap<>();
        cases.put("default_active_allies", testCase(Map.of(), "ally_active", Map.of()));
        cases.put("actor", testCase(Map.of("scope", "actor"), "ally_active", Map.of()));
        cases.put("actor_missing", testCase(Map.of("scope", "self"), "missing", Map.of()));
        cases.put("target", testCase(Map.of("scope", "target"), "ally_active", Map.of("target_id", "enemy_active")));
        cases.put("target_missing", testCase(Map.of("scope", "action_target"), "ally_active", Map.of("target_id", "missing")));
        cases.put("target_non_string_does_not_coerce", testCase(Map.of("scope", "target"), "ally_active", Map.of("target_id", 7)));
        cases.put("targets_preserve_payload_order", testCase(Map.of("scope", "targets"), "ally_active", Map.of("target_ids", List.of("enemy_active", "missing", "ally_active", "enemy_active"))));
        cases.put("targets_ignore_non_string_ids", testCase(Map.of("scope", "targets"), "ally_active", Map.of("target_ids", List.of(7, "enemy_active"))));
        cases.put("targets_string_is_not_sequence", testCase(Map.of("scope", "action_targets"), "ally_active", Map.of("target_ids", "enemy_active")));
        cases.put("all_active_filters_fainted_later", testCase(Map.of("scope", "all_active"), "ally_active", Map.of()));
        cases.put("all_allies_includes_inactive_by_default", testCase(Map.of("scope", "all_allies"), "ally_active", Map.of()));
        cases.put("allies_explicit_exclude_inactive", testCase(Map.of("scope", "allies", "include_inactive", false), "ally_active", Map.of()));
        cases.put("active_allies", testCase(Map.of("scope", "self_team"), "ally_active", Map.of()));
        cases.put("all_enemies_includes_inactive", testCase(Map.of("scope", "foes"), "ally_active", Map.of()));
        cases.put("active_enemies", testCase(Map.of("scope", "foe_active"), "ally_active", Map.of()));
        cases.put("all_pokemon_default_inactive", testCase(Map.of("scope", "all_pokemon"), "ally_active", Map.of()));
        cases.put("all_include_fainted", testCase(Map.of("scope", "all", "include_fainted", true), "ally_active", Map.of()));
        cases.put("required_status_any", testCase(Map.of("scope", "all", "require_status", List.of("Sleep", "Poisoned")), "ally_active", Map.of()));
        cases.put("excluded_status_any", testCase(Map.of("scope", "all", "exclude_status", List.of("Burned", "Sleep")), "ally_active", Map.of()));
        cases.put("required_and_excluded", testCase(Map.of("scope", "all", "require_status", "Poisoned", "exclude_status", "Burned"), "ally_active", Map.of()));
        cases.put("limit_preserves_order", testCase(Map.of("scope", "all", "include_fainted", true, "limit", 2), "ally_active", Map.of()));
        cases.put("float_string_limit_int_like", testCase(Map.of("scope", "all", "include_fainted", true, "limit", "2.9"), "ally_active", Map.of()));
        cases.put("unknown_scope_safe_default", testCase(Map.of("scope", "mystery"), "ally_active", Map.of()));
        cases.put("target_alias_precedence", testCase(Map.of("target", "all_enemies"), "ally_active", Map.of()));
        cases.put("false_scope_falls_back_to_target", testCase(Map.of("scope", false, "target", "all_enemies"), "ally_active", Map.of()));
        cases.put("scope_precedes_target", testCase(Map.of("scope", "active_allies", "target", "all_enemies"), "ally_active", Map.of()));
        cases.put("bool_like_include_fainted", testCase(Map.of("scope", "all", "include_fainted", "yes"), "ally_active", Map.of()));

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            Case current = entry.getValue();
            var context = new TrainerFeatureTargetResolution.Context("t1", current.actorId, current.payload, combatants);
            String actual = String.join(",", TrainerFeatureTargetResolution.resolve(current.rules, context));
            assertEquals(expected.get(entry.getKey()), actual, entry.getKey());
        }
    }

    private static TrainerFeatureTargetResolution.CombatantView mon(
            String id, String controllerId, boolean active, boolean fainted, String... statuses
    ) {
        return new TrainerFeatureTargetResolution.CombatantView(id, controllerId, active, fainted, Set.of(statuses));
    }

    private static Case testCase(Map<String, ?> rules, String actorId, Map<String, ?> payload) {
        return new Case(rules, actorId, payload);
    }

    private static Map<String, String> readFixture(Path path) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return out;
    }

    private record Case(Map<String, ?> rules, String actorId, Map<String, ?> payload) {}
}
