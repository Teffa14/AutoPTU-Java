package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic geometry policy used by interception before any movement is committed.
 * Candidate and line-tile ordering intentionally mirror the pinned Python oracle's
 * stable footprint-distance sorts. The caller remains responsible for eligibility,
 * legal-shift generation, RNG/check resolution, and committing authoritative movement.
 */
public final class InterceptGeometryResolution {
    private InterceptGeometryResolution() {}

    public record Candidate(String combatantId, GridCoord position, String sizeLabel) {
        public Candidate {
            if (combatantId == null || combatantId.isBlank()) {
                throw new IllegalArgumentException("combatantId is required");
            }
            if (position == null) throw new IllegalArgumentException("position is required");
            sizeLabel = sizeLabel == null || sizeLabel.isBlank() ? "Medium" : sizeLabel.strip();
        }
    }

    /** Python sorts eligible interceptors by footprint distance to the Medium target anchor. */
    public static List<Candidate> orderCandidates(Collection<Candidate> candidates, GridCoord targetPosition) {
        if (targetPosition == null) throw new IllegalArgumentException("target position is required");
        ArrayList<Candidate> ordered = new ArrayList<>();
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate != null) ordered.add(candidate);
            }
        }
        ordered.sort(Comparator.comparingInt(candidate -> Targeting.footprintDistance(
                candidate.position(), candidate.sizeLabel(), targetPosition, "Medium"
        )));
        return List.copyOf(ordered);
    }

    /**
     * If already on the attack line, preserve the current anchor. Otherwise restrict the
     * line to legal Shift tiles and choose the nearest tile by footprint distance. Stable
     * sort semantics preserve the line's original order for equal distances.
     */
    public static GridCoord nearestReachableAttackLineTile(
            Candidate interceptor,
            Collection<GridCoord> attackLine,
            Collection<GridCoord> legalShiftTiles
    ) {
        if (interceptor == null) throw new IllegalArgumentException("interceptor is required");
        if (attackLine == null || attackLine.isEmpty()) return null;

        List<GridCoord> line = attackLine.stream().filter(java.util.Objects::nonNull).toList();
        if (line.contains(interceptor.position())) return interceptor.position();

        Set<GridCoord> reachable = new LinkedHashSet<>();
        if (legalShiftTiles != null) {
            for (GridCoord coord : legalShiftTiles) if (coord != null) reachable.add(coord);
        }
        ArrayList<GridCoord> lineTiles = new ArrayList<>();
        for (GridCoord coord : line) if (reachable.contains(coord)) lineTiles.add(coord);
        if (lineTiles.isEmpty()) return null;

        lineTiles.sort(Comparator.comparingInt(coord -> Targeting.footprintDistance(
                interceptor.position(), interceptor.sizeLabel(), coord, "Medium"
        )));
        return lineTiles.get(0);
    }

    /**
     * Python computes the Intercept check DC from footprint distance to the selected
     * intercept anchor and floors an overlapping distance to one before multiplying by 3.
     */
    public static int checkDistance(Candidate interceptor, GridCoord interceptPosition) {
        if (interceptor == null) throw new IllegalArgumentException("interceptor is required");
        if (interceptPosition == null) throw new IllegalArgumentException("intercept position is required");
        return Math.max(1, Targeting.footprintDistance(
                interceptor.position(), interceptor.sizeLabel(), interceptPosition, "Medium"
        ));
    }
}