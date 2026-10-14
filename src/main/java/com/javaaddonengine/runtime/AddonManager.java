package com.javaaddonengine.runtime;

import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.blocks.BlockParser;
import com.javaaddonengine.commands.FunctionParser;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.AddonCompatibilityException;
import com.javaaddonengine.core.AddonState;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.entities.EntityParser;
import com.javaaddonengine.items.ItemParser;
import com.javaaddonengine.loot.AddonLootTable;
import com.javaaddonengine.loot.LootEngine;
import com.javaaddonengine.loot.LootParser;
import com.javaaddonengine.npc.AddonNpc;
import com.javaaddonengine.npc.NpcManager;
import com.javaaddonengine.npc.NpcParser;
import com.javaaddonengine.parser.AddonManifest;
import com.javaaddonengine.parser.PackDetector;
import com.javaaddonengine.parser.ParsedAddon;
import com.javaaddonengine.parser.SafeArchiveExtractor;
import com.javaaddonengine.recipes.AddonRecipe;
import com.javaaddonengine.recipes.RecipeParser;
import com.javaaddonengine.recipes.RecipeRuntime;
import com.javaaddonengine.resourcepack.ResourcePackGenerator;
import com.javaaddonengine.scripting.ScriptAnalyzer;
import com.javaaddonengine.storage.AddonStore;
import com.javaaddonengine.translator.AdapterRegistry;
import com.javaaddonengine.translator.AnimationParser;
import com.javaaddonengine.translator.ParticleMapper;
import com.javaaddonengine.translator.SoundParser;
import com.javaaddonengine.version.VersionAdapter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Central import pipeline. Heavy work (extraction, JSON parsing, pack generation)
 * runs on the async executor; Bukkit registration happens on the main thread.
 */
public final class AddonManager {

    private final Plugin plugin;
    private final JaLogger log;
    private final Executor async;
    private final ApiImpl api;
    private final AdapterRegistry adapters;
    private final LootEngine lootEngine;
    private final VersionAdapter version;
    private final RecipeRuntime recipeRuntime;
    private final NpcManager npcManager;
    private final AddonStore store;
    private final File addonsDir;
    private final File importDir;
    private final long maxBytes;
    private final int maxFiles;
    private final boolean autoGeneratePack;
    private final AddonBlockSpec.RenderMode blockMode;

    public AddonManager(Plugin plugin, JaLogger log, Executor async, ApiImpl api,
                        AdapterRegistry adapters, LootEngine lootEngine, VersionAdapter version,
                        File addonsDir, RecipeRuntime recipeRuntime,
                        NpcManager npcManager, AddonStore store,
                        long maxBytes, int maxFiles,
                        boolean autoGeneratePack, AddonBlockSpec.RenderMode blockMode) {
        this.plugin = plugin;
        this.log = log;
        this.async = async;
        this.api = api;
        this.adapters = adapters;
        this.lootEngine = lootEngine;
        this.version = version;
        this.addonsDir = addonsDir;
        this.importDir = new File(addonsDir, "incoming");
        this.importDir.mkdirs();
        this.recipeRuntime = recipeRuntime;
        this.npcManager = npcManager;
        this.store = store;
        this.maxBytes = maxBytes;
        this.maxFiles = maxFiles;
        this.autoGeneratePack = autoGeneratePack;
        this.blockMode = blockMode;
    }

    public File getImportDir() { return importDir; }
    public File getAddonsDir() { return addonsDir; }

    /** Async import of an .mcaddon/.mcpack/.zip archive. Never blocks the main thread. */
    public CompletableFuture<Addon> importAddon(File archive) {
        return CompletableFuture.supplyAsync(() -> {
            String fileName = archive.getName();
            String id = fileName.replaceAll("(?i)\\.(mcaddon|mcpack|zip)$", "")
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            if (id.isEmpty()) id = "addon";
            File dest = new File(addonsDir, id);
            deleteRecursively(dest);
            dest.mkdirs();
            CompatibilityReport report = new CompatibilityReport(id, id, "1.0.0");
            SafeArchiveExtractor.ExtractResult extracted;
            try {
                extracted = SafeArchiveExtractor.extract(archive, new File(dest, "extracted"), maxBytes, maxFiles);
            } catch (AddonCompatibilityException e) {
                report.add("Import", new CompatibilityEntry("import", CompatibilityLevel.UNSUPPORTED,
                        "Extraction failed: " + e.getMessage(), fileName, -1));
                Addon failed = new Addon(id, dest);
                failed.setReport(report);
                throw new RuntimeException(e.toReportString(), e);
            }
            PackDetector.Detection detection = PackDetector.detect(extracted.directory, report);
            Addon addon = new Addon(id, dest);
            PackDetector.applyToAddon(addon, detection, report);

            File scanRoot = detection.behaviorDir != null ? detection.behaviorDir : extracted.directory;
            ParsedAddon behaviorParsed = ParsedAddon.scan(scanRoot);
            addon.setParsed(behaviorParsed);
            File resRoot = detection.resourceDir != null ? detection.resourceDir : extracted.directory;
            ParsedAddon resourceParsed = ParsedAddon.scan(resRoot);

            List<String> broken = behaviorParsed.validateJson();
            for (String b : broken) {
                report.add("Validate", new CompatibilityEntry("json", CompatibilityLevel.UNSUPPORTED,
                        "Invalid JSON (skipped): " + b, b, -1));
            }

            EntityParser.parse(behaviorParsed, scanRoot, report, addon.getEntities(), adapters);
            ItemParser.parse(behaviorParsed, report, addon.getItems(), adapters);
            BlockParser.parse(behaviorParsed, report, addon.getBlocks(), adapters, blockMode);
            List<AddonRecipe> recipes = RecipeParser.parse(behaviorParsed, report);
            Map<String, AddonLootTable> loot = LootParser.parse(behaviorParsed, report);
            addon.getFunctions().addAll(FunctionParser.parse(behaviorParsed, report));
            NpcParser.parse(behaviorParsed, report, addon);
            AnimationParser.parse(resourceParsed, report);
            ParticleMapper.parse(resourceParsed, report);
            Map<String, String> sounds = SoundParser.parse(resourceParsed, report);
            analyzeScripts(behaviorParsed, resourceParsed, report);

            ConvertedStore.store(addon, recipes, loot);

            if (autoGeneratePack) {
                try {
                    ResourcePackGenerator.generate(dest, resourceParsed, sounds, report,
                            version.defaultPackFormat(), "JavaAddonEngine: " + addon.getName());
                } catch (Exception e) {
                    report.add("ResourcePack", new CompatibilityEntry("resource-pack",
                            CompatibilityLevel.PARTIAL, "Generation failed: " + e.getMessage(), "", -1));
                }
            }
            // Sync manifest name/version into the report header, preserving categories.
            AddonManifest bm = addon.getBehaviorManifest();
            if (bm != null) {
                CompatibilityReport fixed = new CompatibilityReport(id, bm.getName(), bm.getVersion().toString());
                for (CompatibilityEntry e : report.getEntries()) {
                    fixed.add(e.getCategory(), e);
                }
                for (String d : report.getMissingDependencies()) fixed.addMissingDependency(d);
                fixed.setResourcePackConverted(report.isResourcePackConverted());
                addon.setReport(fixed);
            } else {
                addon.setReport(report);
            }
            writeReports(addon);
            persistMeta(addon);
            api.put(addon);
            return addon;
        }, async);
    }

    /** Re-scan the addons directory on startup: rebuild converted models without re-import. */
    public void loadAllFromDisk() {
        File[] dirs = addonsDir.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File dir : dirs) {
            if (dir.getName().equals("incoming")) continue;
            try {
                reparseFromDisk(dir);
            } catch (Exception e) {
                log.warn("Could not load add-on from " + dir.getName() + ": " + e.getMessage());
            }
        }
    }

    private void reparseFromDisk(File dir) throws Exception {
        CompatibilityReport report = new CompatibilityReport(dir.getName(), dir.getName(), "1.0.0");
        PackDetector.Detection detection = PackDetector.detect(dir, report);
        if (detection.behaviorDir == null && detection.resourceDir == null) {
            // Maybe an imported layout with extracted/ subdir.
            File extracted = new File(dir, "extracted");
            if (extracted.isDirectory()) {
                detection = PackDetector.detect(extracted, report);
            } else {
                return;
            }
        }
        if (detection.behaviorDir == null && detection.resourceDir == null) return;
        Addon addon = new Addon(dir.getName(), dir);
        PackDetector.applyToAddon(addon, detection, report);
        File scanRoot = detection.behaviorDir != null ? detection.behaviorDir : dir;
        ParsedAddon behaviorParsed = ParsedAddon.scan(scanRoot);
        addon.setParsed(behaviorParsed);
        File resRoot = detection.resourceDir != null ? detection.resourceDir : dir;
        ParsedAddon resourceParsed = ParsedAddon.scan(resRoot);
        EntityParser.parse(behaviorParsed, scanRoot, report, addon.getEntities(), adapters);
        ItemParser.parse(behaviorParsed, report, addon.getItems(), adapters);
        BlockParser.parse(behaviorParsed, report, addon.getBlocks(), adapters, blockMode);
        List<AddonRecipe> recipes = RecipeParser.parse(behaviorParsed, report);
        Map<String, AddonLootTable> loot = LootParser.parse(behaviorParsed, report);
        addon.getFunctions().addAll(FunctionParser.parse(behaviorParsed, report));
        NpcParser.parse(behaviorParsed, report, addon);
        AnimationParser.parse(resourceParsed, report);
        ParticleMapper.parse(resourceParsed, report);
        SoundParser.parse(resourceParsed, report);
        analyzeScripts(behaviorParsed, resourceParsed, report);
        ConvertedStore.store(addon, recipes, loot);
        AddonManifest bm = addon.getBehaviorManifest();
        if (bm != null) {
            CompatibilityReport fixed = new CompatibilityReport(addon.getId(), bm.getName(), bm.getVersion().toString());
            for (CompatibilityEntry e : report.getEntries()) fixed.add(e.getCategory(), e);
            for (String d : report.getMissingDependencies()) fixed.addMissingDependency(d);
            addon.setReport(fixed);
        } else {
            addon.setReport(report);
        }
        writeReports(addon);
        persistMeta(addon);
        api.put(addon);
        enableOnMainThread(addon);
    }

    /** Enable phase; safe to call from any thread (Bukkit work is scheduled to main). */
    public void enable(Addon addon) {
        if (Bukkit.isPrimaryThread()) {
            enableOnMainThread(addon);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> enableOnMainThread(addon));
        }
    }

    private void enableOnMainThread(Addon addon) {
        addon.setState(AddonState.ENABLE);
        ConvertedStore.Converted c = ConvertedStore.get(addon);
        if (c != null) {
            lootEngine.registerAll(c.loot);
            List<String> keys = recipeRuntime.register(addon, c.recipes);
            addon.getRecipeKeys().clear();
            addon.getRecipeKeys().addAll(keys);
        }
        for (AddonNpc npc : addon.getNpcs().values()) {
            npcManager.define(npc);
        }
        addon.setEnabled(true);
        addon.setState(AddonState.RUNNING);
        persistMeta(addon);
        log.info("Loaded Add-On: " + addon.getName());
        if (addon.getReport() != null) {
            log.info("Entities: " + addon.getEntities().size());
            log.info("Items: " + addon.getItems().size());
            log.info("Compatibility: " + String.format("%.0f%%", addon.getReport().score()));
        }
    }

    public void disable(Addon addon) {
        Runnable task = () -> {
            addon.setState(AddonState.DISABLE);
            addon.setEnabled(false);
            try {
                recipeRuntime.unregisterKeys(addon.getRecipeKeys());
            } catch (Exception ignored) {}
            addon.getRecipeKeys().clear();
            try {
                npcManager.despawn(addon.getNpcs().keySet());
                npcManager.undefineAll(addon.getNpcs().keySet());
            } catch (Exception ignored) {}
            rebuildLoot();
            addon.setState(AddonState.UNLOAD);
            persistMeta(addon);
            log.info("Disabled Add-On: " + addon.getName());
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Reload = disable, re-parse from disk, enable. Async parse, main-thread registration. */
    public CompletableFuture<Addon> reload(Addon addon) {
        disable(addon);
        ConvertedStore.drop(addon.getId());
        return CompletableFuture.supplyAsync(() -> {
            try {
                reparseFromDisk(addon.getDirectory());
            } catch (Exception e) {
                throw new RuntimeException("Reload failed: " + e.getMessage(), e);
            }
            Addon fresh = api.get(addon.getId());
            if (fresh == null) throw new RuntimeException("Reload failed: add-on vanished.");
            return fresh;
        }, async);
    }

    private void rebuildLoot() {
        lootEngine.clear();
        for (Addon a : api.getAddons()) {
            if (!a.isEnabled()) continue;
            ConvertedStore.Converted c = ConvertedStore.get(a);
            if (c != null) lootEngine.registerAll(c.loot);
        }
    }

    public void remove(Addon addon) {
        disable(addon);
        api.remove(addon.getId());
        ConvertedStore.drop(addon.getId());
        deleteRecursively(addon.getDirectory());
        if (store != null) {
            try {
                store.deleteAddon(addon.getId());
            } catch (Exception ignored) {}
        }
    }

    private void analyzeScripts(ParsedAddon behavior, ParsedAddon resource, CompatibilityReport report) {
        List<File> scripts = new ArrayList<>();
        scripts.addAll(behavior.getScriptFiles());
        scripts.addAll(resource.getScriptFiles());
        int count = 0;
        for (File f : scripts) {
            if (count++ > 200) break;
            try {
                String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (src.length() > 500_000) {
                    report.add("Scripts", new CompatibilityEntry("script", CompatibilityLevel.UNSUPPORTED,
                            "Script too large; skipped: " + f.getName(), rel(behavior.getRoot(), f), -1));
                    continue;
                }
                ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, f.getName());
                report.add("Scripts", ScriptAnalyzer.toEntry(a, rel(behavior.getRoot(), f)));
            } catch (Exception e) {
                report.add("Scripts", new CompatibilityEntry("script", CompatibilityLevel.UNSUPPORTED,
                        "Unreadable script: " + e.getMessage(), f.getName(), -1));
            }
        }
        if (scripts.isEmpty()) {
            report.add("Scripts", new CompatibilityEntry("script", CompatibilityLevel.FULL,
                    "No scripts present; nothing to sandbox.", "", -1));
        }
    }

    private void writeReports(Addon addon) {
        try {
            CompatibilityReport r = addon.getReport();
            Files.write(new File(addon.getDirectory(), "report.txt").toPath(),
                    r.toText().getBytes(StandardCharsets.UTF_8));
            Files.write(new File(addon.getDirectory(), "report.json").toPath(),
                    r.toJson().getBytes(StandardCharsets.UTF_8));
            Files.write(new File(addon.getDirectory(), "report.html").toPath(),
                    r.toHtml().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not write reports for " + addon.getId() + ": " + e.getMessage());
        }
    }

    private void persistMeta(Addon addon) {
        if (store == null || addon.getReport() == null) return;
        try {
            store.upsertAddon(addon.getId(), addon.getName(), addon.getVersion(),
                    addon.isEnabled(), addon.getReport().score(), addon.getState().name());
        } catch (Exception ignored) {}
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        f.delete();
    }

    private static String rel(File root, File f) {
        try {
            return root.toURI().relativize(f.toURI()).getPath();
        } catch (Exception e) {
            return f.getName();
        }
    }

    /** Holds converted models between async import and main-thread enable. */
    public static final class ConvertedStore {
        private static final Map<String, Converted> STORE = new java.util.concurrent.ConcurrentHashMap<>();

        public static final class Converted {
            public final List<AddonRecipe> recipes;
            public final Map<String, AddonLootTable> loot;
            Converted(List<AddonRecipe> r, Map<String, AddonLootTable> l) { recipes = r; loot = l; }
        }

        static void store(Addon addon, List<AddonRecipe> r, Map<String, AddonLootTable> l) {
            STORE.put(addon.getId(), new Converted(r, l));
        }

        public static Converted get(Addon addon) { return STORE.get(addon.getId()); }
        public static void drop(String id) { STORE.remove(id); }
    }
}
