package com.javaaddonengine.commands;

import com.javaaddonengine.core.CompatibilityLevel;
import com.javaaddonengine.core.CompatibilityReport;
import com.javaaddonengine.parser.ParsedAddon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FunctionParserTest {

    @Test
    void classifiesSupportedPartialAndUnsupported(@TempDir File tmp) throws Exception {
        File dir = new File(tmp, "functions");
        dir.mkdirs();
        String fn = "# comment\n"
                + "give @p myaddon:sword 1\n"
                + "summon myaddon:robot ~ ~1 ~\n"
                + "camerashake add @p 0.5 1\n"
                + "dothingweird foo bar\n";
        Files.write(new File(dir, "intro.mcfunction").toPath(), fn.getBytes(StandardCharsets.UTF_8));
        ParsedAddon parsed = ParsedAddon.scan(tmp);
        assertEquals(1, parsed.getFunctionFiles().size());
        CompatibilityReport r = new CompatibilityReport("t", "T", "1.0.0");
        List<FunctionParser.ParsedFunction> fns = FunctionParser.parse(parsed, r);
        assertEquals(1, fns.size());
        assertEquals(4, fns.get(0).lines.size());
        assertTrue(r.getEntries().stream().anyMatch(e ->
                e.getComponent().equals("command:give") && e.getLevel() == CompatibilityLevel.FULL));
        assertTrue(r.getEntries().stream().anyMatch(e ->
                e.getComponent().equals("command:camerashake") && e.getLevel() == CompatibilityLevel.PARTIAL));
        assertTrue(r.getEntries().stream().anyMatch(e ->
                e.getComponent().equals("command:dothingweird") && e.getLevel() == CompatibilityLevel.UNSUPPORTED));
    }

    @Test
    void translateLineSkipsUnsupported() {
        assertNotNull(FunctionParser.translateLine("give @p stone 1"));
        assertNotNull(FunctionParser.translateLine("camerashake add @p 1 1"));
        assertNull(FunctionParser.translateLine("dothingweird foo"));
        assertNull(FunctionParser.translateLine("# comment"));
        assertNull(FunctionParser.translateLine(""));
        assertNull(FunctionParser.translateLine(null));
    }
}
