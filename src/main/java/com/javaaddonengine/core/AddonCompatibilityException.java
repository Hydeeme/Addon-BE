package com.javaaddonengine.core;

/** Controlled failure for any malformed or untranslatable Add-On content. Never crashes the server. */
public class AddonCompatibilityException extends Exception {
    private final String addon;
    private final String file;
    private final int line;

    public AddonCompatibilityException(String message) {
        super(message);
        this.addon = "";
        this.file = "";
        this.line = -1;
    }

    public AddonCompatibilityException(String message, String addon, String file, int line) {
        super(message);
        this.addon = addon == null ? "" : addon;
        this.file = file == null ? "" : file;
        this.line = line;
    }

    public AddonCompatibilityException(String message, Throwable cause) {
        super(message, cause);
        this.addon = "";
        this.file = "";
        this.line = -1;
    }

    public String getAddon() { return addon; }
    public String getFile() { return file; }
    public int getLine() { return line; }

    public String toReportString() {
        StringBuilder sb = new StringBuilder("[JavaAddonEngine] ").append(getMessage());
        if (!addon.isEmpty()) sb.append("\nAdd-On: ").append(addon);
        if (!file.isEmpty()) sb.append("\nFile: ").append(file);
        if (line > 0) sb.append("\nLine: ").append(line);
        return sb.toString();
    }
}
