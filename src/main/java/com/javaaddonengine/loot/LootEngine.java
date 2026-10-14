package com.javaaddonengine.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Java loot engine. Rolls are pure-Java (no Bukkit dependency) so they are unit-testable.
 * Pools roll {@code rolls} times; each roll picks one entry weighted by {@code weight}.
 */
public final class LootEngine {
    private final Map<String, AddonLootTable> tables = new LinkedHashMap<>();
    private final Random random = new Random();

    public void register(AddonLootTable table) {
        tables.put(table.getId(), table);
    }

    public void registerAll(Map<String, AddonLootTable> all) {
        tables.putAll(all);
    }

    public AddonLootTable get(String id) { return tables.get(id); }
    public int size() { return tables.size(); }
    public void clear() { tables.clear(); }

    public static final class Drop {
        public final String item;
        public final int count;
        public Drop(String item, int count) { this.item = item; this.count = count; }
        @Override public String toString() { return count + "x " + item; }
    }

    /** Roll a table by id. Unknown table -> empty list (never throws). */
    public List<Drop> roll(String tableId) {
        AddonLootTable table = tables.get(tableId);
        List<Drop> out = new ArrayList<>();
        if (table == null) return out;
        for (AddonLootTable.Pool pool : table.getPools()) {
            for (int r = 0; r < Math.max(0, pool.rolls); r++) {
                AddonLootTable.Entry picked = pick(pool.entries);
                if (picked == null || !"item".equalsIgnoreCase(picked.type)) continue;
                int count = picked.minCount;
                if (picked.maxCount > picked.minCount) {
                    count = picked.minCount + random.nextInt(picked.maxCount - picked.minCount + 1);
                }
                out.add(new Drop(picked.name, count));
            }
        }
        return out;
    }

    private AddonLootTable.Entry pick(List<AddonLootTable.Entry> entries) {
        int total = 0;
        for (AddonLootTable.Entry e : entries) total += Math.max(0, e.weight);
        if (total <= 0 || entries.isEmpty()) return null;
        int roll = random.nextInt(total);
        int acc = 0;
        for (AddonLootTable.Entry e : entries) {
            acc += Math.max(0, e.weight);
            if (roll < acc) return e;
        }
        return entries.get(entries.size() - 1);
    }
}
