package com.javaaddonengine.translator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized intermediate representation: Bedrock JSON -> NormalizedComponent -> JavaAdapter. */
public final class NormalizedComponent {
    private final String bedrockId;
    private final JsonObject raw;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public NormalizedComponent(String bedrockId, JsonObject raw) {
        this.bedrockId = bedrockId;
        this.raw = raw == null ? new JsonObject() : raw;
        if (raw != null) {
            for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive()) {
                    if (v.getAsJsonPrimitive().isNumber()) properties.put(e.getKey(), v.getAsDouble());
                    else if (v.getAsJsonPrimitive().isBoolean()) properties.put(e.getKey(), v.getAsBoolean());
                    else properties.put(e.getKey(), v.getAsString());
                }
            }
        }
    }

    public String getBedrockId() { return bedrockId; }
    public JsonObject getRaw() { return raw; }

    public double num(String key, double def) {
        Object v = properties.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }

    public String str(String key, String def) {
        Object v = properties.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public boolean bool(String key, boolean def) {
        Object v = properties.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }

    public void set(String key, Object value) { properties.put(key, value); }
    public Map<String, Object> getProperties() { return new LinkedHashMap<>(properties); }
}
