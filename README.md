# JavaAddonEngine

Run Minecraft Bedrock Add-Ons on a Java Spigot/Paper server — through a real
compatibility and translation engine, not magic.

JavaAddonEngine imports `.mcaddon` / `.mcpack` / `.zip` Bedrock packages,
parses Behavior and Resource packs, translates every supported component into
Java equivalents (entities, items, blocks, recipes, loot, functions, NPCs,
particles, sounds, animations, scripts), generates a Java resource pack, and
reports exactly what converted and what did not — per component, with file
locations and one of five honest levels: **FULL / EMULATED / PARTIAL /
FALLBACK / UNSUPPORTED**.

## Quick start

1. Drop `JavaAddonEngine-<version>.jar` into `plugins/` and restart.
2. Drop your `.mcaddon` into `plugins/JavaAddonEngine/addons/incoming/`.
3. Run:
   ```
   /addon import my-addon.mcaddon
   /addon report my-addon
   /addon enable my-addon
   ```
4. Deliver the generated pack (see `docs/USER_GUIDE.md`).

## What it does

- **Safe import** — Zip-Slip/path-traversal protection, file-count and size
  caps, JSON validation, UUID validation, dependency detection. Malformed
  add-ons become controlled errors, never crashes.
- **Entity engine** — health, movement, attack, scale, follow range,
  knockback resistance, loot, XP, type families, and emulated AI behaviors
  mapped onto vanilla Bukkit entities with persistent tags.
- **Item engine** — custom ids, stack size, durability, damage, cooldown,
  rarity, food, tags, icons; items persist through restarts, containers and
  death via `PersistentDataContainer`, with `custom_model_data` slots wired
  to the generated resource pack.
- **Block engine** — hardness, resistance, light, loot, states, tags; honest
  fallback rendering (vanilla stand-in + display entity + resource-pack
  models) because the Bukkit API cannot register arbitrary blocks.
- **Recipes, loot, functions** — shaped/shapeless/furnace (+ best-effort
  smithing/stonecutter), weighted loot rolls with `set_count`, and a
  per-line classified `.mcfunction` dispatcher.
- **NPCs, economy, GUIs** — dialogue scenes and NPC definitions emulated via
  chat + inventory GUIs, shops through Vault (or an internal ledger),
  Bedrock forms translated to chest menus.
- **Scripts sandboxed** — Bedrock Script API is analyzed, never executed;
  the supported `@minecraft/server` event subset is emulated via Bukkit
  events, dangerous constructs are blocked and reported.
- **MoLang engine** — arithmetic, comparisons, ternary over `query.*` /
  `variable.*` with controlled errors for unknown queries.
- **Resource packs** — textures copied byte-for-byte (validated PNGs),
  item models, language entries, remapped `sounds.json`, zip packaging,
  SHA-1, and an optional local HTTP server for delivery.
- **Integrations** — Vault, Citizens, ModelEngine, PlaceholderAPI,
  ProtocolLib, WorldGuard, MythicMobs: all soft, all optional, all
  reflection/plugin-manager based. No hard dependencies.
- **Public API + SDK** — `JavaAddonEngine.getAPI()` plus a
  `ComponentAdapter` SPI so other plugins can extend component translation.

## Commands

```
/addon list | status | debug
/addon import <file> | analyze <addon> | report <addon>
/addon enable|disable|reload|remove <addon>
/addon resourcepack generate|send <addon> [player]
/addon give <addon> <item> [player]
/addon summon <addon> <entity>
/addon function <addon> <name>
/addon npc list|spawn <addon> [npc]
/addon block place <addon> <block>
```

Permissions: `javaaddonengine.use` (default true), `javaaddonengine.import`,
`javaaddonengine.manage`, `javaaddonengine.debug`, `javaaddonengine.admin`.

## Compatibility philosophy

Never "100% Bedrock compatible". Every component is classified FULL,
EMULATED, PARTIAL, FALLBACK, or UNSUPPORTED, with a score, a grade, and
`report.txt` / `report.json` / `report.html` per add-on. See
`docs/COMPATIBILITY.md`.

## Security

Add-ons are untrusted input: no arbitrary script execution, no filesystem
or network access for add-on content, injection-safe function dispatch.
See `docs/SECURITY.md`.

## Docs

- `docs/INSTALLATION.md` — build and install
- `docs/USER_GUIDE.md` — importing, reports, packs, shops
- `docs/ADMIN_GUIDE.md` — config reference, storage, performance
- `docs/DEVELOPER_API.md` — public API + adapter SPI
- `docs/COMPATIBILITY.md` — full support matrix
- `docs/TROUBLESHOOTING.md` — common problems
- `docs/SECURITY.md` — threat model

## Example

`examples/example-addon/` is a complete Bedrock add-on (entity, item,
block, recipe, loot, functions, NPC dialogue + shop, sounds, particles,
animations, textures, geometry) scoring ~92% through the real pipeline —
see `ExampleAddonImportTest`.

## Build

```
mvn package
```

Requires Java 21. Paper API is `provided`; Gson/SQLite shade into the jar.
93 tests: `mvn test`.

## License

MIT — see `LICENSE`.
