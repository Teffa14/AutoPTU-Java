package io.autoptu.core.hook;

/**
 * Stable origin categories for authoritative battle hooks.
 *
 * These categories describe where a rule comes from. They do not determine
 * execution order; each registry owns explicit ordering so Python parity can be
 * preserved when interactions between sources are order-sensitive.
 */
public enum HookSource {
    STATUS,
    ABILITY,
    ITEM,
    MOVE,
    TRAINER_FEATURE,
    PERK,
    TERRAIN,
    WEATHER,
    HAZARD,
    ZONE,
    AURA,
    TEMPORARY_EFFECT,
    REACTION,
    SYSTEM
}
