package com.javaaddonengine.integrations;

import com.javaaddonengine.core.JaLogger;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/**
 * Best-effort ModelEngine bridge via reflection only — never a hard dependency.
 *
 * <p>When ModelEngine is installed and exposes a compatible API, entity geometry
 * references stored on converted specs are applied as real ModelEngine models.
 * Otherwise (or on any reflection failure) the caller keeps its display-entity
 * fallback. Every failure degrades silently to {@code false} plus one debug log.
 */
public final class ModelEngineBridge {

    private final Plugin plugin;
    private final JaLogger log;
    private final AtomicBoolean warned = new AtomicBoolean(false);
    private volatile Boolean available;

    public ModelEngineBridge(Plugin plugin, JaLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        boolean ok = Bukkit.getPluginManager().getPlugin("ModelEngine") != null && probe();
        available = ok;
        return ok;
    }

    /** Apply a Bedrock geometry id as a ModelEngine model. Returns false on any failure. */
    public boolean applyModel(LivingEntity entity, String geometryId) {
        if (geometryId == null || geometryId.isEmpty()) return false;
        if (!isAvailable()) return false;
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            Object manager = apiClass.getMethod("getModelManager").invoke(api);
            String modelId = modelIdOf(geometryId);
            Object active = createActiveModel(manager, modelId);
            if (active == null) return false;
            Object modeled = manager.getClass().getMethod("createModeledEntity", LivingEntity.class)
                    .invoke(manager, entity);
            Method addModel = findAddModel(modeled.getClass());
            if (addModel == null) return false;
            addModel.invoke(modeled, active, true);
            return true;
        } catch (Throwable t) {
            warnOnce("ModelEngine apply failed for " + geometryId + ": " + t.getMessage());
            return false;
        }
    }

    private boolean probe() {
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            apiClass.getMethod("getModelManager").invoke(api);
            return true;
        } catch (Throwable t) {
            warnOnce("ModelEngine API probe failed (" + t.getMessage() + "); using display fallback.");
            return false;
        }
    }

    private static Object createActiveModel(Object manager, String modelId) {
        // ModelEngine 3/4 both expose createActiveModel(String) on the model manager.
        try {
            return manager.getClass().getMethod("createActiveModel", String.class)
                    .invoke(manager, modelId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findAddModel(Class<?> modeledClass) {
        for (Method m : modeledClass.getMethods()) {
            if (!m.getName().equals("addModel") || m.getParameterCount() != 2) continue;
            if (m.getParameterTypes()[1] == boolean.class) return m;
        }
        return null;
    }

    private static String modelIdOf(String geometryId) {
        String s = geometryId.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.startsWith("geometry.")) s = s.substring("geometry.".length());
        return s.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private void warnOnce(String msg) {
        if (warned.compareAndSet(false, true)) log.debug(msg);
    }
}
