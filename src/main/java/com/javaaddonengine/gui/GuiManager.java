package com.javaaddonengine.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/** Translates Bedrock forms/menus into chest inventory GUIs. */
public final class GuiManager {

    private final Plugin plugin;
    /** Open menu per player: slot -> command. Slot-based, never label-matched. */
    private final Map<UUID, List<Button>> openMenus = new ConcurrentHashMap<>();

    public GuiManager(Plugin plugin) { this.plugin = plugin; }

    public static final class Button {
        public final String label;
        public final String command;
        public Button(String label, String command) { this.label = label; this.command = command; }
    }

    /** Open a simple menu GUI. Must run on the main thread. */
    public void openMenu(Player player, String title, List<Button> buttons) {
        int size = 9;
        while (size < buttons.size() && size < 54) size += 9;
        Inventory inv = Bukkit.createInventory(null, size, truncate(title, 32));
        for (int i = 0; i < buttons.size() && i < size; i++) {
            Button b = buttons.get(i);
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + b.label);
                if (b.command != null && !b.command.isEmpty() && !b.command.startsWith("jae-shop:")) {
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Click to run");
                    meta.setLore(lore);
                }
                stack.setItemMeta(meta);
            }
            inv.setItem(i, stack);
        }
        openMenus.put(player.getUniqueId(), new ArrayList<>(buttons));
        player.openInventory(inv);
    }

    /** Resolve a click by raw slot. Returns null for non-menu clicks. */
    public String clickCommand(Player player, int rawSlot) {
        List<Button> buttons = openMenus.get(player.getUniqueId());
        if (buttons == null || rawSlot < 0 || rawSlot >= buttons.size()) return null;
        return buttons.get(rawSlot).command;
    }

    public boolean hasMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    public void closeMenu(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public void openText(Player player, String title, List<String> lines) {
        List<Button> buttons = new ArrayList<>();
        for (String line : lines) buttons.add(new Button(line, ""));
        openMenu(player, title, buttons);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "Menu";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
