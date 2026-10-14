package com.javaaddonengine;

import com.javaaddonengine.core.AddonCompatibilityException;
import com.javaaddonengine.parser.SafeArchiveExtractor;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class SafeExtractorTest {

    @TempDir
    File temp;

    private File zip(String name, String... entries) throws Exception {
        File f = new File(temp, name);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write("{\"header\": {}}".getBytes());
                z.closeEntry();
            }
        }
        return f;
    }

    @Test
    public void extractsValidArchive() throws Exception {
        File archive = zip("good.mcaddon", "behavior_pack/manifest.json", "behavior_pack/entities/robot.json");
        SafeArchiveExtractor.ExtractResult r =
                SafeArchiveExtractor.extract(archive, new File(temp, "out1"), 10_000_000, 100);
        assertEquals(1, r.manifests.size());
        assertTrue(new File(temp, "out1/behavior_pack/entities/robot.json").isFile());
    }

    @Test
    public void rejectsZipSlip() throws Exception {
        File archive = zip("evil.mcaddon", "../evil.json");
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(archive, new File(temp, "out2"), 10_000_000, 100));
    }

    @Test
    public void rejectsAbsolutePath() throws Exception {
        File archive = zip("evil2.mcaddon", "/tmp/evil.json");
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(archive, new File(temp, "out3"), 10_000_000, 100));
    }

    @Test
    public void enforcesFileCountLimit() throws Exception {
        File archive = zip("many.mcaddon", "a.json", "b.json", "c.json");
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(archive, new File(temp, "out4"), 10_000_000, 2));
    }

    @Test
    public void rejectsWrongExtension() {
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(new File(temp, "x.txt"), new File(temp, "out5"), 10_000_000, 100));
    }
}
