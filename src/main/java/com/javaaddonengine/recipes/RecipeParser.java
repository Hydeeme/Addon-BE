package com.javaaddonengine.recipes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses Bedrock recipe files (shaped/shapeless/furnace/smithing variants). */
public final class RecipeParser {

    private RecipeParser() {}

    public static List<AddonRecipe> parse(ParsedAddon parsed, CompatibilityReport report) {
        List<AddonRecipe> out = new ArrayList<>();
        for (File f : parsed.getRecipeFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Recipes", new CompatibilityEntry("recipe", CompatibilityLevel.UNSUPPORTED,
                        "Unparseable recipe JSON: " + e.getMessage(), rel, -1));
                continue;
            }
            for (Map.Entry<String, JsonElement> top : root.entrySet()) {
                String kind = top.getKey();
                if (!top.getValue().isJsonObject()) continue;
                JsonObject body = top.getValue().getAsJsonObject();
                AddonRecipe.Type type = mapType(kind);
                AddonRecipe recipe = new AddonRecipe(basename(f), type);
                try {
                    if (type == AddonRecipe.Type.SHAPED) {
                        if (body.has("pattern")) {
                            for (JsonElement el : body.getAsJsonArray("pattern")) recipe.getShape().add(el.getAsString());
                        }
                        if (body.has("key") && body.get("key").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> k : body.getAsJsonObject("key").entrySet()) {
                                recipe.getKey().put(k.getKey(), itemOf(k.getValue()));
                            }
                        }
                        recipe.setResult(itemOf(body.get("result")));
                        recipe.setCount(countOf(body.get("result"), 1));
                        report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.FULL,
                                "Shaped recipe converted: " + recipe.getId(), rel, -1));
                    } else if (type == AddonRecipe.Type.SHAPELESS) {
                        if (body.has("ingredients")) {
                            for (JsonElement el : body.getAsJsonArray("ingredients")) recipe.getIngredients().add(itemOf(el));
                        }
                        recipe.setResult(itemOf(body.get("result")));
                        recipe.setCount(countOf(body.get("result"), 1));
                        report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.FULL,
                                "Shapeless recipe converted: " + recipe.getId(), rel, -1));
                    } else if (type == AddonRecipe.Type.FURNACE) {
                        recipe.setIngredient(itemOf(body.get("input")));
                        recipe.setResult(itemOf(body.get("output")));
                        report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.FULL,
                                "Furnace recipe converted: " + recipe.getId(), rel, -1));
                    } else if (type == AddonRecipe.Type.SMITHING || type == AddonRecipe.Type.STONECUTTER) {
                        recipe.setExtra(body.toString());
                        recipe.setResult(itemOf(body.get("result")));
                        report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.PARTIAL,
                                "Smithing/stonecutter recipe kept with best-effort mapping: " + recipe.getId(), rel, -1));
                    } else {
                        recipe.setExtra(body.toString());
                        report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.UNSUPPORTED,
                                "Unknown recipe type; kept as metadata only.", rel, -1));
                    }
                    out.add(recipe);
                } catch (Exception e) {
                    report.add("Recipes", new CompatibilityEntry("recipe:" + kind, CompatibilityLevel.UNSUPPORTED,
                            "Failed to convert recipe: " + e.getMessage(), rel, -1));
                }
            }
        }
        return out;
    }

    public static AddonRecipe.Type mapType(String kind) {
        String k = kind == null ? "" : kind.toLowerCase();
        // Strip the namespace first: "minecraft:..." itself contains "craft".
        int colon = k.lastIndexOf(':');
        String name = colon >= 0 ? k.substring(colon + 1) : k;
        if (name.contains("shapeless")) return AddonRecipe.Type.SHAPELESS;
        if (name.contains("shaped")) return AddonRecipe.Type.SHAPED;
        if (name.contains("furnace") || name.contains("smelt") || name.contains("campfire") || name.contains("smoker") || name.contains("blast")) {
            return AddonRecipe.Type.FURNACE;
        }
        if (name.contains("smithing") || name.contains("transform")) return AddonRecipe.Type.SMITHING;
        if (name.contains("stonecut")) return AddonRecipe.Type.STONECUTTER;
        if (name.contains("craft")) return AddonRecipe.Type.SHAPELESS;
        return AddonRecipe.Type.UNKNOWN;
    }

    static String itemOf(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) return o.get("item").getAsString();
            if (o.has("name")) return o.get("name").getAsString();
            if (o.has("id")) return o.get("id").getAsString();
        }
        return el.toString();
    }

    static int countOf(JsonElement el, int def) {
        if (el != null && el.isJsonObject() && el.getAsJsonObject().has("count")) {
            try { return el.getAsJsonObject().get("count").getAsInt(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static String basename(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static String rel(File root, File f) {
        return root.toURI().relativize(f.toURI()).getPath();
    }
}
