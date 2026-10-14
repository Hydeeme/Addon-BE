package com.javaaddonengine.core;

/** Single compatibility finding for one Bedrock component or feature. */
public final class CompatibilityEntry {
    private final String category;
    private final String component;
    private final CompatibilityLevel level;
    private final String message;
    private final String file;
    private final int line;

    public CompatibilityEntry(String component, CompatibilityLevel level, String message, String file, int line) {
        this("Converted", component, level, message, file, line);
    }

    public CompatibilityEntry(String category, String component, CompatibilityLevel level,
                              String message, String file, int line) {
        this.category = category == null ? "Converted" : category;
        this.component = component == null ? "unknown" : component;
        this.level = level == null ? CompatibilityLevel.UNSUPPORTED : level;
        this.message = message == null ? "" : message;
        this.file = file == null ? "" : file;
        this.line = line;
    }

    public CompatibilityEntry(String component, CompatibilityLevel level, String message) {
        this(component, level, message, "", -1);
    }

    public String getCategory() { return category; }
    public String getComponent() { return component; }
    public CompatibilityLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public String getFile() { return file; }
    public int getLine() { return line; }

    @Override
    public String toString() {
        String loc = file.isEmpty() ? "" : (" (" + file + (line > 0 ? ":" + line : "") + ")");
        return "[" + level + "] " + component + " - " + message + loc;
    }
}
