package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpatialDamageAuraOracleParityTest {
    @Test
    void spatialDamageAurasMatchPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.spatial.damage.auras.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] c = lines.get(index).split("\\t", -1);
            String name = c[0];
            String moveType = c[1];
            String category = c[2];
            String ability = c[3];
            String holderTeam = c[4];
            GridCoord holderPosition = new GridCoord(Integer.parseInt(c[5]), Integer.parseInt(c[6]));
            boolean active = Boolean.parseBoolean(c[7]);
            boolean fainted = Boolean.parseBoolean(c[8]);
            String holderPrimaryType = c[9];
            String expectedSource = c[10];
            int expectedBonus = Integer.parseInt(c[11]);
            int expectedEvents = Integer.parseInt(c[12]);

            PostDamageHookResult result = BuiltinPostDamageHooks.standardRegistry().resolve(
                    context(
                            moveType,
                            category,
                            ability,
                            holderTeam,
                            holderPosition,
                            active,
                            fainted,
                            holderPrimaryType,
                            name.equals("first_source_wins")
                    )
            );

            String actualSource = result.events().stream()
                    .filter(RuleEffectEvent.class::isInstance)
                    .map(RuleEffectEvent.class::cast)
                    .map(RuleEffectEvent::actorId)
                    .findFirst()
                    .orElse("");
            assertEquals(expectedBonus, result.flatDamageBonus(), name + " final damage bonus");
            assertEquals(expectedEvents, result.events().size(), name + " event count");
            assertEquals(expectedSource, actualSource, name + " selected source");
        }
    }

    private static PostDamageHookContext context(
            String moveType,
            String category,
            String ability,
            String holderTeam,
            GridCoord holderPosition,
            boolean active,
            boolean fainted,
            String holderPrimaryType,
            boolean secondHolder
    ) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 20, List.of(), List.of("Normal"));
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 0), 20, List.of(), List.of("Normal"));
        RuntimeCombatantState holder = combatant(
                "ally-1",
                holderPosition,
                fainted ? 0 : 20,
                List.of(ability),
                List.of(holderPrimaryType.isBlank() ? "Normal" : holderPrimaryType)
        );
        List<RuntimeCombatantState> combatants = secondHolder
                ? List.of(
                        actor,
                        target,
                        holder,
                        combatant(
                                "ally-2",
                                new GridCoord(2, 1),
                                20,
                                List.of(ability),
                                List.of(holderPrimaryType.isBlank() ? "Normal" : holderPrimaryType)
                        )
                )
                : List.of(actor, target, holder);
        Map<String, CombatantAffiliationState> affiliations = secondHolder
                ? Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true),
                        "ally-1", new CombatantAffiliationState(holderTeam, active),
                        "ally-2", new CombatantAffiliationState("A", true))
                : Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true),
                        "ally-1", new CombatantAffiliationState(holderTeam, active));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations, Map.of(), Map.of()
        );
        MoveOption move = MoveOption.standard(
                "oracle-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, 6, 20, category, moveType)
        );
        return new PostDamageHookContext(state, "actor", "target", actor, target, move, move.requireCombatProfile());
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            List<String> abilities,
            List<String> types
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                20,
                new ActionBudget(),
                null, null, 0, false, false, false, false,
                types, List.of(), abilities
        );
    }
}
