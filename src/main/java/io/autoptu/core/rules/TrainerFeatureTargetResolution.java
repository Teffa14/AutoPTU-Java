package io.autoptu.core.rules;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Language-neutral target-scope contract for Trainer Feature effects.
 *
 * <p>This mirrors the pinned Python dispatcher's _targets_for_feature() and
 * _target_ids_for_scope() behavior. It deliberately stops before effect
 * application so target selection can be reused by all Feature effect families.
 */
public final class TrainerFeatureTargetResolution {
    private TrainerFeatureTargetResolution() {}

    public record CombatantView(
            String id,
            String controllerId,
            boolean active,
            boolean fainted,
            Set<String> statuses
    ) {
        public CombatantView {
            id = Objects.requireNonNull(id, "id");
            controllerId = controllerId == null ? "" : controllerId;
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (statuses != null) {
                for (String status : statuses) {
                    String token = normalize(status);
                    if (!token.isEmpty()) normalized.add(token);
                }
            }
            statuses = Set.copyOf(normalized);
        }

        boolean hasStatus(String status) {
            return statuses.contains(normalize(status));
        }
    }

    public record Context(
            String trainerId,
            String actorId,
            Map<String, ?> payload,
            List<CombatantView> combatants
    ) {
        public Context {
            trainerId = trainerId == null ? "" : trainerId;
            payload = payload == null ? Map.of() : Map.copyOf(payload);
            combatants = combatants == null ? List.of() : List.copyOf(combatants);
        }
    }

    public static List<String> resolve(Map<String, ?> rules, Context context) {
        Objects.requireNonNull(context, "context");
        Map<String, ?> cfg = rules == null ? Map.of() : rules;
        String scope = normalize(firstNonBlank(cfg.get("scope"), cfg.get("target"), "active_allies"));
        boolean defaultIncludeInactive = Set.of("all_allies", "all_enemies", "all", "all_pokemon").contains(scope);
        boolean includeInactive = boolLike(cfg.get("include_inactive"), defaultIncludeInactive);
        boolean includeFainted = boolLike(cfg.get("include_fainted"), false);

        List<String> candidates = candidateIds(scope, context);
        List<String> requiredStatuses = tokens(cfg.get("require_status"));
        List<String> blockedStatuses = tokens(cfg.get("exclude_status"));
        List<String> filtered = new ArrayList<>();

        for (String id : candidates) {
            CombatantView combatant = find(context.combatants(), id);
            if (combatant == null) continue;
            if (!includeFainted && combatant.fainted()) continue;
            if (!includeInactive && !combatant.active()) continue;
            if (!requiredStatuses.isEmpty() && requiredStatuses.stream().noneMatch(combatant::hasStatus)) continue;
            if (!blockedStatuses.isEmpty() && blockedStatuses.stream().anyMatch(combatant::hasStatus)) continue;
            filtered.add(id);
        }

        int limit = intLike(cfg.get("limit"), 0);
        if (limit > 0 && filtered.size() > limit) {
            return List.copyOf(filtered.subList(0, limit));
        }
        return List.copyOf(filtered);
    }

    private static List<String> candidateIds(String scope, Context context) {
        List<CombatantView> combatants = context.combatants();
        if (Set.of("actor", "self", "acting").contains(scope)) {
            return find(combatants, context.actorId()) == null ? List.of() : List.of(context.actorId());
        }
        if (Set.of("target", "action_target").contains(scope)) {
            Object raw = context.payload().get("target_id");
            String id = raw == null ? null : String.valueOf(raw);
            return find(combatants, id) == null ? List.of() : List.of(id);
        }
        if (Set.of("targets", "action_targets").contains(scope)) {
            return payloadTargetIds(context.payload().get("target_ids"), combatants);
        }
        if (Set.of("all_active", "active").contains(scope)) {
            return combatants.stream().filter(CombatantView::active).map(CombatantView::id).toList();
        }
        if (Set.of("all_allies", "allies").contains(scope)) {
            return combatants.stream().filter(mon -> mon.controllerId().equals(context.trainerId())).map(CombatantView::id).toList();
        }
        if (Set.of("active_allies", "ally_active", "self_team").contains(scope)) {
            return combatants.stream().filter(mon -> mon.controllerId().equals(context.trainerId()) && mon.active()).map(CombatantView::id).toList();
        }
        if (Set.of("all_enemies", "enemies", "foes").contains(scope)) {
            return combatants.stream().filter(mon -> !mon.controllerId().equals(context.trainerId())).map(CombatantView::id).toList();
        }
        if (Set.of("active_enemies", "enemy_active", "foe_active").contains(scope)) {
            return combatants.stream().filter(mon -> !mon.controllerId().equals(context.trainerId()) && mon.active()).map(CombatantView::id).toList();
        }
        if (Set.of("all", "all_pokemon").contains(scope)) {
            return combatants.stream().map(CombatantView::id).toList();
        }
        return combatants.stream().filter(mon -> mon.controllerId().equals(context.trainerId()) && mon.active()).map(CombatantView::id).toList();
    }

    private static List<String> payloadTargetIds(Object raw, List<CombatantView> combatants) {
        if (raw == null || raw instanceof CharSequence) return List.of();
        List<?> values;
        if (raw instanceof List<?> list) values = list;
        else if (raw instanceof Collection<?> collection) values = new ArrayList<>(collection);
        else if (raw.getClass().isArray()) {
            List<Object> arrayValues = new ArrayList<>();
            for (int i = 0; i < Array.getLength(raw); i++) arrayValues.add(Array.get(raw, i));
            values = arrayValues;
        } else return List.of();

        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value == null) continue;
            String id = String.valueOf(value);
            if (find(combatants, id) != null) out.add(id);
        }
        return out;
    }

    private static CombatantView find(List<CombatantView> combatants, String id) {
        if (id == null) return null;
        for (CombatantView combatant : combatants) {
            if (combatant.id().equals(id)) return combatant;
        }
        return null;
    }

    private static Object firstNonBlank(Object first, Object second, Object fallback) {
        if (!normalize(first).isEmpty()) return first;
        if (!normalize(second).isEmpty()) return second;
        return fallback;
    }

    private static List<String> tokens(Object value) {
        if (value == null) return List.of();
        List<?> raw;
        if (value instanceof Collection<?> collection) raw = new ArrayList<>(collection);
        else if (value.getClass().isArray()) {
            List<Object> array = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) array.add(Array.get(value, i));
            raw = array;
        } else raw = List.of(value);
        List<String> out = new ArrayList<>();
        for (Object item : raw) {
            String token = normalize(item);
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    private static boolean boolLike(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String token = normalize(value);
        if (Set.of("1", "true", "yes", "y", "on").contains(token)) return true;
        if (Set.of("0", "false", "no", "n", "off").contains(token)) return false;
        return fallback;
    }

    private static int intLike(Object value, int fallback) {
        if (value == null || "".equals(value)) return fallback;
        try {
            if (value instanceof Number number) return number.intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException first) {
            try {
                return (int) Double.parseDouble(String.valueOf(value));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }
}
