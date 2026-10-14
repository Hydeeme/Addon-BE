# User Guide

## Importing an Add-On

```
/addon import fantasy-rpg.mcaddon
```

The engine extracts the archive (Zip-Slip protected, size/file-count capped),
detects behavior vs resource packs via `manifest.json`, parses entities, items,
blocks, recipes, loot tables, functions, NPC dialogue, animations, particles,
sounds and scripts, then prints a summary with a compatibility score:

```
JavaAddonEngine
Behavior Pack detected
Resource Pack detected
Analyzing Add-On...
Entities: 12  Items: 35  Blocks: 8
Compatibility: 81% (GOOD)
Use /addon report fantasy-rpg
```

## Reading the report

```
/addon report fantasy-rpg
/addon analyze fantasy-rpg
```

`report` prints the full detail list (component, level, file, line).
`analyze` prints the per-category summary. Machine-readable `report.json`
and `report.html` are written into the add-on folder.

## Enabling

```
/addon enable fantasy-rpg
```

Registers converted recipes, activates loot tables, defines NPCs, and makes
items/entities available:

```
/addon give fantasy-rpg laser_sword
/addon summon fantasy-rpg clockwork_golem
/addon function fantasy-rpg golem_intro
/addon npc list fantasy-rpg
/addon npc spawn fantasy-rpg keeper
/addon block place fantasy-rpg spark_ore
/addon list
/addon reload fantasy-rpg
/addon disable fantasy-rpg
/addon remove fantasy-rpg
```

Items, entities and blocks keep their add-on identity in
`PersistentDataContainer`, so they survive restarts, containers and death.

## Resource packs

```
/addon resourcepack generate fantasy-rpg
```

Converts textures + sounds into `converted-resourcepack/` (plus a `.zip`
for delivery). Custom items get `custom_model_data` slots wired to generated
item models; sounds remap to `javaaddonengine:<name>`; language entries give
items their Bedrock display names.

To deliver, host the zip over HTTP(S), set `resource-pack.url` (and `sha1`)
in `config.yml`, then:

```
/addon resourcepack send fantasy-rpg <player>
```

## NPCs and shops

```
/addon npc list fantasy-rpg     # show converted NPC definitions
/addon npc spawn fantasy-rpg keeper   # spawn at your location (needs manage perm)
```

Spawned NPCs use Citizens when `npc.provider: CITIZENS` is configured and
Citizens is installed; otherwise they use the internal villager+hologram
implementation. Talk to a converted NPC (right-click) to see its dialogue.
NPCs with shops open a GUI; purchases use Vault when installed, otherwise
the internal ledger (`/addon debug` shows which economy is active and which
NPC provider is in use). NPC buttons run the converted Bedrock button
commands through the safe function dispatcher.

Disabling an add-on despawns its NPC bodies and unregisters their
definitions; a full plugin disable removes every spawned body and stops all
behavior tasks.

## Blocks

```
/addon block place fantasy-rpg spark_ore   # place at your target block
```

Java cannot register arbitrary blocks via the Bukkit API, so placement uses
the honest fallback: a vanilla stand-in block plus a display-entity marker
tagged with the add-on/block ids. The chat confirmation names the stand-in
material used.

## Scores and grades

EXCELLENT ≥ 90, GOOD ≥ 75, FAIR ≥ 50, POOR ≥ 25, otherwise MINIMAL.
Weights: FULL 1.0, EMULATED 0.8, PARTIAL 0.6, FALLBACK 0.4, UNSUPPORTED 0.
