package com.javaaddonengine.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityReportTest {

    @Test
    void emptyReportScoresZero() {
        CompatibilityReport r = new CompatibilityReport("a", "A", "1.0.0");
        assertEquals(0.0, r.score(), 1e-9);
        assertEquals("MINIMAL", r.grade());
    }

    @Test
    void weightedScore() {
        CompatibilityReport r = new CompatibilityReport("a", "A", "1.0.0");
        r.add("Entities", new CompatibilityEntry("minecraft:health", CompatibilityLevel.FULL, "ok"));
        r.add("Entities", new CompatibilityEntry("minecraft:physics", CompatibilityLevel.EMULATED, "ok"));
        r.add("Items", new CompatibilityEntry("custom", CompatibilityLevel.UNSUPPORTED, "no"));
        // (1.0 + 0.8 + 0.0) / 3 = 0.6
        assertEquals(60.0, r.score(), 1e-9);
        assertEquals("FAIR", r.grade());
    }

    @Test
    void textJsonHtmlRender() {
        CompatibilityReport r = new CompatibilityReport("my-addon", "My Addon", "1.2.0");
        r.add("Entities", new CompatibilityEntry("minecraft:health", CompatibilityLevel.FULL, "Maps to health."));
        r.setResourcePackConverted(true);
        String text = r.toText();
        assertTrue(text.contains("JavaAddonEngine Compatibility Report"));
        assertTrue(text.contains("My Addon"));
        String json = r.toJson();
        assertTrue(json.contains("my-addon"));
        String html = r.toHtml();
        assertTrue(html.contains("<table"));
    }

    @Test
    void exceptionReportString() {
        AddonCompatibilityException e = new AddonCompatibilityException(
                "Unable to translate: x", "example", "entities/robot.json", 84);
        String s = e.toReportString();
        assertTrue(s.contains("example"));
        assertTrue(s.contains("entities/robot.json"));
        assertTrue(s.contains("84"));
    }

    @Test
    void entryPreservesCategory() {
        CompatibilityEntry e = new CompatibilityEntry("Items", "minecraft:food",
                CompatibilityLevel.FULL, "ok", "f.json", 3);
        assertEquals("Items", e.getCategory());
        assertEquals("minecraft:food", e.getComponent());
    }

    @Test
    void addNormalizesDefaultCategory() {
        CompatibilityReport r = new CompatibilityReport("a", "A", "1.0.0");
        // 5-arg entry defaults to "Converted"; add-category must win so
        // per-category breakdowns don't collapse into one bucket.
        r.add("Entities", new CompatibilityEntry("minecraft:health", CompatibilityLevel.FULL, "ok", "entities/t_rex.json", -1));
        r.add("Items", new CompatibilityEntry("custom", CompatibilityLevel.UNSUPPORTED, "no", "items/claw.json", -1));
        assertEquals(1, r.getEntries().stream().filter(e -> "Entities".equals(e.getCategory())).count());
        assertEquals(1, r.getEntries().stream().filter(e -> "Items".equals(e.getCategory())).count());
        assertEquals(100.0, r.categoryScore("Entities"), 1e-9);
        assertEquals(0.0, r.categoryScore("Items"), 1e-9);
        assertEquals(50.0, r.score(), 1e-9);
    }

    @Test
    void perCategoryAndPerFileBreakdown() {
        CompatibilityReport r = new CompatibilityReport("dino", "Dino Pack", "1.0.0");
        // T-Rex mob: 2 FULL + 1 EMULATED + 1 PARTIAL + 1 UNSUPPORTED = (1+1+.8+.6+0)/5 = 68%
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:health", CompatibilityLevel.FULL, "ok", "entities/t_rex.json", -1));
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:movement", CompatibilityLevel.FULL, "ok", "entities/t_rex.json", -1));
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:behavior.melee_attack", CompatibilityLevel.EMULATED, "ok", "entities/t_rex.json", -1));
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:collision_box", CompatibilityLevel.PARTIAL, "ok", "entities/t_rex.json", -1));
        r.add("Entities", new CompatibilityEntry("Entities", "custom:roar", CompatibilityLevel.UNSUPPORTED, "no", "entities/t_rex.json", -1));
        // Raptor mob: all FULL = 100%
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:health", CompatibilityLevel.FULL, "ok", "entities/raptor.json", -1));
        r.add("Entities", new CompatibilityEntry("Entities", "minecraft:movement", CompatibilityLevel.FULL, "ok", "entities/raptor.json", -1));
        // One item: FULL
        r.add("Items", new CompatibilityEntry("Items", "minecraft:durability", CompatibilityLevel.FULL, "ok", "items/dino_tooth.json", -1));

        assertEquals(68.0, r.fileScore("entities/t_rex.json"), 1e-9);
        assertEquals(100.0, r.fileScore("entities/raptor.json"), 1e-9);
        assertEquals(0.0, r.fileScore("entities/missing.json"), 1e-9);
        assertEquals(3, r.fileScores().size() - 0); // t_rex + raptor + dino_tooth
        // Entities category: 7 entries (5 t_rex + 2 raptor) = (3.4+2)/7 = 77.14%
        assertEquals((3.4 + 2.0) / 7 * 100.0, r.categoryScore("Entities"), 1e-6);
        assertEquals(100.0, r.categoryScore("Items"), 1e-9);
        assertEquals(0.0, r.categoryScore("Nope"), 1e-9);
        assertTrue(r.categoryScores().containsKey("Entities"));
        assertTrue(r.categoryScores().containsKey("Items"));

        String text = r.toText();
        assertTrue(text.contains("Per-category breakdown"));
        assertTrue(text.contains("Per-file breakdown"));
        assertTrue(text.contains("entities/t_rex.json"));
        String json = r.toJson();
        assertTrue(json.contains("categoryScores"));
        assertTrue(json.contains("fileScores"));
        String html = r.toHtml();
        assertTrue(html.contains("Per-file breakdown"));
        assertTrue(html.contains("entities/t_rex.json"));
    }
}
