package com.javaaddonengine.blocks;

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

class BlockParserTest {

    private static final String ORE = "{"
            + "\"format_version\": \"1.10.0\","
            + "\"minecraft:block\": {"
            + " \"description\": {\"identifier\": \"myaddon:spark_ore\","
            + "  \"properties\": {\"lit\": [false, true]}},"
            + " \"components\": {"
            + "  \"minecraft:hardness\": {\"value\": 3.5},"
            + "  \"minecraft:resistance\": {\"value\": 6.0},"
            + "  \"minecraft:light\": {\"emission\": 9},"
            + "  \"minecraft:loot\": {\"table\": \"spark_ore_loot\"},"
            + "  \"minecraft:tags\": {\"tags\": [\"ore\"]}"
            + " }}}";

    @Test
    void parsesBlockComponents(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "blocks");
        dir.mkdirs();
        Files.write(new File(dir, "spark_ore.json").toPath(), ORE.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonBlockSpec> out = new LinkedHashMap<>();
        BlockParser.parse(parsed, r, out, new AdapterRegistry(), AddonBlockSpec.RenderMode.FALLBACK);

        AddonBlockSpec spec = out.get("myaddon:spark_ore");
        assertNotNull(spec);
        assertEquals(3.5, spec.getHardness(), 1e-9);
        assertEquals(6.0, spec.getResistance(), 1e-9);
        assertEquals(9, spec.getLight());
        assertEquals("spark_ore_loot", spec.getLoot());
        assertTrue(spec.getTags().contains("ore"));
        // FALLBACK config resolves to an explicit runtime mode, never left ambiguous.
        assertNotEquals(AddonBlockSpec.RenderMode.FALLBACK, spec.getRenderMode());
    }
}
