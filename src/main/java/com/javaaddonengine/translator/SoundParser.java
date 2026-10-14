package com.javaaddonengine.translator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Parses sounds.json; identifiers are namespaced to javaaddonengine:<name>. */
public final class SoundParser {

    private SoundParser() {}

    public static Map<String, String> parse(ParsedAddon parsed, CompatibilityReport report) {
        Map<String, String> out = new LinkedHashMap<>();
        for (File f : parsed.getSoundFiles()) {
            if (!f.getName().equalsIgnoreCase("sounds.json")) {
                // raw audio file; handled by resource pack generator
                continue;
            }
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Sounds", new CompatibilityEntry("sound",
                        CompatibilityLevel.UNSUPPORTED, "Unparseable sounds.json.", rel, -1));
                continue;
            }
            try {
                JsonObject defs = root.has("sound_definitions") ? root.getAsJsonObject("sound_definitions") : root;
                for (Map.Entry<String, JsonElement> e : defs.entrySet()) {
                    String javaId = "javaaddonengine:" + e.getKey().toLowerCase(Locale.ROOT)
                            .replace(":", ".").replaceAll("[^a-z0-9_./-]", "_");
                    out.put(e.getKey(), javaId);
                    report.add("Sounds", new CompatibilityEntry("sound:" + e.getKey(),
                            CompatibilityLevel.FULL, "Remapped to " + javaId + ".", rel, -1));
                }
            } catch (Exception ex) {
                report.add("Sounds", new CompatibilityEntry("sound",
                        CompatibilityLevel.PARTIAL, "Non-standard sounds.json layout.", rel, -1));
            }
        }
        return out;
    }

    public static String remap(String bedrockId) {
        return "javaaddonengine:" + bedrockId.toLowerCase(Locale.ROOT)
                .replace(":", ".").replaceAll("[^a-z0-9_./-]", "_");
    }

    private static String rel(File root, File f) {
        return root.toURI().relativize(f.toURI()).getPath();
    }
}
