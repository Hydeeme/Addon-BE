package com.javaaddonengine.npc;

import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class NpcParserTest {

    private void write(File root, String sub, String name, String json) throws Exception {
        File dir = new File(root, sub);
        dir.mkdirs();
        Files.write(new File(dir, name).toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesBedrockDialogueScene(@TempDir File tmp) throws Exception {
        write(tmp, "dialogue", "keeper.json", "{"
                + "\"minecraft:npc_dialogue\": {"
                + " \"scenes\": [{"
                + "  \"scene_tag\": \"greeting\","
                + "  \"npc_name\": \"Keeper\","
                + "  \"text\": [\"Welcome, traveler.\"],"
                + "  \"buttons\": [{\"name\": \"Trade\", \"commands\": [\"give @p minecraft:apple 1\"]}]"
                + " }]}}");
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        assertEquals(1, parsed.getDialogueFiles().size());
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Addon addon = new Addon("t", tmp);
        NpcParser.parse(parsed, r, addon);
        assertEquals(1, addon.getNpcs().size());
        AddonNpc npc = addon.getNpcs().values().iterator().next();
        assertEquals("Keeper", npc.getName());
        assertTrue(npc.getDialogue().contains("Welcome, traveler."));
        assertTrue(npc.getCommands().contains("give @p minecraft:apple 1"));
        assertTrue(r.getEntries().stream().anyMatch(e -> e.getLevel() == CompatibilityLevel.EMULATED));
    }

    @Test
    void parsesExtendedNpcWithShop(@TempDir File tmp) throws Exception {
        write(tmp, "npcs", "trader.json", "{"
                + "\"id\": \"trader\", \"name\": \"Trader\","
                + "\"dialogue\": [\"Fine wares.\"],"
                + "\"shop\": {\"minecraft:apple\": 5},"
                + "\"permission\": \"jae.shop\", \"cooldown_seconds\": 5}");
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Addon addon = new Addon("t", tmp);
        NpcParser.parse(parsed, r, addon);
        AddonNpc npc = addon.getNpcs().get("trader");
        assertNotNull(npc);
        assertEquals(5.0, npc.getShop().get("minecraft:apple"));
        assertEquals("jae.shop", npc.getPermission());
        assertEquals(5, npc.getCooldownSeconds());
    }

    @Test
    void npcManagerLifecycle() {
        // NpcManager requires a Plugin; pass null since define/get/undefineAll
        // never touch Bukkit (spawn path is the only Bukkit consumer).
        NpcManager manager = new NpcManager(null);
        AddonNpc npc = new AddonNpc("keeper");
        npc.setName("Keeper");
        manager.define(npc);
        assertEquals(1, manager.count());
        assertNotNull(manager.get("keeper"));
        assertEquals(java.util.List.of("keeper"), manager.definitionIds());
        manager.undefineAll(java.util.List.of("keeper"));
        assertEquals(0, manager.count());
        // despawn with no Bukkit server must not throw (entities simply resolve null).
        assertDoesNotThrow(() -> manager.despawn(java.util.List.of("keeper")));
        assertDoesNotThrow(manager::despawnAll);
    }
}
