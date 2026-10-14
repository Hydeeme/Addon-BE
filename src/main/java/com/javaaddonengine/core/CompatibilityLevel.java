package com.javaaddonengine.core;

/** Compatibility classification. Never claim FULL unless genuinely supported. */
public enum CompatibilityLevel {
    FULL,
    PARTIAL,
    EMULATED,
    FALLBACK,
    UNSUPPORTED;

    /** Weight used for compatibility score computation (0.0 - 1.0). */
    public double weight() {
        switch (this) {
            case FULL: return 1.0;
            case EMULATED: return 0.8;
            case PARTIAL: return 0.6;
            case FALLBACK: return 0.4;
            default: return 0.0;
        }
    }

    public boolean isSupported() {
        return this != UNSUPPORTED;
    }
}
