package io.autoptu.core.rules;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.GridState;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Deterministic legal-decision boundary for AI/autoplay and external controllers.
 *
 * Minecraft, Cobblemon, and Craftics must not decide PTU legality. They translate
 * world state into core DTOs, request choices here, and execute/render the choice
 * selected by the authoritative battle controller.
 */
public final class AutobattlerActionSpace {
    private AutobattlerActionSpace() {
    }

    public static List<BattleChoice> legalChoices(
            String actorId,
            String actorSize,
            MovementGrid grid,
            MovementProfile movement,
            ActionBudget budget,
            List<MoveOption> moves,
            List<TargetCandidate> targetCandidates,
            Set<GridCoord> lineOfSightBlockers,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        List<BattleChoice> choices = new ArrayList<>();
        choices.addAll(legalShiftChoices(
                actorId,
                grid,
                movement,
                budget,
                movementPenalty,
                canFit
        ));
        choices.addAll(legalMoveChoices(
                actorId,
                actorSize,
                grid,
                movement.position(),
                budget,
                moves,
                targetCandidates,
                lineOfSightBlockers
        ));
        choices.sort(Comparator.comparing(BattleChoice::stableKey));
        return List.copyOf(choices);
    }

    public static List<ShiftChoice> legalShiftChoices(
            String actorId,
            MovementGrid grid,
            MovementProfile movement,
            ActionBudget budget,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        requireActor(actorId);
        requireGrid(grid);
        requireBudget(budget);
        if (movement == null) {
            throw new IllegalArgumentException("movement is required");
        }
        if (!hasCapacity(budget, ActionType.SHIFT)) {
            return List.of();
        }

        List<ShiftChoice> choices = new ArrayList<>();
        for (GridCoord destination : Movement.legalShiftTiles(grid, movement, movementPenalty, canFit)) {
            // Python includes the origin in reachability. Staying in place is not a Shift action.
            if (!destination.equals(movement.position())) {
                choices.add(new ShiftChoice(actorId, destination));
            }
        }
        choices.sort(Comparator
                .comparingInt((ShiftChoice choice) -> choice.destination().x())
                .thenComparingInt(choice -> choice.destination().y()));
        return List.copyOf(choices);
    }

    public static List<MoveChoice> legalMoveChoices(
            String actorId,
            String actorSize,
            MovementGrid movementGrid,
            GridCoord actorAnchor,
            ActionBudget budget,
            List<MoveOption> moves,
            List<TargetCandidate> targetCandidates,
            Set<GridCoord> lineOfSightBlockers
    ) {
        requireActor(actorId);
        requireGrid(movementGrid);
        requireBudget(budget);
        if (actorAnchor == null) {
            throw new IllegalArgumentException("actorAnchor is required");
        }

        GridState grid = new GridState(movementGrid.width(), movementGrid.height());
        List<MoveOption> safeMoves = moves == null ? List.of() : moves;
        List<TargetCandidate> safeTargets = targetCandidates == null ? List.of() : targetCandidates;
        Set<GridCoord> blockers = lineOfSightBlockers == null ? Set.of() : lineOfSightBlockers;
        String normalizedActorSize = actorSize == null || actorSize.isBlank() ? "Medium" : actorSize;

        List<MoveChoice> choices = new ArrayList<>();
        for (MoveOption move : safeMoves) {
            if (move == null || !hasCapacity(budget, move.actionType())) {
                continue;
            }

            String targetKind = Targeting.normalizedTargetKind(move.spec());
            String areaKind = Targeting.normalizedAreaKind(move.spec());

            if (targetKind.equals("field")) {
                choices.add(new MoveChoice(
                        actorId,
                        move.moveId(),
                        ChoiceTargetMode.FIELD,
                        "",
                        actorAnchor,
                        move.actionType()
                ));
                continue;
            }

            boolean directionalSelfArea = targetKind.equals("self")
                    && (areaKind.equals("line") || areaKind.equals("cone") || areaKind.equals("closeblast"));
            boolean tileAimedArea = directionalSelfArea
                    || (!targetKind.equals("self") && !areaKind.isEmpty());

            if (targetKind.equals("self") && !directionalSelfArea) {
                choices.add(new MoveChoice(
                        actorId,
                        move.moveId(),
                        ChoiceTargetMode.SELF,
                        actorId,
                        actorAnchor,
                        move.actionType()
                ));
                continue;
            }

            if (tileAimedArea) {
                List<GridCoord> anchors = new ArrayList<>(Targeting.targetAnchorTiles(grid, actorAnchor, move.spec()));
                anchors.sort(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y));
                for (GridCoord anchor : anchors) {
                    if (move.requiresLineOfSight()
                            && !Targeting.lineOfSightClear(grid, actorAnchor, anchor, blockers)) {
                        continue;
                    }
                    choices.add(new MoveChoice(
                            actorId,
                            move.moveId(),
                            ChoiceTargetMode.TILE,
                            "",
                            anchor,
                            move.actionType()
                    ));
                }
                continue;
            }

            for (TargetCandidate target : safeTargets) {
                if (target == null) {
                    continue;
                }
                if (!Targeting.isTargetInRange(
                        actorAnchor,
                        target.anchor(),
                        move.spec(),
                        normalizedActorSize,
                        target.sizeLabel(),
                        grid
                )) {
                    continue;
                }
                if (move.requiresLineOfSight()
                        && !Targeting.lineOfSightClear(grid, actorAnchor, target.anchor(), blockers)) {
                    continue;
                }
                choices.add(new MoveChoice(
                        actorId,
                        move.moveId(),
                        ChoiceTargetMode.COMBATANT,
                        target.combatantId(),
                        target.anchor(),
                        move.actionType()
                ));
            }
        }

        choices.sort(Comparator.comparing(MoveChoice::stableKey));
        return List.copyOf(choices);
    }

    private static boolean hasCapacity(ActionBudget budget, ActionType actionType) {
        if (actionType == ActionType.FREE) {
            return true;
        }
        return budget.hasActionAvailable(actionType) || budget.extraCount(actionType) > 0;
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
    }

    private static void requireGrid(MovementGrid grid) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
    }

    private static void requireBudget(ActionBudget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("budget is required");
        }
    }
}
