package com.javaaddonengine;

import com.javaaddonengine.api.JavaAddonEngineAPI;
import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.blocks.BlockRuntime;
import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.economy.EconomyManager;
import com.javaaddonengine.entities.BehaviorEmulator;
import com.javaaddonengine.entities.EntityRuntime;
import com.javaaddonengine.gui.GuiManager;
import com.javaaddonengine.integrations.IntegrationManager;
import com.javaaddonengine.items.ItemRuntime;
import com.javaaddonengine.loot.LootEngine;
import com.javaaddonengine.npc.NpcManager;
import com.javaaddonengine.recipes.RecipeRuntime;
import com.javaaddonengine.resourcepack.PackServer;
import com.javaaddonengine.runtime.AddonCommand;
import com.javaaddonengine.runtime.AddonListener;
import com.javaaddonengine.runtime.AddonManager;
import com.javaaddonengine.runtime.ApiImpl;
import com.javaaddonengine.runtime.FunctionDispatcher;
import com.javaaddonengine.storage.AddonStore;
import com.javaaddonengine.translator.AdapterRegistry;
import com.javaaddonengine.version.VersionAdapter;
import com.javaaddonengine.version.VersionAdapters;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JavaAddonEngine plugin entrypoint.
 *
 * <p>Lifecycle: parse/import runs async, Bukkit registration (recipes, loot,
 * NPCs, listeners-as-delegates) runs on the main thread, disable/unload
 * releases every listener, task, recipe key and cache.
 */
public final class JavaAddonEnginePlugin extends JavaPlugin {

    private static JavaAddonEnginePlugin instance;
    private static JavaAddonEngineAPI api;

    private JaLogger log;
    private ExecutorService async;
    private AddonManager addonManager;
    private ApiImpl apiImpl;
    private LootEngine lootEngine;
    private EntityRuntime entityRuntime;
    private BehaviorEmulator behaviorEmulator;
    private ItemRuntime itemRuntime;
    private RecipeRuntime recipeRuntime;
    private BlockRuntime blockRuntime;
    private NpcManager npcManager;
    private EconomyManager economyManager;
    private IntegrationManager integrations;
    private GuiManager guiManager;
    private FunctionDispatcher functionDispatcher;
    private AddonStore store;
    private PackServer packServer;
    private VersionAdapter versionAdapter;
    private AddonListener addonListener;
    private AddonCommand addonCommand;

    public static JavaAddonEnginePlugin getInstance() { return instance; }

    /** Public API for third-party plugins. */
    public static JavaAddonEngineAPI getAPI() { return api; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("addons.yml", false);
        saveResource("compatibility.yml", false);
        saveResource("resourcepack.yml", false);
        saveResource("logging.yml", false);
        saveResource("messages.yml", false);

        log = new JaLogger(getLogger(), getConfig().getBoolean("engine.debug", false));
        async = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "JavaAddonEngine-async");
                    t.setDaemon(true);
                    return t;
                });

        versionAdapter = resolveVersionAdapter();
        AdapterRegistry adapters = new AdapterRegistry();
        lootEngine = new LootEngine();
        apiImpl = new ApiImpl(adapters);
        api = apiImpl;

        entityRuntime = new EntityRuntime(this);
        behaviorEmulator = new BehaviorEmulator(this, log);
        itemRuntime = new ItemRuntime(this);
        recipeRuntime = new RecipeRuntime(this);
        npcManager = new NpcManager(this);
        economyManager = new EconomyManager();
        economyManager.setProvider(providerOf(getConfig().getString("economy.provider", "AUTO")));
        integrations = new IntegrationManager(this, log, economyManager);
        guiManager = new GuiManager(this);
        functionDispatcher = new FunctionDispatcher(this, log);

        try {
            String jdbc = getConfig().getString("storage.jdbc-url", "");
            store = new AddonStore(getDataFolder(), jdbc);
        } catch (Exception e) {
            log.warn("Storage unavailable (" + e.getMessage() + "); runtime metadata will be memory-only.");
            store = null;
        }

        long maxBytes = getConfig().getLong("security.max-addon-size-mb", 500) * 1024L * 1024L;
        int maxFiles = getConfig().getInt("security.max-files", 10000);
        boolean autoPack = getConfig().getBoolean("resource-pack.auto-generate", true);
        AddonBlockSpec.RenderMode blockMode = renderModeOf(
                getConfig().getString("blocks.rendering-mode", "AUTO"));

        File addonsDir = new File(getDataFolder(), "addons");
        addonsDir.mkdirs();
        blockRuntime = new BlockRuntime(this, blockMode);

        addonManager = new AddonManager(this, log, async, apiImpl, adapters, lootEngine,
                versionAdapter, addonsDir, recipeRuntime, npcManager, store,
                maxBytes, maxFiles, autoPack, blockMode);

        // Wire optional services after store + integrations exist.
        entityRuntime.setServices(integrations.getModelEngine(), behaviorEmulator);
        npcManager.setServices(integrations.getCitizens(), store);

        addonListener = new AddonListener(this, log, apiImpl, itemRuntime, lootEngine,
                npcManager, economyManager, guiManager, integrations);
        Bukkit.getPluginManager().registerEvents(addonListener, this);
        addonListener.startEntityTasks();

        PluginCommand cmd = getCommand("addon");
        if (cmd != null) {
            addonCommand = new AddonCommand(this, log, addonManager, apiImpl, lootEngine,
                    recipeRuntime, integrations, economyManager, versionAdapter, store);
            cmd.setExecutor(addonCommand);
            cmd.setTabCompleter(addonCommand);
        } else {
            log.error("Command 'addon' missing from plugin.yml; command system disabled.");
        }

        integrations.detectAll();
        integrations.refreshProviders(getConfig().getString("npc.provider", "INTERNAL"));

        int packPort = getConfig().getInt("resource-pack.http.port", 0);
        String packHost = getConfig().getString("resource-pack.http.host", "0.0.0.0");
        if (getConfig().getBoolean("resource-pack.http.enabled", false) && packPort > 0) {
            File packsDir = new File(getDataFolder(), "packs");
            packsDir.mkdirs();
            packServer = new PackServer(packsDir, packHost, packPort, log);
            try {
                packServer.start();
            } catch (Exception e) {
                log.warn("Pack HTTP server failed to start: " + e.getMessage());
                packServer = null;
            }
        }

        boolean autoLoad = getConfig().getBoolean("addons.auto-load", true);
        if (autoLoad) {
            Bukkit.getScheduler().runTaskLater(this, () -> addonManager.loadAllFromDisk(), 20L);
        }

        boolean autoSend = getConfig().getBoolean("resource-pack.auto-send", false);
        if (autoSend) {
            log.warn("resource-pack.auto-send is enabled without a per-player pack selection; "
                    + "use '/addon resourcepack send <player>' to deliver packs explicitly.");
        }

        log.info("Enabled v" + getDescription().getVersion()
                + " on " + versionAdapter.minecraftVersion());
    }

    @Override
    public void onDisable() {
        try {
            if (addonCommand != null) addonCommand.shutdown();
        } catch (Exception ignored) {}
        try {
            if (addonListener != null) addonListener.shutdown();
        } catch (Exception ignored) {}
        try {
            if (behaviorEmulator != null) behaviorEmulator.stopAll();
        } catch (Exception ignored) {}
        try {
            if (npcManager != null) npcManager.despawnAll();
        } catch (Exception ignored) {}
        if (apiImpl != null) {
            for (com.javaaddonengine.core.Addon a : apiImpl.getAddons()) {
                try {
                    addonManager.disable(a);
                } catch (Exception ignored) {}
            }
            apiImpl.clear();
        }
        if (packServer != null) {
            try {
                packServer.stop();
            } catch (Exception ignored) {}
            packServer = null;
        }
        if (async != null) {
            async.shutdownNow();
            async = null;
        }
        if (store != null) {
            try {
                store.close();
            } catch (Exception ignored) {}
            store = null;
        }
        instance = null;
    }

    /** Reload configs and re-read debug flags (add-ons reload via /addon reload). */
    public void reloadJaeConfig() {
        reloadConfig();
        if (log != null) log.setDebug(getConfig().getBoolean("engine.debug", false));
        if (integrations != null) {
            integrations.refreshProviders(getConfig().getString("npc.provider", "INTERNAL"));
        }
    }

    public AddonManager getAddonManager() { return addonManager; }
    public LootEngine getLootEngine() { return lootEngine; }
    public EntityRuntime getEntityRuntime() { return entityRuntime; }
    public BehaviorEmulator getBehaviorEmulator() { return behaviorEmulator; }
    public ItemRuntime getItemRuntime() { return itemRuntime; }
    public RecipeRuntime getRecipeRuntime() { return recipeRuntime; }
    public BlockRuntime getBlockRuntime() { return blockRuntime; }
    public NpcManager getNpcManager() { return npcManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public IntegrationManager getIntegrations() { return integrations; }
    public GuiManager getGuiManager() { return guiManager; }
    public FunctionDispatcher getFunctionDispatcher() { return functionDispatcher; }
    public AddonStore getStore() { return store; }
    public PackServer getPackServer() { return packServer; }
    public VersionAdapter getVersionAdapter() { return versionAdapter; }

    private VersionAdapter resolveVersionAdapter() {
        try {
            return VersionAdapters.current();
        } catch (Throwable t) {
            getLogger().warning("[JavaAddonEngine] Version detection failed ("
                    + t.getMessage() + "); using 1.21 defaults.");
            return new com.javaaddonengine.version.v1_21.AdapterV121();
        }
    }

    private static EconomyManager.Provider providerOf(String s) {
        if (s == null) return EconomyManager.Provider.AUTO;
        try {
            return EconomyManager.Provider.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EconomyManager.Provider.AUTO;
        }
    }

    private static AddonBlockSpec.RenderMode renderModeOf(String s) {
        if (s == null) return AddonBlockSpec.RenderMode.FALLBACK;
        String v = s.trim().toUpperCase();
        if ("AUTO".equals(v)) return AddonBlockSpec.RenderMode.FALLBACK;
        try {
            return AddonBlockSpec.RenderMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return AddonBlockSpec.RenderMode.FALLBACK;
        }
    }
}
