package com.javaaddonengine;

import com.javaaddonengine.commands.FunctionParser;
import com.javaaddonengine.core.AddonCompatibilityException;
import com.javaaddonengine.scripting.MolangEngine;
import com.javaaddonengine.scripting.ScriptAnalyzer;
import com.javaaddonengine.scripting.ScriptSandbox;
import com.javaaddonengine.translator.ParticleMapper;
import com.javaaddonengine.translator.SoundParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ScriptMolangCommandTest {

    @Test
    public void molangArithmetic() throws Exception {
        MolangEngine.Context ctx = new MolangEngine.Context()
                .query("health", 10).query("max_health", 20.0);
        assertEquals(0.5, MolangEngine.evaluate("query.health / query.max_health", ctx), 1e-9);
        assertEquals(1.0, MolangEngine.evaluate("query.health > 5 ? 1 : 0", ctx), 1e-9);
        assertEquals(1.0, MolangEngine.evaluate("query.health == 10 && query.max_health == 20", ctx), 1e-9);
    }

    @Test
    public void molangUnknownQueryFailsControlled() {
        MolangEngine.Context ctx = new MolangEngine.Context();
        AddonCompatibilityException e = assertThrows(AddonCompatibilityException.class,
                () -> MolangEngine.evaluate("query.does_not_exist + 1", ctx));
        assertTrue(e.getMessage().contains("Unsupported MoLang"));
    }

    @Test
    public void molangDivisionByZeroSafe() throws Exception {
        MolangEngine.Context ctx = new MolangEngine.Context().query("health", 5);
        assertEquals(0.0, MolangEngine.evaluate("query.health / 0", ctx), 1e-9);
    }

    @Test
    public void scriptSupportedSubsetEmulated() {
        String src = "import { world, system } from \"@minecraft/server\";\n"
                + "world.afterEvents.playerJoin.subscribe((e) => { world.sendMessage(\"hi\"); });\n"
                + "system.runTimeout(() => {}, 20);";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "main.js");
        assertTrue(a.dangerous.isEmpty());
        assertTrue(a.events.contains("afterEvents.playerJoin"));
    }

    @Test
    public void scriptMaliciousBlocked() {
        String src = "const fs = require(\"fs\"); eval(\"evil\"); fetch(\"http://evil\");";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "evil.js");
        assertFalse(a.dangerous.isEmpty());
        assertEquals(com.javaaddonengine.core.CompatibilityLevel.UNSUPPORTED, a.level);
    }

    @Test
    public void sandboxRejectsInjection() {
        assertFalse(ScriptSandbox.isSafeCommandArgument("hello\nworld"));
        assertFalse(ScriptSandbox.isSafeCommandArgument("$(evil)"));
        assertTrue(ScriptSandbox.isSafeCommandArgument("give @p diamond 1"));
    }

    @Test
    public void functionLineTranslation() {
        assertNotNull(FunctionParser.translateLine("give @p diamond 1"));
        assertNotNull(FunctionParser.translateLine("execute as @a run say hi"));
        // PARTIAL commands map best-effort (Bedrock-only flags ignored at runtime).
        assertNotNull(FunctionParser.translateLine("camerashake add @p 1 2"));
        assertTrue(FunctionParser.isFull("give @p diamond 1"));
        assertFalse(FunctionParser.isFull("camerashake add @p 1 2"));
        assertNull(FunctionParser.translateLine("some_unknown_command x y z"));
        assertNull(FunctionParser.translateLine("# comment"));
    }

    @Test
    public void particleMapping() {
        assertEquals("FLAME", ParticleMapper.toBukkit("minecraft:flame"));
        assertNull(ParticleMapper.toBukkit("minecraft:totally_custom_xyz"));
    }

    @Test
    public void soundRemap() {
        String remapped = SoundParser.remap("myaddon:laser_shoot");
        assertTrue(remapped.startsWith("javaaddonengine:"));
        assertFalse(remapped.contains(":") && remapped.indexOf(':') != remapped.length() - 1
                && remapped.substring(remapped.indexOf(':') + 1).contains(":"));
    }
}
