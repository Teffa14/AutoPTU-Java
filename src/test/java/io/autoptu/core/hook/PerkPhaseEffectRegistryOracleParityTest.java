package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundInjuryHistoryState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerkPhaseEffectRegistryOracleParityTest {
    @Test
    void genericRegistrySemanticsMatchPinnedPythonContract() throws IOException {
        Map<String, Integer> oracle = oracleContracts();
        for (String contract : List.of(
                "registry_is_phase_scoped",
                "registration_normalizes_named_perk",
                "registry_preserves_registration_order",
                "registry_filters_by_trainer_feature",
                "registry_supports_global_hooks",
                "end_passive_hooks_present",
                "defense_mastery_is_end_scoped",
                "stat_mastery_is_end_scoped",
                "links_resolve_trainer_from_actor_controller",
                "links_require_trainer_ap",
                "links_spend_trainer_ap",
                "perk_filter_is_actor_feature_owned"
        )) {
            assertEquals(1, oracle.get(contract), contract);
        }

        List<String> observed = new ArrayList<>();
        PerkPhaseEffectRegistry registry = PerkPhaseEffectRegistry.builder()
                .register("wrong-phase", null, TurnPhase.START, 1, (context, perk) -> {
                    observed.add("wrong-phase");
                    return LifecycleHookResult.empty();
                })
                .register("global", null, TurnPhase.END, 10, (context, perk) -> {
                    observed.add("global");
                    return LifecycleHookResult.pendingStatusSkip(
                            new PendingStatusSkipRequest("Confused", context.phase(), "global")
                    );
                })
                .register("defense-mastery", "Defense Mastery", TurnPhase.END, 20, (context, perk) -> {
                    observed.add(perk);
                    return LifecycleHookResult.pendingStatusSkip(
                            new PendingStatusSkipRequest("Flinch", context.phase(), "feature")
                    );
                })
                .register("other-feature", "Attack Link", TurnPhase.END, 30, (context, perk) -> {
                    observed.add("other-feature");
                    return LifecycleHookResult.empty();
                })
                .build();

        LifecycleHookResult result = registry.resolve(context(TurnPhase.END), List.of("defense mastery"));

        assertEquals(List.of("global", "Defense Mastery"), observed);
        assertEquals("Flinch", result.pendingStatusSkip().status());
        assertEquals("feature", result.pendingStatusSkip().reason());
    }

    @Test
    void duplicateRegistrationIdsFailClosed() {
        PerkPhaseEffectRegistry.Builder builder = PerkPhaseEffectRegistry.builder()
                .register("same", null, TurnPhase.END, 10, (context, perk) -> LifecycleHookResult.empty());
        assertThrows(IllegalArgumentException.class, () ->
                builder.register("SAME", null, TurnPhase.END, 20, (context, perk) -> LifecycleHookResult.empty())
        );
    }

    private static LifecycleHookContext context(TurnPhase phase) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 1), 20, 20, new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor)
        );
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                2,
                "actor",
                phase
        );
    }

    private static Map<String, Integer> oracleContracts() throws IOException {
        String raw = System.getProperty("autoptu.perk.phase.oracle", "");
        Assumptions.assumeTrue(!raw.isBlank(), "perk phase oracle fixture not configured");
        List<String> lines = Files.readAllLines(Path.of(raw));
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\t");
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
