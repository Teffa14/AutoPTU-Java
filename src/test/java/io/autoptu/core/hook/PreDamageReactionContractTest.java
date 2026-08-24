package io.autoptu.core.hook;

import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.runtime.BattleRuntimeState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreDamageReactionContractTest {
    @Test
    void defaultDecisionAllowsOptionalReactionAndBuildsPythonPayload() {
        OutOfTurnDecisionRequest request = OutOfTurnDecisionRequest.preDamageInterrupt(
                "defender", "Telepathy", "Surf", "Effective Surf",
                "attacker", "defender", true
        );

        assertTrue(OutOfTurnDecisionGate.allowWhenUnconfigured().shouldTrigger(request));
        assertEquals("pre_damage_interrupt", request.phase());
        assertEquals("Surf", request.moveName());
        assertEquals("Effective Surf", request.triggerMoveName());
        assertTrue(request.optional());
    }

    @Test
    void contextPreservesOriginalEffectiveMoveAndTargetKind() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()), List.of()
        );
        PreDamageReactionContext context = PreDamageReactionContext.of(
                state, "attacker", "defender", "Slash", "Effective Slash", "Melee", List.of()
        );

        OutOfTurnDecisionRequest request = context.decisionRequest("defender", "Parry", true);
        assertEquals("Slash", request.moveName());
        assertEquals("Effective Slash", request.triggerMoveName());
        assertEquals("melee", context.effectiveTargetKind());
        assertEquals("attacker", request.attackerId());
        assertEquals("defender", request.defenderId());
    }

    @Test
    void registryContinuesAfterEarlierHookCancelsHit() {
        AtomicBoolean laterHookRan = new AtomicBoolean(false);
        PreDamageReactionHookRegistry registry = PreDamageReactionHookRegistry.builder()
                .register("first", HookSource.ABILITY, 10, (context, current) -> current.cancelHit(List.of()))
                .register("second", HookSource.ABILITY, 20, (context, current) -> {
                    laterHookRan.set(true);
                    assertFalse(current.hit());
                    assertEquals(0, current.damage());
                    assertEquals(0.0, current.typeMultiplier());
                    return current;
                })
                .build();

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()), List.of()
        );
        PreDamageReactionResult result = registry.resolve(
                PreDamageReactionContext.of(state, "attacker", "defender", "Surf", List.of()),
                PreDamageReactionResult.of(true, 12, 2.0)
        );

        assertTrue(laterHookRan.get());
        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(0.0, result.typeMultiplier());
    }

    @Test
    void matchesPinnedPythonContractWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_PRE_DAMAGE_REACTION_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertEquals("property\tvalue", lines.getFirst());
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(2, parts.length, "malformed row: " + line);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        for (String property : List.of(
                "missing_decision_callback_allows",
                "decision_payload_complete",
                "perception_requires_ready",
                "perception_first_decision_precedes_ready_consumption",
                "perception_checks_round_scoped_usage",
                "perception_uses_second_optional_decision",
                "perception_records_next_round_expiry",
                "perception_cancels_hit",
                "perception_zeroes_damage",
                "perception_zeroes_type_multiplier",
                "perception_errata_requires_exact_variant",
                "perception_errata_requires_distinct_attacker",
                "perception_errata_requires_allied_attacker",
                "perception_errata_rejects_status_moves",
                "perception_errata_limits_escape_to_one",
                "perception_errata_has_no_ready_usage_bookkeeping",
                "perception_errata_cancels_hit",
                "perception_errata_zeroes_damage",
                "perception_errata_zeroes_type_multiplier",
                "parry_requires_ready",
                "parry_uses_optional_decision",
                "parry_consumes_ready_after_decision",
                "parry_checks_melee_after_ready_consumption",
                "parry_checks_round_scoped_usage",
                "parry_records_current_round_usage",
                "parry_cancels_hit",
                "parry_zeroes_damage",
                "parry_zeroes_type_multiplier",
                "telepathy_uses_optional_decision",
                "telepathy_cancels_hit",
                "telepathy_zeroes_damage",
                "telepathy_zeroes_type_multiplier",
                "ability_registry_continues_after_hook",
                "pre_damage_interrupt_only_runs_after_hit",
                "ordinary_move_resolution_precedes_pre_damage_interrupt",
                "pre_damage_interrupt_precedes_post_result",
                "interrupt_result_replaces_result_before_post_result",
                "shield_block_check_sits_between_pre_damage_and_post_result",
                "interrupt_suppression_precedes_pre_damage_interrupt",
                "post_result_precedes_attacker_item_damage_bonus",
                "post_result_precedes_hp_mutation"
        )) {
            assertEquals(1, values.get(property), property);
        }
    }
}
