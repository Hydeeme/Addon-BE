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
        String cat = category == null ? "Converted" : category;
        // Normalize: most parsers build entries with the default "Converted"
        // category (5-arg constructor); the add-category is authoritative so
        // per-category breakdowns and manifest fixup copies stay correct.
        CompatibilityEntry effective = entry;
        if (entry != null && "Converted".equals(entry.getCategory()) && !"Converted".equals(cat)) {
            effective = new CompatibilityEntry(cat, entry.getComponent(), entry.getLevel(),
                    entry.getMessage(), entry.getFile(), entry.getLine());
        }
        entries.add(effective);
        EnumMap<CompatibilityLevel, Integer> counts =
                categories.computeIfAbsent(cat, k -> new EnumMap<>(CompatibilityLevel.class));
        counts.merge(effective.getLevel(), 1, Integer::sum);
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

    /** Weighted score 0-100 for a single category. Empty category scores 0. */
    public synchronized double categoryScore(String category) {
        double sum = 0.0;
        int n = 0;
        for (CompatibilityEntry e : entries) {
            if (category != null && category.equals(e.getCategory())) {
                sum += e.getLevel().weight();
                n++;
            }
        }
        return n == 0 ? 0.0 : (sum / n) * 100.0;
    }

    /** All category names mapped to their weighted 0-100 score (insertion order). */
    public synchronized Map<String, Double> categoryScores() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String cat : categories.keySet()) out.put(cat, categoryScore(cat));
        return out;
    }

    /** Weighted score 0-100 for a single file (rel path). Empty file scores 0. */
    public synchronized double fileScore(String file) {
        double sum = 0.0;
        int n = 0;
        for (CompatibilityEntry e : entries) {
            if (file != null && file.equals(e.getFile())) {
                sum += e.getLevel().weight();
                n++;
            }
        }
        return n == 0 ? 0.0 : (sum / n) * 100.0;
    }

    /**
     * Per-file breakdown: rel path -&gt; weighted 0-100 score.
     * Entries without a file are grouped under "(global)".
     * Insertion order follows first appearance in the report.
     */
    public synchronized Map<String, Double> fileScores() {
        Map<String, List<CompatibilityEntry>> byFile = new LinkedHashMap<>();
        for (CompatibilityEntry e : entries) {
            String f = e.getFile() == null || e.getFile().isEmpty() ? "(global)" : e.getFile();
            byFile.computeIfAbsent(f, k -> new ArrayList<>()).add(e);
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<CompatibilityEntry>> en : byFile.entrySet()) {
            double sum = 0.0;
            for (CompatibilityEntry e : en.getValue()) sum += e.getLevel().weight();
            out.put(en.getKey(), (sum / en.getValue().size()) * 100.0);
        }
        return out;
    }

    /** Entry counts for one file, keyed by level (for detail views). */
    public synchronized EnumMap<CompatibilityLevel, Integer> fileCounts(String file) {
        EnumMap<CompatibilityLevel, Integer> counts = new EnumMap<>(CompatibilityLevel.class);
        for (CompatibilityEntry e : entries) {
            String f = e.getFile() == null || e.getFile().isEmpty() ? "(global)" : e.getFile();
            if (f.equals(file)) counts.merge(e.getLevel(), 1, Integer::sum);
        }
        return counts;
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
        sb.append("Compatibility Score:\n").append(String.format("%.0f%%", score()))
          .append(" (").append(grade()).append(")\n\n");
        sb.append("Per-category breakdown:\n");
        for (Map.Entry<String, EnumMap<CompatibilityLevel, Integer>> cat : categories.entrySet()) {
            sb.append(cat.getKey()).append(" — ").append(String.format("%.0f%%", categoryScore(cat.getKey()))).append("\n");
            EnumMap<CompatibilityLevel, Integer> c = cat.getValue();
            sb.append("  Full: ").append(c.getOrDefault(CompatibilityLevel.FULL, 0)).append("\n");
            sb.append("  Partial: ").append(
                    c.getOrDefault(CompatibilityLevel.PARTIAL, 0)
                    + c.getOrDefault(CompatibilityLevel.EMULATED, 0)
                    + c.getOrDefault(CompatibilityLevel.FALLBACK, 0)).append("\n");
            sb.append("  Unsupported: ").append(c.getOrDefault(CompatibilityLevel.UNSUPPORTED, 0)).append("\n\n");
        }
        Map<String, Double> files = fileScores();
        if (!files.isEmpty()) {
            sb.append("Per-file breakdown (each mob/item/block/function file scores on its own):\n");
            for (Map.Entry<String, Double> en : files.entrySet()) {
                sb.append("  ").append(String.format("%3.0f%%", en.getValue())).append("  ").append(en.getKey()).append("\n");
            }
            sb.append("\n");
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
        Map<String, Long> catScores = new LinkedHashMap<>();
        for (Map.Entry<String, Double> en : categoryScores().entrySet()) {
            catScores.put(en.getKey(), Math.round(en.getValue()));
        }
        root.put("categoryScores", catScores);
        Map<String, Long> fScores = new LinkedHashMap<>();
        for (Map.Entry<String, Double> en : fileScores().entrySet()) {
            fScores.put(en.getKey(), Math.round(en.getValue()));
        }
        root.put("fileScores", fScores);
        List<Map<String, String>> det = new ArrayList<>();
        for (CompatibilityEntry e : entries) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("category", e.getCategory());
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
          .append(escape(displayName)).append("</title>");
        sb.append("<style>body{font-family:sans-serif;max-width:900px;margin:2em auto;padding:0 1em}"
          + ".bar{background:#eee;border-radius:4px;height:14px;width:220px;display:inline-block;vertical-align:middle}"
          + ".fill{background:#4caf50;height:14px;border-radius:4px;display:inline-block}</style>");
        sb.append("</head><body>");
        sb.append("<h1>JavaAddonEngine Compatibility Report</h1>");
        sb.append("<p><b>Addon:</b> ").append(escape(displayName)).append("</p>");
        sb.append("<p><b>Version:</b> ").append(escape(version)).append("</p>");
        sb.append("<p><b>Score:</b> ").append(String.format("%.0f%%", score()))
          .append(" (").append(grade()).append(")</p>");
        sb.append("<h2>Per-category breakdown</h2>");
        sb.append("<table border=\"1\"><tr><th>Category</th><th>Score</th><th>Full</th><th>Partial*</th><th>Unsupported</th></tr>");
        for (Map.Entry<String, EnumMap<CompatibilityLevel, Integer>> cat : categories.entrySet()) {
            EnumMap<CompatibilityLevel, Integer> c = cat.getValue();
            sb.append("<tr><td>").append(escape(cat.getKey())).append("</td><td>")
              .append(String.format("%.0f%%", categoryScore(cat.getKey()))).append("</td><td>")
              .append(c.getOrDefault(CompatibilityLevel.FULL, 0)).append("</td><td>")
              .append(c.getOrDefault(CompatibilityLevel.PARTIAL, 0)
                    + c.getOrDefault(CompatibilityLevel.EMULATED, 0)
                    + c.getOrDefault(CompatibilityLevel.FALLBACK, 0)).append("</td><td>")
              .append(c.getOrDefault(CompatibilityLevel.UNSUPPORTED, 0)).append("</td></tr>");
        }
        sb.append("</table><p><small>*Partial = PARTIAL + EMULATED + FALLBACK.</small></p>");
        sb.append("<h2>Per-file breakdown (each mob/item/block/function file scores on its own)</h2>");
        sb.append("<table border=\"1\"><tr><th>Score</th><th>File</th></tr>");
        for (Map.Entry<String, Double> en : fileScores().entrySet()) {
            long pct = Math.round(en.getValue());
            sb.append("<tr><td>").append(pct).append("% ")
              .append("<span class=\"bar\"><span class=\"fill\" style=\"width:").append(pct)
              .append("px\"></span></span></td><td>").append(escape(en.getKey())).append("</td></tr>");
        }
        sb.append("</table>");
        sb.append("<h2>Details</h2>");
        sb.append("<table border=\"1\"><tr><th>Category</th><th>Component</th><th>Level</th><th>Message</th><th>File</th></tr>");
        for (CompatibilityEntry e : entries) {
            sb.append("<tr><td>").append(escape(e.getCategory())).append("</td><td>")
              .append(escape(e.getComponent())).append("</td><td>")
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
