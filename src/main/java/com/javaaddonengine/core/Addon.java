package com.javaaddonengine.core;

import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.commands.FunctionParser;
import com.javaaddonengine.entities.AddonEntitySpec;
import com.javaaddonengine.items.AddonItemSpec;
import com.javaaddonengine.npc.AddonNpc;
import com.javaaddonengine.parser.AddonManifest;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory model of one imported Bedrock Add-On and its converted specs. */
public final class Addon {
    private final String id;
    private String name;
    private String version;
    private final File directory;
    private AddonManifest behaviorManifest;
    private AddonManifest resourceManifest;
    private ParsedAddon parsed;
    private CompatibilityReport report;
    private AddonState state = AddonState.LOAD;
    private boolean enabled;
    private final Map<String, AddonEntitySpec> entities = new LinkedHashMap<>();
    private final Map<String, AddonItemSpec> items = new LinkedHashMap<>();
    private final Map<String, AddonBlockSpec> blocks = new LinkedHashMap<>();
    private final List<String> recipeKeys = new ArrayList<>();
    private final List<FunctionParser.ParsedFunction> functions = new ArrayList<>();
    private final Map<String, AddonNpc> npcs = new LinkedHashMap<>();

    public Addon(String id, File directory) {
        this.id = id;
        this.directory = directory;
        this.name = id;
        this.version = "1.0.0";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public File getDirectory() { return directory; }
    public AddonManifest getBehaviorManifest() { return behaviorManifest; }
    public void setBehaviorManifest(AddonManifest m) { this.behaviorManifest = m; }
    public AddonManifest getResourceManifest() { return resourceManifest; }
    public void setResourceManifest(AddonManifest m) { this.resourceManifest = m; }
    public ParsedAddon getParsed() { return parsed; }
    public void setParsed(ParsedAddon p) { this.parsed = p; }
    public CompatibilityReport getReport() { return report; }
    public void setReport(CompatibilityReport r) { this.report = r; }
    public AddonState getState() { return state; }
    public void setState(AddonState s) { this.state = s; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public Map<String, AddonEntitySpec> getEntities() { return entities; }
    public Map<String, AddonItemSpec> getItems() { return items; }
    public Map<String, AddonBlockSpec> getBlocks() { return blocks; }
    /** Bukkit recipe keys ("namespace:key") registered at enable time; unregistered at disable. */
    public List<String> getRecipeKeys() { return recipeKeys; }
    /** Converted .mcfunction scripts, executed via FunctionDispatcher. */
    public List<FunctionParser.ParsedFunction> getFunctions() { return functions; }
    /** Converted NPC definitions, spawned via NpcManager. */
    public Map<String, AddonNpc> getNpcs() { return npcs; }
}
