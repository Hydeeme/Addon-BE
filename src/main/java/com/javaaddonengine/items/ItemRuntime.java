package com.javaaddonengine.items;

import com.javaaddonengine.core.Addon;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Builds persistent Bukkit ItemStacks from converted specs (PDC survives restarts/saves). */
public final class ItemRuntime {

    private final Plugin plugin;

    public ItemRuntime(Plugin plugin) { this.plugin = plugin; }

    public NamespacedKey addonKey() { return new NamespacedKey(plugin, "addon_id"); }
    public NamespacedKey itemKey() { return new NamespacedKey(plugin, "item_id"); }
    public NamespacedKey versionKey() { return new NamespacedKey(plugin, "version"); }

    public ItemStack build(Addon addon, AddonItemSpec spec) {
        Material mat;
        try {
            mat = Material.valueOf(spec.getBaseMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.IRON_SWORD;
        }
        ItemStack stack = new ItemStack(mat, clampStack(spec.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(spec.getDisplayName().isEmpty() ? spec.getId() : spec.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(spec.getId()));
            if (spec.getDurability() > 0) lore.add(Component.text("Durability: " + spec.getDurability()));
            if (spec.getDamage() > 0) lore.add(Component.text("Damage: " + spec.getDamage()));
            if (!"common".equalsIgnoreCase(spec.getRarity())) lore.add(Component.text(spec.getRarity()));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(addonKey(), PersistentDataType.STRING, addon.getId());
            meta.getPersistentDataContainer().set(itemKey(), PersistentDataType.STRING, spec.getId());
            meta.getPersistentDataContainer().set(versionKey(), PersistentDataType.STRING, addon.getVersion());
            if (spec.getCustomModelData() > 0) {
                meta.setCustomModelData(spec.getCustomModelData());
            }
            if (spec.getDurability() > 0 && meta instanceof Damageable) {
                ((Damageable) meta).setMaxDamage(spec.getDurability());
            }
            if (spec.isFood()) {
                applyFood(meta, spec);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void applyFood(ItemMeta meta, AddonItemSpec spec) {
        // Paper food components: nutrition + saturation via the food API when present.
        try {
            org.bukkit.inventory.meta.components.FoodComponent food = meta.getFood();
            food.setNutrition(spec.getNutrition());
            food.setSaturation(spec.getSaturation());
            meta.setFood(food);
        } catch (Throwable ignored) {
            // Older Spigot builds lack the component API; nutrition stays in PDC/lore.
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("jae_nutrition", "v"),
                    PersistentDataType.INTEGER, spec.getNutrition());
        }
    }

    private static int clampStack(int max) {
        return Math.min(64, Math.max(1, max <= 0 ? 64 : max));
    }

    /** True if this ItemStack was created by JavaAddonEngine (any add-on). */
    public boolean isAddonItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey(), PersistentDataType.STRING);
    }

    public String itemIdOf(ItemStack stack) {
        if (!isAddonItem(stack)) return "";
        return stack.getItemMeta().getPersistentDataContainer().get(itemKey(), PersistentDataType.STRING);
    }
}
