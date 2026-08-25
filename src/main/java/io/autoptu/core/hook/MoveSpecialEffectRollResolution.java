package io.autoptu.core.hook;

import java.util.List;

/**
 * Language-neutral resolver for Python move-special secondary-effect rolls.
 *
 * <p>This keeps repeated move-special roll modifiers in one deterministic contract. Runtime
 * adapters are expected to derive these inputs from authoritative battle state; Minecraft does
 * not provide the final roll.</p>
 */
public final class MoveSpecialEffectRollResolution {
    private MoveSpecialEffectRollResolution() {}

    public static int resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (input.immutableMindBlocked()) return -1;
        if (input.effectRangeBlocked()) return 0;

        int roll = input.baseRoll();
        if (input.sereneGrace()) roll += 2;
        if (input.stenchFlinch()) roll += 2;
        if (input.firebrandBurn()) roll += 2;
        roll -= input.rollPenalty();
        if (input.mindbreakPsychicDamaging()) roll += 1;
        if (input.polishedShineSteel()) roll += 2;
        if (input.brutalTraining()) roll += 1;
        for (Integer bonus : input.effectRangeBonuses()) {
            if (bonus != null) roll += bonus;
        }
        if (input.statStratagemApplies()) {
            roll += Math.min(3, Math.max(0, input.statStratagemSpAtkStage()));
        }
        roll += input.hardenedCritBonus();
        return roll;
    }

    public record Input(
            int baseRoll,
            boolean immutableMindBlocked,
            boolean effectRangeBlocked,
            boolean sereneGrace,
            boolean stenchFlinch,
            boolean firebrandBurn,
            int rollPenalty,
            boolean mindbreakPsychicDamaging,
            boolean polishedShineSteel,
            boolean brutalTraining,
            List<Integer> effectRangeBonuses,
            boolean statStratagemApplies,
            int statStratagemSpAtkStage,
            int hardenedCritBonus
    ) {
        public Input {
            effectRangeBonuses = List.copyOf(effectRangeBonuses == null ? List.of() : effectRangeBonuses);
        }
    }
}
