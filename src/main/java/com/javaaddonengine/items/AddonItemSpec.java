package com.javaaddonengine.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converted item spec persisted via PersistentDataContainer at runtime. */
public final class AddonItemSpec {
    private final String id;
    private String baseMaterial = "IRON_SWORD";
    private String displayName = "";
    private int maxStackSize = 64;
    private int durability = 0;
    private int damage = 0;
    private double cooldownSeconds = 0.0;
    private String rarity = "common";
    private boolean food = false;
    private int nutrition = 0;
    private float saturation = 0.0f;
    private String iconTexture = "";
    private int customModelData = 0;
    private final List<String> tags = new ArrayList<>();
    private final List<String> lore = new ArrayList<>();
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public AddonItemSpec(String id) { this.id = id; }

    public String getId() { return id; }
    public String getBaseMaterial() { return baseMaterial; }
    public void setBaseMaterial(String v) { this.baseMaterial = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public int getMaxStackSize() { return maxStackSize; }
    public void setMaxStackSize(int v) { this.maxStackSize = v; }
    public int getDurability() { return durability; }
    public void setDurability(int v) { this.durability = v; }
    public int getDamage() { return damage; }
    public void setDamage(int v) { this.damage = v; }
    public double getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(double v) { this.cooldownSeconds = v; }
    public String getRarity() { return rarity; }
    public void setRarity(String v) { this.rarity = v; }
    public boolean isFood() { return food; }
    public void setFood(boolean v) { this.food = v; }
    public int getNutrition() { return nutrition; }
    public void setNutrition(int v) { this.nutrition = v; }
    public float getSaturation() { return saturation; }
    public void setSaturation(float v) { this.saturation = v; }
    public String getIconTexture() { return iconTexture; }
    public void setIconTexture(String v) { this.iconTexture = v == null ? "" : v; }
    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int v) { this.customModelData = v; }
    public List<String> getTags() { return tags; }
    public List<String> getLore() { return lore; }
    public Map<String, Object> getExtra() { return extra; }
}
