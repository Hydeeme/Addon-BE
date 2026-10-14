package com.javaaddonengine.version;

/** Version abstraction: all NMS-adjacent code lives behind this interface. */
public interface VersionAdapter {
    String minecraftVersion();
    boolean supportsScaleAttribute();
    boolean supportsDisplayEntities();
    int defaultPackFormat();
}
