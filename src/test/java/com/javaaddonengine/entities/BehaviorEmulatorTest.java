package com.javaaddonengine.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BehaviorEmulator lifecycle without a Bukkit server: construction,
 * empty-state operations, and argument validation must never throw
 * or touch the scheduler.
 */
class BehaviorEmulatorTest {

    @Test
    void emptyOperationsAreSafe() {
        // Plugin + logger are only touched when tracking live entities.
        BehaviorEmulator emulator = new BehaviorEmulator(null, null);
        assertEquals(0, emulator.trackedCount());
        assertDoesNotThrow(emulator::stopAll);
        assertDoesNotThrow(() -> emulator.stop(java.util.UUID.randomUUID()));
        assertDoesNotThrow(() -> emulator.track(null, null));
        assertDoesNotThrow(() -> emulator.track(null, new AddonEntitySpec("x")));
        AddonEntitySpec plain = new AddonEntitySpec("my_addon:plain");
        assertDoesNotThrow(() -> emulator.track(null, plain));
        assertEquals(0, emulator.trackedCount());
    }

    @Test
    void specBehaviorFlags() {
        AddonEntitySpec spec = new AddonEntitySpec("my_addon:wanderer");
        spec.getBehaviors().add("minecraft:behavior.random_stroll");
        spec.getBehaviors().add("minecraft:behavior.look_at_player");
        assertTrue(spec.getBehaviors().contains("minecraft:behavior.random_stroll"));
        assertTrue(spec.getBehaviors().contains("minecraft:behavior.look_at_player"));
    }
}
