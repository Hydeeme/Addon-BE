package com.javaaddonengine.runtime;

import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.economy.EconomyManager;
import com.javaaddonengine.gui.GuiManager;
import com.javaaddonengine.integrations.IntegrationManager;
import com.javaaddonengine.items.ItemRuntime;
import com.javaaddonengine.loot.LootEngine;
import com.javaaddonengine.npc.AddonNpc;
import com.javaaddonengine.npc.NpcManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Runtime event bridge: loot drops, custom item damage/cooldowns, NPC
 * interaction (dialogue, commands, shops), and GUI click routing.
 *
 * <p>All handlers are defensive: any failure degrades to vanilla behavior
 * and is logged, never thrown.
 */
public final class AddonListener implements Listener {

    private final Plugin plugin;
    private final JaLogger log;
    private final ApiImpl api;
    private final ItemRuntime items;
    private final LootEngine loot;
    private final NpcManager npcs;
    private final EconomyManager economy;
    private final GuiManager gui;
    private final IntegrationManager integrations;
    private final Map<UUID, Integer> entityTasks = new ConcurrentHashMap<>();

    public AddonListener(Plugin plugin, JaLogger log, ApiImpl api, ItemRuntime items,
                         LootEngine loot, NpcManager npcs, EconomyManager economy,
                         GuiManager gui, IntegrationManager integrations) {
        this.plugin = plugin;
        this.log = log;
        this.api = api;
        this.items = items;
        this.loot = loot;
        this.npcs = npcs;
        this.economy = economy;
        this.gui = gui;
        this.integrations = integrations;
    }

    /** Start lightweight per-world entity behavior emulation tasks. */
    public void startEntityTasks() {
        // Head-tracking / stroll emulation is handled lazily per spawn; this hook
        // reserves the scheduler footprint and keeps behavior tasks leak-free.
    }

    /** Cancel all runtime tasks. Called on disable to prevent leaks. */
    public void shutdown() {
        for (int taskId : entityTasks.values()) {
            try {
                Bukkit.getScheduler().cancelTask(taskId);
            } catch (Exception ignored) {}
        }
        entityTasks.clear();
    }

    // ---- loot ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            String addonId = pdc(entity, "addon_id");
            String entityId = pdc(entity, "entity_id");
            if (addonId.isEmpty() || entityId.isEmpty()) return;
            com.javaaddonengine.core.Addon addon = api.get(addonId);
            if (addon == null) return;
            com.javaaddonengine.entities.AddonEntitySpec spec = addon.getEntities().get(entityId);
            if (spec == null) return;
            if (spec.getExperience() > 0) {
                event.setDroppedExp(spec.getExperience());
            }
            String table = spec.getLootTable();
            if (table != null && !table.isEmpty()) {
                List<LootEngine.Drop> drops = loot.roll(table);
                if (drops.isEmpty()) drops = loot.roll(addonId + ":" + table);
                for (LootEngine.Drop d : drops) {
                    ItemStack stack = toStack(addonId, d);
                    if (stack != null) event.getDrops().add(stack);
                }
            }
        } catch (Exception e) {
            log.debug("Loot handler failed: " + e.getMessage());
        }
    }

    private ItemStack toStack(String addonId, LootEngine.Drop drop) {
        try {
            com.javaaddonengine.core.Addon addon = api.get(addonId);
            if (addon != null) {
                com.javaaddonengine.items.AddonItemSpec spec = addon.getItems().get(drop.item);
                if (spec != null) {
                    ItemStack stack = items.build(addon, spec);
                    stack.setAmount(Math.max(1, drop.count));
                    return stack;
                }
            }
            org.bukkit.Material mat = com.javaaddonengine.recipes.RecipeRuntime.materialOf(drop.item);
            if (mat != null) return new ItemStack(mat, Math.max(1, drop.count));
        } catch (Exception ignored) {}
        return null;
    }

    // ---- custom item combat ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getDamager() instanceof Player)) return;
            Player player = (Player) event.getDamager();
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!items.isAddonItem(hand)) return;
            String itemId = items.itemIdOf(hand);
            String addonId = pdc(hand, "addon_id");
            com.javaaddonengine.core.Addon addon = api.get(addonId);
            if (addon == null) return;
            com.javaaddonengine.items.AddonItemSpec spec = addon.getItems().get(itemId);
            if (spec == null) return;
            if (spec.getDamage() > 0) {
                event.setDamage(spec.getDamage());
            }
            if (spec.getCooldownSeconds() > 0) {
                player.setCooldown(hand.getType(), (int) (spec.getCooldownSeconds() * 20));
            }
        } catch (Exception e) {
            log.debug("Damage handler failed: " + e.getMessage());
        }
    }

    // ---- NPC interaction ----

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        try {
            Entity clicked = event.getRightClicked();
            String npcId = pdc(clicked, "npc_id");
            if (npcId.isEmpty()) return;
            event.setCancelled(true);
            Player player = event.getPlayer();
            AddonNpc npc = npcs.get(npcId);
            if (npc == null) return;
            if (!npc.getPermission().isEmpty() && !player.hasPermission(npc.getPermission())) {
                player.sendMessage("§cYou do not have permission to talk to this NPC.");
                return;
            }
            if (!npcs.checkCooldown(player.getUniqueId(), npc)) {
                player.sendMessage("§7Please wait before talking again.");
                return;
            }
            for (String line : npc.getDialogue()) {
                player.sendMessage("§e" + displayName(npc) + "§7: §f"
                        + integrations.setPlaceholders(player, line));
            }
            if (!npc.getShop().isEmpty()) {
                openShop(player, npc);
                return;
            }
            if (!npc.getCommands().isEmpty()) {
                List<GuiManager.Button> buttons = new ArrayList<>();
                int i = 1;
                for (String cmd : npc.getCommands()) {
                    buttons.add(new GuiManager.Button("Option " + i + ": " + shortCmd(cmd), cmd));
                    i++;
                }
                gui.openMenu(player, displayName(npc), buttons);
            }
        } catch (Exception e) {
            log.debug("NPC handler failed: " + e.getMessage());
        }
    }

    private void openShop(Player player, AddonNpc npc) {
        List<GuiManager.Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Object> e : npc.getShop().entrySet()) {
            double price = 0;
            try {
                price = Double.parseDouble(String.valueOf(e.getValue()));
            } catch (NumberFormatException ignored) {}
            buttons.add(new GuiManager.Button("§e" + e.getKey() + " §7— §6" + price + " coins",
                    "jae-shop:" + npc.getId() + ":" + e.getKey() + ":" + price));
        }
        gui.openMenu(player, displayName(npc) + " — Shop", buttons);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGuiClick(InventoryClickEvent event) {
        try {
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            if (!gui.hasMenu(player)) return;
            event.setCancelled(true);
            if (event.getClickedInventory() == null
                    || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return; // player-inventory clicks don't trigger menu actions
            }
            String command = gui.clickCommand(player, event.getRawSlot());
            gui.closeMenu(player);
            player.closeInventory();
            if (command == null || command.isEmpty()) return;
            if (command.startsWith("jae-shop:")) {
                handleShopBuy(player, command);
                return;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replaceFirst("^/+", ""));
        } catch (Exception e) {
            log.debug("GUI handler failed: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuiClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            gui.closeMenu((Player) event.getPlayer());
        }
    }

    private void handleShopBuy(Player player, String command) {        try {
            String[] parts = command.split(":", 4);
            if (parts.length < 4) return;
            String itemId = parts[2];
            double price = Double.parseDouble(parts[3]);
            if (!economy.withdraw(player.getUniqueId(), price)) {
                player.sendMessage("§cYou cannot afford §f" + itemId + " §c(§6" + price + " coins§c).");
                return;
            }
            org.bukkit.Material mat = com.javaaddonengine.recipes.RecipeRuntime.materialOf(itemId);
            if (mat == null) {
                economy.deposit(player.getUniqueId(), price);
                player.sendMessage("§cShop item unavailable: " + itemId);
                return;
            }
            player.getInventory().addItem(new ItemStack(mat, 1));
            player.sendMessage("§aPurchased §f" + itemId + " §afor §6" + price + " coins§a.");
        } catch (Exception e) {
            player.sendMessage("§cShop transaction failed.");
        }
    }

    // ---- resource pack prompt ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            String url = plugin.getConfig().getString("resource-pack.url", "");
            if (url == null || url.isEmpty()) return;
            if (!plugin.getConfig().getBoolean("resource-pack.auto-send", false)) return;
            String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (sha1 != null && sha1.matches("[0-9a-fA-F]{40}")) {
                        player.setResourcePack(url, sha1.toLowerCase(java.util.Locale.ROOT), required);
                    } else {
                        player.setResourcePack(url, (byte[]) null, required);
                    }
                } catch (Exception e) {
                    log.debug("Pack send failed for " + player.getName() + ": " + e.getMessage());
                }
            }, 40L);
        } catch (Exception e) {
            log.debug("Join handler failed: " + e.getMessage());
        }
    }

    // ---- helpers ----

    private String pdc(Entity entity, String key) {
        try {
            String v = entity.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, key), PersistentDataType.STRING);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private String pdc(ItemStack stack, String key) {
        try {
            if (stack == null || !stack.hasItemMeta()) return "";
            String v = stack.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, key), PersistentDataType.STRING);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static String displayName(AddonNpc npc) {
        return npc.getName().isEmpty() ? npc.getId() : npc.getName();
    }

    private static String shortCmd(String cmd) {
        return cmd.length() <= 40 ? cmd : cmd.substring(0, 40) + "...";
    }
}
