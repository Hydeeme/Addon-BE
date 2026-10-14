# Compatibility Reference

Levels: **FULL** (native mapping), **EMULATED** (behavior-equivalent task),
**PARTIAL** (metadata kept, approximation), **FALLBACK** (stand-in used),
**UNSUPPORTED** (detected, reported, skipped — never silent).

## Entities

FULL: health, movement (+ basic), type_family, loot, experience_reward,
scale, knockback_resistance.
EMULATED: attack, physics, follow_range, nearest_attackable_target,
melee_attack, ranged_attack, follow_owner, avoid_mob, float, random_stroll
(wander-heading nudges via `BehaviorEmulator`; locomotion stays with base
mob AI), look_at_player / random_look_around (head/body tracking task),
plus any unknown `minecraft:behavior.*` (best-effort task).
PARTIAL: collision_box (stored; exact hitbox needs display fallback),
damage_sensor (simple filters translated, complex kept as metadata),
unknown `minecraft:*` components (kept as metadata).

Vanilla mapping prefers strong identifier keywords (robot/golem, creeper,
villager...) over generic families; geometry references are stored in PDC
and routed to ModelEngine when installed, display entities otherwise.

## Items

FULL: max_stack_size, durability, food (+nutrition/saturation), damage,
cooldown, rarity, display_name, tags, icon texture reference.
PARTIAL: use_animation (arm swing / consumption effects), enchantable
(allowed-enchant list), shooter/projectile, wearable/armor, hand_equipped,
glint.
Items persist via PDC (`addon_id`, `item_id`, `version`) and get stable
`custom_model_data` slots wired to generated models.

## Blocks

FULL: hardness (custom break timing), resistance, light emission, loot.
PARTIAL: material (closest vanilla match, flagged), collision (barrier /
display approximation), sound (closest vanilla sound).
Rendering modes: NATIVE (vanilla stand-in), DISPLAY_ENTITY, ITEM_DISPLAY,
RESOURCE_PACK, PACKET (reserved), FALLBACK. AUTO resolves to
DISPLAY_ENTITY + stand-in. Arbitrary new block ids are never claimed as
natively registered — the report says FALLBACK explicitly.

## Recipes / Loot / Functions

FULL: shaped, shapeless, furnace (incl. smoker/blast/campfire variants).
PARTIAL: smithing/transform, stonecutter (best-effort vanilla mapping).
Loot pools/rolls/weights/`set_count` fully rolled; exotic conditions kept
as metadata and reported PARTIAL.
Functions: give/summon/tp/teleport/effect/kill/tag/say/tellraw/title/
playsound/particle/time/weather/gamemode/xp/clear/scoreboard/execute/
setblock/fill/spreadplayers translate FULL; camera/camerashake/fog/music/
playanimation/ride/structure/loot/dialogue/hud/inputpermission/event are
PARTIAL (Bedrock-only flags ignored, `dialogue` emulated as NPC chat);
everything else is UNSUPPORTED per line and skipped with a warning.

## Scripts / MoLang / Animation / Particles / Sounds

Scripts are analyzed, never executed: the supported `@minecraft/server`
event subset (`beforeEvents`/`afterEvents` + `system.run*` + common `world.*`
calls) is EMULATED via Bukkit events; `eval`, `Function()`, `fs`,
`child_process`, `fetch`/`XMLHttpRequest`, and timer globals are
UNSUPPORTED and blocked.
MoLang: arithmetic, comparisons, `&&`/`||`/`!`, ternary over `query.*` /
`variable.*`; unknown queries and `math.*` raise controlled errors.
Animations: simple transform loops EMULATED via display-entity
interpolation; bone rigs PARTIAL (ModelEngine recommended, display
fallback otherwise); controllers kept as metadata.
Particles: known Bedrock ids map to Bukkit (`redstone`→DUST,
`magic`→WITCH, `totem`→TOTEM_OF_UNDYING...); custom particles are PARTIAL
with resource-pack alternatives suggested.
Sounds: `sounds.json` definitions remap to `javaaddonengine:<name>`;
`.ogg` shipped; `.fsb`/`.wav` flagged for conversion.
