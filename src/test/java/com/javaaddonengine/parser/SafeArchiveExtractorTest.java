package com.javaaddonengine.parser;

import com.javaaddonengine.core.AddonCompatibilityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SafeArchiveExtractorTest {

    private static File zipWith(File dir, String name, String entryName, byte[] data) throws Exception {
        File zip = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(data);
            zos.closeEntry();
        }
        return zip;
    }

    @Test
    void extractsValidArchive(@TempDir File tmp) throws Exception {
        File work = new File(tmp, "work");
        work.mkdirs();
        File archive = zipWith(tmp, "pack.mcaddon", "behavior_pack/manifest.json", "{}".getBytes(StandardCharsets.UTF_8));
        SafeArchiveExtractor.ExtractResult r =
                SafeArchiveExtractor.extract(archive, new File(work, "out"), 10_000_000, 1000);
        assertTrue(new File(r.directory, "behavior_pack/manifest.json").isFile());
    }

    @Test
    void rejectsZipSlip(@TempDir File tmp) {
        assertThrows(AddonCompatibilityException.class, () -> {
            File work = new File(tmp, "work");
            work.mkdirs();
            File archive = zipWith(tmp, "evil.mcaddon", "../evil.txt", "x".getBytes(StandardCharsets.UTF_8));
            SafeArchiveExtractor.extract(archive, new File(work, "out"), 10_000_000, 1000);
        });
    }

    @Test
    void rejectsAbsoluteEntry(@TempDir File tmp) {
        assertThrows(AddonCompatibilityException.class, () -> {
            File work = new File(tmp, "work");
            work.mkdirs();
            File archive = zipWith(tmp, "evil2.mcaddon", "/etc/evil.txt", "x".getBytes(StandardCharsets.UTF_8));
            SafeArchiveExtractor.extract(archive, new File(work, "out"), 10_000_000, 1000);
        });
    }

    @Test
    void rejectsWrongExtension(@TempDir File tmp) throws Exception {
        File f = new File(tmp, "evil.exe");
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(f, new File(tmp, "out"), 10_000_000, 1000));
    }

    @Test
    void enforcesFileCountLimit(@TempDir File tmp) throws Exception {
        File archive = new File(tmp, "many.mcaddon");
        try (FileOutputStream fos = new FileOutputStream(archive);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (int i = 0; i < 10; i++) {
                zos.putNextEntry(new ZipEntry("f" + i + ".txt"));
                zos.write("x".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(archive, new File(tmp, "out"), 10_000_000, 5));
    }

    @Test
    void enforcesSizeLimit(@TempDir File tmp) throws Exception {
        File archive = zipWith(tmp, "big.mcaddon", "data.bin", new byte[10_000]);
        assertThrows(AddonCompatibilityException.class, () ->
                SafeArchiveExtractor.extract(archive, new File(tmp, "out"), 100, 1000));
    }
}
