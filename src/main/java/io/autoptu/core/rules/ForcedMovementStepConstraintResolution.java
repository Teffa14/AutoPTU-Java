package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generic candidate-step constraints for server-owned forced movement.
 *
 * <p>Python expresses these rules inline inside apply_forced_movement. Java keeps the behavior
 * declarative: temporary-effect families materialize language-neutral constraints, and the shared
 * displacement loop asks this resolver whether each candidate anchor remains legal.</p>
 */
public final class ForcedMovementStepConstraintResolution {
    public enum Kind {
        MAX_FOOTPRINT_DISTANCE_FROM_ANCHOR
    }

    private record TemporaryRule(String effectName, Kind kind, int limit, String referenceSize) {}

    private static final List<TemporaryRule> TEMPORARY_RULES = List.of(
            new TemporaryRule("shadow_tag_anchor", Kind.MAX_FOOTPRINT_DISTANCE_FROM_ANCHOR, 5, "Medium")
    );

    /** Language-neutral projection of temporary-effect metadata used by this rule family. */
    public record TemporaryEffect(String name, Integer expiresRound, GridCoord anchor) {
        public TemporaryEffect {
            name = normalize(name);
        }

        public boolean activeAt(int currentRound) {
            return expiresRound == null || currentRound <= expiresRound;
        }
    }

    /** Immutable rule instance evaluated for every candidate forced-movement anchor. */
    public record Constraint(
            String sourceEffect,
            Kind kind,
            GridCoord anchor,
            int limit,
            String referenceSize
    ) {
        public Constraint {
            sourceEffect = normalize(sourceEffect);
            if (kind == null) throw new IllegalArgumentException("constraint kind is required");
            if (anchor == null) throw new IllegalArgumentException("constraint anchor is required");
            if (limit < 0) throw new IllegalArgumentException("constraint limit cannot be negative");
            referenceSize = referenceSize == null || referenceSize.isBlank() ? "Medium" : referenceSize.strip();
        }
    }

    public record Decision(boolean allowed, String blockingEffect) {
        static Decision allowedDecision() {
            return new Decision(true, null);
        }

        static Decision blocked(String effect) {
            return new Decision(false, normalize(effect));
        }
    }

    private ForcedMovementStepConstraintResolution() {}

    /** Effect families that runtime state should project into this resolver. */
    public static Set<String> temporaryEffectNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (TemporaryRule rule : TEMPORARY_RULES) names.add(rule.effectName());
        return Set.copyOf(names);
    }

    /**
     * Convert scalar temporary-effect payload into the language-neutral projection.
     *
     * <p>Java stores tuple-like coordinates as anchor_x/anchor_y scalar fields so state remains
     * serialization-safe for headless tests and adapters.</p>
     */
    public static Optional<TemporaryEffect> projectTemporaryEffect(String name, Map<String, ?> payload) {
        String normalized = normalize(name);
        if (!temporaryEffectNames().contains(normalized)) return Optional.empty();
        Map<String, ?> values = payload == null ? Map.of() : payload;
        Integer anchorX = integer(values.get("anchor_x"));
        Integer anchorY = integer(values.get("anchor_y"));
        if (anchorX == null || anchorY == null) return Optional.empty();
        return Optional.of(new TemporaryEffect(
                normalized,
                integer(values.get("expires_round")),
                new GridCoord(anchorX, anchorY)
        ));
    }

    /** Materialize active candidate-step constraints in stable rule/effect order. */
    public static List<Constraint> resolve(List<TemporaryEffect> temporaryEffects, int currentRound) {
        List<TemporaryEffect> effects = temporaryEffects == null ? List.of() : temporaryEffects;
        ArrayList<Constraint> constraints = new ArrayList<>();
        for (TemporaryRule rule : TEMPORARY_RULES) {
            for (TemporaryEffect effect : effects) {
                if (effect == null || !effect.name().equals(rule.effectName())) continue;
                if (!effect.activeAt(currentRound) || effect.anchor() == null) continue;
                constraints.add(new Constraint(
                        rule.effectName(), rule.kind(), effect.anchor(), rule.limit(), rule.referenceSize()
                ));
                // Python stops scanning shadow_tag_anchor entries after the first active valid anchor.
                break;
            }
        }
        return List.copyOf(constraints);
    }

    /** Evaluate all active constraints against one candidate anchor. */
    public static Decision evaluate(
            List<Constraint> constraints,
            GridCoord candidate,
            String movingSize
    ) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        List<Constraint> active = constraints == null ? List.of() : constraints;
        for (Constraint constraint : active) {
            if (constraint == null) continue;
            if (constraint.kind() == Kind.MAX_FOOTPRINT_DISTANCE_FROM_ANCHOR) {
                int distance = Targeting.footprintDistance(
                        candidate,
                        movingSize == null ? "Medium" : movingSize,
                        constraint.anchor(),
                        constraint.referenceSize()
                );
                if (distance > constraint.limit()) return Decision.blocked(constraint.sourceEffect());
            }
        }
        return Decision.allowedDecision();
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
