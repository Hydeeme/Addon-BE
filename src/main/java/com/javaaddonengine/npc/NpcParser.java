package com.javaaddonengine.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;

/**
 * Parses Bedrock NPC dialogue files and JAE extended NPC definitions.
 *
 * <p>Bedrock format ({@code dialogue/*.json} with {@code minecraft:npc_dialogue} scenes)
 * is translated scene-by-scene into {@link AddonNpc} definitions, emulated at
 * runtime through chat dialogue plus inventory GUIs.
 *
 * <p>JAE extended format ({@code npcs/*.json}) is fully supported natively:
 * <pre>
 * {
 *   "id": "keeper",
 *   "name": "Golem Keeper",
 *   "dialogue": ["Welcome, traveler."],
 *   "commands": ["jae give spark_sword"],
 *   "shop": { "spark_shard": 10 },
 *   "permission": "jae.shop",
 *   "cooldown_seconds": 5
 * }
 * </pre>
 */
public final class NpcParser {

    private NpcParser() {}

    private static final int MAX_FILES = 100;
    private static final long MAX_BYTES = 1024L * 1024L;

    public static void parse(ParsedAddon parsed, CompatibilityReport report, Addon addon) {
        int count = 0;
        for (File f : parsed.getDialogueFiles()) {
            if (count++ >= MAX_FILES) break;
            String rel = rel(parsed.getRoot(), f);
            if (f.length() > MAX_BYTES) {
                report.add("NPCs", new CompatibilityEntry("npc",
                        CompatibilityLevel.UNSUPPORTED, "Dialogue file too large; skipped.", rel, -1));
                continue;
            }
            JsonObject root;
            try {
                root = ParsedAddon.readJson(f);
            } catch (Exception e) {
                report.add("NPCs", new CompatibilityEntry("npc",
                        CompatibilityLevel.UNSUPPORTED, "Unparseable dialogue JSON: " + e.getMessage(), rel, -1));
                continue;
            }
            try {
                if (root.has("minecraft:npc_dialogue")) {
                    parseBedrock(root.getAsJsonObject("minecraft:npc_dialogue"), basename(f), rel, report, addon);
                } else if (root.has("name") || root.has("dialogue") || root.has("id")) {
                    parseExtended(root, basename(f), rel, report, addon);
                } else {
                    report.add("NPCs", new CompatibilityEntry("npc",
                            CompatibilityLevel.PARTIAL, "Unknown dialogue layout; kept as metadata.", rel, -1));
                }
            } catch (Exception e) {
                report.add("NPCs", new CompatibilityEntry("npc",
                        CompatibilityLevel.UNSUPPORTED, "Failed to convert dialogue: " + e.getMessage(), rel, -1));
            }
        }
    }

    private static void parseBedrock(JsonObject dialogue, String base, String rel,
                                     CompatibilityReport report, Addon addon) {
        if (!dialogue.has("scenes") || !dialogue.get("scenes").isJsonArray()) {
            report.add("NPCs", new CompatibilityEntry("npc_dialogue",
                    CompatibilityLevel.PARTIAL, "No scenes array; kept as metadata.", rel, -1));
            return;
        }
        for (JsonElement sel : dialogue.getAsJsonArray("scenes")) {
            if (!sel.isJsonObject()) continue;
            JsonObject scene = sel.getAsJsonObject();
            String tag = scene.has("scene_tag") ? scene.get("scene_tag").getAsString() : base;
            String id = (base + "_" + tag).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            AddonNpc npc = new AddonNpc(id);
            if (scene.has("npc_name")) npc.setName(scene.get("npc_name").getAsString());
            if (scene.has("text")) {
                try {
                    for (JsonElement t : scene.getAsJsonArray("text")) npc.getDialogue().add(t.getAsString());
                } catch (Exception e) {
                    npc.getDialogue().add(scene.get("text").getAsString());
                }
            } else if (scene.has("dialogue")) {
                npc.getDialogue().add(scene.get("dialogue").getAsString());
            }
            if (scene.has("buttons") && scene.get("buttons").isJsonArray()) {
                for (JsonElement b : scene.getAsJsonArray("buttons")) {
                    if (!b.isJsonObject()) continue;
                    JsonObject btn = b.getAsJsonObject();
                    String label = btn.has("name") ? btn.get("name").getAsString() : "Continue";
                    if (btn.has("commands") && btn.get("commands").isJsonArray()) {
                        for (JsonElement c : btn.getAsJsonArray("commands")) {
                            npc.getCommands().add(c.getAsString());
                        }
                    }
                    npc.getDialogue().add("[" + label + "]");
                }
            }
            addon.getNpcs().put(id, npc);
            report.add("NPCs", new CompatibilityEntry("npc_dialogue:" + tag,
                    CompatibilityLevel.EMULATED,
                    "Scene '" + tag + "' emulated via chat dialogue + GUI buttons.", rel, -1));
        }
    }

    private static void parseExtended(JsonObject root, String base, String rel,
                                      CompatibilityReport report, Addon addon) {
        String id = root.has("id") ? root.get("id").getAsString()
                : base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        AddonNpc npc = new AddonNpc(id);
        if (root.has("name")) npc.setName(root.get("name").getAsString());
        if (root.has("skin")) npc.setSkin(root.get("skin").getAsString());
        if (root.has("dialogue") && root.get("dialogue").isJsonArray()) {
            for (JsonElement d : root.getAsJsonArray("dialogue")) npc.getDialogue().add(d.getAsString());
        }
        if (root.has("commands") && root.get("commands").isJsonArray()) {
            for (JsonElement c : root.getAsJsonArray("commands")) {
                try {
                    npc.getCommands().add(c.getAsString());
                } catch (Exception ignored) {}
            }
        }
        if (root.has("shop") && root.get("shop").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("shop").entrySet()) {
                try {
                    npc.getShop().put(e.getKey(), e.getValue().getAsDouble());
                } catch (Exception ignored) {
                    npc.getShop().put(e.getKey(), 0.0);
                }
            }
        }
        if (root.has("permission")) npc.setPermission(root.get("permission").getAsString());
        if (root.has("cooldown_seconds")) {
            try {
                npc.setCooldownSeconds(root.get("cooldown_seconds").getAsInt());
            } catch (Exception ignored) {}
        }
        addon.getNpcs().put(id, npc);
        report.add("NPCs", new CompatibilityEntry("npc:" + id,
                CompatibilityLevel.FULL, "NPC definition converted (" + npc.getDialogue().size()
                        + " lines, " + npc.getCommands().size() + " commands"
                        + (npc.getShop().isEmpty() ? "" : ", shop with " + npc.getShop().size() + " trades")
                        + ").", rel, -1));
    }

    /** Read a plain-text dialogue source (unused helper kept for API symmetry). */
    static String readText(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
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
