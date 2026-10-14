package com.javaaddonengine.scripting;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;

/**
 * Restricted compatibility API exposed to translated scripts.
 * Scripts may ONLY touch the server through this surface.
 */
public final class ScriptSandbox {

    private ScriptSandbox() {}

    public interface SandboxWorld {
        void broadcast(String message);
        int playerCount();
        long timeOfDay();
    }

    public interface SandboxEntity {
        String id();
        double health();
        void damage(double amount);
    }

    /** Validate that a script-provided string cannot be used for command injection. */
    public static boolean isSafeCommandArgument(String arg) {
        if (arg == null) return false;
        if (arg.length() > 256) return false;
        for (char c : arg.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '`' || c == '$') return false;
        }
        return true;
    }

    public static CompatibilityEntry blocked(String feature, String file) {
        return new CompatibilityEntry("script:" + feature, CompatibilityLevel.UNSUPPORTED,
                "Blocked by sandbox (no arbitrary JVM/IO access): " + feature, file, -1);
    }
}
