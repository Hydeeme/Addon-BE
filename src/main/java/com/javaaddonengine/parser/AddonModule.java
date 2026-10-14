package com.javaaddonengine.parser;

/** A module entry inside manifest.json. */
public final class AddonModule {
    private final String type;
    private final String uuid;
    private final AddonVersion version;

    public AddonModule(String type, String uuid, AddonVersion version) {
        this.type = type == null ? "" : type;
        this.uuid = uuid == null ? "" : uuid;
        this.version = version == null ? new AddonVersion(0, 0, 0) : version;
    }

    public String getType() { return type; }
    public String getUuid() { return uuid; }
    public AddonVersion getVersion() { return version; }
}
