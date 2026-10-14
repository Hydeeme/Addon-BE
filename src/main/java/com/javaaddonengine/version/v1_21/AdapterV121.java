package com.javaaddonengine.version.v1_21;

import com.javaaddonengine.version.VersionAdapter;

/** Minecraft 1.21 adapter. No NMS calls; Bukkit/Paper API only. */
public final class AdapterV121 implements VersionAdapter {
    @Override public String minecraftVersion() { return "1.21.x"; }
    @Override public boolean supportsScaleAttribute() { return true; }
    @Override public boolean supportsDisplayEntities() { return true; }
    @Override public int defaultPackFormat() { return 48; }
}
