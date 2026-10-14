package com.javaaddonengine.entities;

import com.javaaddonengine.core.CompatibilityLevel;
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

class EntityParserTest {

    private static final String ROBOT = "{"
            + "\"format_version\": \"1.10.0\","
            + "\"minecraft:entity\": {"
            + " \"description\": {\"identifier\": \"my_addon:robot\"},"
            + " \"components\": {"
            + "  \"minecraft:health\": {\"value\": 100, \"max\": 100},"
            + "  \"minecraft:movement\": {\"value\": 0.3},"
            + "  \"minecraft:attack\": {\"damage\": 10},"
            + "  \"minecraft:scale\": {\"value\": 1.2},"
            + "  \"minecraft:follow_range\": {\"value\": 24},"
            + "  \"minecraft:knockback_resistance\": {\"value\": 0.5},"
            + "  \"minecraft:loot\": {\"table\": \"robot_loot\"},"
            + "  \"minecraft:experience_reward\": {\"value\": 12},"
            + "  \"minecraft:type_family\": {\"family\": [\"robot\", \"monster\"]},"
            + "  \"minecraft:behavior.melee_attack\": {\"priority\": 2},"
            + "  \"minecraft:behavior.random_stroll\": {\"priority\": 6},"
            + "  \"minecraft:unknown_future_thing\": {}"
            + " }}}";

    @Test
    void parsesCoreAttributes(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "entities");
        dir.mkdirs();
        Files.write(new File(dir, "robot.json").toPath(), ROBOT.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonEntitySpec> out = new LinkedHashMap<>();
        EntityParser.parse(parsed, tmp, r, out, new AdapterRegistry());

        AddonEntitySpec spec = out.get("my_addon:robot");
        assertNotNull(spec);
        assertEquals(100.0, spec.getHealth(), 1e-9);
        assertEquals(0.3, spec.getMovementSpeed(), 1e-9);
        assertEquals(10.0, spec.getAttackDamage(), 1e-9);
        assertEquals(1.2, spec.getScale(), 1e-9);
        assertEquals(24.0, spec.getFollowRange(), 1e-9);
        assertEquals(0.5, spec.getKnockbackResistance(), 1e-9);
        assertEquals("robot_loot", spec.getLootTable());
        assertEquals(12, spec.getExperience());
        assertTrue(spec.getBehaviors().contains("minecraft:behavior.melee_attack"));
        assertEquals("IRON_GOLEM", spec.getBaseVanilla());
    }

    @Test
    void unknownComponentsDoNotFail(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "entities");
        dir.mkdirs();
        Files.write(new File(dir, "robot.json").toPath(), ROBOT.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonEntitySpec> out = new LinkedHashMap<>();
        assertDoesNotThrow(() -> EntityParser.parse(parsed, tmp, r, out, new AdapterRegistry()));
        assertFalse(r.getEntries().isEmpty());
        assertTrue(r.getEntries().stream().anyMatch(e ->
                e.getLevel() == CompatibilityLevel.PARTIAL
                        || e.getLevel() == CompatibilityLevel.EMULATED
                        || e.getLevel() == CompatibilityLevel.FULL));
    }

    @Test
    void unparseableFileIsReported(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "entities");
        dir.mkdirs();
        Files.write(new File(dir, "bad.json").toPath(), "{{{".getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonEntitySpec> out = new LinkedHashMap<>();
        assertDoesNotThrow(() -> EntityParser.parse(parsed, tmp, r, out, new AdapterRegistry()));
        assertTrue(out.isEmpty());
    }

    @Test
    void geometryStrippedWithoutQuotes(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "entities");
        dir.mkdirs();
        String json = "{\"format_version\": \"1.10.0\","
                + "\"minecraft:entity\": {"
                + " \"description\": {\"identifier\": \"my_addon:geo\", \"geometry\": \"geometry.robot\"},"
                + " \"components\": {\"minecraft:health\": {\"value\": 20}}}}";
        Files.write(new File(dir, "geo.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        Map<String, AddonEntitySpec> out = new LinkedHashMap<>();
        EntityParser.parse(parsed, tmp, r, out, new AdapterRegistry());
        AddonEntitySpec spec = out.get("my_addon:geo");
        assertNotNull(spec);
        // Must be the bare id (ModelEngineBridge.modelIdOf depends on this).
        assertEquals("geometry.robot", spec.getGeometry());
    }
}
