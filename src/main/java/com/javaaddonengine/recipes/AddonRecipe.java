package com.javaaddonengine.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalized recipe: Bedrock JSON -> this -> Bukkit recipe registration. */
public final class AddonRecipe {
    public enum Type { SHAPED, SHAPELESS, FURNACE, SMITHING, STONECUTTER, UNKNOWN }

    private final String id;
    private final Type type;
    private String result = "";
    private int count = 1;
    private final List<String> shape = new ArrayList<>();
    private final Map<String, String> key = new LinkedHashMap<>();
    private final List<String> ingredients = new ArrayList<>();
    private String ingredient = "";
    private String extra = "";

    public AddonRecipe(String id, Type type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getResult() { return result; }
    public void setResult(String v) { this.result = v; }
    public int getCount() { return count; }
    public void setCount(int v) { this.count = v; }
    public List<String> getShape() { return shape; }
    public Map<String, String> getKey() { return key; }
    public List<String> getIngredients() { return ingredients; }
    public String getIngredient() { return ingredient; }
    public void setIngredient(String v) { this.ingredient = v; }
    public String getExtra() { return extra; }
    public void setExtra(String v) { this.extra = v; }
}
