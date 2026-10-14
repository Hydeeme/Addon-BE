package com.javaaddonengine.api;

import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.entities.AddonEntitySpec;
import com.javaaddonengine.items.AddonItemSpec;
import java.util.Collection;

/** Public API for third-party plugins. Obtain via JavaAddonEngine.getAPI(). */
public interface JavaAddonEngineAPI {
    Addon getAddon(String id);
    Collection<Addon> getAddons();
    AddonEntitySpec getEntity(String addonId, String entityId);
    AddonItemSpec getItem(String addonId, String itemId);
    AddonBlockSpec getBlock(String addonId, String blockId);
    void registerComponentAdapter(String componentId, ComponentAdapter adapter);
    void unregisterComponentAdapter(String componentId);
    double compatibilityScore(String addonId);
}
