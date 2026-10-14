package com.javaaddonengine.version;

import org.bukkit.Bukkit;

/** Auto-detects the running version and returns the best adapter. */
public final class VersionAdapters {

    private VersionAdapters() {}

    public static VersionAdapter current() {
        String v = Bukkit.getMinecraftVersion();
        if (v.startsWith("1.21")) return new V121Adapter(v);
        return new V121Adapter(v);
    }

    private static final class V121Adapter implements VersionAdapter {
        private final String version;
        V121Adapter(String version) { this.version = version; }
        @Override public String minecraftVersion() { return version; }
        @Override public boolean supportsScaleAttribute() { return true; }
        @Override public boolean supportsDisplayEntities() { return true; }
        @Override public int defaultPackFormat() { return 48; }
    }
}
