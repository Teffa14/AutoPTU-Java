package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptAttemptInputFactoryTest {
    @Test
    void runtimeInputOwnershipMatchesPinnedPythonContract() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.attempt.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("attempt_calls_cannot_miss_helper"));
        assertEquals(1, fixture.get("priority_reads_move_priority"));
        assertEquals(1, fixture.get("interrupt_reads_range_keyword"));
        assertEquals(1, fixture.get("speed_gate_reads_raw_spec_spd"));
        assertEquals(1, fixture.get("cannot_miss_uses_exact_keywords"));
        assertEquals(1, fixture.get("cannot_miss_reads_effects_text"));
        assertEquals(1, fixture.get("cannot_miss_false_surrender"));
        assertEquals(1, fixture.get("cannot_miss_feint_attack"));
        assertEquals(1, fixture.get("cannot_miss_future_sight"));
    }

    @Test
    void derivesPriorityTargetShapeAndRawBaseSpeedFromCanonicalState() {
        BattleRuntimeState state = state(combatant("attacker", 1, 1, 20, 6), combatant("interceptor", 3, 1, 21, -6));
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6", List.of(), "", 1);

        InterceptAttemptPolicy.Input input = RuntimeInterceptAttemptInputFactory.fromState(
                state,
                "attacker",
                "interceptor",
                MoveOption.standard("ordinary attack", spec)
        );

        assertFalse(input.cannotMiss());
        assertFalse(input.areaAttack());
        assertEquals("ranged", input.targetKind());
        assertTrue(input.priorityOrInterrupt());
        assertEquals(21, input.interceptorSpeed());
        assertEquals(20, input.attackerSpeed());
    }

    @Test
    void rangeTextInterruptActivatesSpeedGateWithoutPriority() {
        BattleRuntimeState state = state(combatant("attacker", 1, 1, 20, 0), combatant("interceptor", 3, 1, 19, 0));
        MoveSpec spec = new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee, Interrupt", List.of(), "", 0);

        InterceptAttemptPolicy.Input input = RuntimeInterceptAttemptInputFactory.fromState(
                state, "attacker", "interceptor", MoveOption.standard("interrupt attack", spec));

        assertTrue(input.priorityOrInterrupt());
        assertFalse(InterceptAttemptPolicy.resolve(input).allowed());
        assertEquals(InterceptAttemptPolicy.BlockReason.PRIORITY_SPEED, InterceptAttemptPolicy.resolve(input).blockReason());
    }

    @Test
    void derivesCannotMissFromCanonicalNameKeywordAndEffectsText() {
        BattleRuntimeState state = state(combatant("attacker", 1, 1, 10, 0), combatant("interceptor", 3, 1, 20, 0));
        MoveSpec plain = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6", List.of(), "", 0);
        MoveSpec keyword = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6", List.of("Cannot Miss"), "", 0);
        MoveSpec text = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged 6", List.of(), "This attack always hits its target.", 0);

        assertTrue(RuntimeInterceptAttemptInputFactory.fromState(
                state, "attacker", "interceptor", MoveOption.standard("Future Sight", plain)).cannotMiss());
        assertTrue(RuntimeInterceptAttemptInputFactory.fromState(
                state, "attacker", "interceptor", MoveOption.standard("other", keyword)).cannotMiss());
        assertTrue(RuntimeInterceptAttemptInputFactory.fromState(
                state, "attacker", "interceptor", MoveOption.standard("other", text)).cannotMiss());
    }

    @Test
    void areaKindBlocksInterceptionBeforeGeometry() {
        BattleRuntimeState state = state(combatant("attacker", 1, 1, 10, 0), combatant("interceptor", 3, 1, 20, 0));
        MoveSpec burst = new MoveSpec("Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1", List.of(), "", 0);

        InterceptAttemptPolicy.Input input = RuntimeInterceptAttemptInputFactory.fromState(
                state, "attacker", "interceptor", MoveOption.standard("burst attack", burst));

        assertTrue(input.areaAttack());
        assertEquals(InterceptAttemptPolicy.BlockReason.AREA_ATTACK, InterceptAttemptPolicy.resolve(input).blockReason());
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(new MovementGrid(8, 8, Set.of(), Map.of()), List.of(combatants));
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int baseSpeed, int speedStage) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, baseSpeed),
                Map.of(CombatStat.SPD, speedStage),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget(),
                stats
        );
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> fixture = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("key\t")) continue;
            String[] parts = line.split("\\t", 2);
            fixture.put(parts[0], Integer.parseInt(parts[1]));
        }
        return fixture;
    }
}
