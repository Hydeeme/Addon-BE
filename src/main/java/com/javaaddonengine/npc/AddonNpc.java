package com.javaaddonengine.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NPC definition converted from Bedrock dialogue/npc concepts. */
public final class AddonNpc {
    private final String id;
    private String name = "";
    private String skin = "";
    private final List<String> dialogue = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private final Map<String, Object> shop = new LinkedHashMap<>();
    private String permission = "";
    private int cooldownSeconds = 0;

    public AddonNpc(String id) { this.id = id; }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSkin() { return skin; }
    public void setSkin(String v) { this.skin = v; }
    public List<String> getDialogue() { return dialogue; }
    public List<String> getCommands() { return commands; }
    public Map<String, Object> getShop() { return shop; }
    public String getPermission() { return permission; }
    public void setPermission(String v) { this.permission = v; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int v) { this.cooldownSeconds = v; }
}
