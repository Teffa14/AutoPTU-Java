package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.JumpProfile;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/** Pure PTU movement-legality primitives extracted from Python movement.py. */
public final class Movement {
    private Movement() {
    }

    public static List<GridCoord> neighboringTiles(GridCoord coord) {
        return List.of(
                new GridCoord(coord.x() + 1, coord.y()),
                new GridCoord(coord.x() - 1, coord.y()),
                new GridCoord(coord.x(), coord.y() + 1),
                new GridCoord(coord.x(), coord.y() - 1)
        );
    }

    /** Match Python _step_toward: each step may move on both axes. */
    public static List<GridCoord> stepToward(GridCoord origin, GridCoord destination) {
        int x = origin.x();
        int y = origin.y();
        List<GridCoord> path = new ArrayList<>();
        while (x != destination.x() || y != destination.y()) {
            if (x < destination.x()) {
                x++;
            } else if (x > destination.x()) {
                x--;
            }
            if (y < destination.y()) {
                y++;
            } else if (y > destination.y()) {
                y--;
            }
            path.add(new GridCoord(x, y));
        }
        return path;
    }

    /**
     * Port of Python legal_shift_tiles after BattleState-specific capability resolution.
     *
     * canFit represents footprint/collision legality for a landing tile. The origin is
     * always retained, matching the Python implementation.
     */
    public static Set<GridCoord> legalShiftTiles(
            MovementGrid grid,
            MovementProfile actor,
            int limitPenalty,
            Predicate<GridCoord> canFit
    ) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        Predicate<GridCoord> fit = canFit == null ? ignored -> true : canFit;
        int penalty = Math.max(0, limitPenalty);

        int landLimit = Math.max(0, actor.overland() - penalty);
        int swimLimit = Math.max(0, actor.swimSpeed() - penalty);
        int skyLimit = Math.max(0, actor.skySpeed() - penalty);
        if (actor.sprintMultiplier() != 1.0) {
            landLimit = (int) Math.ceil(landLimit * actor.sprintMultiplier());
            swimLimit = (int) Math.ceil(swimLimit * actor.sprintMultiplier());
            skyLimit = (int) Math.ceil(skyLimit * actor.sprintMultiplier());
        }

        record State(GridCoord coord, int wallrunUsed) {}
        record Node(int cost, GridCoord coord, int wallrunUsed) {}

        Comparator<Node> nodeOrder = Comparator
                .comparingInt(Node::cost)
                .thenComparingInt(node -> node.coord().x())
                .thenComparingInt(node -> node.coord().y())
                .thenComparingInt(Node::wallrunUsed);

        Map<State, Integer> visited = new HashMap<>();
        State initial = new State(actor.position(), 0);
        visited.put(initial, 0);

        Set<GridCoord> reachable = new LinkedHashSet<>();
        reachable.add(actor.position());

        PriorityQueue<Node> heap = new PriorityQueue<>(nodeOrder);
        heap.add(new Node(0, actor.position(), 0));

        while (!heap.isEmpty()) {
            Node current = heap.remove();
            State currentState = new State(current.coord(), current.wallrunUsed());
            if (current.cost() > visited.getOrDefault(currentState, 0)) {
                continue;
            }

            for (GridCoord next : neighboringTiles(current.coord())) {
                if (!grid.inBounds(next)) {
                    continue;
                }

                String tileType = normalizedTileType(grid.tileType(next));
                if (tileType.contains("void")) {
                    continue;
                }

                boolean isWater = tileType.contains("water");
                int limit;
                if (actor.canFly()) {
                    limit = skyLimit;
                } else {
                    limit = landLimit;
                    if (isWater) {
                        if (!actor.canSwim()) {
                            continue;
                        }
                        limit = swimLimit;
                    }
                }
                if (limit <= 0) {
                    continue;
                }

                int stepCost = 1;
                if (!actor.canFly()
                        && !actor.liquefied()
                        && !actor.ignoresRoughTerrain()
                        && (tileType.contains("difficult") || tileType.contains("rough"))) {
                    stepCost = 2;
                }

                int newCost = current.cost() + stepCost;
                if (newCost > limit) {
                    continue;
                }

                boolean blocked = isBlocked(grid, next, tileType);
                int nextWallrunUsed = current.wallrunUsed();
                if (blocked && !(actor.canFly() || actor.canBurrow() || actor.canPhase() || actor.liquefied())) {
                    nextWallrunUsed++;
                    if (actor.wallrunnerLimit() <= 0 || nextWallrunUsed > actor.wallrunnerLimit()) {
                        continue;
                    }
                }

                boolean landingAllowed = !blocked
                        || actor.canFly()
                        || actor.canBurrow()
                        || actor.canPhase()
                        || actor.liquefied();
                if (landingAllowed && !fit.test(next)) {
                    continue;
                }

                State nextState = new State(next, nextWallrunUsed);
                Integer previousCost = visited.get(nextState);
                if (previousCost == null || newCost < previousCost) {
                    visited.put(nextState, newCost);
                    heap.add(new Node(newCost, next, nextWallrunUsed));
                    if (landingAllowed) {
                        reachable.add(next);
                    }
                }
            }
        }

        reachable.removeIf(coord -> !coord.equals(actor.position()) && !fit.test(coord));
        return reachable;
    }

    public static Set<GridCoord> legalShiftTiles(
            MovementGrid grid,
            MovementProfile actor
    ) {
        return legalShiftTiles(grid, actor, 0, ignored -> true);
    }

    /** Match Python _jump_path_blocked_steps after capabilities are resolved. */
    public static int jumpPathBlockedSteps(
            MovementGrid grid,
            JumpProfile actor,
            GridCoord origin,
            GridCoord destination
    ) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (actor.canFly() || actor.canPhase() || actor.liquefied()) {
            return 0;
        }

        List<GridCoord> path = stepToward(origin, destination);
        if (path.size() <= 1) {
            return 0;
        }

        int blockedSteps = 0;
        for (int index = 0; index < path.size() - 1; index++) {
            GridCoord coord = path.get(index);
            String tileType = normalizedTileType(grid.tileType(coord));
            if (isBlocked(grid, coord, tileType) || tileType.contains("void")) {
                blockedSteps++;
            }
        }
        return blockedSteps;
    }

    /** Port of Python legal_long_jump_tiles with a resolved jump profile. */
    public static Set<GridCoord> legalLongJumpTiles(
            MovementGrid grid,
            JumpProfile actor,
            Predicate<GridCoord> canFit
    ) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        Predicate<GridCoord> fit = canFit == null ? ignored -> true : canFit;
        GridCoord origin = actor.position();
        Set<GridCoord> reachable = new LinkedHashSet<>();
        reachable.add(origin);

        int limit = actor.longJump();
        if (limit <= 0) {
            return reachable;
        }

        int maxLimit = limit + actor.wallrunnerLimit();
        for (int dx = -maxLimit; dx <= maxLimit; dx++) {
            for (int dy = -maxLimit; dy <= maxLimit; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                if (Math.max(Math.abs(dx), Math.abs(dy)) > maxLimit) {
                    continue;
                }

                GridCoord destination = new GridCoord(origin.x() + dx, origin.y() + dy);
                if (!grid.inBounds(destination)) {
                    continue;
                }
                String tileType = normalizedTileType(grid.tileType(destination));
                if (tileType.contains("void")) {
                    continue;
                }

                boolean blocked = isBlocked(grid, destination, tileType);
                if (blocked && !(actor.canFly() || actor.canBurrow() || actor.canPhase() || actor.liquefied())) {
                    continue;
                }

                boolean water = tileType.contains("water");
                if (water && !(actor.canFly() || actor.canSwim())) {
                    continue;
                }
                if (!fit.test(destination)) {
                    continue;
                }

                int blockedSteps = jumpPathBlockedSteps(grid, actor, origin, destination);
                if (blockedSteps > 0) {
                    if (blockedSteps > actor.wallrunnerLimit()) {
                        continue;
                    }
                } else if (Math.max(Math.abs(dx), Math.abs(dy)) > limit) {
                    continue;
                }
                reachable.add(destination);
            }
        }
        return reachable;
    }

    public static Set<GridCoord> legalLongJumpTiles(MovementGrid grid, JumpProfile actor) {
        return legalLongJumpTiles(grid, actor, ignored -> true);
    }

    /** Port of Python legal_high_jump_tiles with a resolved jump profile. */
    public static Set<GridCoord> legalHighJumpTiles(
            MovementGrid grid,
            JumpProfile actor,
            Predicate<GridCoord> canFit
    ) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        Predicate<GridCoord> fit = canFit == null ? ignored -> true : canFit;
        GridCoord origin = actor.position();
        Set<GridCoord> reachable = new LinkedHashSet<>();
        reachable.add(origin);

        int limit = actor.highJump();
        if (limit <= 0) {
            return reachable;
        }

        for (int dx = -limit; dx <= limit; dx++) {
            for (int dy = -limit; dy <= limit; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                if (Math.max(Math.abs(dx), Math.abs(dy)) > limit) {
                    continue;
                }

                GridCoord destination = new GridCoord(origin.x() + dx, origin.y() + dy);
                if (!grid.inBounds(destination)) {
                    continue;
                }
                String tileType = normalizedTileType(grid.tileType(destination));
                if (tileType.contains("void")) {
                    continue;
                }

                boolean blocked = isBlocked(grid, destination, tileType);
                if (blocked && !(actor.canFly() || actor.canBurrow() || actor.canPhase() || actor.liquefied())) {
                    continue;
                }
                if (!fit.test(destination)) {
                    continue;
                }
                reachable.add(destination);
            }
        }
        return reachable;
    }

    public static Set<GridCoord> legalHighJumpTiles(MovementGrid grid, JumpProfile actor) {
        return legalHighJumpTiles(grid, actor, ignored -> true);
    }

    /** Python legal_jump_tiles is currently an alias for long jump. */
    public static Set<GridCoord> legalJumpTiles(MovementGrid grid, JumpProfile actor) {
        return legalLongJumpTiles(grid, actor);
    }

    private static boolean isBlocked(MovementGrid grid, GridCoord coord, String tileType) {
        return grid.isBlocker(coord)
                || tileType.contains("wall")
                || tileType.contains("blocker")
                || tileType.contains("blocking");
    }

    private static String normalizedTileType(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
