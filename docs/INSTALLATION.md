# Installation

## Requirements

- Java 21 (server runtime)
- Paper 1.21+ recommended; Spigot 1.20.4+ supported where the APIs used
  exist (display entities require 1.19.4+; the engine degrades gracefully)
- Maven 3.8+ (build only)

## Install the plugin

1. Build, or download the release jar:
   ```
   mvn package
   ```
   The shaded jar is `target/JavaAddonEngine-1.0.0.jar`.
2. Copy it into your server's `plugins/` folder.
3. Start the server once. This creates:
   ```
   plugins/JavaAddonEngine/
     config.yml  addons.yml  compatibility.yml  resourcepack.yml
     logging.yml  messages.yml
     addons/     (imported add-ons)
     addons/incoming/  (drop .mcaddon files here)
     packs/      (generated pack zips, when http server enabled)
   ```
4. Drop a Bedrock `.mcaddon` / `.mcpack` / `.zip` into `addons/incoming/`.
5. In game or console:
   ```
   /addon import my-addon.mcaddon
   /addon report my-addon
   /addon enable my-addon
   ```

## Deliver the resource pack

Option A — external hosting (recommended for production):

1. `/addon resourcepack generate my-addon`
2. Host `plugins/JavaAddonEngine/addons/my-addon/converted-resourcepack.zip`
   over HTTPS.
3. Set in `config.yml`:
   ```yaml
   resource-pack:
     url: "https://example.com/my-addon.zip"
     sha1: "<40 hex chars>"
     required: false
   ```
4. `/addon resourcepack send my-addon <player>` (or enable `auto-send`).

Option B — local HTTP server (testing / LAN):

```yaml
resource-pack:
  http:
    enabled: true
    host: "127.0.0.1"
    port: 8089
```

Copy the generated zip into `plugins/JavaAddonEngine/packs/`, set
`resource-pack.url` to `http://<your-ip>:8089/packs/<file>.zip`, then send.

## Updating

1. Stop the server (or `/addon disable <each>` first for a clean unload).
2. Replace the jar.
3. Start. Add-ons re-parse from disk automatically (`addons.auto-load`).

Your converted data (PDC tags on items/entities) survives updates; recipe
keys are namespaced per add-on and re-registered on enable.
