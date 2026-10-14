package com.javaaddonengine.api;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.translator.NormalizedComponent;

/** SPI for third-party component adapters. */
public interface ComponentAdapter {
    String componentId();
    CompatibilityEntry classify(NormalizedComponent component, String file);
}
