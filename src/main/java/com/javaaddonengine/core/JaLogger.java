package com.javaaddonengine.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Structured prefixed logger with debug/trace gates. */
public final class JaLogger {
    private final Logger logger;
    private volatile boolean debug;

    public JaLogger(Logger logger, boolean debug) {
        this.logger = logger;
        this.debug = debug;
    }

    public void setDebug(boolean debug) { this.debug = debug; }

    public void info(String msg) { logger.info("[JavaAddonEngine] " + msg); }
    public void warn(String msg) { logger.warning("[JavaAddonEngine] " + msg); }
    public void error(String msg) { logger.severe("[JavaAddonEngine] " + msg); }
    public void error(String msg, Throwable t) { logger.log(Level.SEVERE, "[JavaAddonEngine] " + msg, t); }
    public void debug(String msg) { if (debug) logger.info("[JavaAddonEngine][DEBUG] " + msg); }
    public void trace(String msg) { if (debug) logger.fine("[JavaAddonEngine][TRACE] " + msg); }

    public void compatibility(AddonCompatibilityException e) {
        logger.warning(e.toReportString());
    }
}
