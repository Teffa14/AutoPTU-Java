package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaTrapEffectPlanTest {
    @Test
    void preservesTargetOrderAndFrozenPythonEffectShape() {
        List<ArenaTrapEffectPlan.Effect> effects = ArenaTrapEffectPlan.forTargets(
                "trap-holder",
                List.of("foe-b", "foe-a")
        );

        assertEquals(List.of("foe-b", "foe-a"), effects.stream().map(ArenaTrapEffectPlan.Effect::targetId).toList());
        for (ArenaTrapEffectPlan.Effect effect : effects) {
            assertEquals("Slowed", effect.status());
            assertEquals(1, effect.durationRounds());
            assertEquals("Arena Trap", effect.source());
            assertEquals("trap-holder", effect.sourceId());
            assertEquals("Arena Trap", effect.ability());
            assertEquals("slowed", effect.action());
            assertEquals("Arena Trap slows nearby foes.", effect.description());
        }
    }
}
