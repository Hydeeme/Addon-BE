package com.javaaddonengine.blocks;

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

/** Parses Bedrock block files (both description/components and legacy material layouts). */
public final class BlockParser {

    private BlockParser() {}

    public static void parse(ParsedAddon parsed, CompatibilityReport report,
                             Map<String, AddonBlockSpec> out, AdapterRegistry adapters,
                             AddonBlockSpec.RenderMode configured) {
        for (File f : parsed.getBlockFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Blocks", ComponentRegistry.classify("Blocks", "block:unparseable", rel));
                continue;
            }
            try {
                JsonObject block = root.has("minecraft:block") ? root.getAsJsonObject("minecraft:block") : root;
                JsonObject desc = block.has("description") ? block.getAsJsonObject("description") : new JsonObject();
                String identifier = desc.has("identifier") ? desc.get("identifier").getAsString() : basename(f);
                AddonBlockSpec spec = new AddonBlockSpec(identifier.toLowerCase(Locale.ROOT));
                JsonObject components = block.has("components") ? block.getAsJsonObject("components") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : block.entrySet()) {
                    if (e.getKey().startsWith("minecraft:") && !components.has(e.getKey())) {
                        components.add(e.getKey(), e.getValue());
                    }
                }
                for (Map.Entry<String, JsonElement> e : components.entrySet()) {
                    String cid = e.getKey();
                    JsonObject raw = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : primitive(e.getValue());
                    NormalizedComponent nc = new NormalizedComponent(cid, raw);
                    if (adapters != null && adapters.get(cid) != null) {
                        report.add("Blocks", adapters.get(cid).classify(nc, rel));
                        continue;
                    }
                    report.add("Blocks", ComponentRegistry.classify("Blocks", cid, rel));
                    apply(spec, nc);
                }
                if (desc.has("properties")) {
                    try {
                        for (Map.Entry<String, JsonElement> e : desc.getAsJsonObject("properties").entrySet()) {
                            spec.getStates().add(e.getKey() + "=" + e.getValue());
                        }
                    } catch (Exception ignored) {}
                }
                spec.setRenderMode(configured == AddonBlockSpec.RenderMode.FALLBACK ? autoMode() : configured);
                out.put(spec.getId(), spec);
            } catch (Exception ex) {
                report.add("Blocks", ComponentRegistry.classify("Blocks", "block:failed", rel));
            }
        }
    }

    private static void apply(AddonBlockSpec spec, NormalizedComponent nc) {
        JsonObject raw = nc.getRaw();
        switch (nc.getBedrockId()) {
            case "minecraft:hardness":
                spec.setHardness(nc.num("value", 1.0));
                break;
            case "minecraft:resistance":
            case "minecraft:blast_resistance":
                spec.setHardness(spec.getHardness());
                spec.setResistance(nc.num("value", 1.0));
                break;
            case "minecraft:material":
            case "minecraft:map_color":
                spec.setMaterial(nc.str("value", nc.str("color", "stone")));
                break;
            case "minecraft:light":
            case "minecraft:light_emission":
                spec.setLight((int) nc.num("emission", nc.num("value", 0)));
                break;
            case "minecraft:sound":
                spec.setSound(nc.str("sound", raw.toString()));
                break;
            case "minecraft:loot":
                spec.setLoot(nc.str("table", ""));
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
            default:
                spec.getExtra().put(nc.getBedrockId(), raw.toString());
                break;
        }
    }

    private static JsonObject primitive(JsonElement el) {
        JsonObject o = new JsonObject();
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isNumber()) o.addProperty("value", el.getAsDouble());
            else if (el.getAsJsonPrimitive().isBoolean()) o.addProperty("value", el.getAsBoolean());
            else o.addProperty("value", el.getAsString());
        }
        return o;
    }

    private static AddonBlockSpec.RenderMode autoMode() {
        return AddonBlockSpec.RenderMode.DISPLAY_ENTITY;
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
