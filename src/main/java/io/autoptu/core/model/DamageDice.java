package io.autoptu.core.model;

/** PTU Damage Base dice expression: number of dice, die size, and flat modifier. */
public record DamageDice(int count, int sides, int flat) {
}
