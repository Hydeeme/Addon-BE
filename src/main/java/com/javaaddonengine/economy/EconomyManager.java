package com.javaaddonengine.economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Economy abstraction. Uses Vault when present, otherwise a simple internal balance store.
 * Real Vault wiring happens in IntegrationManager via reflection (no hard dependency).
 */
public final class EconomyManager {

    public enum Provider { AUTO, VAULT, INTERNAL }

    private Provider active = Provider.INTERNAL;
    private Object vaultEconomy;
    private final Map<UUID, Double> internal = new ConcurrentHashMap<>();

    public Provider getActive() { return active; }

    public void setVaultEconomy(Object vaultEconomy) {
        this.vaultEconomy = vaultEconomy;
        if (vaultEconomy != null) active = Provider.VAULT;
        else if (active == Provider.VAULT) active = Provider.INTERNAL;
    }

    public void setProvider(Provider p) {
        if (p == Provider.VAULT && vaultEconomy == null) active = Provider.INTERNAL;
        else if (p != Provider.AUTO) active = p;
        else active = vaultEconomy != null ? Provider.VAULT : Provider.INTERNAL;
    }

    public double balance(UUID player) {
        if (active == Provider.VAULT && vaultEconomy != null) {
            try {
                Object p = org.bukkit.Bukkit.getPlayer(player);
                if (p == null) return 0.0;
                return (double) vaultEconomy.getClass()
                        .getMethod("getBalance", org.bukkit.OfflinePlayer.class)
                        .invoke(vaultEconomy, (org.bukkit.OfflinePlayer) p);
            } catch (Exception e) {
                return internal.getOrDefault(player, 0.0);
            }
        }
        return internal.getOrDefault(player, 0.0);
    }

    public boolean withdraw(UUID player, double amount) {
        if (amount < 0) return false;
        if (active == Provider.VAULT && vaultEconomy != null) {
            try {
                Object p = org.bukkit.Bukkit.getPlayer(player);
                if (p == null) return false;
                Object r = vaultEconomy.getClass()
                        .getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class)
                        .invoke(vaultEconomy, (org.bukkit.OfflinePlayer) p, amount);
                return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
            } catch (Exception e) {
                return false;
            }
        }
        double bal = internal.getOrDefault(player, 0.0);
        if (bal < amount) return false;
        internal.put(player, bal - amount);
        return true;
    }

    public void deposit(UUID player, double amount) {
        if (amount < 0) return;
        if (active == Provider.VAULT && vaultEconomy != null) {
            try {
                Object p = org.bukkit.Bukkit.getPlayer(player);
                if (p == null) return;
                vaultEconomy.getClass()
                        .getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                        .invoke(vaultEconomy, (org.bukkit.OfflinePlayer) p, amount);
                return;
            } catch (Exception ignored) {}
        }
        internal.merge(player, amount, Double::sum);
    }
}
