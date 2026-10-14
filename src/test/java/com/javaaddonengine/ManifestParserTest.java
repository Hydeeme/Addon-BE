package com.javaaddonengine;

import com.javaaddonengine.core.AddonCompatibilityException;
import com.javaaddonengine.parser.AddonManifest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ManifestParserTest {

    private static final String BEHAVIOR = "{"
            + "\"format_version\": 2,"
            + "\"header\": {\"name\": \"Test BP\", \"description\": \"desc\","
            + " \"uuid\": \"123e4567-e89b-12d3-a456-426614174000\", \"version\": [1, 0, 0],"
            + " \"min_engine_version\": [1, 20, 0]},"
            + "\"modules\": [{\"type\": \"data\", \"uuid\": \"123e4567-e89b-12d3-a456-426614174001\", \"version\": [1, 0, 0]},"
            + " {\"type\": \"script\", \"uuid\": \"123e4567-e89b-12d3-a456-426614174002\", \"version\": [1, 0, 0]}],"
            + "\"dependencies\": [{\"uuid\": \"123e4567-e89b-12d3-a456-426614174003\", \"version\": [2, 0, 0]}]"
            + "}";

    @Test
    public void validBehaviorManifest() throws Exception {
        AddonManifest m = AddonManifest.parse(BEHAVIOR, "manifest.json");
        assertEquals("Test BP", m.getName());
        assertEquals("1.0.0", m.getVersion().toString());
        assertTrue(m.isBehaviorPack());
        assertTrue(m.hasScriptModule());
        assertEquals(1, m.getDependencies().size());
        assertEquals("123e4567-e89b-12d3-a456-426614174003", m.getDependencies().get(0).getUuid());
    }

    @Test
    public void resourceManifestDetected() throws Exception {
        String json = "{\"format_version\": 2, \"header\": {\"name\": \"RP\","
                + " \"uuid\": \"123e4567-e89b-12d3-a456-426614174000\", \"version\": [1, 2, 3]},"
                + " \"modules\": [{\"type\": \"resources\", \"uuid\": \"123e4567-e89b-12d3-a456-426614174001\", \"version\": [1, 0, 0]}]}";
        AddonManifest m = AddonManifest.parse(json, "manifest.json");
        assertTrue(m.isResourcePack());
        assertEquals("1.2.3", m.getVersion().toString());
    }

    @Test
    public void invalidUuidRejected() {
        String json = "{\"header\": {\"name\": \"x\", \"uuid\": \"not-a-uuid\", \"version\": [1,0,0]}, \"modules\": []}";
        assertThrows(AddonCompatibilityException.class, () -> AddonManifest.parse(json, "manifest.json"));
    }

    @Test
    public void invalidJsonRejected() {
        assertThrows(AddonCompatibilityException.class, () -> AddonManifest.parse("{broken", "manifest.json"));
    }

    @Test
    public void missingManifestFieldsDefault() throws Exception {
        AddonManifest m = AddonManifest.parse("{\"header\": {}}", "manifest.json");
        assertNotNull(m.getName());
        assertEquals("0.0.0", m.getVersion().toString());
    }
}
