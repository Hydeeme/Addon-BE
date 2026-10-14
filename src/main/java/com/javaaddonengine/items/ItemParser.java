package com.javaaddonengine.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import com.javaaddonengine.translator.AdapterRegistry;
import com.javaaddonengine.translator.ComponentRegistry;
import com.javaaddonengine.translator.NormalizedComponent;
import java.io.File;
import java.util.Locale;
import java.util.Map;

/** Parses Bedrock item files. Supports both new (minecraft:item/description) and legacy (components) layouts. */
public final class ItemParser {

    private ItemParser() {}

    public static void parse(ParsedAddon parsed, CompatibilityReport report,
                             Map<String, AddonItemSpec> out, AdapterRegistry adapters) {
        for (File f : parsed.getItemFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Items", ComponentRegistry.classify("Items", "item:unparseable", rel));
                continue;
            }
            try {
                JsonObject item = root.has("minecraft:item") ? root.getAsJsonObject("minecraft:item") : root;
                JsonObject desc = item.has("description") ? item.getAsJsonObject("description") : new JsonObject();
                String identifier = desc.has("identifier") ? desc.get("identifier").getAsString() : basename(f);
                AddonItemSpec spec = new AddonItemSpec(identifier.toLowerCase(Locale.ROOT));
                if (desc.has("category")) spec.getExtra().put("category", desc.get("category").getAsString());
                JsonObject components = item.has("components") ? item.getAsJsonObject("components") : new JsonObject();
                // legacy top-level minecraft:* keys
                for (Map.Entry<String, JsonElement> e : item.entrySet()) {
                    if (e.getKey().startsWith("minecraft:") && !components.has(e.getKey())) {
                        components.add(e.getKey(), e.getValue());
                    }
                }
                for (Map.Entry<String, JsonElement> e : components.entrySet()) {
                    String cid = e.getKey();
                    JsonObject raw = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
                    NormalizedComponent nc = new NormalizedComponent(cid, raw);
                    if (adapters != null && adapters.get(cid) != null) {
                        report.add("Items", adapters.get(cid).classify(nc, rel));
                        continue;
                    }
                    report.add("Items", ComponentRegistry.classify("Items", cid, rel));
                    apply(spec, nc);
                }
                if (spec.getDisplayName().isEmpty()) {
                    spec.setDisplayName(pretty(spec.getId()));
                }
                spec.setBaseMaterial(guessMaterial(spec));
                out.put(spec.getId(), spec);
            } catch (Exception ex) {
                report.add("Items", ComponentRegistry.classify("Items", "item:failed", rel));
            }
        }
    }

    private static void apply(AddonItemSpec spec, NormalizedComponent nc) {
        String cid = nc.getBedrockId();
        JsonObject raw = nc.getRaw();
        switch (cid) {
            case "minecraft:max_stack_size":
                spec.setMaxStackSize((int) nc.num("value", 64));
                break;
            case "minecraft:durability":
                spec.setDurability((int) nc.num("max_durability", nc.num("value", 0)));
                break;
            case "minecraft:damage":
                spec.setDamage((int) nc.num("value", 0));
                break;
            case "minecraft:cooldown":
                spec.setCooldownSeconds(nc.num("duration", 0.0));
                break;
            case "minecraft:rarity":
                spec.setRarity(nc.str("value", "common"));
                break;
            case "minecraft:food":
                spec.setFood(true);
                spec.setNutrition((int) nc.num("nutrition", 0));
                if (raw.has("saturation_modifier")) {
                    try { spec.setSaturation(raw.get("saturation_modifier").getAsFloat()); } catch (Exception ignored) {}
                }
                break;
            case "minecraft:tags":
                if (raw.has("tags")) {
                    try {
                        for (JsonElement el : raw.getAsJsonArray("tags")) spec.getTags().add(el.getAsString());
                    } catch (Exception e) {
                        spec.getTags().add(raw.get("tags").getAsString());
                    }
                }
                break;
            case "minecraft:display_name":
                spec.setDisplayName(nc.str("value", spec.getDisplayName()));
                break;
            case "minecraft:icon":
                spec.getExtra().put("icon", raw.toString());
                if (raw.has("texture")) {
                    try {
                        spec.setIconTexture(raw.get("texture").getAsString());
                    } catch (Exception ignored) {}
                } else if (raw.has("textures") && raw.get("textures").isJsonObject()) {
                    try {
                        JsonObject tex = raw.getAsJsonObject("textures");
                        if (tex.has("default")) spec.setIconTexture(tex.get("default").getAsString());
                    } catch (Exception ignored) {}
                }
                break;
            case "minecraft:use_animation":
            case "minecraft:use_modifiers":
            case "minecraft:enchantable":
            case "minecraft:shooter":
            case "minecraft:projectile":
            case "minecraft:wearable":
            case "minecraft:armor":
            case "minecraft:hand_equipped":
            case "minecraft:glint":
                spec.getExtra().put(cid, raw.toString());
                break;
            default:
                spec.getExtra().put(cid, raw.toString());
                break;
        }
    }

    private static String guessMaterial(AddonItemSpec spec) {
        String id = spec.getId();
        if (id.contains("sword")) return spec.getDamage() > 8 ? "DIAMOND_SWORD" : "IRON_SWORD";
        if (id.contains("pickaxe")) return "IRON_PICKAXE";
        if (id.contains("axe")) return "IRON_AXE";
        if (id.contains("shovel")) return "IRON_SHOVEL";
        if (id.contains("hoe")) return "IRON_HOE";
        if (id.contains("helmet")) return "IRON_HELMET";
        if (id.contains("chestplate")) return "IRON_CHESTPLATE";
        if (id.contains("leggings")) return "IRON_LEGGINGS";
        if (id.contains("boots")) return "IRON_BOOTS";
        if (id.contains("bow")) return "BOW";
        if (spec.isFood()) return "APPLE";
        if (id.contains("block") || id.contains("ore") || id.contains("stone")) return "STONE";
        return "IRON_SWORD";
    }

    private static String pretty(String id) {
        String[] parts = id.split(":");
        String base = parts[parts.length - 1].replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : base.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
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
