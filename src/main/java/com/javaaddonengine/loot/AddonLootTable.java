package com.javaaddonengine.loot;

import java.util.ArrayList;
import java.util.List;

/** Normalized loot table: pools with rolls, weighted entries, conditions and functions. */
public final class AddonLootTable {
    private final String id;

    public static final class Entry {
        public String type = "item";
        public String name = "";
        public int weight = 1;
        public int minCount = 1;
        public int maxCount = 1;
        public final List<String> functions = new ArrayList<>();
        public final List<String> conditions = new ArrayList<>();
    }

    public static final class Pool {
        public int rolls = 1;
        public final List<Entry> entries = new ArrayList<>();
        public final List<String> conditions = new ArrayList<>();
    }

    private final List<Pool> pools = new ArrayList<>();

    public AddonLootTable(String id) { this.id = id; }
    public String getId() { return id; }
    public List<Pool> getPools() { return pools; }
}
