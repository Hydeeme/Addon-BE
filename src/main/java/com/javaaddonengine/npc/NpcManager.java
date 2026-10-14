package com.javaaddonengine.npc;

import com.javaaddonengine.integrations.CitizensBridge;
import com.javaaddonengine.storage.AddonStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Internal NPC implementation (armor-stand hologram + villager body).
 * Used when Citizens is not installed; never a hard dependency.
 */
public final class NpcManager {

    private final Plugin plugin;
    private final Map<String, AddonNpc> definitions = new LinkedHashMap<>();
    private final Map<UUID, String> spawned = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldowns = new LinkedHashMap<>();
    private CitizensBridge citizens;
    private AddonStore store;

    public NpcManager(Plugin plugin) { this.plugin = plugin; }

    /**
     * Attach optional services. Called by the plugin after IntegrationManager
     * exists; safe to skip in tests (Citizens/store stay unused).
     */
    public void setServices(CitizensBridge citizens, AddonStore store) {
        this.citizens = citizens;
        this.store = store;
    }

    public void define(AddonNpc npc) {
        definitions.put(npc.getId(), npc);
        if (store != null) {
            try {
                store.saveNpc(npc.getId(), "", npc.getName(), String.join("\n", npc.getDialogue()));
            } catch (Exception ignored) {}
        }
    }
    public AddonNpc get(String id) { return definitions.get(id); }
    public Collection<AddonNpc> all() { return definitions.values(); }
    public int count() { return definitions.size(); }
    public void clearDefinitions() { definitions.clear(); }

    /** Spawn an NPC body. Must run on the main thread. */
    public Entity spawn(String npcId, Location loc) {
        AddonNpc npc = definitions.get(npcId);
        if (npc == null) return null;
        // Citizens when configured + installed; internal otherwise.
        if (citizens != null) {
            try {
                Entity via = citizens.spawn(npc, loc);
                if (via != null) {
                    via.getPersistentDataContainer().set(new NamespacedKey(plugin, "npc_id"),
                            PersistentDataType.STRING, npcId);
                    spawned.put(via.getUniqueId(), npcId);
                    return via;
                }
            } catch (Exception ignored) {}
        }
        return spawnInternal(npcId, npc, loc);
    }

    private Villager spawnInternal(String npcId, AddonNpc npc, Location loc) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(npc.getName().isEmpty() ? npcId : npc.getName());
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setInvulnerable(true);
        v.getPersistentDataContainer().set(new NamespacedKey(plugin, "npc_id"),
                PersistentDataType.STRING, npcId);
        spawned.put(v.getUniqueId(), npcId);
        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(
                loc.clone().add(0, 2.0, 0), EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setMarker(true);
        hologram.setCustomName(npc.getName().isEmpty() ? npcId : npc.getName());
        hologram.setCustomNameVisible(true);
        // Tag holograms so despawnAll/listener cleanup removes them with the body.
        hologram.getPersistentDataContainer().set(new NamespacedKey(plugin, "npc_id"),
                PersistentDataType.STRING, npcId);
        return v;
    }

    public boolean isNpc(UUID uuid) { return spawned.containsKey(uuid); }

    public boolean checkCooldown(UUID player, AddonNpc npc) {
        if (npc.getCooldownSeconds() <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player);
        if (last != null && now - last < npc.getCooldownSeconds() * 1000L) return false;
        cooldowns.put(player, now);
        return true;
    }

    public void despawnAll() {
        for (UUID id : new ArrayList<>(spawned.keySet())) {
            try {
                Entity e = org.bukkit.Bukkit.getEntity(id);
                if (e != null) e.remove();
            } catch (Exception ignored) {}
        }
        spawned.clear();
        cooldowns.clear();
    }

    /** Remove spawned bodies for one add-on's NPCs (used on disable/remove). */
    public void despawn(Collection<String> npcIds) {
        if (npcIds == null || npcIds.isEmpty()) return;
        java.util.Set<String> ids = new java.util.HashSet<>(npcIds);
        for (Map.Entry<UUID, String> e : new ArrayList<>(spawned.entrySet())) {
            if (!ids.contains(e.getValue())) continue;
            try {
                Entity ent = org.bukkit.Bukkit.getEntity(e.getKey());
                if (ent != null) ent.remove();
            } catch (Exception ignored) {}
            spawned.remove(e.getKey());
        }
    }

    /** Remove definitions for one add-on's NPCs (used on disable/remove). */
    public void undefineAll(Collection<String> npcIds) {
        for (String id : npcIds) definitions.remove(id);
    }

    public List<String> definitionIds() { return new ArrayList<>(definitions.keySet()); }
}
