package com.javaaddonengine.scripting;

import com.javaaddonengine.core.CompatibilityEntry;
import com.javaaddonengine.core.CompatibilityLevel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sandboxed script analyzer. NEVER executes arbitrary Add-On JavaScript.
 * Detects the supported Script API subset and reports everything else.
 */
public final class ScriptAnalyzer {

    private ScriptAnalyzer() {}

    private static final Pattern REQUIRE = Pattern.compile("require\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");
    private static final Pattern SYSTEM_CALL = Pattern.compile("system\\s*\\.\\s*(run|runTimeout|runInterval|clearRun)\\s*\\(");
    private static final Pattern WORLD_CALL = Pattern.compile("world\\s*\\.\\s*(getPlayers|getDimension|sendMessage|scoreboard|getEntity|spawnEntity|playSound|spawnParticle|gameRules|getTimeOfDay|setTimeOfDay)\\b");
    private static final Pattern EVENT_SUB = Pattern.compile("(world|system)\\s*\\.(beforeEvents|afterEvents)\\s*\\.\\s*(\\w+)\\s*\\.\\s*subscribe\\s*\\(");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(eval|Function\\s*\\(|process\\s*\\.|require\\s*\\(\\s*[\"'](fs|child_process|http|https|net|os)[\"']|XMLHttpRequest|fetch\\s*\\(|import\\s*\\(|setInterval|setTimeout)");

    public static final class Analysis {
        public final Set<String> modules = new LinkedHashSet<>();
        public final Set<String> events = new LinkedHashSet<>();
        public final Set<String> systemCalls = new LinkedHashSet<>();
        public final Set<String> worldCalls = new LinkedHashSet<>();
        public final List<String> dangerous = new ArrayList<>();
        public CompatibilityLevel level = CompatibilityLevel.PARTIAL;
        public String summary = "";
    }

    public static Analysis analyze(String source, String file) {
        Analysis a = new Analysis();
        if (source == null) source = "";
        Matcher m = REQUIRE.matcher(source);
        while (m.find()) a.modules.add(m.group(1));
        Matcher s = SYSTEM_CALL.matcher(source);
        while (s.find()) a.systemCalls.add(s.group(1));
        Matcher w = WORLD_CALL.matcher(source);
        while (w.find()) a.worldCalls.add(w.group(1));
        Matcher e = EVENT_SUB.matcher(source);
        while (e.find()) a.events.add(e.group(2) + "." + e.group(3));
        Matcher d = DANGEROUS.matcher(source);
        while (d.find()) {
            if (!a.dangerous.contains(d.group(1))) a.dangerous.add(d.group(1));
        }
        boolean onlyServerModule = a.modules.isEmpty() || (a.modules.size() == 1 && a.modules.contains("@minecraft/server"));
        boolean noDanger = a.dangerous.isEmpty();
        if (onlyServerModule && noDanger && !a.events.isEmpty()) {
            a.level = CompatibilityLevel.EMULATED;
            a.summary = "Uses supported @minecraft/server subset (" + a.events.size() + " event subscriptions); emulated via Bukkit events.";
        } else if (noDanger) {
            a.level = CompatibilityLevel.PARTIAL;
            a.summary = "Partially supported script; unsupported modules/calls must be re-implemented: " + a.modules;
        } else {
            a.level = CompatibilityLevel.UNSUPPORTED;
            a.summary = "Blocked dangerous constructs: " + a.dangerous + ". Script will NOT run.";
        }
        return a;
    }

    public static CompatibilityEntry toEntry(Analysis a, String file) {
        return new CompatibilityEntry("script", a.level, a.summary, file, -1);
    }
}
