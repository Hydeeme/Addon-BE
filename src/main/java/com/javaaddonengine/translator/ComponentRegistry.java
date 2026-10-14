package com.javaaddonengine.translator;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Registry mapping Bedrock component ids to support levels + notes. */
public final class ComponentRegistry {
    private static final Map<String, Entry> REGISTRY = new HashMap<>();

    static {
        full("minecraft:health", "Maps to Bukkit max health attribute.");
        full("minecraft:movement", "Maps to movement speed attribute.");
        full("minecraft:movement.basic", "Maps to movement speed attribute.");
        full("minecraft:type_family", "Stored as metadata tags; no runtime cost.");
        full("minecraft:loot", "Routed into the JAE loot engine.");
        full("minecraft:experience_reward", "Applied as dropped experience.");
        full("minecraft:scale", "Applied via generic scale attribute.");
        full("minecraft:knockback_resistance", "Maps to knockback resistance attribute.");
        emulated("minecraft:attack", "Damage applied through synthetic damage events.");
        emulated("minecraft:physics", "Approximated with vanilla gravity/collision.");
        emulated("minecraft:follow_range", "Approximated by target radius expansion.");
        emulated("minecraft:behavior.random_stroll", "Wander-heading nudges via BehaviorEmulator; locomotion uses base mob AI.");
        emulated("minecraft:behavior.look_at_player", "Head/body tracking task via BehaviorEmulator.");
        emulated("minecraft:behavior.random_look_around", "Head/body tracking task via BehaviorEmulator.");
        emulated("minecraft:behavior.nearest_attackable_target", "Mapped to nearest-player targeting.");
        emulated("minecraft:behavior.melee_attack", "Mapped to melee damage tick.");
        emulated("minecraft:behavior.ranged_attack", "Mapped to projectile task.");
        emulated("minecraft:behavior.follow_owner", "Mapped to follow-owner task.");
        emulated("minecraft:behavior.avoid_mob", "Mapped to flee task.");
        emulated("minecraft:behavior.float", "Mapped to water-float task.");
        partial("minecraft:collision_box", "Box stored as metadata; exact hitbox needs display fallback.");
        partial("minecraft:damage_sensor", "Only simple damage filters are translated; complex filters reported.");
        partial("minecraft:behavior", "Unknown behavior keys are kept as metadata only.");
        // Items
        full("minecraft:max_stack_size", "Applied to ItemStack max size.");
        full("minecraft:durability", "Applied via Damageable meta.");
        full("minecraft:food", "Applied via food components where supported.");
        full("minecraft:damage", "Offensive damage stored in PDC + applied on hit.");
        full("minecraft:cooldown", "Applied with player setCooldown.");
        full("minecraft:rarity", "Stored in display metadata.");
        full("minecraft:tags", "Stored as namespaced tags.");
        partial("minecraft:use_animation", "Approximated with arm swing / consumption effects.");
        partial("minecraft:enchantable", "Mapped to allowed enchant list where possible.");
        // Blocks
        full("minecraft:hardness", "Stored hardness; used by custom-break timing.");
        full("minecraft:resistance", "Stored blast resistance.");
        full("minecraft:light", "Approximated with Lightable/fake light where supported.");
        partial("minecraft:material", "Mapped to closest vanilla material; inexact matches flagged.");
        partial("minecraft:collision", "Approximated with barrier/display fallback.");
        partial("minecraft:sound", "Mapped to closest vanilla sound.");
        // Scripts / molang / animation
        partial("molang", "Common queries supported; unknown queries flagged UNSUPPORTED.");
        partial("animation", "Simple transforms emulated; complex bone animation needs ModelEngine.");
        partial("script", "Supported Script API subset translated; rest reported.");
    }

    private ComponentRegistry() {}

    private static final class Entry {
        final CompatibilityLevel level;
        final String note;
        Entry(CompatibilityLevel l, String n) { level = l; note = n; }
    }

    private static void full(String id, String note) { REGISTRY.put(id, new Entry(CompatibilityLevel.FULL, note)); }
    private static void emulated(String id, String note) { REGISTRY.put(id, new Entry(CompatibilityLevel.EMULATED, note)); }
    private static void partial(String id, String note) { REGISTRY.put(id, new Entry(CompatibilityLevel.PARTIAL, note)); }

    public static CompatibilityEntry classify(String category, String componentId, String file) {
        Entry e = REGISTRY.get(componentId);
        if (e != null) return new CompatibilityEntry(componentId, e.level, e.note, file, -1);
        if (componentId.startsWith("minecraft:behavior.")) {
            return new CompatibilityEntry(componentId, CompatibilityLevel.EMULATED,
                    "Generic behavior emulated with best-effort task.", file, -1);
        }
        if (componentId.startsWith("minecraft:")) {
            return new CompatibilityEntry(componentId, CompatibilityLevel.PARTIAL,
                    "Known namespace but no dedicated adapter; kept as metadata.", file, -1);
        }
        return new CompatibilityEntry(componentId, CompatibilityLevel.UNSUPPORTED,
                "Unknown custom component; ignored at runtime.", file, -1);
    }

    public static Map<String, CompatibilityLevel> snapshot() {
        Map<String, CompatibilityLevel> m = new HashMap<>();
        for (Map.Entry<String, Entry> e : REGISTRY.entrySet()) m.put(e.getKey(), e.getValue().level);
        return Collections.unmodifiableMap(m);
    }
}
