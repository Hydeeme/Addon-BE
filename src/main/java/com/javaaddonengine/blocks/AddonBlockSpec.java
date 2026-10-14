package com.javaaddonengine.blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converted block spec. Java cannot register arbitrary blocks via Bukkit, so rendering is a fallback mode. */
public final class AddonBlockSpec {
    public enum RenderMode { NATIVE, DISPLAY_ENTITY, ITEM_DISPLAY, RESOURCE_PACK, PACKET, FALLBACK }

    private final String id;
    private double hardness = 1.0;
    private double resistance = 1.0;
    private String material = "stone";
    private int light = 0;
    private String sound = "";
    private final List<String> states = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private String loot = "";
    private RenderMode renderMode = RenderMode.FALLBACK;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public AddonBlockSpec(String id) { this.id = id; }

    public String getId() { return id; }
    public double getHardness() { return hardness; }
    public void setHardness(double v) { this.hardness = v; }
    public double getResistance() { return resistance; }
    public void setResistance(double v) { this.resistance = v; }
    public String getMaterial() { return material; }
    public void setMaterial(String v) { this.material = v; }
    public int getLight() { return light; }
    public void setLight(int v) { this.light = v; }
    public String getSound() { return sound; }
    public void setSound(String v) { this.sound = v; }
    public List<String> getStates() { return states; }
    public List<String> getTags() { return tags; }
    public String getLoot() { return loot; }
    public void setLoot(String v) { this.loot = v; }
    public RenderMode getRenderMode() { return renderMode; }
    public void setRenderMode(RenderMode v) { this.renderMode = v; }
    public Map<String, Object> getExtra() { return extra; }
}
