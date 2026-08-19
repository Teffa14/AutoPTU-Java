package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRoundController;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TrainerRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkPerkOracleParityTest {
    @Test
    void attackLinkRaisesNonpositiveStageAndSpendsOneAp() {
        Fixture fixture = fixture("Attack Link", CombatStat.ATK, 0, 2);

        List<BattleEvent> events = advanceToEnd(fixture.controller());

        assertEquals(1, fixture.actor().combatStages().get(CombatStat.ATK));
        assertEquals(1, fixture.trainer().ap());
        TrainerFeatureEvent event = events.stream()
                .filter(TrainerFeatureEvent.class::isInstance)
                .map(TrainerFeatureEvent.class::cast)
                .filter(candidate -> candidate.feature().equals("Attack Link"))
                .findFirst()
                .orElseThrow();
        assertEquals("trainer", event.trainer());
        assertEquals("raise_cs", event.effect());
        assertEquals("atk", event.stat());
        assertEquals(1, event.amount());
        assertEquals(1, event.apSpent());
        assertEquals("end", event.phase());
    }

    @Test
    void attackLinkRaisesNegativeStageByExactlyOne() {
        Fixture fixture = fixture("Attack Link", CombatStat.ATK, -2, 2);

        advanceToEnd(fixture.controller());

        assertEquals(-1, fixture.actor().combatStages().get(CombatStat.ATK));
        assertEquals(1, fixture.trainer().ap());
    }

    @Test
    void positiveStageAndMissingApBothFailClosed() {
        Fixture positive = fixture("Attack Link", CombatStat.ATK, 1, 2);
        List<BattleEvent> positiveEvents = advanceToEnd(positive.controller());
        assertEquals(1, positive.actor().combatStages().get(CombatStat.ATK));
        assertEquals(2, positive.trainer().ap());
        assertTrue(positiveEvents.stream().noneMatch(event -> event instanceof TrainerFeatureEvent feature
                && feature.feature().equals("Attack Link")));

        Fixture noAp = fixture("Attack Link", CombatStat.ATK, 0, 0);
        List<BattleEvent> noApEvents = advanceToEnd(noAp.controller());
        assertEquals(0, noAp.actor().combatStages().get(CombatStat.ATK));
        assertEquals(0, noAp.trainer().ap());
        assertTrue(noApEvents.stream().noneMatch(event -> event instanceof TrainerFeatureEvent feature
                && feature.feature().equals("Attack Link")));
    }

    @Test
    void fixedLinkFeaturesTargetTheirCanonicalCombatStages() {
        Map<String, CombatStat> mappings = Map.of(
                "Attack Link", CombatStat.ATK,
                "Defense Link", CombatStat.DEF,
                "Special Attack Link", CombatStat.SPATK,
                "Special Defense Link", CombatStat.SPDEF,
                "Speed Link", CombatStat.SPD
        );

        for (Map.Entry<String, CombatStat> mapping : mappings.entrySet()) {
            Fixture fixture = fixture(mapping.getKey(), mapping.getValue(), 0, 1);
            advanceToEnd(fixture.controller());
            assertEquals(1, fixture.actor().combatStages().get(mapping.getValue()), mapping.getKey());
            assertEquals(0, fixture.trainer().ap(), mapping.getKey());
        }
    }

    private static List<BattleEvent> advanceToEnd(BattleRoundController controller) {
        controller.beginTurn("actor");
        controller.advancePhase();
        controller.advancePhase();
        return controller.advancePhase();
    }

    private static Fixture fixture(String feature, CombatStat stagedStat, int stage, int ap) {
        EnumMap<CombatStat, Integer> baseStats = new EnumMap<>(CombatStat.class);
        EnumMap<CombatStat, Integer> stages = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) {
            baseStats.put(stat, 10);
            stages.put(stat, 0);
        }
        stages.put(stagedStat, stage);
        CombatantStatProfile statProfile = new CombatantStatProfile(baseStats, stages, Map.of(), Set.of());
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 2),
                20,
                20,
                new ActionBudget(),
                statProfile
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of(actor)
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of(feature), ap);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        return new Fixture(actor, trainer, new BattleRoundController(state, 3));
    }

    private record Fixture(
            RuntimeCombatantState actor,
            TrainerRuntimeState trainer,
            BattleRoundController controller
    ) {}
}
