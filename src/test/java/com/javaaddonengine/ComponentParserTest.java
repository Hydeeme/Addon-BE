package com.javaaddonengine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.entities.AddonEntitySpec;
import com.javaaddonengine.entities.EntityParser;
import com.javaaddonengine.items.AddonItemSpec;
import com.javaaddonengine.items.ItemParser;
import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.blocks.BlockParser;
import com.javaaddonengine.loot.AddonLootTable;
import com.javaaddonengine.loot.LootEngine;
import com.javaaddonengine.loot.LootParser;
import com.javaaddonengine.parser.ParsedAddon;
import com.javaaddonengine.recipes.AddonRecipe;
import com.javaaddonengine.recipes.RecipeParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class ComponentParserTest {

    @TempDir
    File temp;

    private File write(String rel, String content) throws Exception {
        File f = new File(temp, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void entityHealthMovementAttack() throws Exception {
        write("bp/entities/robot.json", "{ \"minecraft:entity\": {"
                + " \"description\": {\"identifier\": \"myaddon:robot\"},"
                + " \"components\": {"
                + " \"minecraft:health\": {\"value\": 100, \"max\": 100},"
                + " \"minecraft:movement\": {\"value\": 0.3},"
                + " \"minecraft:attack\": {\"damage\": 10},"
                + " \"minecraft:scale\": {\"value\": 1.2},"
                + " \"minecraft:type_family\": {\"family\": [\"robot\", \"monster\"]},"
                + " \"minecraft:loot\": {\"table\": \"robot_loot\"},"
                + " \"minecraft:experience_reward\": {\"value\": 12},"
                + " \"minecraft:behavior.melee_attack\": {},"
                + " \"example:overdrive_core\": {\"x\": 1}"
                + "}}}");
        ParsedAddon parsed = ParsedAddon.scan(temp);
        CompatibilityReport report = new CompatibilityReport("t", "t", "1.0.0");
        Map<String, AddonEntitySpec> out = new HashMap<>();
        EntityParser.parse(parsed, temp, report, out, null);
        AddonEntitySpec spec = out.get("myaddon:robot");
        assertNotNull(spec);
        assertEquals(100.0, spec.getHealth());
        assertEquals(0.3, spec.getMovementSpeed(), 1e-9);
        assertEquals(10.0, spec.getAttackDamage());
        assertEquals(1.2, spec.getScale(), 1e-9);
        assertEquals("robot_loot", spec.getLootTable());
        assertEquals(12, spec.getExperience());
        assertTrue(spec.getTypeFamilies().contains("robot"));
        assertTrue(spec.getBehaviors().contains("minecraft:behavior.melee_attack"));
        boolean hasUnsupported = report.getEntries().stream()
                .anyMatch(e -> e.getComponent().equals("example:overdrive_core")
                        && e.getLevel() == CompatibilityLevel.UNSUPPORTED);
        assertTrue(hasUnsupported, "unknown component must be UNSUPPORTED");
    }

    @Test
    public void itemDurabilityFoodCooldown() throws Exception {
        write("bp2/items/laser_sword.json", "{ \"minecraft:item\": {"
                + " \"description\": {\"identifier\": \"myaddon:laser_sword\"},"
                + " \"components\": {"
                + " \"minecraft:max_stack_size\": {\"value\": 1},"
                + " \"minecraft:durability\": {\"max_durability\": 1500},"
                + " \"minecraft:damage\": {\"value\": 12},"
                + " \"minecraft:cooldown\": {\"duration\": 0.5},"
                + " \"minecraft:rarity\": {\"value\": \"epic\"},"
                + " \"minecraft:tags\": {\"tags\": [\"weapon\"]}"
                + "}}}");
        ParsedAddon parsed = ParsedAddon.scan(temp);
        CompatibilityReport report = new CompatibilityReport("t", "t", "1.0.0");
        Map<String, AddonItemSpec> out = new HashMap<>();
        ItemParser.parse(parsed, report, out, null);
        AddonItemSpec spec = out.get("myaddon:laser_sword");
        assertNotNull(spec);
        assertEquals(1, spec.getMaxStackSize());
        assertEquals(1500, spec.getDurability());
        assertEquals(12, spec.getDamage());
        assertEquals(0.5, spec.getCooldownSeconds(), 1e-9);
        assertEquals("epic", spec.getRarity());
    }

    @Test
    public void blockHardnessLight() throws Exception {
        write("bp3/blocks/laser_ore.json", "{ \"minecraft:block\": {"
                + " \"description\": {\"identifier\": \"myaddon:laser_ore\"},"
                + " \"components\": {"
                + " \"minecraft:hardness\": {\"value\": 3.5},"
                + " \"minecraft:light\": {\"emission\": 9},"
                + " \"minecraft:tags\": {\"tags\": [\"ore\"]}"
                + "}}}");
        ParsedAddon parsed = ParsedAddon.scan(temp);
        CompatibilityReport report = new CompatibilityReport("t", "t", "1.0.0");
        Map<String, AddonBlockSpec> out = new HashMap<>();
        BlockParser.parse(parsed, report, out, null, AddonBlockSpec.RenderMode.FALLBACK);
        AddonBlockSpec spec = out.get("myaddon:laser_ore");
        assertNotNull(spec);
        assertEquals(3.5, spec.getHardness(), 1e-9);
        assertEquals(9, spec.getLight());
        assertTrue(spec.getTags().contains("ore"));
    }

    @Test
    public void recipesShapedShapelessFurnace() throws Exception {
        write("bp4/recipes/sword.json", "{ \"minecraft:recipe_shaped\": {"
                + " \"pattern\": [\" L \", \" L \", \" S \"],"
                + " \"key\": {\"L\": {\"item\": \"myaddon:laser\"}, \"S\": {\"item\": \"minecraft:stick\"}},"
                + " \"result\": {\"item\": \"myaddon:laser_sword\", \"count\": 1}}}");
        write("bp4/recipes/dust.json", "{ \"minecraft:recipe_shapeless\": {"
                + " \"ingredients\": [{\"item\": \"myaddon:laser_ore\"}],"
                + " \"result\": {\"item\": \"myaddon:laser_dust\", \"count\": 4}}}");
        write("bp4/recipes/smelting.json", "{ \"minecraft:recipe_furnace\": {"
                + " \"input\": \"myaddon:laser_ore\", \"output\": \"myaddon:laser\"}}");
        ParsedAddon parsed = ParsedAddon.scan(temp);
        CompatibilityReport report = new CompatibilityReport("t", "t", "1.0.0");
        List<AddonRecipe> recipes = RecipeParser.parse(parsed, report);
        assertEquals(3, recipes.size());
        AddonRecipe shaped = recipes.stream().filter(r -> r.getType() == AddonRecipe.Type.SHAPED).findFirst().orElse(null);
        assertNotNull(shaped);
        assertEquals(3, shaped.getShape().size());
        assertEquals("myaddon:laser", shaped.getKey().get("L"));
    }

    @Test
    public void lootPoolsWeights() throws Exception {
        write("bp5/loot_tables/robot.json", "{ \"pools\": [{"
                + " \"rolls\": 2,"
                + " \"entries\": ["
                + " {\"type\": \"item\", \"name\": \"myaddon:laser\", \"weight\": 3,"
                + " \"functions\": [{\"function\": \"set_count\", \"count\": {\"min\": 1, \"max\": 3}}]},"
                + " {\"type\": \"item\", \"name\": \"minecraft:iron_ingot\", \"weight\": 1}"
                + "]}]}");
        ParsedAddon parsed = ParsedAddon.scan(temp);
        CompatibilityReport report = new CompatibilityReport("t", "t", "1.0.0");
        Map<String, AddonLootTable> tables = LootParser.parse(parsed, report);
        assertTrue(tables.containsKey("robot"));
        LootEngine engine = new LootEngine();
        engine.registerAll(tables);
        // roll many times: weighted entry must appear, counts in range
        boolean sawLaser = false;
        for (int i = 0; i < 200; i++) {
            for (LootEngine.Drop d : engine.roll("robot")) {
                if (d.item.equals("myaddon:laser")) {
                    sawLaser = true;
                    assertTrue(d.count >= 1 && d.count <= 3);
                }
            }
        }
        assertTrue(sawLaser);
        assertTrue(engine.roll("missing_table").isEmpty());
    }
}
