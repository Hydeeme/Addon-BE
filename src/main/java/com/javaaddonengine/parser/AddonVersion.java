package com.javaaddonengine.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Version triple used in manifests and dependencies. */
public final class AddonVersion {
    private final int major;
    private final int minor;
    private final int patch;

    public AddonVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static AddonVersion parse(Object o) {
        if (o instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) o;
            return new AddonVersion(num(l, 0), num(l, 1), num(l, 2));
        }
        if (o instanceof String) {
            String[] p = ((String) o).split("\\.");
            return new AddonVersion(n(p, 0), n(p, 1), n(p, 2));
        }
        return new AddonVersion(0, 0, 0);
    }

    private static int num(java.util.List<?> l, int i) {
        if (i >= l.size()) return 0;
        Object v = l.get(i);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return 0; }
    }

    private static int n(String[] p, int i) {
        if (i >= p.length) return 0;
        try { return Integer.parseInt(p[i].trim()); } catch (NumberFormatException e) { return 0; }
    }

    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }

    public boolean atLeast(AddonVersion other) {
        if (major != other.major) return major > other.major;
        if (minor != other.minor) return minor > other.minor;
        return patch >= other.patch;
    }

    @Override
    public String toString() { return major + "." + minor + "." + patch; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AddonVersion)) return false;
        AddonVersion v = (AddonVersion) o;
        return major == v.major && minor == v.minor && patch == v.patch;
    }

    @Override
    public int hashCode() { return Objects.hash(major, minor, patch); }
}
