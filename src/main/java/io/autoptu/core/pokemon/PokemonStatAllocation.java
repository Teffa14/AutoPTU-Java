package io.autoptu.core.pokemon;

import java.util.*;
import io.autoptu.core.pokemon.PokemonCreation.MoveProfile;
import io.autoptu.core.pokemon.PokemonCreation.Stats;

/** Deterministic CSV campaign allocation policy, isolated from content and persistence.
 * This is the Python project's minmax policy, not a claim that tabletop builds must use it.
 */
public final class PokemonStatAllocation {
    private static final int HP = 0, ATK = 1, DEF = 2, SPATK = 3, SPDEF = 4, SPD = 5;
    private enum Build { SWEEPER, WALL, MIXED_SWEEPER, MIXED_BRUISER, BRUISER }
    private record Profile(int primary, int secondary, int physical, int special, int repeatable, int status, Build build) {}
    private PokemonStatAllocation() {}

    public static Stats allocate(int level, Stats base, List<MoveProfile> moves) {
        PokemonCreation.checkLevel(level);
        int points = level + 10;
        int[] totals = base.values();
        Profile p = profile(totals, moves);
        int[] weights = weights(totals, p);
        List<int[]> candidates = new ArrayList<>(); candidates.add(weights);
        int[] speed = weights.clone();
        speed[SPD] += 10; speed[p.primary] += 4; speed[HP] = Math.max(0, speed[HP] - 6); candidates.add(speed);
        int[] offense = weights.clone();
        offense[p.primary] += 12; offense[SPD] += 4;
        offense[p.secondary] = Math.max(0, offense[p.secondary] - 4); offense[HP] = Math.max(0, offense[HP] - 4); candidates.add(offense);
        int[] bulk = weights.clone();
        bulk[HP] += 12; bulk[DEF] += 6; bulk[SPDEF] += 6;
        bulk[p.primary] = Math.max(0, bulk[p.primary] - 8); bulk[SPD] = Math.max(0, bulk[SPD] - 4); candidates.add(bulk);
        int[] mixed = weights.clone(); mixed[ATK] += 4; mixed[SPATK] += 4; mixed[SPD] += 4; mixed[HP] += 2; candidates.add(mixed);
        int[] best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] candidate : candidates) {
            int[] allocation = shares(points, candidate);
            rebalance(allocation, minimumHp(points, p.build), p);
            double score = score(points, totals, allocation, p);
            if (score > bestScore) { bestScore = score; best = allocation; }
        }
        return Stats.of(Objects.requireNonNull(best));
    }
    private static Profile profile(int[] totals, List<MoveProfile> moves) {
        int physical = 0, special = 0, repeatable = 0, status = 0;
        for (var move : moves) {
            String category = move.category().strip().toLowerCase(Locale.ROOT);
            if (move.damageBase() > 0) {
                if (category.equals("physical")) physical++;
                else if (category.equals("special")) special++;
            }
            if (category.equals("status") || move.damageBase() <= 0) status++;
            else {
                String frequency = move.frequency().strip().toLowerCase(Locale.ROOT);
                if (frequency.contains("at-will") || frequency.contains("eot")
                        || Set.of("standard", "free", "shift", "action").contains(frequency)) repeatable++;
            }
        }
        int primary = physical > special || (physical == special && totals[ATK] >= totals[SPATK]) ? ATK : SPATK;
        int secondary = primary == ATK ? SPATK : ATK;
        int offense = totals[primary], speed = totals[SPD], bulk = Math.max(totals[HP], Math.max(totals[DEF], totals[SPDEF]));
        Build build;
        if (physical > 0 && special > 0 && Math.abs(physical - special) <= 1)
            build = speed >= offense - 1 ? Build.MIXED_SWEEPER : Build.MIXED_BRUISER;
        else if (speed >= offense && speed >= bulk - 1) build = Build.SWEEPER;
        else if (bulk >= offense + 2) build = Build.WALL;
        else build = Build.BRUISER;
        return new Profile(primary, secondary, physical, special, repeatable, status, build);
    }
    private static int[] weights(int[] totals, Profile p) {
        int better = totals[DEF] >= totals[SPDEF] ? DEF : SPDEF;
        int[] weights = new int[6];
        switch (p.build) {
            case SWEEPER -> { weights[p.primary] = 46; weights[SPD] = 34; weights[HP] = 12; weights[better] = 8; }
            case WALL -> { weights[HP] = 38; weights[DEF] = 24; weights[SPDEF] = 24; weights[SPD] = 8; weights[p.primary] = 6; }
            case MIXED_SWEEPER -> weights = new int[]{12, 28, 4, 28, 4, 24};
            case MIXED_BRUISER -> weights = new int[]{26, 22, 8, 22, 8, 14};
            case BRUISER -> { weights[p.primary] = 38; weights[HP] = 28; weights[SPD] = 16; weights[DEF] = 8; weights[SPDEF] = 8; weights[p.secondary] = 2; }
        }
        return weights;
    }
    private static int[] shares(int points, int[] weights) {
        int[] allocation = new int[6];
        List<Integer> ranked = new ArrayList<>();
        for (int i = 0; i < 6; i++) if (weights[i] > 0) ranked.add(i);
        ranked.sort(Comparator.<Integer>comparingInt(i -> -weights[i]).thenComparingInt(i -> i));
        if (ranked.isEmpty()) { allocation[HP] = points; return allocation; }
        int seeded = Math.min(points, Math.min(2, ranked.size()));
        for (int i = 0; i < seeded; i++) allocation[ranked.get(i)]++;
        int remaining = points - seeded, totalWeight = Arrays.stream(weights).sum(), distributed = 0;
        double[] remainder = new double[6];
        for (int i : ranked) {
            double raw = ((double) weights[i] / totalWeight) * remaining;
            int floor = (int) raw; allocation[i] += floor; distributed += floor; remainder[i] = raw - floor;
        }
        ranked.sort(Comparator.<Integer>comparingDouble(i -> -remainder[i]).thenComparingInt(i -> -weights[i]).thenComparingInt(i -> i));
        for (int i = 0; i < remaining - distributed; i++) allocation[ranked.get(i % ranked.size())]++;
        return allocation;
    }
    private static int minimumHp(int points, Build build) {
        double ratio = switch (build) {
            case SWEEPER -> 0.22; case MIXED_SWEEPER -> 0.20; case BRUISER -> 0.26;
            case MIXED_BRUISER -> 0.28; case WALL -> 0.34;
        };
        ratio = Math.min(0.42, ratio + Math.min(0.05, Math.max(0.0, (points - 20.0) / 90.0 * 0.05)));
        // Python round uses ties-to-even, not Java Math.round.
        int floor = Math.max(1, (int) Math.rint(points * ratio));
        if (points >= 4) floor = Math.max(2, floor);
        return Math.min(points, floor);
    }
    private static void rebalance(int[] allocation, int floor, Profile p) {
        int deficit = Math.max(0, floor - allocation[HP]);
        int[] priority = switch (p.build) {
            case WALL -> new int[]{DEF, SPDEF, SPD, p.primary, p.secondary, ATK, SPATK};
            case MIXED_SWEEPER, MIXED_BRUISER -> new int[]{ATK, SPATK, SPD, DEF, SPDEF, HP};
            default -> new int[]{p.primary, SPD, DEF, SPDEF, p.secondary, ATK, SPATK};
        };
        var donors = new LinkedHashSet<Integer>();
        for (int i : priority) if (i != HP) donors.add(i);
        for (int i = 1; i < 6; i++) donors.add(i);
        for (int i : donors) {
            int take = Math.min(allocation[i], deficit); allocation[i] -= take; allocation[HP] += take; deficit -= take;
            if (deficit == 0) break;
        }
    }
    private static double score(int points, int[] totals, int[] allocation, Profile p) {
        int[] stats = new int[6]; for (int i = 0; i < 6; i++) stats[i] = totals[i] + allocation[i];
        int maxHp = Math.max(1, points - 10) + 3 * stats[HP] + 10;
        double primary = stats[p.primary], secondary = stats[p.secondary], speed = stats[SPD];
        double defense = stats[DEF], spdef = stats[SPDEF], hp = stats[HP];
        double breakpoints = (stats[SPD] / 5) * 1.1 + (stats[DEF] / 5) * 0.9 + (stats[SPDEF] / 5) * 0.9;
        double linear = hp * 3.0 + (defense + spdef) * 0.9;
        double product = Math.sqrt(maxHp * (defense + spdef + 2.0));
        double mixed = p.physical > 0 && p.special > 0 ? 1.0 : 0.0;
        double reliability = 1.0 + Math.min(2, Math.max(0, p.repeatable)) * 0.12;
        double setup = Math.min(2, Math.max(0, p.status)) * 0.15;
        double offense = (primary * 1.0 + secondary * (0.35 + 0.25 * mixed)) * reliability;
        return switch (p.build) {
            case SWEEPER -> offense * 3.8 + speed * 2.4 + breakpoints * 1.6 + linear * 0.7 + setup;
            case MIXED_SWEEPER -> offense * 3.4 + speed * 2.0 + breakpoints * 1.7 + linear * 0.85 + setup;
            case WALL -> linear * 3.1 + product * 0.55 + breakpoints * 2.1 + offense * 1.3 + setup;
            case MIXED_BRUISER -> offense * 2.5 + linear * 2.3 + breakpoints * 1.8 + speed * 1.1 + setup;
            case BRUISER -> offense * 3.0 + linear * 1.8 + breakpoints * 1.7 + speed * 1.2 + setup;
        };
    }
}
