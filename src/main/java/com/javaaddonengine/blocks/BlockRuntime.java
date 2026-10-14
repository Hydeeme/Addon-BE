package com.javaaddonengine.blocks;

import com.javaaddonengine.core.Addon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Block fallback renderer. Java cannot register arbitrary blocks via the Bukkit API,
 * so AUTO mode places a vanilla stand-in block and attaches a display entity marker.
 */
public final class BlockRuntime {

    private final Plugin plugin;
    private final AddonBlockSpec.RenderMode configured;

    public BlockRuntime(Plugin plugin, AddonBlockSpec.RenderMode configured) {
        this.plugin = plugin;
        this.configured = configured;
    }

    public Material standIn(AddonBlockSpec spec) {
        String m = spec.getMaterial().toLowerCase();
        if (m.contains("wood") || m.contains("log") || m.contains("plank")) return Material.OAK_PLANKS;
        if (m.contains("glass")) return Material.GLASS;
        if (m.contains("metal") || m.contains("iron")) return Material.IRON_BLOCK;
        if (m.contains("sand")) return Material.SANDSTONE;
        if (m.contains("dirt") || m.contains("grass")) return Material.DIRT;
        if (spec.getLight() > 0) return Material.GLOWSTONE;
        return Material.STONE;
    }

    /** Place the fallback representation. Must run on the main thread. */
    public void place(Addon addon, AddonBlockSpec spec, Location loc) {
        AddonBlockSpec.RenderMode mode = configured == AddonBlockSpec.RenderMode.FALLBACK
                ? AddonBlockSpec.RenderMode.DISPLAY_ENTITY : configured;
        if (mode == AddonBlockSpec.RenderMode.NATIVE) {
            loc.getBlock().setType(standIn(spec));
            return;
        }
        loc.getBlock().setType(standIn(spec));
        if (mode == AddonBlockSpec.RenderMode.DISPLAY_ENTITY
                || mode == AddonBlockSpec.RenderMode.ITEM_DISPLAY
                || mode == AddonBlockSpec.RenderMode.RESOURCE_PACK) {
            BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(
                    loc.clone().add(0.5, 0.5, 0.5), EntityType.BLOCK_DISPLAY);
            try {
                display.setBlock(new ItemStack(standIn(spec)).getType().createBlockData());
            } catch (Exception ignored) {}
            display.getPersistentDataContainer().set(new NamespacedKey(plugin, "addon_id"),
                    PersistentDataType.STRING, addon.getId());
            display.getPersistentDataContainer().set(new NamespacedKey(plugin, "block_id"),
                    PersistentDataType.STRING, spec.getId());
            display.setBillboard(Display.Billboard.FIXED);
        }
    }
}
