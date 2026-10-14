package com.javaaddonengine.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LootEngineTest {

    private static final String TABLE = "{"
            + "\"pools\": [{"
            + " \"rolls\": 2,"
            + " \"entries\": ["
            + "  {\"type\": \"item\", \"name\": \"minecraft:iron_ingot\", \"weight\": 3,"
            + "   \"functions\": [{\"function\": \"set_count\", \"count\": {\"min\": 1, \"max\": 3}}]},"
            + "  {\"type\": \"item\", \"name\": \"minecraft:coal\", \"weight\": 1}"
            + " ]}]}";

    private static File lootFile(File root) throws Exception {
        File dir = new File(root, "loot_tables");
        dir.mkdirs();
        File f = new File(dir, "golem_loot.json");
        Files.write(f.toPath(), TABLE.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    void parsesPoolsAndCounts(@TempDir File tmp) throws Exception {
        lootFile(tmp);
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonLootTable> tables = LootParser.parse(parsed, r);
        assertEquals(1, tables.size());
        AddonLootTable table = tables.get("golem_loot");
        assertNotNull(table);
        assertEquals(1, table.getPools().size());
        assertEquals(2, table.getPools().get(0).rolls);
        assertEquals(1, table.getPools().get(0).entries.get(0).minCount);
        assertEquals(3, table.getPools().get(0).entries.get(0).maxCount);
    }

    @Test
    void weightedRollsRespectWeights(@TempDir File tmp) throws Exception {
        lootFile(tmp);
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        LootEngine engine = new LootEngine();
        engine.registerAll(LootParser.parse(parsed, r));
        int iron = 0;
        int total = 0;
        for (int i = 0; i < 400; i++) {
            List<LootEngine.Drop> drops = engine.roll("golem_loot");
            assertEquals(2, drops.size());
            for (LootEngine.Drop d : drops) {
                total++;
                if (d.item.equals("minecraft:iron_ingot")) iron++;
                assertTrue(d.count >= 1 && d.count <= 3 || d.item.equals("minecraft:coal"));
            }
        }
        double ratio = (double) iron / total;
        assertTrue(ratio > 0.55 && ratio < 0.95, "iron ratio was " + ratio);
    }

    @Test
    void unknownTableRollsEmpty() {
        assertTrue(new LootEngine().roll("nope").isEmpty());
    }

    @Test
    void malformedJsonIsReportedNotThrown(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "loot_tables");
        dir.mkdirs();
        Files.write(new File(dir, "bad.json").toPath(), "{oops".getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        assertDoesNotThrow(() -> LootParser.parse(parsed, r));
    }
}
