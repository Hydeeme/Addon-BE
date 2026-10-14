package com.javaaddonengine.entities;

import com.javaaddonengine.core.JaLogger;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Best-effort emulation of Bedrock wander and look-at-player behaviors.
 *
 * <p>No NMS, no new AI goals: {@code random_stroll} is emulated with periodic
 * wander-heading nudges on top of the base mob's own locomotion, and
 * {@code look_at_player} with head/body tracking toward the nearest visible
 * player. Each emulated entity gets tracked tasks that stop automatically when
 * the entity dies, despawns, unloads, or when {@link #stopAll()} runs on
 * disable — so no scheduler or entity-reference leaks.
 */
public final class BehaviorEmulator {

    private static final long WANDER_PERIOD_TICKS = 60L;
    private static final long LOOK_PERIOD_TICKS = 10L;
    private static final double LOOK_RANGE_SQUARED = 8 * 8;

    private final Plugin plugin;
    private final JaLogger log;
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<>();

    private static final class Tracked {
        final UUID entityId;
        final boolean wander;
        final boolean look;
        volatile Integer wanderTask;
        volatile Integer lookTask;
        Tracked(UUID id, boolean wander, boolean look) {
            this.entityId = id;
            this.wander = wander;
            this.look = look;
        }
    }

    public BehaviorEmulator(Plugin plugin, JaLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    /** Start emulation for a freshly spawned entity. Main thread only. */
    public void track(Entity entity, AddonEntitySpec spec) {
        if (entity == null || spec == null) return;
        boolean wander = spec.getBehaviors().contains("minecraft:behavior.random_stroll");
        boolean look = spec.getBehaviors().contains("minecraft:behavior.look_at_player")
                || spec.getBehaviors().contains("minecraft:behavior.random_look_around");
        if (!wander && !look) return;
        Tracked t = new Tracked(entity.getUniqueId(), wander, look);
        tracked.put(t.entityId, t);
        if (wander) {
            t.wanderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Entity e = Bukkit.getEntity(t.entityId);
                if (!alive(e)) {
                    stop(t.entityId);
                    return;
                }
                nudgeWander((LivingEntity) e);
            }, WANDER_PERIOD_TICKS, WANDER_PERIOD_TICKS).getTaskId();
        }
        if (look) {
            t.lookTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Entity e = Bukkit.getEntity(t.entityId);
                if (!alive(e)) {
                    stop(t.entityId);
                    return;
                }
                trackNearestPlayer((LivingEntity) e);
            }, LOOK_PERIOD_TICKS, LOOK_PERIOD_TICKS).getTaskId();
        }
    }

    /** Stop tracking one entity (death, unload, manual). Safe to call repeatedly. */
    public void stop(UUID entityId) {
        Tracked t = tracked.remove(entityId);
        if (t == null) return;
        cancel(t.wanderTask);
        cancel(t.lookTask);
    }

    /** Stop every task. Called on disable. */
    public void stopAll() {
        Iterator<Tracked> it = tracked.values().iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            it.remove();
            cancel(t.wanderTask);
            cancel(t.lookTask);
        }
    }

    public int trackedCount() { return tracked.size(); }

    private void cancel(Integer taskId) {
        if (taskId == null) return;
        try {
            Bukkit.getScheduler().cancelTask(taskId);
        } catch (Exception ignored) {}
    }

    private static boolean alive(Entity e) {
        return e != null && e.isValid() && !e.isDead()
                && e.getLocation().getWorld() != null;
    }

    private void nudgeWander(LivingEntity entity) {
        try {
            // Small heading nudge; actual pathing stays with the base mob AI
            // so we never fight vanilla locomotion or cause jitter.
            if (!(entity instanceof Mob)) return;
            Location loc = entity.getLocation();
            float yaw = loc.getYaw() + (float) ((Math.random() - 0.5) * 90.0);
            loc.setYaw(yaw);
            Vector dir = loc.getDirection().setY(0).normalize().multiply(0.4);
            entity.setVelocity(entity.getVelocity().add(dir.multiply(0.25)));
        } catch (Exception e) {
            log.debug("Wander nudge failed: " + e.getMessage());
        }
    }

    private void trackNearestPlayer(LivingEntity entity) {
        try {
            Player nearest = null;
            double best = LOOK_RANGE_SQUARED;
            for (Player p : entity.getWorld().getPlayers()) {
                if (p.equals(entity)) continue;
                double d = p.getLocation().distanceSquared(entity.getLocation());
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest == null) return;
            Location from = entity.getLocation();
            Location to = nearest.getEyeLocation();
            Vector dir = to.toVector().subtract(from.toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            from.setYaw(yaw);
            entity.teleport(from);
        } catch (Exception e) {
            log.debug("Look tracking failed: " + e.getMessage());
        }
    }
}
