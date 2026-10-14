package com.javaaddonengine.commands;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parses .mcfunction files and classifies each command line as FULL/PARTIAL/UNSUPPORTED. */
public final class FunctionParser {

    private FunctionParser() {}

    private static final Set<String> FULL = new HashSet<>(Arrays.asList(
            "give", "summon", "tp", "teleport", "effect", "kill", "tag", "say",
            "tellraw", "title", "playsound", "particle", "time", "weather", "gamemode",
            "xp", "clear", "scoreboard", "execute", "setblock", "fill", "clone", "spreadplayers"));

    private static final Set<String> PARTIAL = new HashSet<>(Arrays.asList(
            "camera", "camerashake", "fog", "music", "playanimation", "ride",
            "structure", "loot", "dialogue", "hud", "inputpermission", "event"));

    public static final class ParsedFunction {
        public final String name;
        public final List<String> lines = new ArrayList<>();
        public ParsedFunction(String name) { this.name = name; }
    }

    public static List<ParsedFunction> parse(ParsedAddon parsed, CompatibilityReport report) {
        List<ParsedFunction> out = new ArrayList<>();
        for (File f : parsed.getFunctionFiles()) {
            String rel = rel(parsed.getRoot(), f);
            String text;
            try {
                text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                report.add("Functions", new CompatibilityEntry("function", CompatibilityLevel.UNSUPPORTED,
                        "Unreadable function file: " + e.getMessage(), rel, -1));
                continue;
            }
            ParsedFunction fn = new ParsedFunction(basename(f));
            int lineNo = 0;
            for (String rawLine : text.split("\r?\n")) {
                lineNo++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                fn.lines.add(line);
                String cmd = line.split("\\s+")[0].toLowerCase(Locale.ROOT);
                if (FULL.contains(cmd)) {
                    report.add("Functions", new CompatibilityEntry("command:" + cmd,
                            CompatibilityLevel.FULL, "Translated to Bukkit dispatch.", rel, lineNo));
                } else if (PARTIAL.contains(cmd)) {
                    report.add("Functions", new CompatibilityEntry("command:" + cmd,
                            CompatibilityLevel.PARTIAL, "Partially mapped; Bedrock-only flags ignored.", rel, lineNo));
                } else {
                    report.add("Functions", new CompatibilityEntry("command:" + cmd,
                            CompatibilityLevel.UNSUPPORTED, "No Java equivalent; line skipped at runtime.", rel, lineNo));
                }
            }
            out.add(fn);
        }
        return out;
    }

    /**
     * Translate a single Bedrock function line into a Java-side command string.
     * FULL commands map directly; PARTIAL commands map best-effort (Bedrock-only
     * flags are passed through and ignored by vanilla where unknown — Bukkit
     * rejects unknown commands safely). Unknown commands return null so
     * unsupported syntax is never blindly executed.
     */
    public static String translateLine(String line) {
        if (line == null) return null;
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) return null;
        if (t.regionMatches(true, 0, "minecraft:", 0, 10)) t = t.substring(10);
        String[] parts = t.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        if (FULL.contains(cmd) || PARTIAL.contains(cmd)) return t;
        return null;
    }

    /** True when the command has a direct Java equivalent. */
    public static boolean isFull(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) return false;
        return FULL.contains(t.split("\\s+")[0].toLowerCase(Locale.ROOT));
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
