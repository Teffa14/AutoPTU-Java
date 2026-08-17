package io.autoptu.core.model;

/** Observable output of the invariant PTU damage-roll pipeline. */
public record DamageResult(
        DamageDice dice,
        int baseRoll,
        int criticalExtraRoll,
        int damageRoll,
        int preModifierDamage,
        int preTypeDamage,
        int damage
) {
}
