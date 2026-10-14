package com.javaaddonengine.integrations;

import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.npc.AddonNpc;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

/**
 * Best-effort Citizens bridge via reflection only — never a hard dependency.
 *
 * <p>When {@code npc.provider: citizens} is configured and Citizens exposes its
 * stable 2.x API, NPCs spawn as real Citizens NPCs. Otherwise the internal
 * villager+hologram implementation is used. Every failure degrades to
 * {@code null} plus one debug log; callers always fall back.
 */
public final class CitizensBridge {

    private final Plugin plugin;
    private final JaLogger log;
    private final AtomicBoolean warned = new AtomicBoolean(false);
    private volatile Boolean available;
    private volatile boolean providerWanted;

    public CitizensBridge(Plugin plugin, JaLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    public void setProviderWanted(boolean wanted) {
        this.providerWanted = wanted;
        this.available = null;
    }

    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        boolean ok = providerWanted
                && Bukkit.getPluginManager().getPlugin("Citizens") != null
                && probe();
        available = ok;
        return ok;
    }

    /** Spawn via Citizens. Returns the spawned entity id, or null to use internal. */
    public org.bukkit.entity.Entity spawn(AddonNpc npc, Location loc) {
        if (!isAvailable()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            Object created = registry.getClass()
                    .getMethod("createNPC", EntityType.class, String.class)
                    .invoke(registry, EntityType.VILLAGER,
                            npc.getName().isEmpty() ? npc.getId() : npc.getName());
            created.getClass().getMethod("setProtected", boolean.class).invoke(created, true);
            Object spawned = created.getClass().getMethod("spawn", Location.class).invoke(created, loc);
            if (spawned instanceof org.bukkit.entity.Entity) return (org.bukkit.entity.Entity) spawned;
            return null;
        } catch (Throwable t) {
            warnOnce("Citizens spawn failed (" + t.getMessage() + "); using internal NPC.");
            return null;
        }
    }

    private boolean probe() {
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            return registry != null;
        } catch (Throwable t) {
            warnOnce("Citizens API probe failed (" + t.getMessage() + "); using internal NPCs.");
            return false;
        }
    }

    public static boolean wantsCitizens(String configured) {
        return configured != null && configured.trim().equalsIgnoreCase("citizens");
    }

    /** Parse provider strings; unknown values resolve to internal. */
    public static String normalize(String configured) {
        if (configured == null) return "INTERNAL";
        String v = configured.trim().toUpperCase(Locale.ROOT);
        return "CITIZENS".equals(v) ? "CITIZENS" : "INTERNAL";
    }

    private void warnOnce(String msg) {
        if (warned.compareAndSet(false, true)) log.debug(msg);
    }
}
