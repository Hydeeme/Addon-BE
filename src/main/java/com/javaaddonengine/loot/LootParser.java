package com.javaaddonengine.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses Bedrock loot tables into AddonLootTable models. */
public final class LootParser {

    private LootParser() {}

    public static Map<String, AddonLootTable> parse(ParsedAddon parsed, CompatibilityReport report) {
        Map<String, AddonLootTable> out = new LinkedHashMap<>();
        for (File f : parsed.getLootFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Loot", new CompatibilityEntry("loot", CompatibilityLevel.UNSUPPORTED,
                        "Unparseable loot table: " + e.getMessage(), rel, -1));
                continue;
            }
            try {
                AddonLootTable table = new AddonLootTable(basename(f));
                if (root.has("pools") && root.get("pools").isJsonArray()) {
                    for (JsonElement pel : root.getAsJsonArray("pools")) {
                        if (!pel.isJsonObject()) continue;
                        JsonObject p = pel.getAsJsonObject();
                        AddonLootTable.Pool pool = new AddonLootTable.Pool();
                        pool.rolls = intOf(p.get("rolls"), 1);
                        if (p.has("conditions")) {
                            for (JsonElement c : p.getAsJsonArray("conditions")) {
                                pool.conditions.add(c.toString());
                            }
                            report.add("Loot", new CompatibilityEntry("loot:condition", CompatibilityLevel.PARTIAL,
                                    "Pool conditions kept as metadata; common ones evaluated at runtime.", rel, -1));
                        }
                        if (p.has("entries")) {
                            for (JsonElement eel : p.getAsJsonArray("entries")) {
                                if (!eel.isJsonObject()) continue;
                                JsonObject e = eel.getAsJsonObject();
                                AddonLootTable.Entry entry = new AddonLootTable.Entry();
                                entry.type = e.has("type") ? e.get("type").getAsString() : "item";
                                entry.name = e.has("name") ? e.get("name").getAsString() : "";
                                entry.weight = intOf(e.get("weight"), 1);
                                if (e.has("functions")) {
                                    for (JsonElement fn : e.getAsJsonArray("functions")) {
                                        entry.functions.add(fn.toString());
                                        if (fn.isJsonObject() && fn.getAsJsonObject().has("function")) {
                                            String fnName = fn.getAsJsonObject().get("function").getAsString();
                                            if (fnName.contains("set_count")) {
                                                parseCount(fn.getAsJsonObject(), entry);
                                            }
                                        }
                                    }
                                }
                                if (e.has("conditions")) {
                                    for (JsonElement c : e.getAsJsonArray("conditions")) entry.conditions.add(c.toString());
                                }
                                pool.entries.add(entry);
                            }
                        }
                        table.getPools().add(pool);
                    }
                    report.add("Loot", new CompatibilityEntry("loot:" + table.getId(), CompatibilityLevel.FULL,
                            "Loot table converted (" + table.getPools().size() + " pools).", rel, -1));
                } else {
                    report.add("Loot", new CompatibilityEntry("loot:" + table.getId(), CompatibilityLevel.PARTIAL,
                            "No pools array; kept as metadata.", rel, -1));
                }
                out.put(table.getId(), table);
            } catch (Exception e) {
                report.add("Loot", new CompatibilityEntry("loot", CompatibilityLevel.UNSUPPORTED,
                        "Failed to convert loot table: " + e.getMessage(), rel, -1));
            }
        }
        return out;
    }

    private static void parseCount(JsonObject fn, AddonLootTable.Entry entry) {
        try {
            JsonObject count = fn.getAsJsonObject("count");
            if (count.has("min")) entry.minCount = count.get("min").getAsInt();
            if (count.has("max")) entry.maxCount = count.get("max").getAsInt();
            if (count.has("value")) entry.minCount = entry.maxCount = count.get("value").getAsInt();
        } catch (Exception ignored) {}
    }

    static int intOf(JsonElement el, int def) {
        if (el == null || el.isJsonNull()) return def;
        try {
            if (el.isJsonPrimitive()) return el.getAsInt();
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("min")) return o.get("min").getAsInt();
                if (o.has("value")) return o.get("value").getAsInt();
            }
        } catch (Exception ignored) {}
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
