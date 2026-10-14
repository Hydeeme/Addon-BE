# Troubleshooting

## Import fails: "Unsupported package type"

Only `.mcaddon`, `.mcpack`, `.zip` are accepted. Rename or re-zip the pack.

## Import fails: "No manifest.json found"

The archive must contain at least one `manifest.json` (behavior or resource
pack, top level or one folder deep). Re-export from Bridge/blockbench and
check the zip layout: `unzip -l pack.mcaddon`.

## Low compatibility score

Run `/addon analyze <id>` to see which category drags the score, then
`/addon report <id>` for file-level detail. Typical causes:

- Custom `math.*`/unknown `query.*` MoLang → rewrite with supported queries.
- Bone-rig animations without ModelEngine → install ModelEngine or accept
  the display-entity fallback.
- `.fsb` audio → convert to `.ogg`.

## Recipes don't show up

Only enabled add-ons register recipes (`/addon enable <id>`). Smithing and
stonecutter map best-effort; check the report for PARTIAL entries with the
exact reason.

## Items look like vanilla iron swords

The base material is a stand-in. Install the generated resource pack
(`/addon resourcepack generate`, then `send`) — the `custom_model_data`
models give each item its Bedrock texture.

## NPC doesn't talk / shop doesn't open

Right-click the villager body (not the hologram). Check permission and
cooldown messages in chat, and `/addon debug` for NPC definition counts.

## Pack `send` says no URL configured

Set `resource-pack.url` (and `sha1`) in `config.yml`, or enable the local
`resource-pack.http` server for testing and copy the zip into `packs/`.

## "Player not found" / console errors

`/addon give` and `/addon summon` need a player context; from console pass
the player name explicitly. Function lines that fail print a WARN with the
add-on, function, and line — paste that when asking for help.
