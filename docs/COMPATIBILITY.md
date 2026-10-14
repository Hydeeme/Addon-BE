# Compatibility Reference

How to read this file: each Bedrock feature gets one level, one score weight,
and one clear runtime behavior. Nothing is ever silently skipped — every
finding lands in `/addon report <id>` (`report.txt` / `report.json` / `report.html`).

## 1. Levels, weights, grades

| Level | Meaning | Weight |
|---|---|---|
| FULL | Native mapping. Works on plain Paper/Spigot, no extra plugin. | 1.0 |
| EMULATED | No native equivalent, but a behavior-equivalent task runs instead. | 0.8 |
| PARTIAL | Approximated or kept as metadata. Works best-effort, flagged in report. | 0.6 |
| FALLBACK | A stand-in is used (vanilla block, pack skeleton, display entity). | 0.4 |
| UNSUPPORTED | Detected, reported, skipped at runtime. Never executed. | 0.0 |

Score = average weight × 100, computed over all entries. The same formula
powers the total score, the per-category score, and the per-file score
(e.g. one dinosaur = one file = its own percentage).

| Score | Grade |
|---|---|
| ≥ 90% | EXCELLENT |
| ≥ 75% | GOOD |
| ≥ 50% | FAIR |
| ≥ 25% | POOR |
| < 25% | MINIMAL |

Check your add-on in game:

```
/addon analyze <id>            # total + per-category + per-file percentages
/addon analyze <id> t_rex      # filter to one mob / file
/addon report <id>             # full detail list + report.txt/html/json
```

Example: a T-Rex file with 2 FULL + 1 EMULATED + 1 PARTIAL + 1 UNSUPPORTED
scores (1 + 1 + 0.8 + 0.6 + 0) / 5 = **68%**.

## 2. Entities (mobs)

Every `entities/*.json` component gets its own entry. The mob always spawns
as a vanilla stand-in (default `ZOMBIE`) with real attributes applied on top.

| Component | Level | What happens in Java |
|---|---|---|
| `minecraft:health` | FULL | Sets max health + current health. |
| `minecraft:movement`, `minecraft:movement.basic` | FULL | Sets movement speed (clamped ≤ 1.0). |
| `minecraft:type_family` | FULL | Stored as metadata tags. |
| `minecraft:loot` | FULL | Routed into the JAE loot engine. |
| `minecraft:experience_reward` | FULL | Dropped as XP. |
| `minecraft:scale` | FULL | Applied via scale attribute (0.1–16). |
| `minecraft:knockback_resistance` | FULL | Applied (0–1). |
| `minecraft:attack` | EMULATED | Damage applied through synthetic damage events. |
| `minecraft:physics` | EMULATED | Approximated with vanilla gravity / collision. |
| `minecraft:follow_range` | EMULATED | Approximated by target-radius expansion (1–2048). |
| `minecraft:behavior.random_stroll` | EMULATED | Wander nudges via `BehaviorEmulator`; locomotion stays with base mob AI. |
| `minecraft:behavior.look_at_player`, `random_look_around` | EMULATED | Head/body tracking task. |
| `minecraft:behavior.melee_attack`, `ranged_attack` | EMULATED | Melee tick / projectile task. |
| `minecraft:behavior.nearest_attackable_target` | EMULATED | Nearest-player targeting. |
| `minecraft:behavior.follow_owner`, `avoid_mob`, `float` | EMULATED | Follow / flee / water-float tasks. |
| Any other `minecraft:behavior.*` | EMULATED | Best-effort generic task. |
| `minecraft:collision_box` | PARTIAL | Stored; exact hitbox needs the display fallback. |
| `minecraft:damage_sensor` | PARTIAL | Simple filters translated; complex ones kept as metadata. |
| Any other `minecraft:*` | PARTIAL | Known namespace, no dedicated adapter; kept as metadata. |
| Custom (non-`minecraft:`) keys | UNSUPPORTED | Ignored at runtime, listed in report. |

Also converted:

- `description.identifier` → entity id (required).
- `description.geometry` (e.g. `geometry.t_rex`) → stored in PDC; applied as a
  real ModelEngine model when ModelEngine is installed, display-entity
  fallback otherwise.
- `description.properties`, `runtime_identifier`, `events`,
  `component_groups` → merged / kept as metadata, never executed.
- Base vanilla pick: strong id keywords win (`robot`/`golem` → `IRON_GOLEM`,
  `villager`/`npc` → `VILLAGER`, `creeper`, `skeleton`, `zombie`), then
  `type_family` hints, else `ZOMBIE`.

Not scanned (won't affect the score): `spawn_rules/`, `render_controllers/`,
client-entity files. Natural Bedrock spawning therefore doesn't transfer —
spawn mobs with `/addon summon <addon> <mob>`.

## 3. Items

| Component | Level | What happens in Java |
|---|---|---|
| `minecraft:max_stack_size` | FULL | Stack size, clamped 1–64. |
| `minecraft:durability` | FULL | Applied via `Damageable` meta. |
| `minecraft:food` | FULL | Nutrition + saturation via food component where supported. |
| `minecraft:damage` | FULL | Stored in PDC, applied on hit. |
| `minecraft:cooldown` | FULL | Applied with `setCooldown`. |
| `minecraft:rarity` | FULL | Stored in display metadata / lore. |
| `minecraft:tags` | FULL | Stored as namespaced tags. |
| `minecraft:display_name` | FULL | Applied as the item name. |
| `minecraft:icon` | PARTIAL | Texture reference kept; matched against shipped textures by base filename when generating the model, `iron_sword` texture fallback otherwise. |
| `minecraft:use_animation`, `use_modifiers` | PARTIAL | Approximated (arm swing / consumption); raw kept as metadata. |
| `minecraft:enchantable` | PARTIAL | Allowed-enchant list where possible. |
| `minecraft:shooter`, `projectile`, `wearable`, `armor`, `hand_equipped`, `glint` | PARTIAL | Kept as metadata, best-effort behavior only. |
| Any other `minecraft:*` | PARTIAL | Kept as metadata. |
| Custom keys | UNSUPPORTED | Ignored at runtime. |

Runtime notes: items persist via PDC (`addon_id`, `item_id`, `version`),
get stable `custom_model_data` slots, and fall back to `IRON_SWORD`-family
materials by id keyword when no better match exists.

## 4. Blocks

Java cannot register new blocks via the Bukkit API, so every custom block is
an honest fallback: a vanilla stand-in plus a display-entity marker.

| Component | Level | What happens in Java |
|---|---|---|
| `minecraft:hardness` | FULL | Custom break timing. |
| `minecraft:resistance`, `blast_resistance` | FULL | Stored blast resistance. |
| `minecraft:light`, `light_emission` | FULL | Light approximation. |
| `minecraft:loot` | FULL | Loot table reference (same engine as §6). |
| `minecraft:material`, `map_color` | PARTIAL | Closest vanilla match, flagged. |
| `minecraft:collision` | PARTIAL | Barrier / display approximation. |
| `minecraft:sound` | PARTIAL | Closest vanilla sound. |
| Block `properties` (states) | PARTIAL | Kept as `key=value` metadata. |

Rendering modes (`NATIVE`, `DISPLAY_ENTITY`, `ITEM_DISPLAY`,
`RESOURCE_PACK`, `PACKET` reserved, `FALLBACK`): AUTO resolves to
`DISPLAY_ENTITY` + stand-in. Stand-in pick: wood → `OAK_PLANKS`,
glass → `GLASS`, metal/iron → `IRON_BLOCK`, sand → `SANDSTONE`,
dirt/grass → `DIRT`, emitting → `GLOWSTONE`, else `STONE`.

## 5. Recipes

| Type | Level | Notes |
|---|---|---|
| `shaped` (`minecraft:recipe_shaped`) | FULL | Pattern + key + result converted. |
| `shapeless` (`minecraft:recipe_shapeless`, generic `*craft*`) | FULL | Ingredients + result converted. |
| `furnace` / `smelt` / `smoker` / `blast` / `campfire` | FULL | Input → output converted. |
| `smithing` / `transform` | PARTIAL | Best-effort vanilla mapping, raw kept. |
| `stonecut*` | PARTIAL | Best-effort vanilla mapping, raw kept. |
| Unknown type | UNSUPPORTED | Metadata only. |

Unparseable JSON or a failed conversion is UNSUPPORTED per file/type.

## 6. Loot

| Feature | Level | Notes |
|---|---|---|
| Table with `pools` | FULL | Converted with pool count. |
| `rolls`, per-entry `weight`, `set_count` (min/max/value) | FULL | Rolled by `LootEngine`: each pool rolls N times, one entry per roll weighted by `weight`. |
| Pool/entry `conditions` | PARTIAL | Kept as metadata; common ones evaluated at runtime. |
| Table without `pools` | PARTIAL | Metadata only. |
| Unparseable table | UNSUPPORTED | Skipped. |

Only `type: "item"` entries drop; other entry types are skipped at roll time.
Unknown table id rolls to an empty list (never throws).

## 7. Functions (`.mcfunction`, per line)

FULL — translated and dispatched via Bukkit:

```
give, summon, tp, teleport, effect, kill, tag, say, tellraw, title,
playsound, particle, time, weather, gamemode, xp, clear, scoreboard,
execute, setblock, fill, clone, spreadplayers
```

PARTIAL — parsed per line; only `dialogue` runs (as NPC chat within 64
blocks, else broadcast). The rest (`camera`, `camerashake`, `fog`, `music`,
`playanimation`, `ride`, `structure`, `loot`, `hud`, `inputpermission`,
`event`) are logged as skipped Bedrock-only commands — no Java equivalent.

Anything else — UNSUPPORTED per line, skipped with a warning. Lines over 512
chars, with newlines/backticks/`$`, or non-printable characters are blocked.

## 8. NPCs / dialogue

| Source | Level | Notes |
|---|---|---|
| JAE extended `npcs/*.json` (`id`, `name`, `dialogue`, `commands`, `shop`, `permission`, `cooldown`) | FULL | Converted with line/command/shop counts. |
| Bedrock `dialogue/*.json` scenes (`scene_tag`, `npc_name`, `text`, `buttons` + commands) | EMULATED | Chat dialogue + GUI buttons. |
| Dialogue without `scenes`, unknown layout | PARTIAL | Metadata only. |
| Oversize / unparseable / failed dialogue | UNSUPPORTED | Skipped. |

## 9. Scripts (`.js` / `.ts` in `scripts/`)

Scripts are **analyzed, never executed**. Each file gets one verdict:

| Situation | Level |
|---|---|
| Only `@minecraft/server` + event subscriptions, no dangerous calls | EMULATED via Bukkit events |
| No dangerous calls, but other modules / calls | PARTIAL — must be re-implemented |
| Any dangerous construct | UNSUPPORTED — script will NOT run |

Detected surface: `system.run / runTimeout / runInterval / clearRun`,
`world.getPlayers / getDimension / sendMessage / scoreboard / getEntity /
spawnEntity / playSound / spawnParticle / gameRules / getTimeOfDay /
setTimeOfDay`, `(world|system).(beforeEvents|afterEvents).* .subscribe`.

Blocked: `eval`, `Function()`, `fs`, `child_process`, `http`/`https`/`net`/
`os` requires, `XMLHttpRequest`, `fetch()`, `import()`, `setInterval`,
`setTimeout`, `process.*`.

## 10. MoLang, animations, particles, sounds, resource pack

**MoLang** (`ComponentRegistry: molang` = PARTIAL): arithmetic, comparisons,
`&&` / `||` / `!`, ternary over `query.*` / `variable.*` (`var.*` alias, bare
identifiers count as `variable.*`). Unknown queries/variables and all
`math.*` raise controlled errors instead of running.

**Animations**: simple transform loops → EMULATED via display-entity
interpolation; complex bone rigs (contains `bones` and > 4000 chars) →
PARTIAL (ModelEngine recommended, display fallback otherwise);
`animation_controllers` / unknown layouts → PARTIAL metadata; unparseable →
UNSUPPORTED.

**Particles**: 18 known Bedrock ids → FULL with fixed Bukkit mapping
(`magic`→`WITCH`, `redstone`→`DUST`, `totem`→`TOTEM_OF_UNDYING`,
`huge_explosion`→`EXPLOSION_EMITTER`, `soul`→`SOUL`, `note`→`NOTE`,
`portal`→`PORTAL`, `crit`→`CRIT`, `smoke`→`SMOKE`, `flame`→`FLAME`,
`explosion`→`EXPLOSION`, plus `LAVA`, `WATER`, `HEART`, `SNOWBALL`, `SLIME`,
`FIREWORKS`, `DRAGON_BREATH`). Anything else → PARTIAL with a
resource-pack alternative suggested.

**Sounds**: every `sounds.json` definition → FULL, remapped to
`javaaddonengine:<name>`. `.ogg` files ship in the generated pack;
`.fsb` / `.wav` are kept as-is and flagged (`convert to .ogg for Java
playback`). The pack entry is FULL when clean, PARTIAL when issues exist,
FALLBACK skeleton when nothing convertible was found.

**Resource pack**: textures copied byte-for-byte with PNG validation,
per-item models with `custom_model_data` slots, `en_us.json` names,
`pack.mcmeta`, `sounds.json`, zip + SHA-1 + file counts.
