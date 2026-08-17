package io.autoptu.core.rules;

import io.autoptu.core.model.DamageDice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure PTU core lookup tables mirrored from Python auto_ptu.ptu_engine. */
public final class PtuTables {
    private record TypePair(String attack, String defense) {}

    private static final Map<Integer, DamageDice> DB_TABLE = Map.ofEntries(
            Map.entry(2, new DamageDice(1, 6, 3)),
            Map.entry(3, new DamageDice(1, 6, 5)),
            Map.entry(4, new DamageDice(1, 8, 6)),
            Map.entry(5, new DamageDice(1, 8, 8)),
            Map.entry(6, new DamageDice(2, 6, 8)),
            Map.entry(7, new DamageDice(2, 6, 10)),
            Map.entry(8, new DamageDice(2, 8, 10)),
            Map.entry(9, new DamageDice(2, 10, 10)),
            Map.entry(10, new DamageDice(3, 8, 10)),
            Map.entry(11, new DamageDice(3, 10, 10)),
            Map.entry(12, new DamageDice(3, 12, 10)),
            Map.entry(13, new DamageDice(4, 10, 10)),
            Map.entry(14, new DamageDice(4, 10, 15)),
            Map.entry(15, new DamageDice(4, 10, 20))
    );

    private static final Map<TypePair, Integer> TYPE_STEPS = buildTypeSteps();

    private PtuTables() {
    }

    /** Match Python db_to_dice exactly, including the historical beyond-table extension. */
    public static DamageDice dbToDice(int db) {
        DamageDice direct = DB_TABLE.get(db);
        if (direct != null) {
            return direct;
        }
        DamageDice db15 = DB_TABLE.get(15);
        return new DamageDice(db15.count(), db15.sides(), db15.flat() + 5 * (db - 15));
    }

    /**
     * Match Python's PTU step chart. Type names are intentionally case-sensitive,
     * because the Python table uses exact string keys and this is a parity port.
     */
    public static double typeMultiplier(String moveType, List<String> targetTypes) {
        int step = 0;
        if (targetTypes == null) {
            targetTypes = List.of();
        }
        for (String targetType : targetTypes) {
            Integer value = TYPE_STEPS.get(new TypePair(moveType, targetType));
            if (value != null && value == 0) {
                return 0.0;
            }
            if (value != null) {
                step += value;
            }
        }
        return switch (step) {
            case -2 -> 0.25;
            case -1 -> 0.5;
            case 0 -> 1.0;
            case 1 -> 1.5;
            default -> step >= 2 ? 2.0 : 1.0;
        };
    }

    private static Map<TypePair, Integer> buildTypeSteps() {
        Map<TypePair, Integer> values = new HashMap<>();

        add(values, "Fire", 1, "Grass", "Ice", "Bug", "Steel");
        add(values, "Water", 1, "Fire", "Ground", "Rock");
        add(values, "Electric", 1, "Water", "Flying");
        add(values, "Grass", 1, "Water", "Ground", "Rock");
        add(values, "Ice", 1, "Grass", "Ground", "Flying", "Dragon");
        add(values, "Fighting", 1, "Normal", "Ice", "Rock", "Dark", "Steel");
        add(values, "Poison", 1, "Grass", "Fairy");
        add(values, "Ground", 1, "Fire", "Electric", "Poison", "Rock", "Steel");
        add(values, "Flying", 1, "Grass", "Fighting", "Bug");
        add(values, "Psychic", 1, "Fighting", "Poison");
        add(values, "Bug", 1, "Grass", "Psychic", "Dark");
        add(values, "Rock", 1, "Fire", "Ice", "Flying", "Bug");
        add(values, "Ghost", 1, "Psychic", "Ghost");
        add(values, "Dragon", 1, "Dragon");
        add(values, "Dark", 1, "Psychic", "Ghost");
        add(values, "Steel", 1, "Ice", "Rock", "Fairy");
        add(values, "Fairy", 1, "Fighting", "Dragon", "Dark");

        add(values, "Fire", -1, "Fire", "Water", "Rock", "Dragon");
        add(values, "Water", -1, "Water", "Grass", "Dragon");
        add(values, "Electric", -1, "Electric", "Grass", "Dragon");
        add(values, "Grass", -1, "Fire", "Grass", "Poison", "Flying", "Bug", "Dragon", "Steel");
        add(values, "Ice", -1, "Fire", "Water", "Ice", "Steel");
        add(values, "Fighting", -1, "Poison", "Flying", "Psychic", "Bug", "Fairy");
        add(values, "Poison", -1, "Poison", "Ground", "Rock", "Ghost");
        add(values, "Ground", -1, "Grass", "Bug");
        add(values, "Flying", -1, "Electric", "Rock", "Steel");
        add(values, "Psychic", -1, "Psychic", "Steel");
        add(values, "Bug", -1, "Fire", "Fighting", "Poison", "Flying", "Ghost", "Steel", "Fairy");
        add(values, "Rock", -1, "Fighting", "Ground", "Steel");
        add(values, "Ghost", -1, "Dark");
        add(values, "Dragon", -1, "Steel");
        add(values, "Dark", -1, "Fighting", "Dark", "Fairy");
        add(values, "Steel", -1, "Fire", "Water", "Electric", "Steel");
        add(values, "Fairy", -1, "Fire", "Poison", "Steel");

        add(values, "Normal", 0, "Ghost");
        add(values, "Fighting", 0, "Ghost");
        add(values, "Poison", 0, "Steel");
        add(values, "Ground", 0, "Flying");
        add(values, "Psychic", 0, "Dark");
        add(values, "Ghost", 0, "Normal");
        add(values, "Electric", 0, "Ground");
        add(values, "Dragon", 0, "Fairy");

        return Map.copyOf(values);
    }

    private static void add(Map<TypePair, Integer> values, String attack, int step, String... defenses) {
        for (String defense : defenses) {
            values.put(new TypePair(attack, defense), step);
        }
    }
}
