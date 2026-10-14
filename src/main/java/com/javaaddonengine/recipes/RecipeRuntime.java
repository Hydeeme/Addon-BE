package com.javaaddonengine.recipes;

import com.javaaddonengine.core.Addon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/** Registers converted recipes with Bukkit, namespaced per add-on. */
public final class RecipeRuntime {

    private final Plugin plugin;

    public RecipeRuntime(Plugin plugin) { this.plugin = plugin; }

    public List<String> register(Addon addon, List<AddonRecipe> recipes) {
        List<String> keys = new ArrayList<>();
        for (AddonRecipe r : recipes) {
            try {
                NamespacedKey key = new NamespacedKey(plugin,
                        addon.getId().replace(":", "_") + "_" + slug(r.getId()));
                Material result = materialOf(r.getResult());
                if (result == null) continue;
                ItemStack out = new ItemStack(result, Math.max(1, r.getCount()));
                if (r.getType() == AddonRecipe.Type.SHAPED && !r.getShape().isEmpty()) {
                    ShapedRecipe sr = new ShapedRecipe(key, out);
                    sr.shape(r.getShape().toArray(new String[0]));
                    for (Map.Entry<String, String> e : r.getKey().entrySet()) {
                        Material m = materialOf(e.getValue());
                        if (m != null && e.getKey().length() == 1) sr.setIngredient(e.getKey().charAt(0), m);
                    }
                    Bukkit.addRecipe(sr);
                    keys.add(key.toString());
                } else if (r.getType() == AddonRecipe.Type.SHAPELESS && !r.getIngredients().isEmpty()) {
                    ShapelessRecipe sr = new ShapelessRecipe(key, out);
                    for (String ing : r.getIngredients()) {
                        Material m = materialOf(ing);
                        if (m != null) sr.addIngredient(m);
                    }
                    Bukkit.addRecipe(sr);
                    keys.add(key.toString());
                } else if (r.getType() == AddonRecipe.Type.FURNACE && !r.getIngredient().isEmpty()) {
                    Material in = materialOf(r.getIngredient());
                    if (in != null) {
                        Bukkit.addRecipe(new FurnaceRecipe(key, out, in, 0.7f, 200));
                        keys.add(key.toString());
                    }
                }
            } catch (Exception ignored) {
                // One bad recipe must never break the rest.
            }
        }
        return keys;
    }

    public void unregister(List<NamespacedKey> keys) {
        for (NamespacedKey k : keys) {
            try { Bukkit.removeRecipe(k); } catch (Exception ignored) {}
        }
    }

    /** Unregister by stored "namespace:key" strings (survives reloads without key objects). */
    @SuppressWarnings("deprecation")
    public void unregisterKeys(List<String> keys) {
        for (String raw : keys) {
            try {
                int colon = raw.indexOf(':');
                if (colon <= 0) continue;
                NamespacedKey k = new NamespacedKey(raw.substring(0, colon), raw.substring(colon + 1));
                Bukkit.removeRecipe(k);
            } catch (Exception ignored) {}
        }
    }

    public static Material materialOf(String bedrockId) {
        if (bedrockId == null || bedrockId.isEmpty()) return null;
        String s = bedrockId.toLowerCase(Locale.ROOT);
        if (s.contains(":")) s = s.substring(s.indexOf(':') + 1);
        if (s.contains("log")) return Material.OAK_LOG;
        if (s.contains("plank")) return Material.OAK_PLANKS;
        if (s.contains("stick")) return Material.STICK;
        if (s.contains("iron_ingot")) return Material.IRON_INGOT;
        if (s.contains("gold_ingot")) return Material.GOLD_INGOT;
        if (s.contains("diamond")) return Material.DIAMOND;
        if (s.contains("coal")) return Material.COAL;
        if (s.contains("string")) return Material.STRING;
        if (s.contains("redstone")) return Material.REDSTONE;
        try {
            return Material.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    private static String slug(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
