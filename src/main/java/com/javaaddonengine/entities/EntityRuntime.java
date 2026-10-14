package com.javaaddonengine.entities;

import com.javaaddonengine.core.Addon;
import com.javaaddonengine.integrations.ModelEngineBridge;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Spawns converted entities on the Bukkit thread using attributes + PDC tagging. */
public final class EntityRuntime {

    private final Plugin plugin;
    private ModelEngineBridge modelEngine;
    private BehaviorEmulator behaviors;

    public EntityRuntime(Plugin plugin) { this.plugin = plugin; }

    /**
     * Attach optional services. Called by the plugin after IntegrationManager
     * exists; safe to skip in tests (fallbacks stay internal).
     */
    public void setServices(ModelEngineBridge modelEngine, BehaviorEmulator behaviors) {
        this.modelEngine = modelEngine;
        this.behaviors = behaviors;
    }

    public NamespacedKey addonKey() { return new NamespacedKey(plugin, "addon_id"); }
    public NamespacedKey entityKey() { return new NamespacedKey(plugin, "entity_id"); }

    /** Spawn a converted entity. Must be called on the main thread. */
    public Entity spawn(Addon addon, AddonEntitySpec spec, Location loc) {
        EntityType type;
        try {
            type = EntityType.valueOf(spec.getBaseVanilla());
        } catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }
        Entity entity = loc.getWorld().spawnEntity(loc, type);
        entity.getPersistentDataContainer().set(addonKey(), PersistentDataType.STRING, addon.getId());
        entity.getPersistentDataContainer().set(entityKey(), PersistentDataType.STRING, spec.getId());
        if (!spec.getTypeFamilies().isEmpty()) {
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "type_family"),
                    PersistentDataType.STRING, String.join(",", spec.getTypeFamilies()));
        }
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            try {
                if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                    living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(Math.max(1.0, spec.getHealth()));
                    living.setHealth(Math.max(1.0, spec.getHealth()));
                }
            } catch (Exception ignored) {}
            try {
                if (living.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    double v = Math.min(1.0, spec.getMovementSpeed());
                    living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(v);
                }
            } catch (Exception ignored) {}
            try {
                if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                    living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(Math.max(0.0, spec.getAttackDamage()));
                }
            } catch (Exception ignored) {}
            try {
                if (living.getAttribute(Attribute.SCALE) != null) {
                    living.getAttribute(Attribute.SCALE).setBaseValue(clamp(spec.getScale(), 0.1, 16.0));
                }
            } catch (Exception ignored) {}
            try {
                if (living.getAttribute(Attribute.FOLLOW_RANGE) != null) {
                    living.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(clamp(spec.getFollowRange(), 1.0, 2048.0));
                }
            } catch (Exception ignored) {}
            try {
                if (living.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null) {
                    living.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(clamp(spec.getKnockbackResistance(), 0.0, 1.0));
                }
            } catch (Exception ignored) {}
        }
        if (!spec.getGeometry().isEmpty()) {
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "geometry"),
                    PersistentDataType.STRING, spec.getGeometry());
            // ModelEngine when installed; display-entity fallback handled by callers/docs.
            if (modelEngine != null && entity instanceof LivingEntity) {
                try {
                    modelEngine.applyModel((LivingEntity) entity, spec.getGeometry());
                } catch (Exception ignored) {}
            }
        }
        if (behaviors != null) {
            try {
                behaviors.track(entity, spec);
            } catch (Exception ignored) {}
        }
        return entity;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
