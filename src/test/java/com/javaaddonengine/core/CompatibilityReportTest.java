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
}
