package com.javaaddonengine.entities;

import com.google.gson.JsonArray;
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

/** Parses Bedrock entity files into AddonEntitySpec objects with per-component classification. */
public final class EntityParser {

    private EntityParser() {}

    public static void parse(ParsedAddon parsed, File packRoot, CompatibilityReport report,
                             Map<String, AddonEntitySpec> out, AdapterRegistry adapters) {
        for (File f : parsed.getEntityFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Entities", ComponentRegistry.classify("Entities", "entity:unparseable", rel));
                continue;
            }
            try {
                JsonObject desc = dig(root, "minecraft:entity", "description");
                String identifier = desc != null && desc.has("identifier")
                        ? desc.get("identifier").getAsString() : basename(f);
                AddonEntitySpec spec = new AddonEntitySpec(identifier.toLowerCase(Locale.ROOT));
                if (desc != null && desc.has("properties")) {
                    // keep raw properties as extra metadata
                    spec.getExtra().put("bedrock_properties", desc.get("properties").toString());
                }
                JsonObject components = dig(root, "minecraft:entity", "component_groups") != null
                        ? mergeGroups(root) : dig(root, "minecraft:entity", "components");
                if (components == null) components = dig(root, "minecraft:entity", "component_groups");
                if (components == null) components = new JsonObject();
                // description-level runtime id
                if (desc != null && desc.has("runtime_identifier")) {
                    spec.getExtra().put("runtime_identifier", desc.get("runtime_identifier").getAsString());
                }
                mergeEvents(root, spec);
                for (Map.Entry<String, JsonElement> e : components.entrySet()) {
                    String cid = e.getKey();
                    JsonObject raw = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
                    NormalizedComponent nc = new NormalizedComponent(cid, raw);
                    applyComponent(spec, nc, rel, report, out, adapters);
                }
                // client entity geometry reference (strip Gson quotes: "geometry.x" -> geometry.x)
                if (desc != null && desc.has("geometry") && desc.get("geometry").isJsonPrimitive()) {
                    try {
                        spec.setGeometry(desc.get("geometry").getAsString());
                    } catch (Exception ignored) {}
                }
                // base vanilla mapping from type families / identifier hints
                spec.setBaseVanilla(guessVanilla(spec));
                out.put(spec.getId(), spec);
            } catch (Exception ex) {
                report.add("Entities", ComponentRegistry.classify("Entities", "entity:failed", rel));
            }
        }
    }

    private static void applyComponent(AddonEntitySpec spec, NormalizedComponent nc, String rel,
                                       CompatibilityReport report, Map<String, AddonEntitySpec> out,
                                       AdapterRegistry adapters) {
        String cid = nc.getBedrockId();
        if (adapters != null && adapters.get(cid) != null) {
            report.add("Entities", adapters.get(cid).classify(nc, rel));
            return;
        }
        report.add("Entities", ComponentRegistry.classify("Entities", cid, rel));
        switch (cid) {
            case "minecraft:health":
                spec.setHealth(nc.num("value", nc.num("max", 20.0)));
                break;
            case "minecraft:movement":
            case "minecraft:movement.basic":
                spec.setMovementSpeed(nc.num("value", 0.25));
                break;
            case "minecraft:attack":
                spec.setAttackDamage(nc.num("damage", 2.0));
                break;
            case "minecraft:scale":
                spec.setScale(nc.num("value", 1.0));
                break;
            case "minecraft:follow_range":
                spec.setFollowRange(nc.num("value", 16.0));
                break;
            case "minecraft:knockback_resistance":
                spec.setKnockbackResistance(nc.num("value", 0.0));
                break;
            case "minecraft:experience_reward":
                if (nc.getRaw().has("value") && nc.getRaw().get("value").isJsonPrimitive()) {
                    try { spec.setExperience(nc.getRaw().get("value").getAsInt()); } catch (Exception ignored) {}
                }
                break;
            case "minecraft:loot":
                spec.setLootTable(nc.str("table", ""));
                break;
            case "minecraft:type_family": {
                JsonObject raw = nc.getRaw();
                if (raw.has("family")) {
                    try {
                        for (JsonElement el : raw.getAsJsonArray("family")) spec.getTypeFamilies().add(el.getAsString());
                    } catch (Exception e) {
                        spec.getTypeFamilies().add(raw.get("family").getAsString());
                    }
                }
                break;
            }
            default:
                if (cid.startsWith("minecraft:behavior")) spec.getBehaviors().add(cid);
                else spec.getExtra().put(cid, nc.getRaw().toString());
                break;
        }
    }

    private static JsonObject mergeGroups(JsonObject root) {
        JsonObject entity = root.has("minecraft:entity") ? root.getAsJsonObject("minecraft:entity") : root;
        JsonObject merged = new JsonObject();
        if (entity.has("components") && entity.get("components").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : entity.getAsJsonObject("components").entrySet()) {
                merged.add(e.getKey(), e.getValue());
            }
        }
        if (entity.has("component_groups") && entity.get("component_groups").isJsonObject()) {
            for (Map.Entry<String, JsonElement> g : entity.getAsJsonObject("component_groups").entrySet()) {
                if (g.getValue().isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : g.getValue().getAsJsonObject().entrySet()) {
                        if (!merged.has(e.getKey())) merged.add(e.getKey(), e.getValue());
                    }
                }
            }
        }
        return merged;
    }

    private static void mergeEvents(JsonObject root, AddonEntitySpec spec) {
        JsonObject entity = root.has("minecraft:entity") ? root.getAsJsonObject("minecraft:entity") : null;
        if (entity != null && entity.has("events") && entity.get("events").isJsonObject()) {
            spec.getExtra().put("bedrock_events", entity.get("events").toString());
        }
    }

    private static JsonObject dig(JsonObject root, String... path) {
        JsonObject cur = root;
        for (String key : path) {
            if (cur == null || !cur.has(key)) return null;
            JsonElement el = cur.get(key);
            if (el.isJsonObject()) cur = el.getAsJsonObject();
            else return null;
        }
        return cur;
    }

    private static String guessVanilla(AddonEntitySpec spec) {
        // Strong identifier keywords win over generic families (e.g. a "robot"
        // in the monster family is still best represented as an iron golem).
        String strong = strongIdMatch(spec.getId());
        if (strong != null) return strong;
        for (String fam : spec.getTypeFamilies()) {
            String f = fam.toLowerCase(Locale.ROOT);
            if (f.contains("undead") || f.contains("zombie")) return "ZOMBIE";
            if (f.contains("skeleton")) return "SKELETON";
            if (f.contains("creeper")) return "CREEPER";
            if (f.contains("spider")) return "SPIDER";
            if (f.contains("pig") && !f.contains("zombie")) return "PIG";
            if (f.contains("cow")) return "COW";
            if (f.contains("villager")) return "VILLAGER";
            if (f.contains("illager") || f.contains("pillager")) return "PILLAGER";
            if (f.contains("flying") || f.contains("bird") || f.contains("phantom")) return "PHANTOM";
            if (f.contains("fish")) return "COD";
            if (f.contains("monster") || f.contains("hostile")) return "ZOMBIE";
            if (f.contains("animal") || f.contains("passive")) return "COW";
        }
        return idFallback(spec.getId());
    }

    private static String strongIdMatch(String id) {
        if (id.contains("robot") || id.contains("golem")) return "IRON_GOLEM";
        if (id.contains("npc") || id.contains("villager")) return "VILLAGER";
        if (id.contains("creeper")) return "CREEPER";
        if (id.contains("skeleton")) return "SKELETON";
        if (id.contains("zombie")) return "ZOMBIE";
        return null;
    }

    private static String idFallback(String id) {
        String strong = strongIdMatch(id);
        return strong != null ? strong : "ZOMBIE";
    }

    private static String basename(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static String rel(File root, File f) {
        return root.toURI().relativize(f.toURI()).getPath();
    }

    /** Kept for API symmetry; unused import guard. */
    @SuppressWarnings("unused")
    private static void useJsonArray(JsonArray a) {}
}
