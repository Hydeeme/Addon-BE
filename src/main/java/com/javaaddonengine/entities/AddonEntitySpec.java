package com.javaaddonengine.entities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converted entity spec: identifier + translated attributes + behavior notes + raw leftovers. */
public final class AddonEntitySpec {
    private final String id;
    private String baseVanilla = "ZOMBIE";
    private double health = 20.0;
    private double movementSpeed = 0.25;
    private double attackDamage = 2.0;
    private double scale = 1.0;
    private double followRange = 16.0;
    private double knockbackResistance = 0.0;
    private int experience = 0;
    private String lootTable = "";
    private String geometry = "";
    private final List<String> typeFamilies = new ArrayList<>();
    private final Map<String, Object> extra = new LinkedHashMap<>();
    private final List<String> behaviors = new ArrayList<>();

    public AddonEntitySpec(String id) { this.id = id; }

    public String getId() { return id; }
    public String getBaseVanilla() { return baseVanilla; }
    public void setBaseVanilla(String v) { this.baseVanilla = v; }
    public double getHealth() { return health; }
    public void setHealth(double v) { this.health = v; }
    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double v) { this.movementSpeed = v; }
    public double getAttackDamage() { return attackDamage; }
    public void setAttackDamage(double v) { this.attackDamage = v; }
    public double getScale() { return scale; }
    public void setScale(double v) { this.scale = v; }
    public double getFollowRange() { return followRange; }
    public void setFollowRange(double v) { this.followRange = v; }
    public double getKnockbackResistance() { return knockbackResistance; }
    public void setKnockbackResistance(double v) { this.knockbackResistance = v; }
    public int getExperience() { return experience; }
    public void setExperience(int v) { this.experience = v; }
    public String getLootTable() { return lootTable; }
    public void setLootTable(String v) { this.lootTable = v; }
    public String getGeometry() { return geometry; }
    public void setGeometry(String v) { this.geometry = v; }
    public List<String> getTypeFamilies() { return typeFamilies; }
    public List<String> getBehaviors() { return behaviors; }
    public Map<String, Object> getExtra() { return extra; }
}
