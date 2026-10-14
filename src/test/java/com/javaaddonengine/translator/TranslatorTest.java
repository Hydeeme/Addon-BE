package com.javaaddonengine.translator;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslatorTest {

    @Test
    void knownComponentsClassifyCorrectly() {
        assertEquals(CompatibilityLevel.FULL,
                ComponentRegistry.classify("Entities", "minecraft:health", "e.json").getLevel());
        assertEquals(CompatibilityLevel.EMULATED,
                ComponentRegistry.classify("Entities", "minecraft:behavior.melee_attack", "e.json").getLevel());
        assertEquals(CompatibilityLevel.PARTIAL,
                ComponentRegistry.classify("Entities", "minecraft:collision_box", "e.json").getLevel());
    }

    @Test
    void wanderAndLookBehaviorsAreEmulatedNotFull() {
        // BehaviorEmulator provides nudges/tracking, not native goals.
        assertEquals(CompatibilityLevel.EMULATED,
                ComponentRegistry.classify("Entities", "minecraft:behavior.random_stroll", "e.json").getLevel());
        assertEquals(CompatibilityLevel.EMULATED,
                ComponentRegistry.classify("Entities", "minecraft:behavior.look_at_player", "e.json").getLevel());
        assertEquals(CompatibilityLevel.EMULATED,
                ComponentRegistry.classify("Entities", "minecraft:behavior.random_look_around", "e.json").getLevel());
    }

    @Test
    void unknownBehaviorFallsBackToEmulated() {
        CompatibilityEntry e = ComponentRegistry.classify(
                "Entities", "minecraft:behavior.some_future_ai", "e.json");
        assertEquals(CompatibilityLevel.EMULATED, e.getLevel());
    }

    @Test
    void unknownCustomComponentIsUnsupported() {
        CompatibilityEntry e = ComponentRegistry.classify("Entities", "myaddon:thing", "e.json");
        assertEquals(CompatibilityLevel.UNSUPPORTED, e.getLevel());
    }

    @Test
    void adapterRegistryOverrides() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register("minecraft:health", new com.javaaddonengine.api.ComponentAdapter() {
            @Override
            public String componentId() { return "minecraft:health"; }
            @Override
            public CompatibilityEntry classify(
                    com.javaaddonengine.translator.NormalizedComponent component, String file) {
                return new CompatibilityEntry("minecraft:health", CompatibilityLevel.FALLBACK, "custom", file, -1);
            }
        });
        assertEquals(CompatibilityLevel.FALLBACK,
                registry.get("MINECRAFT:HEALTH").classify(null, "f").getLevel());
        registry.unregister("minecraft:health");
        assertNull(registry.get("minecraft:health"));
    }

    @Test
    void particleMapping() {
        assertEquals("FLAME", ParticleMapper.toBukkit("minecraft:flame"));
        assertEquals("DUST", ParticleMapper.toBukkit("minecraft:redstone"));
        assertNull(ParticleMapper.toBukkit("myaddon:sparkle"));
        assertNull(ParticleMapper.toBukkit(null));
    }

    @Test
    void soundRemap() {
        assertEquals("javaaddonengine:myaddon.spark", SoundParser.remap("myaddon:spark"));
    }
}
