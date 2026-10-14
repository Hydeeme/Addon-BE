package com.javaaddonengine.integrations;

import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.economy.EconomyManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Optional soft integrations. Everything is detected via the plugin manager
 * and reflection; none of these are hard dependencies.
 */
public final class IntegrationManager {

    private final Plugin plugin;
    private final JaLogger log;
    private final EconomyManager economy;
    private final Map<String, Boolean> status = new LinkedHashMap<>();
    private final ModelEngineBridge modelEngine;
    private final CitizensBridge citizens;
    private String npcProvider = "INTERNAL";

    public IntegrationManager(Plugin plugin, JaLogger log, EconomyManager economy) {
        this.plugin = plugin;
        this.log = log;
        this.economy = economy;
        this.modelEngine = new ModelEngineBridge(plugin, log);
        this.citizens = new CitizensBridge(plugin, log);
        for (String name : new String[]{"Vault", "Citizens", "ModelEngine", "PlaceholderAPI", "ProtocolLib", "WorldGuard", "MythicMobs"}) {
            status.put(name, false);
        }
    }

    public ModelEngineBridge getModelEngine() { return modelEngine; }
    public CitizensBridge getCitizens() { return citizens; }
    public String getNpcProvider() { return npcProvider; }

    /** Read the operator-configured NPC provider (INTERNAL or CITIZENS). */
    public void refreshProviders(String npcProviderConfigured) {
        this.npcProvider = CitizensBridge.normalize(npcProviderConfigured);
        citizens.setProviderWanted(CitizensBridge.wantsCitizens(npcProviderConfigured));
        log.info("NPC provider: " + this.npcProvider
                + (citizens.isAvailable() ? " (Citizens active)" : " (internal implementation)"));
    }

    public void detectAll() {
        detectVault();
        for (String name : new String[]{"Citizens", "ModelEngine", "PlaceholderAPI", "ProtocolLib", "WorldGuard", "MythicMobs"}) {
            boolean present = Bukkit.getPluginManager().getPlugin(name) != null;
            status.put(name, present);
            log.info(name + ": " + (present ? "CONNECTED" : "NOT INSTALLED"));
        }
    }

    private void detectVault() {
        boolean present = Bukkit.getPluginManager().getPlugin("Vault") != null;
        status.put("Vault", present);
        if (!present) {
            log.info("Vault: NOT INSTALLED (using internal economy)");
            economy.setVaultEconomy(null);
            return;
        }
        try {
            Object reg = Bukkit.getServicesManager()
                    .getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
            if (reg != null) {
                Object econ = reg.getClass().getMethod("getProvider").invoke(reg);
                economy.setVaultEconomy(econ);
                log.info("Vault: CONNECTED");
            } else {
                economy.setVaultEconomy(null);
                log.info("Vault: installed but no economy provider (using internal economy)");
            }
        } catch (Exception e) {
            economy.setVaultEconomy(null);
            log.info("Vault: integration failed (" + e.getMessage() + "), using internal economy");
        }
    }

    public boolean isAvailable(String name) {
        return status.getOrDefault(name, false);
    }

    public Map<String, Boolean> snapshot() {
        return new LinkedHashMap<>(status);
    }

    /** Placeholder resolution: PlaceholderAPI when present, otherwise passthrough. */
    public String setPlaceholders(org.bukkit.entity.Player player, String text) {
        if (isAvailable("PlaceholderAPI")) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                return (String) api.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class)
                        .invoke(null, player, text);
            } catch (Exception ignored) {}
        }
        return text;
    }
}
