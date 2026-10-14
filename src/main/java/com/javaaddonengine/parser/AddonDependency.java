package com.javaaddonengine.parser;

/** A dependency entry inside manifest.json. */
public final class AddonDependency {
    private final String uuid;
    private final AddonVersion version;
    private final String moduleName;

    public AddonDependency(String uuid, AddonVersion version, String moduleName) {
        this.uuid = uuid == null ? "" : uuid;
        this.version = version == null ? new AddonVersion(0, 0, 0) : version;
        this.moduleName = moduleName == null ? "" : moduleName;
    }

    public String getUuid() { return uuid; }
    public AddonVersion getVersion() { return version; }
    public String getModuleName() { return moduleName; }
}
