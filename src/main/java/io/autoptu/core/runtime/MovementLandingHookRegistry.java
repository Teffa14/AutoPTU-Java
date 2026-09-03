package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic registry for consequences that are resolved after an effective movement landing.
 *
 * <p>The registry is intentionally pure: hooks describe ordered consequences but do not mutate
 * battle state. Runtime execution remains responsible for applying status/event/consumption
 * instructions through server-owned boundaries.</p>
 */
final class MovementLandingHookRegistry {
    enum HookFamily {
        TILE_TRAP
    }

    interface LandingConsequence {
    }

    record TileTrapConsequence(TileEntryTrapResolution.Result resolution) implements LandingConsequence {
        TileTrapConsequence {
            Objects.requireNonNull(resolution, "resolution");
        }
    }

    record LandingContext(
            TileEntryTrapResolution.EntryContext tileEntryContext,
            List<TileEntryTrapResolution.TrapLayer> tileTraps
    ) {
        LandingContext {
            Objects.requireNonNull(tileEntryContext, "tileEntryContext");
            tileTraps = List.copyOf(Objects.requireNonNull(tileTraps, "tileTraps"));
        }
    }

    record ResolvedHook(String hookKey, HookFamily family, LandingConsequence consequence) {
        ResolvedHook {
            hookKey = normalizeKey(hookKey);
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(consequence, "consequence");
        }
    }

    @FunctionalInterface
    interface Hook {
        List<? extends LandingConsequence> resolve(LandingContext context);
    }

    private record RegisteredHook(HookFamily family, Hook hook) {
        private RegisteredHook {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(hook, "hook");
        }
    }

    private final LinkedHashMap<String, RegisteredHook> hooks = new LinkedHashMap<>();

    static MovementLandingHookRegistry standard() {
        MovementLandingHookRegistry registry = new MovementLandingHookRegistry();
        registry.register(HookFamily.TILE_TRAP, "tile_traps", context -> {
            List<LandingConsequence> consequences = new ArrayList<>();
            for (TileEntryTrapResolution.TrapLayer trap : context.tileTraps()) {
                TileEntryTrapResolution.Result result = TileEntryTrapResolution.resolve(
                        context.tileEntryContext(),
                        List.of(trap)
                );
                if (result.triggers().isEmpty() && result.blocks().isEmpty()) {
                    continue;
                }
                consequences.add(new TileTrapConsequence(result));
            }
            return consequences;
        });
        return registry;
    }

    MovementLandingHookRegistry register(HookFamily family, String hookKey, Hook hook) {
        String canonicalKey = normalizeKey(hookKey);
        RegisteredHook previous = hooks.putIfAbsent(
                canonicalKey,
                new RegisteredHook(family, hook)
        );
        if (previous != null) {
            throw new IllegalArgumentException("duplicate movement landing hook: " + canonicalKey);
        }
        return this;
    }

    List<ResolvedHook> resolve(LandingContext context) {
        Objects.requireNonNull(context, "context");
        List<ResolvedHook> resolved = new ArrayList<>();
        for (Map.Entry<String, RegisteredHook> entry : hooks.entrySet()) {
            RegisteredHook registered = entry.getValue();
            List<? extends LandingConsequence> consequences = registered.hook().resolve(context);
            if (consequences == null) {
                throw new IllegalStateException("movement landing hook returned null: " + entry.getKey());
            }
            for (LandingConsequence consequence : consequences) {
                resolved.add(new ResolvedHook(entry.getKey(), registered.family(), consequence));
            }
        }
        return List.copyOf(resolved);
    }

    List<String> hookKeys() {
        return List.copyOf(hooks.keySet());
    }

    private static String normalizeKey(String value) {
        String normalized = Objects.requireNonNull(value, "hookKey").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("hookKey must not be blank");
        }
        return normalized;
    }
}
