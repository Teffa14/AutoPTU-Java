package io.autoptu.core.pokemon;

import io.autoptu.core.random.PythonRandom;
import java.util.*;

/** Creation primitives matching Python AutoPTU, not Minecraft's native stat formula.
 * Allocation precedes nature application, as in CsvRandomCampaignBuilder -> PokemonState.
 * This does not grant moves, apply battle passives, injuries or temporary HP effects.
 */
public final class PokemonCreation {
    public static final String POLICY = "python-csv-minmax-v1";
    private PokemonCreation() {}

    public record Stats(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {
        public int[] values() { return new int[]{hp, attack, defense, specialAttack, specialDefense, speed}; }
        public static Stats of(int[] values) {
            if (values.length != 6) throw new IllegalArgumentException("Six statistics required");
            return new Stats(values[0], values[1], values[2], values[3], values[4], values[5]);
        }
        public int total() { return Arrays.stream(values()).sum(); }
    }
    public record Nature(String name, Stats modifiers) {
        public Nature { Objects.requireNonNull(name); Objects.requireNonNull(modifiers); if (name.isBlank()) throw new IllegalArgumentException("Nature name required"); }
    }
    /** Move metadata is a build-profile input only, never a grant or legality certificate. */
    public record MoveProfile(String category, int damageBase, String frequency) {
        public MoveProfile { Objects.requireNonNull(category); Objects.requireNonNull(frequency); }
    }
    public record AbilityPools(List<String> starting, List<String> basic, List<String> advanced, List<String> high) {
        public AbilityPools {
            starting = List.copyOf(starting); basic = List.copyOf(basic);
            advanced = List.copyOf(advanced); high = List.copyOf(high);
        }
    }
    public record Result(int level, String nature, Stats base, Stats allocation, Stats finalStats,
                         int baseMaximumHp, List<String> abilities, String allocationPolicy) {
        public Result { abilities = List.copyOf(abilities); }
    }

    public static Result create(int level, Stats base, List<MoveProfile> profile,
                                List<Nature> natures, AbilityPools pools, PythonRandom random) {
        checkLevel(level);
        for (int value : base.values()) if (value < 1 || value > 1000) throw new IllegalArgumentException("Invalid PTU base stat");
        Objects.requireNonNull(profile); Objects.requireNonNull(random); Objects.requireNonNull(pools);
        var byName = new TreeMap<String, Nature>();
        for (Nature nature : natures) {
            Nature previous = byName.putIfAbsent(nature.name(), nature);
            if (previous != null && !previous.equals(nature)) throw new IllegalArgumentException("Conflicting nature definitions");
        }
        if (byName.isEmpty()) throw new IllegalArgumentException("Nature catalog required");
        var choices = new ArrayList<>(byName.values());
        Nature nature = choices.get(random.choiceIndex(choices.size()));
        var abilities = selectAbilities(pools, level, random, List.of());
        Stats allocation = PokemonStatAllocation.allocate(level, base, profile);
        int[] values = base.values(), points = allocation.values(), modifiers = nature.modifiers().values();
        for (int i = 0; i < 6; i++) values[i] = Math.max(1, values[i] + points[i] + modifiers[i]);
        Stats resolved = Stats.of(values);
        return new Result(level, nature.name(), base, allocation, resolved,
                Math.addExact(level + 10, Math.multiplyExact(3, resolved.hp())), abilities, POLICY);
    }

    /** Exact tier choice/fallback order of foundry_loader.pick_abilities_for_level.
     * Existing selections are preserved. Cross-tier duplicates retain Python's weighting.
     */
    public static List<String> selectAbilities(AbilityPools pools, int level, PythonRandom random, List<String> existing) {
        checkLevel(level);
        var current = new ArrayList<>(existing.stream().filter(name -> !name.isEmpty()).toList());
        var starting = unique(pools.starting()); var basic = unique(pools.basic());
        var advanced = unique(pools.advanced()); var high = unique(pools.high());
        var base = starting.isEmpty() ? basic : starting;
        var basics = unique(concat(starting, basic));
        int desired = level < 20 ? 1 : level < 40 ? 2 : 3;
        if (current.isEmpty()) {
            for (var pool : List.of(base, basics, advanced, high)) {
                if (pick(pool, current, random)) break;
            }
        }
        if (desired >= 2 && current.size() < 2) pick(concat(basics, advanced), current, random);
        if (desired >= 3 && current.size() < 3) pick(concat(concat(basics, advanced), high), current, random);
        return List.copyOf(current);
    }
    private static boolean pick(List<String> pool, List<String> current, PythonRandom random) {
        Set<String> occupied = new HashSet<>();
        current.forEach(name -> occupied.add(name.toLowerCase(Locale.ROOT)));
        var choices = pool.stream().filter(name -> !occupied.contains(name.toLowerCase(Locale.ROOT))).toList();
        if (choices.isEmpty()) return false;
        current.add(choices.get(random.choiceIndex(choices.size()))); return true;
    }
    private static List<String> unique(List<String> values) { return new ArrayList<>(new LinkedHashSet<>(values)); }
    private static List<String> concat(List<String> left, List<String> right) {
        var result = new ArrayList<>(left); result.addAll(right); return result;
    }
    static void checkLevel(int level) { if (level < 1 || level > 100) throw new IllegalArgumentException("PTU level must be 1..100"); }
}
