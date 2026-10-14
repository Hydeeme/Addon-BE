# Admin Guide

## Configuration (`config.yml`)

```yaml
engine:
  mode: AUTO        # reserved for future strictness profiles
  strict: false     # if true, fail imports with missing dependencies
  debug: false      # verbose logging

addons:
  auto-load: true   # re-parse addons/ on startup
  auto-import: false # reserved: auto-import dropped files

resource-pack:
  enabled: true
  auto-generate: true
  auto-send: false   # send configured url on join
  required: false    # kick players who decline the pack
  url: ""
  sha1: ""
  http:
    enabled: false   # local delivery server (testing/LAN)
    host: "127.0.0.1"
    port: 0          # 0 = disabled

blocks:
  rendering-mode: AUTO  # AUTO -> DISPLAY_ENTITY fallback + stand-in block

npc:
  provider: INTERNAL    # INTERNAL | CITIZENS (Citizens used only when configured + installed)

economy:
  provider: AUTO        # AUTO | VAULT | INTERNAL

integrations:
  vault: AUTO  citizens: AUTO  model-engine: AUTO
  placeholderapi: AUTO  protocollib: AUTO  mythicmobs: AUTO
  worldguard: AUTO

compatibility:
  unsupported-feature-action: WARN  # WARN | IGNORE (reserved SILENT)

security:
  max-addon-size-mb: 500
  max-files: 10000
  max-file-size-mb: 50

storage:
  type: SQLITE
  jdbc-url: ""   # override, e.g. MySQL: jdbc:mysql://host/db?user=..&password=..

messages:
  prefix: "&b[JavaAddonEngine] &r"
```

## Storage

Runtime metadata (add-on states, scores, NPC definitions) lives in
`javaaddonengine.db` (SQLite, shaded driver, no setup). Point
`storage.jdbc-url` at MySQL/MariaDB for multi-server setups; the schema is
created automatically and every query is a prepared statement.

## Performance

- Import, JSON parsing, and pack generation run on a dedicated async pool —
  never the main thread.
- Enable/disable (recipe registration, loot indexing) runs on the main
  thread as Bukkit requires, batched per add-on.
- `/addon reload <addon>` unregisters that add-on's recipe keys and rebuilds
  the loot index from remaining enabled add-ons — no listener/task leaks:
  the listener registry holds no per-addon tasks, recipe keys are tracked
  per add-on, NPC definitions are replaced (not appended) on re-enable, and
  per-addon disable despawns that add-on's NPC bodies. Wander/look
  behavior tasks stop when their entity dies or unloads, and all stop on
  plugin disable via `BehaviorEmulator.stopAll()`.

## Permissions

- `javaaddonengine.use` (default true): list, analyze, report, status,
  give, summon, function, npc list, block place
- `javaaddonengine.import`: import, resourcepack generate/send
- `javaaddonengine.manage`: enable, disable, reload, remove, npc spawn
- `javaaddonengine.debug`: debug diagnostics
- `javaaddonengine.admin`: everything

## Diagnostics

`/addon debug` prints parser/translator status plus every integration
(Vault, Citizens, ModelEngine, PlaceholderAPI, ProtocolLib, WorldGuard,
MythicMobs) as CONNECTED or NOT INSTALLED, the active economy, NPC
counts with the active provider (INTERNAL/CITIZENS), and the number of
behavior-emulated entities currently tracked. `/addon status` shows
version and enabled counts.
