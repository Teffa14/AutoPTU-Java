package io.autoptu.core.hook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveSpecialEffectRollOracleParityTest {
    @Test
    void effectRollModifiersMatchPinnedPython() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.move.special.effect.roll.oracle",
                "build/oracle/move-special-effect-roll.tsv"));
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            expected.put(parts[0], Integer.parseInt(parts[1]));
        }

        assertEquals(expected.get("baseline"), resolve());
        assertEquals(expected.get("immutable"), resolve(input().immutableMindBlocked(true)));
        assertEquals(expected.get("range_block"), resolve(input().effectRangeBlocked(true)));
        assertEquals(expected.get("serene"), resolve(input().sereneGrace(true)));
        assertEquals(expected.get("stench"), resolve(input().stenchFlinch(true)));
        assertEquals(expected.get("firebrand"), resolve(input().firebrandBurn(true)));
        assertEquals(expected.get("roll_penalty"), resolve(input().rollPenalty(3)));
        assertEquals(expected.get("mindbreak"), resolve(input().mindbreakPsychicDamaging(true)));
        assertEquals(expected.get("polished"), resolve(input().polishedShineSteel(true)));
        assertEquals(expected.get("brutal"), resolve(input().brutalTraining(true)));
        assertEquals(expected.get("range_bonus"), resolve(input().effectRangeBonuses(List.of(2, -1))));
        assertEquals(expected.get("stat_stratagem"), resolve(input().statStratagemApplies(true).statStratagemSpAtkStage(5)));
        assertEquals(expected.get("stat_stratagem_stacked"), resolve(input().statStratagemApplications(2).statStratagemSpAtkStage(5)));
        assertEquals(expected.get("hardened"), resolve(input().hardenedCritBonus(4)));
    }

    private static int resolve() {
        return resolve(input());
    }

    private static int resolve(Builder builder) {
        return MoveSpecialEffectRollResolution.resolve(builder.build());
    }

    private static Builder input() {
        return new Builder();
    }

    private static final class Builder {
        int baseRoll = 10;
        boolean immutableMindBlocked;
        boolean effectRangeBlocked;
        boolean sereneGrace;
        boolean stenchFlinch;
        boolean firebrandBurn;
        int rollPenalty;
        boolean mindbreakPsychicDamaging;
        boolean polishedShineSteel;
        boolean brutalTraining;
        List<Integer> effectRangeBonuses = List.of();
        int statStratagemApplications;
        int statStratagemSpAtkStage;
        int hardenedCritBonus;

        Builder immutableMindBlocked(boolean value) { immutableMindBlocked = value; return this; }
        Builder effectRangeBlocked(boolean value) { effectRangeBlocked = value; return this; }
        Builder sereneGrace(boolean value) { sereneGrace = value; return this; }
        Builder stenchFlinch(boolean value) { stenchFlinch = value; return this; }
        Builder firebrandBurn(boolean value) { firebrandBurn = value; return this; }
        Builder rollPenalty(int value) { rollPenalty = value; return this; }
        Builder mindbreakPsychicDamaging(boolean value) { mindbreakPsychicDamaging = value; return this; }
        Builder polishedShineSteel(boolean value) { polishedShineSteel = value; return this; }
        Builder brutalTraining(boolean value) { brutalTraining = value; return this; }
        Builder effectRangeBonuses(List<Integer> value) { effectRangeBonuses = value; return this; }
        Builder statStratagemApplies(boolean value) { statStratagemApplications = value ? 1 : 0; return this; }
        Builder statStratagemApplications(int value) { statStratagemApplications = value; return this; }
        Builder statStratagemSpAtkStage(int value) { statStratagemSpAtkStage = value; return this; }
        Builder hardenedCritBonus(int value) { hardenedCritBonus = value; return this; }

        MoveSpecialEffectRollResolution.Input build() {
            return new MoveSpecialEffectRollResolution.Input(
                    baseRoll,
                    immutableMindBlocked,
                    effectRangeBlocked,
                    sereneGrace,
                    stenchFlinch,
                    firebrandBurn,
                    rollPenalty,
                    mindbreakPsychicDamaging,
                    polishedShineSteel,
                    brutalTraining,
                    effectRangeBonuses,
                    statStratagemApplications,
                    statStratagemSpAtkStage,
                    hardenedCritBonus
            );
        }
    }
}
