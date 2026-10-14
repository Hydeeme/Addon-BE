package com.javaaddonengine;

import com.javaaddonengine.blocks.AddonBlockSpec;
import com.javaaddonengine.blocks.BlockParser;
import com.javaaddonengine.commands.FunctionParser;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.entities.AddonEntitySpec;
import com.javaaddonengine.entities.EntityParser;
import com.javaaddonengine.items.AddonItemSpec;
import com.javaaddonengine.items.ItemParser;
import com.javaaddonengine.loot.AddonLootTable;
import com.javaaddonengine.loot.LootParser;
import com.javaaddonengine.parser.PackDetector;
import com.javaaddonengine.parser.ParsedAddon;
import com.javaaddonengine.parser.SafeArchiveExtractor;
import com.javaaddonengine.recipes.AddonRecipe;
import com.javaaddonengine.recipes.RecipeParser;
import com.javaaddonengine.resourcepack.ResourcePackGenerator;
import com.javaaddonengine.translator.AnimationParser;
import com.javaaddonengine.translator.ParticleMapper;
import com.javaaddonengine.translator.SoundParser;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end: zips examples/example-addon into a .mcaddon and runs the full
 * import pipeline (extract -> detect -> parse all subsystems -> report ->
 * resource pack) without a Bukkit server.
 */
public class ExampleAddonImportTest {

    @TempDir
    File temp;

    @Test
    public void fullPipelineOnExampleAddon() throws Exception {
        File exampleDir = new File("examples/example-addon");
        assertTrue(exampleDir.isDirectory(), "examples/example-addon must exist");

        // 1. Package as .mcaddon
        File archive = new File(temp, "example.mcaddon");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(archive));
             Stream<Path> walk = Files.walk(exampleDir.toPath())) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(p)) continue;
                String rel = exampleDir.toPath().relativize(p).toString().replace('\\', '/');
                z.putNextEntry(new ZipEntry(rel));
                Files.copy(p, z);
                z.closeEntry();
            }
        }

        // 2. Secure extraction
        SafeArchiveExtractor.ExtractResult extracted =
                SafeArchiveExtractor.extract(archive, new File(temp, "extracted"), 50_000_000, 1000);
        assertFalse(extracted.manifests.isEmpty(), "manifests must be found");

        Addon addon = new Addon("example", new File(temp, "addon"));
        addon.getDirectory().mkdirs();
        CompatibilityReport report = new CompatibilityReport("example", "example", "1.0.0");

        // 3. Pack detection
        PackDetector.Detection detection = PackDetector.detect(extracted.directory, report);
        PackDetector.applyToAddon(addon, detection, report);
        assertNotNull(addon.getBehaviorManifest(), "behavior pack must be detected");
        assertNotNull(addon.getResourceManifest(), "resource pack must be detected");
        assertEquals("JAE Example Behavior Pack", addon.getName());

        // 4. Parse every subsystem
        ParsedAddon behavior = ParsedAddon.scan(detection.behaviorDir);
        ParsedAddon resource = ParsedAddon.scan(detection.resourceDir);
        EntityParser.parse(behavior, detection.behaviorDir, report, addon.getEntities(), null);
        ItemParser.parse(behavior, report, addon.getItems(), null);
        BlockParser.parse(behavior, report, addon.getBlocks(), null, AddonBlockSpec.RenderMode.FALLBACK);
        List<AddonRecipe> recipes = RecipeParser.parse(behavior, report);
        Map<String, AddonLootTable> loot = LootParser.parse(behavior, report);
        List<FunctionParser.ParsedFunction> functions = FunctionParser.parse(behavior, report);
        AnimationParser.parse(resource, report);
        ParticleMapper.parse(resource, report);
        Map<String, String> sounds = SoundParser.parse(resource, report);

        // 5. Assert converted content
        assertTrue(addon.getEntities().containsKey("jae_example:clockwork_golem"));
        AddonEntitySpec golem = addon.getEntities().get("jae_example:clockwork_golem");
        assertEquals(80.0, golem.getHealth());
        assertEquals(9.0, golem.getAttackDamage());
        assertEquals("clockwork_golem_loot", golem.getLootTable());
        assertTrue(addon.getItems().containsKey("jae_example:spark_sword"));
        assertEquals(11, addon.getItems().get("jae_example:spark_sword").getDamage());
        assertTrue(addon.getBlocks().containsKey("jae_example:spark_ore"));
        assertEquals(1, recipes.size());
        assertEquals(AddonRecipe.Type.SHAPED, recipes.get(0).getType());
        assertTrue(loot.containsKey("clockwork_golem_loot"));
        assertEquals(1, functions.size());
        assertTrue(functions.get(0).lines.size() >= 4);
        assertTrue(sounds.containsKey("spark_strike"));
        assertTrue(sounds.get("spark_strike").startsWith("javaaddonengine:"));

        // 6. Resource pack + report files
        addon.setReport(report);
        ResourcePackGenerator.Result pack = ResourcePackGenerator.generate(
                addon.getDirectory(), resource, sounds, report, 48, "JAE example");
        assertTrue(new File(pack.directory, "pack.mcmeta").isFile());
        Files.write(new File(addon.getDirectory(), "report.txt").toPath(), report.toText().getBytes());
        Files.write(new File(addon.getDirectory(), "report.json").toPath(), report.toJson().getBytes());
        Files.write(new File(addon.getDirectory(), "report.html").toPath(), report.toHtml().getBytes());

        double score = report.score();
        System.out.println("EXAMPLE SCORE: " + Math.round(score) + "% grade=" + report.grade());
        assertTrue(score > 50, "example addon should score above 50%, got " + score);
        assertFalse(report.getEntries().isEmpty());
    }
}
