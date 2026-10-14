package com.javaaddonengine.recipes;

import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeParserTest {

    private void write(File dir, String name, String json) throws Exception {
        dir.mkdirs();
        Files.write(new File(dir, name).toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesShapedShapelessAndFurnace(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "recipes");
        write(dir, "sword.json", "{\"minecraft:recipe_shaped\": {"
                + "\"pattern\": [\" S \", \" S \", \" I \"],"
                + "\"key\": {\"S\": {\"item\": \"minecraft:stick\"}, \"I\": {\"item\": \"minecraft:iron_ingot\"}},"
                + "\"result\": {\"item\": \"myaddon:sword\", \"count\": 1}}}");
        write(dir, "mix.json", "{\"minecraft:recipe_shapeless\": {"
                + "\"ingredients\": [{\"item\": \"minecraft:apple\"}, {\"item\": \"minecraft:sugar\"}],"
                + "\"result\": {\"item\": \"myaddon:mix\", \"count\": 2}}}");
        write(dir, "smelt.json", "{\"minecraft:recipe_furnace\": {"
                + "\"input\": {\"item\": \"myaddon:ore\"}, \"output\": {\"item\": \"minecraft:iron_ingot\"}}}");
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        List<AddonRecipe> recipes = RecipeParser.parse(parsed, r);
        assertEquals(3, recipes.size());
    }

    @Test
    void unknownTypeIsKeptAsMetadata(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "recipes");
        write(dir, "weird.json", "{\"minecraft:recipe_brewing_mix\": {\"input\": \"x\"}}");
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        List<AddonRecipe> recipes = RecipeParser.parse(parsed, r);
        assertEquals(1, recipes.size());
        assertEquals(AddonRecipe.Type.UNKNOWN, recipes.get(0).getType());
    }

    @Test
    void typeMapping() {
        assertEquals(AddonRecipe.Type.SHAPED, RecipeParser.mapType("minecraft:recipe_shaped"));
        assertEquals(AddonRecipe.Type.SHAPELESS, RecipeParser.mapType("minecraft:recipe_shapeless"));
        assertEquals(AddonRecipe.Type.FURNACE, RecipeParser.mapType("minecraft:recipe_furnace"));
        assertEquals(AddonRecipe.Type.SMITHING, RecipeParser.mapType("minecraft:recipe_smithing_transform"));
        assertEquals(AddonRecipe.Type.STONECUTTER, RecipeParser.mapType("minecraft:recipe_stonecutter"));
    }

    @Test
    void materialMapping() {
        assertEquals(org.bukkit.Material.OAK_LOG, RecipeRuntime.materialOf("minecraft:oak_log"));
        assertEquals(org.bukkit.Material.STICK, RecipeRuntime.materialOf("minecraft:stick"));
        assertEquals(org.bukkit.Material.IRON_INGOT, RecipeRuntime.materialOf("myaddon:iron_ingot"));
        assertNull(RecipeRuntime.materialOf(""));
    }
}
