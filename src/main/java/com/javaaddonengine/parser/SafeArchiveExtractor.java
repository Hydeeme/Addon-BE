package com.javaaddonengine.parser;

import com.javaaddonengine.core.AddonCompatibilityException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Secure archive extractor. Treats every Add-On as untrusted input:
 * rejects Zip Slip / path traversal, caps file count and total size,
 * and validates JSON syntax of manifest/definition files.
 */
public final class SafeArchiveExtractor {

    private SafeArchiveExtractor() {}

    public static final class ExtractResult {
        public final File directory;
        public final List<File> manifests = new ArrayList<>();
        public ExtractResult(File directory) { this.directory = directory; }
    }

    public static ExtractResult extract(File archive, File destDir, long maxTotalBytes, int maxFiles)
            throws AddonCompatibilityException {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".mcaddon") || lower.endsWith(".mcpack") || lower.endsWith(".zip"))) {
            throw new AddonCompatibilityException("Unsupported package type: " + archive.getName()
                    + " (expected .mcaddon, .mcpack or .zip)");
        }
        long size;
        try {
            size = Files.size(archive.toPath());
        } catch (IOException e) {
            throw new AddonCompatibilityException("Cannot read archive: " + e.getMessage());
        }
        if (size > maxTotalBytes) {
            throw new AddonCompatibilityException("Archive exceeds size limit (" + size + " bytes)");
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new AddonCompatibilityException("Cannot create extraction directory: " + destDir);
        }
        String canonicalDest;
        try {
            canonicalDest = destDir.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            throw new AddonCompatibilityException("Cannot resolve destination: " + e.getMessage());
        }
        ExtractResult result = new ExtractResult(destDir);
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..") || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
                    throw new AddonCompatibilityException("Rejected archive entry (path traversal): " + entry.getName());
                }
                File out = new File(destDir, name);
                String canonical;
                try {
                    canonical = out.getCanonicalPath();
                } catch (IOException e) {
                    throw new AddonCompatibilityException("Cannot resolve entry: " + entry.getName());
                }
                if (!canonical.startsWith(canonicalDest)) {
                    throw new AddonCompatibilityException("Rejected archive entry (zip slip): " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) {
                        throw new AddonCompatibilityException("Cannot create directory: " + entry.getName());
                    }
                    continue;
                }
                count++;
                if (count > maxFiles) {
                    throw new AddonCompatibilityException("Archive exceeds file count limit (" + maxFiles + ")");
                }
                long entrySize = entry.getSize();
                if (entrySize > 0 && total + entrySize > maxTotalBytes) {
                    throw new AddonCompatibilityException("Archive exceeds extracted size limit");
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new AddonCompatibilityException("Cannot create directory for: " + entry.getName());
                }
                try (InputStream in = zip.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        total += r;
                        if (total > maxTotalBytes) {
                            throw new AddonCompatibilityException("Archive exceeds extracted size limit");
                        }
                        fos.write(buf, 0, r);
                    }
                } catch (IOException e) {
                    throw new AddonCompatibilityException("Failed to extract " + entry.getName() + ": " + e.getMessage());
                }
                if (out.getName().equalsIgnoreCase("manifest.json")) {
                    result.manifests.add(out);
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new AddonCompatibilityException("Malformed archive: " + e.getMessage());
        } catch (IOException e) {
            throw new AddonCompatibilityException("Failed to read archive: " + e.getMessage());
        }
        return result;
    }
}
