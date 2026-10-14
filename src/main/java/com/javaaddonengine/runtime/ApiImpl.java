package com.javaaddonengine.runtime;

import com.javaaddonengine.api.ComponentAdapter;
import com.javaaddonengine.api.JavaAddonEngineAPI;
import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.entities.AddonEntitySpec;
import com.javaaddonengine.items.AddonItemSpec;
import com.javaaddonengine.translator.AdapterRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Public API implementation backed by the AddonManager registry. */
public final class ApiImpl implements JavaAddonEngineAPI {

    private final Map<String, Addon> addons = new ConcurrentHashMap<>();
    private final AdapterRegistry adapters;

    public ApiImpl(AdapterRegistry adapters) { this.adapters = adapters; }

    public void put(Addon addon) { addons.put(addon.getId(), addon); }
    public void remove(String id) { addons.remove(id); }
    public void clear() { addons.clear(); }
    public Map<String, Addon> registry() { return addons; }

    @Override
    public Addon getAddon(String id) { return addons.get(id); }

    /** Short alias used by the runtime. */
    public Addon get(String id) { return addons.get(id); }

    @Override
    public Collection<Addon> getAddons() { return Collections.unmodifiableCollection(addons.values()); }

    @Override
    public AddonEntitySpec getEntity(String addonId, String entityId) {
        Addon a = addons.get(addonId);
        return a == null ? null : a.getEntities().get(entityId);
    }

    @Override
    public AddonItemSpec getItem(String addonId, String itemId) {
        Addon a = addons.get(addonId);
        return a == null ? null : a.getItems().get(itemId);
    }

    @Override
    public AddonBlockSpec getBlock(String addonId, String blockId) {
        Addon a = addons.get(addonId);
        return a == null ? null : a.getBlocks().get(blockId);
    }

    @Override
    public void registerComponentAdapter(String componentId, ComponentAdapter adapter) {
        adapters.register(componentId, adapter);
    }

    @Override
    public void unregisterComponentAdapter(String componentId) {
        adapters.unregister(componentId);
    }

    @Override
    public double compatibilityScore(String addonId) {
        Addon a = addons.get(addonId);
        return a == null || a.getReport() == null ? 0.0 : a.getReport().score();
    }
}
