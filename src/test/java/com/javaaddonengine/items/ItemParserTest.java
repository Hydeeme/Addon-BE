package com.javaaddonengine.items;

import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import com.javaaddonengine.translator.AdapterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemParserTest {

    private static final String SWORD = "{"
            + "\"format_version\": \"1.10.0\","
            + "\"minecraft:item\": {"
            + " \"description\": {\"identifier\": \"myaddon:laser_sword\", \"category\": \"equipment\"},"
            + " \"components\": {"
            + "  \"minecraft:max_stack_size\": {\"value\": 1},"
            + "  \"minecraft:durability\": {\"max_durability\": 1500},"
            + "  \"minecraft:damage\": {\"value\": 12},"
            + "  \"minecraft:cooldown\": {\"duration\": 0.5},"
            + "  \"minecraft:rarity\": {\"value\": \"epic\"},"
            + "  \"minecraft:tags\": {\"tags\": [\"weapon\"]},"
            + "  \"minecraft:icon\": {\"texture\": \"laser_sword\"},"
            + "  \"minecraft:food\": {\"nutrition\": 4, \"saturation_modifier\": 0.8}"
            + " }}}";

    @Test
    void parsesItemComponents(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "items");
        dir.mkdirs();
        Files.write(new File(dir, "laser_sword.json").toPath(), SWORD.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonItemSpec> out = new LinkedHashMap<>();
        ItemParser.parse(parsed, r, out, new AdapterRegistry());

        AddonItemSpec spec = out.get("myaddon:laser_sword");
        assertNotNull(spec);
        assertEquals(1, spec.getMaxStackSize());
        assertEquals(1500, spec.getDurability());
        assertEquals(12, spec.getDamage());
        assertEquals(0.5, spec.getCooldownSeconds(), 1e-9);
        assertEquals("epic", spec.getRarity());
        assertTrue(spec.isFood());
        assertEquals(4, spec.getNutrition());
        assertEquals("laser_sword", spec.getIconTexture());
        assertTrue(spec.getTags().contains("weapon"));
    }
}
