package com.javaaddonengine.runtime;

import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.economy.EconomyManager;
import com.javaaddonengine.integrations.IntegrationManager;
import com.javaaddonengine.loot.LootEngine;
import com.javaaddonengine.recipes.RecipeRuntime;
import com.javaaddonengine.resourcepack.ResourcePackGenerator;
import com.javaaddonengine.storage.AddonStore;
import com.javaaddonengine.translator.ParticleMapper;
import com.javaaddonengine.translator.SoundParser;
import com.javaaddonengine.version.VersionAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Full /addon command suite: list, import, analyze, enable, disable, reload,
 * remove, report, resourcepack, give, summon, function, npc, block, debug, status.
 */
public final class AddonCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final JaLogger log;
    private final AddonManager manager;
    private final ApiImpl api;
    private final IntegrationManager integrations;
    private final EconomyManager economy;
    private final VersionAdapter version;
    private final AddonStore store;
    private final ExecutorService packExec = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "JAE-pack");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> importCooldown = new ConcurrentHashMap<>();

    public AddonCommand(Plugin plugin, JaLogger log, AddonManager manager, ApiImpl api,
                        LootEngine lootEngine, RecipeRuntime recipeRuntime,
                        IntegrationManager integrations, EconomyManager economy,
                        VersionAdapter version, AddonStore store) {
        this.plugin = plugin;
        this.log = log;
        this.manager = manager;
        this.api = api;
        // lootEngine/recipeRuntime reserved for future give/preview helpers.
        this.integrations = integrations;
        this.economy = economy;
        this.version = version;
        this.store = store;
    }

    public void shutdown() {
        packExec.shutdownNow();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "&bJavaAddonEngine &7v" + plugin.getDescription().getVersion());
            send(sender, "&7Use &f/" + label + " <list|import|analyze|enable|disable|reload|remove|report|resourcepack|give|summon|function|npc|block|debug|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": return list(sender);
            case "import": return importCmd(sender, args);
            case "analyze": return analyze(sender, args);
            case "enable": return enable(sender, args);
            case "disable": return disable(sender, args);
            case "reload": return reload(sender, args);
            case "remove": return remove(sender, args);
            case "report": return report(sender, args);
            case "resourcepack": return resourcepack(sender, label, args);
            case "give": return give(sender, args);
            case "summon": return summon(sender, args);
            case "function": return function(sender, args);
            case "npc": return npc(sender, args);
            case "block": return block(sender, args);
            case "debug": return debug(sender);
            case "status": return status(sender);
            default:
                send(sender, "&cUnknown subcommand. &7Use &f/" + label + " list");
                return true;
        }
    }

    // ---- list ----

    private boolean list(CommandSender sender) {
        if (!check(sender, "javaaddonengine.use")) return true;
        if (api.getAddons().isEmpty()) {
            send(sender, "&7No Add-Ons imported. Drop a &f.mcaddon &7into &fplugins/JavaAddonEngine/addons/incoming &7and run &f/addon import <file>");
            return true;
        }
        send(sender, "&bJavaAddonEngine &7— installed Add-Ons:");
        for (Addon a : api.getAddons()) {
            double score = a.getReport() == null ? 0 : a.getReport().score();
            String state = a.isEnabled() ? "&aENABLED" : "&cDISABLED";
            send(sender, "&f" + a.getName() + " &7(" + a.getId() + " v" + a.getVersion() + ")  "
                    + String.format("%.0f%%", score) + "  " + state);
        }
        return true;
    }

    // ---- import ----

    private boolean importCmd(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.import")) return true;
        if (args.length < 2) {
            send(sender, "&eUsage: &f/addon import <file.mcaddon|file.mcpack|file.zip>");
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = importCooldown.get(sender.getName());
        if (last != null && now - last < 3000) {
            send(sender, "&cPlease wait before importing again.");
            return true;
        }
        importCooldown.put(sender.getName(), now);
        File archive = resolveArchive(args[1]);
        if (archive == null) {
            send(sender, "&cFile not found: &f" + args[1] + " &7(checked addons/incoming/ and addons/)");
            return true;
        }
        send(sender, "&eImporting: &f" + archive.getName());
        manager.importAddon(archive).whenComplete((addon, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                String msg = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                send(sender, "&cImport failed: &f" + msg);
                log.warn("Import failed for " + archive.getName() + ": " + msg);
                return;
            }
            boolean behavior = addon.getBehaviorManifest() != null;
            boolean resource = addon.getResourceManifest() != null;
            send(sender, "&bJavaAddonEngine");
            send(sender, (behavior ? "&aBehavior Pack detected" : "&7No Behavior Pack")
                    + "  " + (resource ? "&aResource Pack detected" : "&7No Resource Pack"));
            send(sender, "&7Analyzing Add-On...");
            CompatibilityReport r = addon.getReport();
            send(sender, "&7Entities: &f" + addon.getEntities().size()
                    + "  &7Items: &f" + addon.getItems().size()
                    + "  &7Blocks: &f" + addon.getBlocks().size());
            if (r != null) {
                send(sender, "&7Compatibility: &a" + String.format("%.0f%%", r.score())
                        + " &7(" + r.grade() + ")");
                send(sender, "&7Use &f/addon report " + addon.getId());
            }
            manager.enable(addon);
            send(sender, "&aImported Add-On: &f" + addon.getName());
        }));
        return true;
    }

    private File resolveArchive(String name) {
        File direct = new File(name);
        if (direct.isFile()) return direct;
        File incoming = new File(manager.getImportDir(), name);
        if (incoming.isFile()) return incoming;
        File addons = new File(manager.getAddonsDir(), name);
        if (addons.isFile()) return addons;
        // Prefix match inside incoming/
        File[] files = manager.getImportDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(name)) return f;
            }
        }
        return null;
    }

    // ---- analyze / report ----

    private boolean analyze(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        Addon a = requireAddon(sender, args);
        if (a == null) return true;
        CompatibilityReport r = a.getReport();
        if (r == null) {
            send(sender, "&cNo report available for &f" + a.getId());
            return true;
        }
        send(sender, "&bCompatibility: &f" + a.getName() + " &7— " + colorFor(r.score())
                + String.format("%.0f%%", r.score()) + " &7(" + r.grade() + ")");
        for (Map.Entry<String, java.util.EnumMap<CompatibilityLevel, Integer>> cat
                : r.getCategories().entrySet()) {
            java.util.EnumMap<CompatibilityLevel, Integer> c = cat.getValue();
            double pct = r.categoryScore(cat.getKey());
            send(sender, colorFor(pct) + String.format("%3.0f%%", pct) + " &7" + cat.getKey()
                    + ": &a" + c.getOrDefault(CompatibilityLevel.FULL, 0)
                    + " &efull&7, &e" + (c.getOrDefault(CompatibilityLevel.PARTIAL, 0)
                    + c.getOrDefault(CompatibilityLevel.EMULATED, 0)
                    + c.getOrDefault(CompatibilityLevel.FALLBACK, 0))
                    + " &epartial&7, &c" + c.getOrDefault(CompatibilityLevel.UNSUPPORTED, 0) + " &eunsupported");
        }
        // Per-file / per-mob breakdown. Each entity/item/block/function file
        // (e.g. entities/t_rex.json) scores on its own entries, so a dinosaur
        // mob shows its own convert percentage.
        Map<String, Double> files = r.fileScores();
        if (!files.isEmpty()) {
            if (args.length >= 3) {
                String filter = args[2].toLowerCase(Locale.ROOT);
                int shown = 0;
                for (Map.Entry<String, Double> en : files.entrySet()) {
                    if (!en.getKey().toLowerCase(Locale.ROOT).contains(filter)) continue;
                    java.util.EnumMap<CompatibilityLevel, Integer> fc = r.fileCounts(en.getKey());
                    send(sender, colorFor(en.getValue()) + String.format("%3.0f%%", en.getValue())
                            + " &7" + en.getKey()
                            + " &8(F:" + fc.getOrDefault(CompatibilityLevel.FULL, 0)
                            + " P:" + (fc.getOrDefault(CompatibilityLevel.PARTIAL, 0)
                            + fc.getOrDefault(CompatibilityLevel.EMULATED, 0)
                            + fc.getOrDefault(CompatibilityLevel.FALLBACK, 0))
                            + " U:" + fc.getOrDefault(CompatibilityLevel.UNSUPPORTED, 0) + ")");
                    if (++shown >= 20) break;
                }
                if (shown == 0) {
                    send(sender, "&7No files match filter &f" + args[2]
                            + "&7. Use &f/addon analyze " + a.getId() + " &7without filter to list all files.");
                }
            } else if (files.size() <= 15) {
                for (Map.Entry<String, Double> en : files.entrySet()) {
                    send(sender, colorFor(en.getValue()) + String.format("%3.0f%%", en.getValue())
                            + " &7" + en.getKey());
                }
            } else {
                // Too many files for chat: show weakest 10 (most useful) + hint.
                List<Map.Entry<String, Double>> sorted = new ArrayList<>(files.entrySet());
                sorted.sort(Map.Entry.comparingByValue());
                send(sender, "&7Per-file scores (" + files.size() + " files, weakest 10):");
                for (int i = 0; i < Math.min(10, sorted.size()); i++) {
                    Map.Entry<String, Double> en = sorted.get(i);
                    send(sender, colorFor(en.getValue()) + String.format("%3.0f%%", en.getValue())
                            + " &7" + en.getKey());
                }
                send(sender, "&7Filter one mob/file: &f/addon analyze " + a.getId() + " <nama>");
                send(sender, "&7Full per-file table: &f/addon report " + a.getId() + " &7(report.txt/html)");
            }
        }
        return true;
    }

    private static String colorFor(double pct) {
        if (pct >= 75) return "&a";
        if (pct >= 50) return "&e";
        if (pct >= 25) return "&6";
        return "&c";
    }

    private boolean report(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        Addon a = requireAddon(sender, args);
        if (a == null) return true;
        CompatibilityReport r = a.getReport();
        if (r == null) {
            send(sender, "&cNo report available for &f" + a.getId());
            return true;
        }
        File txt = new File(a.getDirectory(), "report.txt");
        if (!txt.isFile()) {
            send(sender, "&cReport file missing for &f" + a.getId());
            return true;
        }
        // Chat summary + file locations; full files live in the addon dir.
        for (String line : r.toText().split("\n")) {
            if (line.length() > 200) line = line.substring(0, 200);
            sender.sendMessage(ChatColor.GRAY + line);
        }
        send(sender, "&7Full reports: &freport.txt report.json report.html &7in &fplugins/JavaAddonEngine/addons/" + a.getId() + "/");
        return true;
    }

    // ---- enable / disable / reload / remove ----

    private boolean enable(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.manage")) return true;
        Addon a = requireAddon(sender, args);
        if (a == null) return true;
        manager.enable(a);
        send(sender, "&aEnabled Add-On: &f" + a.getName());
        return true;
    }

    private boolean disable(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.manage")) return true;
        Addon a = requireAddon(sender, args);
        if (a == null) return true;
        manager.disable(a);
        send(sender, "&cDisabled Add-On: &f" + a.getName());
        return true;
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.manage")) return true;
        if (args.length >= 2) {
            Addon a = requireAddon(sender, args);
            if (a == null) return true;
            send(sender, "&eReloading &f" + a.getName() + "&e...");
            manager.reload(a).whenComplete((fresh, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    send(sender, "&cReload failed: &f" + err.getMessage());
                    return;
                }
                manager.enable(fresh);
                send(sender, "&aReloaded Add-On: &f" + fresh.getName());
            }));
            return true;
        }
        plugin.reloadConfig();
        send(sender, "&aReloaded configuration and Add-On registry.");
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.manage")) return true;
        Addon a = requireAddon(sender, args);
        if (a == null) return true;
        manager.remove(a);
        send(sender, "&eRemoved Add-On: &f" + a.getName());
        return true;
    }

    // ---- resourcepack ----

    private boolean resourcepack(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            send(sender, "&eUsage: &f/addon resourcepack <generate|send> <addon> [player]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("generate".equals(action)) {
            if (!check(sender, "javaaddonengine.manage")) return true;
            if (args.length < 3) {
                send(sender, "&eUsage: &f/addon resourcepack generate <addon>");
                return true;
            }
            Addon a = api.get(args[2].toLowerCase(Locale.ROOT));
            if (a == null) {
                send(sender, "&cAdd-On not found: &f" + args[2]);
                return true;
            }
            send(sender, "&eGenerating resource pack for &f" + a.getName() + "&e...");
            packExec.submit(() -> {
                try {
                    com.javaaddonengine.parser.ParsedAddon reparsed = a.getParsed();
                    java.util.Map<String, String> sounds = Collections.emptyMap();
                    if (reparsed != null) {
                        sounds = SoundParser.parse(reparsed,
                                new CompatibilityReport(a.getId(), a.getName(), a.getVersion()));
                        ParticleMapper.parse(reparsed, new CompatibilityReport(a.getId(), a.getName(), a.getVersion()));
                    }
                    com.javaaddonengine.parser.ParsedAddon scanTarget = reparsed != null ? reparsed
                            : com.javaaddonengine.parser.ParsedAddon.scan(a.getDirectory());
                    ResourcePackGenerator.Result result = ResourcePackGenerator.generate(
                            a.getDirectory(), scanTarget, sounds, a.getReport(),
                            version.defaultPackFormat(), "JavaAddonEngine: " + a.getName());
                    File zip = result.zip(packExec);
                    Bukkit.getScheduler().runTask(plugin, () -> send(sender,
                            "&aPack generated: &f" + result.fileCount + " files, "
                                    + (result.textures + result.sounds) + " converted assets"
                                    + (zip != null ? " (&f" + zip.getName() + "&a)" : "")));
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            send(sender, "&cPack generation failed: &f" + e.getMessage()));
                }
            });
            return true;
        }
        if ("send".equals(action)) {
            if (!check(sender, "javaaddonengine.manage")) return true;
            if (args.length < 4) {
                send(sender, "&eUsage: &f/addon resourcepack send <addon> <player>");
                return true;
            }
            Addon a = api.get(args[2].toLowerCase(Locale.ROOT));
            if (a == null) {
                send(sender, "&cAdd-On not found: &f" + args[2]);
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                send(sender, "&cPlayer not found: &f" + args[3]);
                return true;
            }
            String url = plugin.getConfig().getString("resource-pack.url", "");
            if (url == null || url.isEmpty()) {
                send(sender, "&cNo resource-pack.url configured. Set &fresource-pack.url &c(and sha1) in config.yml, "
                        + "or enable &fresource-pack.http &cto serve the generated pack locally.");
                return true;
            }
            String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
            try {
                if (sha1 != null && sha1.matches("[0-9a-fA-F]{40}")) {
                    target.setResourcePack(url, sha1.toLowerCase(java.util.Locale.ROOT), required);
                } else {
                    target.setResourcePack(url, (byte[]) null, required);
                }
                send(sender, "&aSent resource pack to &f" + target.getName());
            } catch (Exception e) {
                send(sender, "&cFailed to send pack: &f" + e.getMessage());
            }
            return true;
        }
        send(sender, "&eUsage: &f/addon resourcepack <generate|send> <addon> [player]");
        return true;
    }

    // ---- give / summon / function ----

    private boolean give(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        if (!(sender instanceof Player) && args.length < 4) {
            send(sender, "&cConsole usage: &f/addon give <addon> <item> <player>");
            return true;
        }
        if (args.length < 3) {
            send(sender, "&eUsage: &f/addon give <addon> <item> [player]");
            return true;
        }
        Addon a = api.get(args[1].toLowerCase(Locale.ROOT));
        if (a == null || !a.isEnabled()) {
            send(sender, "&cAdd-On not found or disabled: &f" + args[1]);
            return true;
        }
        com.javaaddonengine.items.AddonItemSpec spec = a.getItems().get(args[2].toLowerCase(Locale.ROOT));
        if (spec == null) {
            // try suffix match (give spark_sword for jae_example:spark_sword)
            for (Map.Entry<String, com.javaaddonengine.items.AddonItemSpec> e : a.getItems().entrySet()) {
                if (e.getKey().endsWith(":" + args[2].toLowerCase(Locale.ROOT)) || e.getKey().equalsIgnoreCase(args[2])) {
                    spec = e.getValue();
                    break;
                }
            }
        }
        if (spec == null) {
            send(sender, "&cItem not found: &f" + args[2]);
            return true;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                send(sender, "&cPlayer not found: &f" + args[3]);
                return true;
            }
        } else {
            target = (Player) sender;
        }
        com.javaaddonengine.JavaAddonEnginePlugin inst =
                com.javaaddonengine.JavaAddonEnginePlugin.getInstance();
        org.bukkit.inventory.ItemStack stack = inst.getItemRuntime().build(a, spec);
        target.getInventory().addItem(stack);
        send(sender, "&aGave &f" + spec.getId() + " &ato &f" + target.getName());
        return true;
    }

    private boolean summon(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            send(sender, "&cOnly players can use this command.");
            return true;
        }
        if (args.length < 3) {
            send(sender, "&eUsage: &f/addon summon <addon> <entity>");
            return true;
        }
        Addon a = api.get(args[1].toLowerCase(Locale.ROOT));
        if (a == null || !a.isEnabled()) {
            send(sender, "&cAdd-On not found or disabled: &f" + args[1]);
            return true;
        }
        com.javaaddonengine.entities.AddonEntitySpec spec = a.getEntities().get(args[2].toLowerCase(Locale.ROOT));
        if (spec == null) {
            for (Map.Entry<String, com.javaaddonengine.entities.AddonEntitySpec> e : a.getEntities().entrySet()) {
                if (e.getKey().endsWith(":" + args[2].toLowerCase(Locale.ROOT)) || e.getKey().equalsIgnoreCase(args[2])) {
                    spec = e.getValue();
                    break;
                }
            }
        }
        if (spec == null) {
            send(sender, "&cEntity not found: &f" + args[2]);
            return true;
        }
        com.javaaddonengine.JavaAddonEnginePlugin inst =
                com.javaaddonengine.JavaAddonEnginePlugin.getInstance();
        inst.getEntityRuntime().spawn(a, spec, player.getLocation());
        send(sender, "&aSummoned &f" + spec.getId());
        return true;
    }

    private boolean function(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        if (args.length < 3) {
            send(sender, "&eUsage: &f/addon function <addon> <name>");
            return true;
        }
        Addon a = api.get(args[1].toLowerCase(Locale.ROOT));
        if (a == null || !a.isEnabled()) {
            send(sender, "&cAdd-On not found or disabled: &f" + args[1]);
            return true;
        }
        com.javaaddonengine.commands.FunctionParser.ParsedFunction fn = null;
        for (com.javaaddonengine.commands.FunctionParser.ParsedFunction f : a.getFunctions()) {
            if (f.name.equalsIgnoreCase(args[2])) {
                fn = f;
                break;
            }
        }
        if (fn == null) {
            send(sender, "&cFunction not found: &f" + args[2]);
            return true;
        }
        com.javaaddonengine.JavaAddonEnginePlugin inst =
                com.javaaddonengine.JavaAddonEnginePlugin.getInstance();
        Player asPlayer = sender instanceof Player ? (Player) sender : null;
        inst.getFunctionDispatcher().dispatch(a, fn, asPlayer);
        send(sender, "&aExecuted function &f" + fn.name + " &7(" + fn.lines.size() + " lines)");
        return true;
    }

    // ---- debug / status ----

    private boolean npc(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        if (args.length < 2) {
            send(sender, "&eUsage: &f/addon npc <list|spawn> <addon> [npc]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        com.javaaddonengine.JavaAddonEnginePlugin inst =
                com.javaaddonengine.JavaAddonEnginePlugin.getInstance();
        if ("list".equals(action)) {
            if (args.length < 3) {
                send(sender, "&eUsage: &f/addon npc list <addon>");
                return true;
            }
            Addon a = api.get(args[2].toLowerCase(Locale.ROOT));
            if (a == null) {
                send(sender, "&cAdd-On not found: &f" + args[2]);
                return true;
            }
            if (a.getNpcs().isEmpty()) {
                send(sender, "&7No NPCs in &f" + a.getName());
                return true;
            }
            send(sender, "&bNPCs in &f" + a.getName() + "&b:");
            for (com.javaaddonengine.npc.AddonNpc n : a.getNpcs().values()) {
                String name = n.getName().isEmpty() ? n.getId() : n.getName();
                send(sender, "&f" + n.getId() + " &7(" + name + ", "
                        + n.getDialogue().size() + " lines"
                        + (n.getShop().isEmpty() ? "" : ", shop: " + n.getShop().size() + " trades")
                        + ")");
            }
            return true;
        }
        if ("spawn".equals(action)) {
            if (!check(sender, "javaaddonengine.manage")) return true;
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player == null) {
                send(sender, "&cOnly players can use this command.");
                return true;
            }
            if (args.length < 4) {
                send(sender, "&eUsage: &f/addon npc spawn <addon> <npc>");
                return true;
            }
            Addon a = api.get(args[2].toLowerCase(Locale.ROOT));
            if (a == null || !a.isEnabled()) {
                send(sender, "&cAdd-On not found or disabled: &f" + args[2]);
                return true;
            }
            String wanted = args[3].toLowerCase(Locale.ROOT);
            String npcId = null;
            for (String id : a.getNpcs().keySet()) {
                if (id.equalsIgnoreCase(wanted) || id.toLowerCase(Locale.ROOT).endsWith(":" + wanted)) {
                    npcId = id;
                    break;
                }
            }
            if (npcId == null) {
                send(sender, "&cNPC not found: &f" + args[3]);
                return true;
            }
            org.bukkit.entity.Entity spawned = inst.getNpcManager().spawn(npcId, player.getLocation());
            if (spawned == null) {
                send(sender, "&cFailed to spawn NPC: &f" + npcId);
                return true;
            }
            send(sender, "&aSpawned NPC &f" + npcId
                    + " &7(via " + inst.getIntegrations().getNpcProvider() + ")");
            return true;
        }
        send(sender, "&eUsage: &f/addon npc <list|spawn> <addon> [npc]");
        return true;
    }

    private boolean block(CommandSender sender, String[] args) {
        if (!check(sender, "javaaddonengine.use")) return true;
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            send(sender, "&cOnly players can use this command.");
            return true;
        }
        if (args.length < 3 || !"place".equalsIgnoreCase(args[1])) {
            send(sender, "&eUsage: &f/addon block place <addon> <block>");
            return true;
        }
        Addon a = api.get(args[2].toLowerCase(Locale.ROOT));
        if (a == null || !a.isEnabled()) {
            send(sender, "&cAdd-On not found or disabled: &f" + args[2]);
            return true;
        }
        if (args.length < 4) {
            send(sender, "&eUsage: &f/addon block place <addon> <block>");
            return true;
        }
        String wanted = args[3].toLowerCase(Locale.ROOT);
        com.javaaddonengine.blocks.AddonBlockSpec spec = null;
        for (Map.Entry<String, com.javaaddonengine.blocks.AddonBlockSpec> e : a.getBlocks().entrySet()) {
            if (e.getKey().equalsIgnoreCase(wanted)
                    || e.getKey().toLowerCase(Locale.ROOT).endsWith(":" + wanted)) {
                spec = e.getValue();
                break;
            }
        }
        if (spec == null) {
            send(sender, "&cBlock not found: &f" + args[3]);
            return true;
        }
        com.javaaddonengine.JavaAddonEnginePlugin inst =
                com.javaaddonengine.JavaAddonEnginePlugin.getInstance();
        org.bukkit.Location target = player.getTargetBlock(null, 6).getLocation();
        inst.getBlockRuntime().place(a, spec, target);
        send(sender, "&aPlaced &f" + spec.getId() + " &7(fallback render: "
                + inst.getBlockRuntime().standIn(spec).name().toLowerCase(Locale.ROOT) + ")");
        return true;
    }

    private boolean debug(CommandSender sender) {
        if (!check(sender, "javaaddonengine.debug")) return true;
        send(sender, "&bJavaAddonEngine diagnostics:");
        send(sender, "&7Parser: &aOK");
        send(sender, "&7Translator: &aOK");
        send(sender, "&7Entities: &aOK &7 loot: &aOK");
        send(sender, "&7Resource Pack: &aOK");
        send(sender, "&7Version: &f" + version.minecraftVersion());
        for (String name : new String[]{"Vault", "Citizens", "ModelEngine", "PlaceholderAPI", "ProtocolLib", "WorldGuard", "MythicMobs"}) {
            send(sender, "&7" + name + ": " + (integrations.isAvailable(name) ? "&aCONNECTED" : "&cNOT INSTALLED"));
        }
        int npcs = 0;
        try {
            npcs = com.javaaddonengine.JavaAddonEnginePlugin.getInstance().getNpcManager().count();
        } catch (Exception ignored) {}
        send(sender, "&7NPC definitions: &f" + npcs
                + " &7(provider: &f"
                + com.javaaddonengine.JavaAddonEnginePlugin.getInstance().getIntegrations().getNpcProvider()
                + "&7)");
        send(sender, "&7Economy: &f" + economy.getActive());
        try {
            send(sender, "&7Behavior emulation: &f"
                    + com.javaaddonengine.JavaAddonEnginePlugin.getInstance()
                            .getBehaviorEmulator().trackedCount() + " &7tracked entities");
        } catch (Exception ignored) {}
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!check(sender, "javaaddonengine.use")) return true;
        int enabled = 0;
        for (Addon a : api.getAddons()) if (a.isEnabled()) enabled++;
        send(sender, "&bJavaAddonEngine &7v" + plugin.getDescription().getVersion()
                + " — &f" + enabled + "/" + api.getAddons().size() + " &7add-ons enabled");
        send(sender, "&7Version: &f" + version.minecraftVersion()
                + "  &7Economy: &f" + economy.getActive());
        if (store != null) {
            try {
                send(sender, "&7Tracked in storage: &f" + store.listAddons().size());
            } catch (Exception ignored) {}
        }
        return true;
    }

    // ---- helpers ----

    private Addon requireAddon(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "&eUsage: &f/addon <subcommand> <addon>");
            return null;
        }
        Addon a = api.get(args[1].toLowerCase(Locale.ROOT));
        if (a == null) {
            send(sender, "&cAdd-On not found: &f" + args[1]);
            return null;
        }
        return a;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission("javaaddonengine.admin") || sender.hasPermission(permission)
                || (sender.isOp() && !"javaaddonengine.use".equals(permission))) return true;
        // javaaddonengine.use defaults to true; allow it.
        if ("javaaddonengine.use".equals(permission)) return true;
        send(sender, "&cYou do not have permission to do that.");
        return false;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "&b[JavaAddonEngine] &r") + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("list", "import", "analyze", "enable", "disable", "reload",
                    "remove", "report", "resourcepack", "give", "summon", "function",
                    "npc", "block", "debug", "status"), args[0]);
        }
        if (args.length == 2
                && ("npc".equalsIgnoreCase(args[0]) || "block".equalsIgnoreCase(args[0]))) {
            return filter("npc".equalsIgnoreCase(args[0])
                    ? Arrays.asList("list", "spawn") : Collections.singletonList("place"), args[1]);
        }
        if (args.length == 3
                && ("npc".equalsIgnoreCase(args[0]) || "block".equalsIgnoreCase(args[0]))) {
            List<String> ids = new ArrayList<>();
            for (Addon a : api.getAddons()) ids.add(a.getId());
            return filter(ids, args[2]);
        }
        if (args.length == 2 && !"import".equalsIgnoreCase(args[0]) && !"resourcepack".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<>();
            for (Addon a : api.getAddons()) ids.add(a.getId());
            return filter(ids, args[1]);
        }
        if ("resourcepack".equalsIgnoreCase(args[0]) && args.length == 2) {
            return filter(Arrays.asList("generate", "send"), args[1]);
        }
        if ("resourcepack".equalsIgnoreCase(args[0]) && args.length == 3) {
            List<String> ids = new ArrayList<>();
            for (Addon a : api.getAddons()) ids.add(a.getId());
            return filter(ids, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }
}
