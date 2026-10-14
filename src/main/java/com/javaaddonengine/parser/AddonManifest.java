package com.javaaddonengine.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.javaaddonengine.core.AddonCompatibilityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Parsed manifest.json with validation of UUIDs, modules and dependencies. */
public final class AddonManifest {
    private final int formatVersion;
    private final String name;
    private final String description;
    private final String uuid;
    private final AddonVersion version;
    private final AddonVersion minEngineVersion;
    private final List<AddonModule> modules;
    private final List<AddonDependency> dependencies;

    private AddonManifest(int formatVersion, String name, String description, String uuid,
                          AddonVersion version, AddonVersion minEngineVersion,
                          List<AddonModule> modules, List<AddonDependency> dependencies) {
        this.formatVersion = formatVersion;
        this.name = name;
        this.description = description;
        this.uuid = uuid;
        this.version = version;
        this.minEngineVersion = minEngineVersion;
        this.modules = modules;
        this.dependencies = dependencies;
    }

    public static AddonManifest parse(String json, String source) throws AddonCompatibilityException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new AddonCompatibilityException("Invalid manifest JSON in " + source + ": " + e.getMessage(), "", source, -1);
        }
        int format = 1;
        if (root.has("format_version")) {
            try { format = root.get("format_version").getAsInt(); }
            catch (Exception e) { format = 2; }
        }
        JsonObject header = root.has("header") && root.get("header").isJsonObject()
                ? root.getAsJsonObject("header") : new JsonObject();
        String name = str(header, "name", "Unnamed Pack");
        String desc = str(header, "description", "");
        String uuid = str(header, "uuid", "");
        if (!uuid.isEmpty()) validateUuid(uuid, source);
        AddonVersion version = AddonVersion.parse(gsonRaw(header.get("version")));
        AddonVersion minEngine = AddonVersion.parse(gsonRaw(header.get("min_engine_version")));

        List<AddonModule> modules = new ArrayList<>();
        if (root.has("modules") && root.get("modules").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("modules")) {
                if (!el.isJsonObject()) continue;
                JsonObject m = el.getAsJsonObject();
                String type = str(m, "type", "");
                String muuid = str(m, "uuid", "");
                if (!muuid.isEmpty()) validateUuid(muuid, source);
                AddonVersion mv = AddonVersion.parse(gsonRaw(m.get("version")));
                modules.add(new AddonModule(type, muuid, mv));
            }
        }
        List<AddonDependency> deps = new ArrayList<>();
        if (root.has("dependencies") && root.get("dependencies").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("dependencies")) {
                if (!el.isJsonObject()) continue;
                JsonObject d = el.getAsJsonObject();
                String duuid = str(d, "uuid", "");
                if (!duuid.isEmpty()) validateUuid(duuid, source);
                AddonVersion dv = AddonVersion.parse(gsonRaw(d.get("version")));
                deps.add(new AddonDependency(duuid, dv, str(d, "module_name", "")));
            }
        }
        return new AddonManifest(format, name, desc, uuid, version, minEngine, modules, deps);
    }

    private static void validateUuid(String uuid, String source) throws AddonCompatibilityException {
        try {
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new AddonCompatibilityException("Invalid UUID in manifest: " + uuid, "", source, -1);
        }
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static Object gsonRaw(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (JsonElement e : (JsonArray) el) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) l.add(e.getAsInt());
                else l.add(e.getAsString());
            }
            return l;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return java.util.Collections.singletonList(el.getAsInt());
        }
        return el.getAsString();
    }

    public boolean isBehaviorPack() {
        for (AddonModule m : modules) {
            String t = m.getType().toLowerCase();
            if (t.contains("data") || t.contains("script")) return true;
        }
        return false;
    }

    public boolean isResourcePack() {
        for (AddonModule m : modules) {
            if (m.getType().equalsIgnoreCase("resources")) return true;
        }
        return false;
    }

    public boolean hasScriptModule() {
        for (AddonModule m : modules) {
            if (m.getType().equalsIgnoreCase("script")) return true;
        }
        return false;
    }

    public int getFormatVersion() { return formatVersion; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUuid() { return uuid; }
    public AddonVersion getVersion() { return version; }
    public AddonVersion getMinEngineVersion() { return minEngineVersion; }
    public List<AddonModule> getModules() { return new ArrayList<>(modules); }
    public List<AddonDependency> getDependencies() { return new ArrayList<>(dependencies); }
}
