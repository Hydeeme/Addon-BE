package com.javaaddonengine.resourcepack;

import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.items.AddonItemSpec;
import com.javaaddonengine.parser.ParsedAddon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackGeneratorTest {

    // Minimal 1x1 PNG (valid signature + IHDR + IEND).
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
            0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0,
            0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, (byte) 0xDD, (byte) 0x8D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    void generatesPackWithModelsAndMetadata(@TempDir File tmp) throws Exception {
        File texDir = new File(tmp, "textures/items");
        texDir.mkdirs();
        Files.write(new File(texDir, "spark_sword.png").toPath(), PNG);
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        assertEquals(1, parsed.getTextureFiles().size());

        AddonItemSpec spec = new AddonItemSpec("jae_example:spark_sword");
        spec.setDisplayName("Spark Sword");
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        ResourcePackGenerator.Result result = ResourcePackGenerator.generate(
                tmp, parsed, Collections.emptyMap(), List.of(spec), r, 48, "Test pack");

        assertTrue(new File(result.directory, "pack.mcmeta").isFile());
        assertTrue(new File(result.directory, "assets/minecraft/models/item/jae_spark_sword.json").isFile());
        assertTrue(new File(result.directory, "assets/minecraft/lang/en_us.json").isFile());
        assertTrue(result.sha1.matches("[0-9a-f]{40}"));
        assertTrue(r.isResourcePackConverted());
        assertEquals(1, spec.getCustomModelData());
    }

    @Test
    void corruptPngIsReportedNotShipped(@TempDir File tmp) throws Exception {
        File texDir = new File(tmp, "textures");
        texDir.mkdirs();
        Files.write(new File(texDir, "broken.png").toPath(), "not a png".getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        ResourcePackGenerator.Result result = ResourcePackGenerator.generate(
                tmp, parsed, Collections.emptyMap(), null, r, 48, "Test pack");
        assertEquals(0, result.textures);
        assertFalse(new File(result.directory, "assets/minecraft/textures/item/jae/broken.png").exists());
    }

    @Test
    void zipRoundTrip(@TempDir File tmp) throws Exception {
        File texDir = new File(tmp, "textures");
        texDir.mkdirs();
        Files.write(new File(texDir, "a.png").toPath(), PNG);
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        ResourcePackGenerator.Result result = ResourcePackGenerator.generate(
                tmp, parsed, Collections.emptyMap(), null, r, 48, "Test pack");
        File zip = result.zip(null);
        assertNotNull(zip);
        assertTrue(zip.isFile());
        assertTrue(zip.length() > 0);
    }
}
