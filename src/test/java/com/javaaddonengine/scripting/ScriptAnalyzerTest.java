package com.javaaddonengine.scripting;

import com.javaaddonengine.core.CompatibilityLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptAnalyzerTest {

    @Test
    void supportedSubsetIsEmulated() {
        String src = "import { world, system } from \"@minecraft/server\";\n"
                + "world.afterEvents.playerJoin.subscribe(e => { system.run(() => {}); });\n"
                + "system.runTimeout(() => {}, 20);";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "main.js");
        assertEquals(CompatibilityLevel.EMULATED, a.level);
        assertTrue(a.events.contains("afterEvents.playerJoin"));
        assertTrue(a.dangerous.isEmpty());
    }

    @Test
    void dangerousConstructsAreBlocked() {
        String src = "import { world } from \"@minecraft/server\";\n"
                + "eval(\"evil\");\n"
                + "require('fs');\n";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "evil.js");
        assertEquals(CompatibilityLevel.UNSUPPORTED, a.level);
        assertFalse(a.dangerous.isEmpty());
    }

    @Test
    void fetchIsBlocked() {
        String src = "fetch(\"http://evil.example/x\");";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "net.js");
        assertEquals(CompatibilityLevel.UNSUPPORTED, a.level);
    }

    @Test
    void unknownModuleIsPartial() {
        String src = "import { ui } from \"@minecraft/server-ui\";\n"
                + "system.run(() => {});";
        ScriptAnalyzer.Analysis a = ScriptAnalyzer.analyze(src, "ui.js");
        assertEquals(CompatibilityLevel.PARTIAL, a.level);
        assertTrue(a.dangerous.isEmpty());
    }

    @Test
    void sandboxRejectsUnsafeArgs() {
        assertFalse(ScriptSandbox.isSafeCommandArgument("a\nb"));
        assertFalse(ScriptSandbox.isSafeCommandArgument("a`b"));
        assertFalse(ScriptSandbox.isSafeCommandArgument(null));
        assertTrue(ScriptSandbox.isSafeCommandArgument("give @p minecraft:stone 1"));
    }
}
