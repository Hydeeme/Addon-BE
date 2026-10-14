package com.javaaddonengine.runtime;

import com.javaaddonengine.commands.FunctionParser;
import com.javaaddonengine.core.Addon;
import com.javaaddonengine.core.JaLogger;
import com.javaaddonengine.scripting.ScriptSandbox;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Executes converted .mcfunction lines on the main thread.
 *
 * <p>Only the supported command subset runs (see {@link FunctionParser}).
 * Every line is validated before dispatch: no shell, no newlines, no
 * command-block chaining tricks. Unsupported lines are skipped with a
 * warning, never executed.
 */
public final class FunctionDispatcher {

    private static final Pattern SAFE_LINE = Pattern.compile("[\\p{Print} \\t]*");
    private static final int MAX_LINE_LENGTH = 512;

    private final Plugin plugin;
    private final JaLogger log;

    public FunctionDispatcher(Plugin plugin, JaLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    /** Dispatch a whole function as a console-sender command batch. */
    public void dispatch(Addon addon, FunctionParser.ParsedFunction function, Player context) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> dispatch(addon, function, context));
            return;
        }
        CommandSender sender = Bukkit.getConsoleSender();
        Location origin = context != null ? context.getLocation() : null;
        for (String line : function.lines) {
            dispatchLine(addon, function.name, line, sender, origin);
        }
    }

    private void dispatchLine(Addon addon, String function, String line,
                              CommandSender sender, Location origin) {
        String translated = FunctionParser.translateLine(line);
        if (translated == null) {
            log.warn("Skipping unsupported function line in " + addon.getId() + "/" + function + ": " + line);
            return;
        }
        if (translated.length() > MAX_LINE_LENGTH || !SAFE_LINE.matcher(translated).matches()) {
            log.warn("Blocked unsafe function line in " + addon.getId() + "/" + function);
            return;
        }
        String[] parts = translated.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (!ScriptSandbox.isSafeCommandArgument(translated)) {
            log.warn("Blocked function line with unsafe characters in " + addon.getId() + "/" + function);
            return;
        }
        try {
            switch (head) {
                case "give":
                case "summon":
                case "effect":
                case "tp":
                case "teleport":
                case "kill":
                case "tag":
                case "say":
                case "tellraw":
                case "title":
                case "playsound":
                case "particle":
                case "time":
                case "weather":
                case "gamemode":
                case "xp":
                case "clear":
                case "scoreboard":
                case "execute":
                case "setblock":
                case "fill":
                case "clone":
                case "spreadplayers":
                    Bukkit.dispatchCommand(sender, translated.replaceFirst("(?i)^minecraft:", ""));
                    break;
                case "dialogue":
                    // Best-effort PARTIAL emulation: show the remaining text as NPC chat.
                    emulateDialogue(translated, origin);
                    break;
                default:
                    log.warn("Skipping Bedrock-only command '" + head + "' (no Java equivalent) in "
                            + addon.getId() + "/" + function + ": " + line);
                    break;
            }
        } catch (Exception e) {
            log.warn("Function line failed in " + addon.getId() + "/" + function + ": " + e.getMessage());
        }
    }

    private void emulateDialogue(String translated, Location origin) {
        String text = translated.replaceFirst("(?i)^dialogue\\s+", "").trim();
        if (text.isEmpty()) return;
        String message = "§e[NPC]§f " + text.replaceAll("@[aeprs](\\[[^]]*])?", "you");
        if (origin != null && origin.getWorld() != null) {
            for (Player p : origin.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(origin) <= 64 * 64) p.sendMessage(message);
            }
        } else {
            Bukkit.broadcastMessage(message);
        }
    }
}
