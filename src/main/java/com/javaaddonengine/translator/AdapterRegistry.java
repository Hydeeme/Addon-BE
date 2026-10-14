package com.javaaddonengine.translator;

import com.javaaddonengine.api.ComponentAdapter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Third-party adapters can override/extend component classification. */
public final class AdapterRegistry {
    private final Map<String, ComponentAdapter> adapters = new ConcurrentHashMap<>();

    public void register(String componentId, ComponentAdapter adapter) {
        adapters.put(componentId.toLowerCase(), adapter);
    }

    public void unregister(String componentId) {
        adapters.remove(componentId.toLowerCase());
    }

    public ComponentAdapter get(String componentId) {
        return adapters.get(componentId.toLowerCase());
    }

    public int size() { return adapters.size(); }
}
