package com.javaaddonengine;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.recipes.AddonRecipe;
import com.javaaddonengine.recipes.RecipeParser;
import com.javaaddonengine.translator.ComponentRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ReportAndRegistryTest {

    @Test
    public void scoreWeightsAndGrades() {
        CompatibilityReport r = new CompatibilityReport("x", "X", "1.0.0");
        assertEquals(0.0, r.score());
        r.add("Entities", new CompatibilityEntry("minecraft:health", CompatibilityLevel.FULL, "ok"));
        r.add("Entities", new CompatibilityEntry("minecraft:behavior.melee_attack", CompatibilityLevel.EMULATED, "ok"));
        r.add("Entities", new CompatibilityEntry("minecraft:collision_box", CompatibilityLevel.PARTIAL, "ok"));
        r.add("Entities", new CompatibilityEntry("custom:thing", CompatibilityLevel.UNSUPPORTED, "no"));
        // (1.0 + 0.8 + 0.6 + 0.0) / 4 = 0.6 -> 60% -> FAIR
        assertEquals(60.0, r.score(), 1e-9);
        assertEquals("FAIR", r.grade());
        assertTrue(r.toText().contains("Compatibility Report"));
        assertTrue(r.toJson().contains("\"grade\""));
        assertTrue(r.toHtml().contains("<table"));
    }

    @Test
    public void registryClassificationHonest() {
        CompatibilityEntry full = ComponentRegistry.classify("Entities", "minecraft:health", "f.json");
        assertEquals(CompatibilityLevel.FULL, full.getLevel());
        CompatibilityEntry emulated = ComponentRegistry.classify("Entities", "minecraft:behavior.melee_attack", "f.json");
        assertEquals(CompatibilityLevel.EMULATED, emulated.getLevel());
        CompatibilityEntry genericBehavior = ComponentRegistry.classify("Entities", "minecraft:behavior.some_future_ai", "f.json");
        assertEquals(CompatibilityLevel.EMULATED, genericBehavior.getLevel());
        CompatibilityEntry knownPartial = ComponentRegistry.classify("Entities", "minecraft:some_new_thing", "f.json");
        assertEquals(CompatibilityLevel.PARTIAL, knownPartial.getLevel());
        CompatibilityEntry unknown = ComponentRegistry.classify("Entities", "custom:thing", "f.json");
        assertEquals(CompatibilityLevel.UNSUPPORTED, unknown.getLevel());
    }

    @Test
    public void recipeTypeMapping() {
        assertEquals(AddonRecipe.Type.SHAPED, RecipeParser.mapType("minecraft:recipe_shaped"));
        assertEquals(AddonRecipe.Type.SHAPELESS, RecipeParser.mapType("minecraft:recipe_shapeless"));
        assertEquals(AddonRecipe.Type.FURNACE, RecipeParser.mapType("minecraft:recipe_furnace"));
        assertEquals(AddonRecipe.Type.SMITHING, RecipeParser.mapType("minecraft:recipe_smithing_transform"));
        assertEquals(AddonRecipe.Type.UNKNOWN, RecipeParser.mapType("minecraft:recipe_brewing_mix"));
    }
}
