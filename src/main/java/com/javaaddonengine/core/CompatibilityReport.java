package com.javaaddonengine.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregated compatibility report for one imported Add-On. */
public final class CompatibilityReport {
    private final String addonId;
    private final String displayName;
    private final String version;
    private final Map<String, EnumMap<CompatibilityLevel, Integer>> categories = new LinkedHashMap<>();
    private final List<CompatibilityEntry> entries = new ArrayList<>();
    private final List<String> missingDependencies = new ArrayList<>();
    private boolean resourcePackConverted;

    public CompatibilityReport(String addonId, String displayName, String version) {
        this.addonId = addonId;
        this.displayName = displayName;
        this.version = version;
        for (CompatibilityLevel l : CompatibilityLevel.values()) {
            // ensure maps exist lazily via add()
        }
    }

    public synchronized void add(String category, CompatibilityEntry entry) {
        entries.add(entry);
        EnumMap<CompatibilityLevel, Integer> counts =
                categories.computeIfAbsent(category, k -> new EnumMap<>(CompatibilityLevel.class));
        counts.merge(entry.getLevel(), 1, Integer::sum);
    }

    public synchronized void addMissingDependency(String dep) {
        missingDependencies.add(dep);
    }

    public void setResourcePackConverted(boolean v) { this.resourcePackConverted = v; }

    public String getAddonId() { return addonId; }
    public String getDisplayName() { return displayName; }
    public String getVersion() { return version; }
    public List<CompatibilityEntry> getEntries() { return new ArrayList<>(entries); }
    public List<String> getMissingDependencies() { return new ArrayList<>(missingDependencies); }
    public boolean isResourcePackConverted() { return resourcePackConverted; }

    public Map<String, EnumMap<CompatibilityLevel, Integer>> getCategories() {
        return new LinkedHashMap<>(categories);
    }

    /** Weighted score 0-100 across all entries. Empty report scores 0. */
    public synchronized double score() {
        if (entries.isEmpty()) return 0.0;
        double sum = 0.0;
        for (CompatibilityEntry e : entries) sum += e.getLevel().weight();
        return (sum / entries.size()) * 100.0;
    }

    public String grade() {
        double s = score();
        if (s >= 90) return "EXCELLENT";
        if (s >= 75) return "GOOD";
        if (s >= 50) return "FAIR";
        if (s >= 25) return "POOR";
        return "MINIMAL";
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================\n");
        sb.append("JavaAddonEngine Compatibility Report\n");
        sb.append("==================================\n\n");
        sb.append("Addon:\n").append(displayName).append("\n\n");
        sb.append("Version:\n").append(version).append("\n\n");
        sb.append("Compatibility Score:\n").append(String.format("%.0f%%", score())).append("\n\n");
        for (Map.Entry<String, EnumMap<CompatibilityLevel, Integer>> cat : categories.entrySet()) {
            sb.append(cat.getKey()).append("\n");
            EnumMap<CompatibilityLevel, Integer> c = cat.getValue();
            sb.append("  Full: ").append(c.getOrDefault(CompatibilityLevel.FULL, 0)).append("\n");
            sb.append("  Partial: ").append(
                    c.getOrDefault(CompatibilityLevel.PARTIAL, 0)
                    + c.getOrDefault(CompatibilityLevel.EMULATED, 0)
                    + c.getOrDefault(CompatibilityLevel.FALLBACK, 0)).append("\n");
            sb.append("  Unsupported: ").append(c.getOrDefault(CompatibilityLevel.UNSUPPORTED, 0)).append("\n\n");
        }
        sb.append("Resource Pack\n  Converted: ").append(resourcePackConverted ? "YES" : "NO").append("\n\n");
        sb.append("Dependencies\n  Missing: ").append(missingDependencies.size()).append("\n");
        for (String d : missingDependencies) sb.append("  - ").append(d).append("\n");
        sb.append("\nOverall:\n").append(grade()).append("\n");
        if (!entries.isEmpty()) {
            sb.append("\nDetails:\n");
            for (CompatibilityEntry e : entries) sb.append("  ").append(e).append("\n");
        }
        return sb.toString();
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("addon", addonId);
        root.put("name", displayName);
        root.put("version", version);
        root.put("score", Math.round(score()));
        root.put("grade", grade());
        root.put("resourcePackConverted", resourcePackConverted);
        root.put("missingDependencies", missingDependencies);
        List<Map<String, String>> det = new ArrayList<>();
        for (CompatibilityEntry e : entries) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("component", e.getComponent());
            m.put("level", e.getLevel().name());
            m.put("message", e.getMessage());
            m.put("file", e.getFile());
            det.add(m);
        }
        root.put("entries", det);
        return gson.toJson(root);
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>JAE Report - ")
          .append(escape(displayName)).append("</title></head><body>");
        sb.append("<h1>JavaAddonEngine Compatibility Report</h1>");
        sb.append("<p><b>Addon:</b> ").append(escape(displayName)).append("</p>");
        sb.append("<p><b>Version:</b> ").append(escape(version)).append("</p>");
        sb.append("<p><b>Score:</b> ").append(String.format("%.0f%%", score()))
          .append(" (").append(grade()).append(")</p>");
        sb.append("<table border=\"1\"><tr><th>Component</th><th>Level</th><th>Message</th><th>File</th></tr>");
        for (CompatibilityEntry e : entries) {
            sb.append("<tr><td>").append(escape(e.getComponent())).append("</td><td>")
              .append(e.getLevel()).append("</td><td>").append(escape(e.getMessage()))
              .append("</td><td>").append(escape(e.getFile())).append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
