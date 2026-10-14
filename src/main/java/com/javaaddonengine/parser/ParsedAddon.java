package com.javaaddonengine.parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.javaaddonengine.core.AddonCompatibilityException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Raw scan of an extracted Add-On directory: counts and file lists per subsystem. */
public final class ParsedAddon {
    private final File root;
    private final List<File> entityFiles = new ArrayList<>();
    private final List<File> itemFiles = new ArrayList<>();
    private final List<File> blockFiles = new ArrayList<>();
    private final List<File> recipeFiles = new ArrayList<>();
    private final List<File> lootFiles = new ArrayList<>();
    private final List<File> functionFiles = new ArrayList<>();
    private final List<File> animationFiles = new ArrayList<>();
    private final List<File> scriptFiles = new ArrayList<>();
    private final List<File> particleFiles = new ArrayList<>();
    private final List<File> dialogueFiles = new ArrayList<>();
    private final List<File> soundFiles = new ArrayList<>();
    private final List<File> textureFiles = new ArrayList<>();

    public ParsedAddon(File root) { this.root = root; }

    public static ParsedAddon scan(File root) {
        ParsedAddon p = new ParsedAddon(root);
        p.walk(root);
        return p;
    }

    private void walk(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { walk(f); continue; }
            String path = f.getAbsolutePath().replace('\\', '/').toLowerCase();
            String name = f.getName().toLowerCase();
            if (name.endsWith(".mcfunction") || path.contains("/functions/") && name.endsWith(".mcfunction")) {
                functionFiles.add(f);
            } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                if (path.contains("/scripts/")) scriptFiles.add(f);
            } else if (name.endsWith(".json")) {
                if (path.contains("/entities/")) entityFiles.add(f);
                else if (path.contains("/items/") || path.contains("/item/")) itemFiles.add(f);
                else if (path.contains("/blocks/")) blockFiles.add(f);
                else if (path.contains("/recipes/")) recipeFiles.add(f);
                else if (path.contains("/loot_tables/")) lootFiles.add(f);
                else if (path.contains("/animations/") || path.contains("/animation_controllers/")) animationFiles.add(f);
                else if (path.contains("/particles/")) particleFiles.add(f);
                else if (path.contains("/dialogue/") || path.contains("/dialogues/")
                        || path.contains("/npcs/") || path.contains("/npc/")) dialogueFiles.add(f);
                else if (name.equals("sounds.json") || name.endsWith(".sound.json") || path.contains("/sounds/")) soundFiles.add(f);
            } else if (name.endsWith(".png") || name.endsWith(".tga") || name.endsWith(".fsb") || name.endsWith(".ogg")) {
                if (path.contains("texture") || name.endsWith(".png") || name.endsWith(".tga")) textureFiles.add(f);
                else soundFiles.add(f);
            }
        }
    }

    /** Validate JSON syntax of every collected definition file. Returns list of broken files. */
    public List<String> validateJson() {
        List<String> broken = new ArrayList<>();
        List<File> all = new ArrayList<>();
        all.addAll(entityFiles); all.addAll(itemFiles); all.addAll(blockFiles);
        all.addAll(recipeFiles); all.addAll(lootFiles); all.addAll(animationFiles); all.addAll(particleFiles);
        all.addAll(dialogueFiles);
        for (File f : all) {
            try {
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                JsonParser.parseString(text);
            } catch (Exception e) {
                broken.add(rel(f) + ": " + e.getMessage());
            }
        }
        return broken;
    }

    public static JsonObject readJson(File f) throws AddonCompatibilityException {
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            throw new AddonCompatibilityException("Invalid JSON in " + f.getName() + ": " + e.getMessage(), "", f.getName(), -1);
        }
    }

    private String rel(File f) {
        String r = root.toURI().relativize(f.toURI()).getPath();
        return r.isEmpty() ? f.getName() : r;
    }

    public File getRoot() { return root; }
    public List<File> getEntityFiles() { return new ArrayList<>(entityFiles); }
    public List<File> getItemFiles() { return new ArrayList<>(itemFiles); }
    public List<File> getBlockFiles() { return new ArrayList<>(blockFiles); }
    public List<File> getRecipeFiles() { return new ArrayList<>(recipeFiles); }
    public List<File> getLootFiles() { return new ArrayList<>(lootFiles); }
    public List<File> getFunctionFiles() { return new ArrayList<>(functionFiles); }
    public List<File> getAnimationFiles() { return new ArrayList<>(animationFiles); }
    public List<File> getScriptFiles() { return new ArrayList<>(scriptFiles); }
    public List<File> getParticleFiles() { return new ArrayList<>(particleFiles); }
    public List<File> getDialogueFiles() { return new ArrayList<>(dialogueFiles); }
    public List<File> getSoundFiles() { return new ArrayList<>(soundFiles); }
    public List<File> getTextureFiles() { return new ArrayList<>(textureFiles); }
}
