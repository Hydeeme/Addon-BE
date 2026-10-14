package com.javaaddonengine.parser;

import com.javaaddonengine.core.AddonCompatibilityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AddonManifestTest {

    private static String manifest(String name, String uuid, String moduleType) {
        return "{"
                + "\"format_version\": 2,"
                + "\"header\": {\"name\": \"" + name + "\", \"description\": \"d\","
                + " \"uuid\": \"" + uuid + "\", \"version\": [1, 0, 0], \"min_engine_version\": [1, 20, 0]},"
                + "\"modules\": [{\"type\": \"" + moduleType + "\","
                + " \"uuid\": \"" + UUID.randomUUID() + "\", \"version\": [1, 0, 0]}],"
                + "\"dependencies\": []"
                + "}";
    }

    @Test
    void parsesValidBehaviorManifest() throws Exception {
        String uuid = UUID.randomUUID().toString();
        AddonManifest m = AddonManifest.parse(manifest("BP", uuid, "data"), "manifest.json");
        assertEquals("BP", m.getName());
        assertEquals(uuid, m.getUuid());
        assertEquals("1.0.0", m.getVersion().toString());
        assertEquals("1.20.0", m.getMinEngineVersion().toString());
        assertTrue(m.isBehaviorPack());
        assertFalse(m.isResourcePack());
    }

    @Test
    void parsesValidResourceManifest() throws Exception {
        AddonManifest m = AddonManifest.parse(
                manifest("RP", UUID.randomUUID().toString(), "resources"), "manifest.json");
        assertTrue(m.isResourcePack());
        assertFalse(m.isBehaviorPack());
    }

    @Test
    void rejectsInvalidUuid() {
        assertThrows(AddonCompatibilityException.class, () ->
                AddonManifest.parse(manifest("X", "not-a-uuid", "data"), "manifest.json"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(AddonCompatibilityException.class, () ->
                AddonManifest.parse("{not json", "manifest.json"));
    }

    @Test
    void detectsMissingManifest(@TempDir File tmp) {
        com.javaaddonengine.core.CompatibilityReport r =
                new com.javaaddonengine.core.CompatibilityReport("t", "T", "1.0.0");
        PackDetector.Detection d = PackDetector.detect(tmp, r);
        assertNull(d.behaviorManifest);
        assertNull(d.resourceManifest);
        assertFalse(r.getEntries().isEmpty());
    }

    @Test
    void detectsDependency(@TempDir File tmp) throws Exception {
        String json = "{"
                + "\"format_version\": 2,"
                + "\"header\": {\"name\": \"D\", \"description\": \"\","
                + " \"uuid\": \"" + UUID.randomUUID() + "\", \"version\": [1, 0, 0]},"
                + "\"modules\": [{\"type\": \"data\", \"uuid\": \"" + UUID.randomUUID() + "\", \"version\": [1,0,0]}],"
                + "\"dependencies\": [{\"uuid\": \"" + UUID.randomUUID() + "\", \"version\": [1, 2, 0]}]"
                + "}";
        Files.write(new File(tmp, "manifest.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        com.javaaddonengine.core.CompatibilityReport r =
                new com.javaaddonengine.core.CompatibilityReport("t", "T", "1.0.0");
        PackDetector.Detection d = PackDetector.detect(tmp, r);
        assertNotNull(d.behaviorManifest);
        assertEquals(1, d.behaviorManifest.getDependencies().size());
        assertFalse(r.getMissingDependencies().isEmpty());
    }

    @Test
    void versionComparison() {
        AddonVersion v = AddonVersion.parse("1.2.3");
        assertEquals("1.2.3", v.toString());
        assertTrue(v.atLeast(AddonVersion.parse("1.2.0")));
        assertFalse(v.atLeast(AddonVersion.parse("2.0.0")));
    }
}
