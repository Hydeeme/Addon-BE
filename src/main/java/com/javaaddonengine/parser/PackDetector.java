package com.javaaddonengine.parser;

import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.AddonCompatibilityException;
import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Detects behavior vs resource packs, parses manifests, resolves dependencies. */
public final class PackDetector {

    private PackDetector() {}

    public static final class Detection {
        public File behaviorDir;
        public AddonManifest behaviorManifest;
        public File resourceDir;
        public AddonManifest resourceManifest;
        public final List<AddonManifest> extraManifests = new ArrayList<>();
    }

    public static Detection detect(File extractedRoot, CompatibilityReport report) {
        Detection d = new Detection();
        List<File> manifests = findManifests(extractedRoot, 0);
        for (File mf : manifests) {
            String json;
            try {
                json = new String(Files.readAllBytes(mf.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                report.add("Manifest", new CompatibilityEntry("manifest.json",
                        CompatibilityLevel.UNSUPPORTED, "Unreadable manifest: " + e.getMessage(),
                        rel(extractedRoot, mf), -1));
                continue;
            }
            AddonManifest m;
            try {
                m = AddonManifest.parse(json, rel(extractedRoot, mf));
            } catch (AddonCompatibilityException e) {
                report.add("Manifest", new CompatibilityEntry("manifest.json",
                        CompatibilityLevel.UNSUPPORTED, e.getMessage(), rel(extractedRoot, mf), -1));
                continue;
            }
            File packDir = mf.getParentFile();
            boolean behavior = m.isBehaviorPack() || containsDir(packDir, "entities")
                    || containsDir(packDir, "items") || containsDir(packDir, "functions")
                    || containsDir(packDir, "scripts");
            boolean resource = m.isResourcePack() || containsDir(packDir, "textures")
                    || containsDir(packDir, "models") || containsDir(packDir, "sounds");
            checkDependencies(m, report);
            if (behavior && d.behaviorManifest == null) {
                d.behaviorManifest = m;
                d.behaviorDir = packDir;
            } else if (resource && d.resourceManifest == null) {
                d.resourceManifest = m;
                d.resourceDir = packDir;
            } else {
                d.extraManifests.add(m);
            }
        }
        // Nested pack layout: root contains behavior_pack/ and resource_pack/ dirs without root manifest.
        if (d.behaviorManifest == null) {
            File bp = new File(extractedRoot, "behavior_pack");
            File bpf = new File(bp, "manifest.json");
            if (bpf.isFile()) {
                try {
                    d.behaviorManifest = AddonManifest.parse(
                            new String(Files.readAllBytes(bpf.toPath()), StandardCharsets.UTF_8), "behavior_pack/manifest.json");
                    d.behaviorDir = bp;
                } catch (Exception ignored) {}
            }
        }
        if (d.resourceManifest == null) {
            File rp = new File(extractedRoot, "resource_pack");
            File rpf = new File(rp, "manifest.json");
            if (rpf.isFile()) {
                try {
                    d.resourceManifest = AddonManifest.parse(
                            new String(Files.readAllBytes(rpf.toPath()), StandardCharsets.UTF_8), "resource_pack/manifest.json");
                    d.resourceDir = rp;
                } catch (Exception ignored) {}
            }
        }
        if (d.behaviorManifest == null && d.resourceManifest == null && manifests.isEmpty()) {
            report.add("Manifest", new CompatibilityEntry("manifest.json",
                    CompatibilityLevel.UNSUPPORTED, "No manifest.json found in package", "", -1));
        }
        return d;
    }

    public static void applyToAddon(Addon addon, Detection d, CompatibilityReport report) {
        if (d.behaviorManifest != null) {
            addon.setBehaviorManifest(d.behaviorManifest);
            addon.setName(d.behaviorManifest.getName());
            addon.setVersion(d.behaviorManifest.getVersion().toString());
            report.add("Manifest", new CompatibilityEntry("manifest.json", CompatibilityLevel.FULL,
                    "Behavior pack: " + d.behaviorManifest.getName() + " v" + d.behaviorManifest.getVersion(), "", -1));
        }
        if (d.resourceManifest != null) {
            addon.setResourceManifest(d.resourceManifest);
            if (d.behaviorManifest == null) {
                addon.setName(d.resourceManifest.getName());
                addon.setVersion(d.resourceManifest.getVersion().toString());
            }
            report.add("Manifest", new CompatibilityEntry("manifest.json", CompatibilityLevel.FULL,
                    "Resource pack: " + d.resourceManifest.getName() + " v" + d.resourceManifest.getVersion(), "", -1));
        }
    }

    private static void checkDependencies(AddonManifest m, CompatibilityReport report) {
        for (AddonDependency dep : m.getDependencies()) {
            String label = dep.getUuid().isEmpty() ? dep.getModuleName()
                    : (dep.getUuid() + " v" + dep.getVersion());
            report.addMissingDependency(label);
            report.add("Dependencies", new CompatibilityEntry("dependency", CompatibilityLevel.PARTIAL,
                    "Unresolved dependency (no pack registry on Java): " + label, "", -1));
        }
    }

    private static List<File> findManifests(File dir, int depth) {
        List<File> out = new ArrayList<>();
        if (depth > 4) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isDirectory()) out.addAll(findManifests(f, depth + 1));
            else if (f.getName().equalsIgnoreCase("manifest.json")) out.add(f);
        }
        return out;
    }

    private static boolean containsDir(File dir, String name) {
        if (dir == null) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) if (f.isDirectory() && f.getName().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static String rel(File root, File f) {
        String r = root.toURI().relativize(f.toURI()).getPath();
        return r.isEmpty() ? f.getName() : r;
    }
}
