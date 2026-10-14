package com.javaaddonengine.translator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.Map;

/** Classifies animations + controllers; simple transforms are emulatable, bone rigs need ModelEngine. */
public final class AnimationParser {

    private AnimationParser() {}

    public static void parse(ParsedAddon parsed, CompatibilityReport report) {
        for (File f : parsed.getAnimationFiles()) {
            String rel = rel(parsed.getRoot(), f);
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("Animations", new CompatibilityEntry("animation",
                        CompatibilityLevel.UNSUPPORTED, "Unparseable animation JSON.", rel, -1));
                continue;
            }
            try {
                if (root.has("animations")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
                        boolean simple = isSimple(e.getValue());
                        report.add("Animations", new CompatibilityEntry("animation:" + e.getKey(),
                                simple ? CompatibilityLevel.EMULATED : CompatibilityLevel.PARTIAL,
                                simple ? "Simple transform loop; emulated via display-entity interpolation."
                                        : "Complex bone animation; needs ModelEngine, display fallback otherwise.",
                                rel, -1));
                    }
                } else if (root.has("animation_controllers")) {
                    report.add("Animations", new CompatibilityEntry("animation_controller",
                            CompatibilityLevel.PARTIAL, "Controller states kept as metadata; transitions approximated.", rel, -1));
                } else {
                    report.add("Animations", new CompatibilityEntry("animation",
                            CompatibilityLevel.PARTIAL, "Unknown animation layout; kept as metadata.", rel, -1));
                }
            } catch (Exception ex) {
                report.add("Animations", new CompatibilityEntry("animation",
                        CompatibilityLevel.UNSUPPORTED, "Failed to analyze: " + ex.getMessage(), rel, -1));
            }
        }
    }

    private static boolean isSimple(JsonElement el) {
        if (!el.isJsonObject()) return true;
        String s = el.toString();
        return !(s.contains("bones") && s.length() > 4000);
    }

    private static String rel(File root, File f) {
        return root.toURI().relativize(f.toURI()).getPath();
    }
}
