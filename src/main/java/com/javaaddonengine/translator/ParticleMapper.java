package com.javaaddonengine.translator;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Maps Bedrock particle ids to Bukkit Particle enum names. */
public final class ParticleMapper {

    private ParticleMapper() {}

    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "minecraft:explosion", "minecraft:smoke", "minecraft:flame", "minecraft:lava",
            "minecraft:water", "minecraft:crit", "minecraft:magic", "minecraft:heart",
            "minecraft:note", "minecraft:portal", "minecraft:redstone", "minecraft:snowball",
            "minecraft:slime", "minecraft:huge_explosion", "minecraft:fireworks",
            "minecraft:dragon_breath", "minecraft:totem", "minecraft:soul"));

    public static String toBukkit(String bedrockId) {
        if (bedrockId == null) return null;
        String b = bedrockId.toLowerCase(Locale.ROOT);
        if (!KNOWN.contains(b)) return null;
        // mostly identical except a few renames
        if (b.equals("minecraft:smoke")) return "SMOKE";
        if (b.equals("minecraft:flame")) return "FLAME";
        if (b.equals("minecraft:crit")) return "CRIT";
        if (b.equals("minecraft:magic")) return "WITCH";
        if (b.equals("minecraft:note")) return "NOTE";
        if (b.equals("minecraft:portal")) return "PORTAL";
        if (b.equals("minecraft:redstone")) return "DUST";
        if (b.equals("minecraft:totem")) return "TOTEM_OF_UNDYING";
        if (b.equals("minecraft:soul")) return "SOUL";
        if (b.equals("minecraft:explosion")) return "EXPLOSION";
        return b.substring("minecraft:".length()).toUpperCase(Locale.ROOT).replace("HUGE_EXPLOSION", "EXPLOSION_EMITTER");
    }

    public static void parse(ParsedAddon parsed, CompatibilityReport report) {
        for (File f : parsed.getParticleFiles()) {
            String rel = rel(parsed.getRoot(), f);
            String name = f.getName().toLowerCase(Locale.ROOT);
            String mapped = toBukkit("minecraft:" + name.replace(".json", ""));
            report.add("Particles", new CompatibilityEntry("particle",
                    mapped != null ? CompatibilityLevel.FULL : CompatibilityLevel.PARTIAL,
                    mapped != null ? "Mapped to Bukkit particle " + mapped + "."
                            : "Custom particle; resource-pack alternative suggested.", rel, -1));
        }
    }

    private static String rel(File root, File f) {
        return root.toURI().relativize(f.toURI()).getPath();
    }
}
