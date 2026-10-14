# Developer API

Obtain the API from your plugin (soft-depend on `JavaAddonEngine`):

```java
JavaAddonEngineAPI api = JavaAddonEnginePlugin.getAPI();
if (api == null) return; // JAE not installed

Addon addon = api.getAddon("my-addon");
AddonEntitySpec robot = api.getEntity("my-addon", "my_addon:robot");
AddonItemSpec laser = api.getItem("my-addon", "myaddon:laser_sword");
AddonBlockSpec ore = api.getBlock("my-addon", "myaddon:spark_ore");
double score = api.compatibilityScore("my-addon");
for (Addon a : api.getAddons()) { /* ... */ }
```

Spawn converted content through the runtime services:

```java
JavaAddonEnginePlugin jae = JavaAddonEnginePlugin.getInstance();
Entity e = jae.getEntityRuntime().spawn(addon, robot, player.getLocation());
ItemStack stack = jae.getItemRuntime().build(addon, laser);
List<LootEngine.Drop> drops = jae.getLootEngine().roll("golem_loot");
jae.getFunctionDispatcher().dispatch(addon, fn, player);
```

## Custom component adapters (SPI)

Implement `ComponentAdapter` and register it — your adapter overrides the
built-in classification for that Bedrock component id (case-insensitive):

```java
public class GlowAdapter implements ComponentAdapter {
    @Override public String componentId() { return "minecraft:glow"; }
    @Override public CompatibilityEntry classify(NormalizedComponent c, String file) {
        return new CompatibilityEntry("Entities", "minecraft:glow",
            CompatibilityLevel.EMULATED, "Glow via potion effect.", file, -1);
    }
}
api.registerComponentAdapter("minecraft:glow", new GlowAdapter());
api.unregisterComponentAdapter("minecraft:glow");
```

Adapters participate in both the compatibility report and the parse phase:
`EntityParser`, `ItemParser` and `BlockParser` consult the registry before
the built-in mapping, so registered components are classified by your code.

## MoLang

```java
MolangEngine.Context ctx = new MolangEngine.Context()
    .query("health", 14).query("max_health", 20);
double ratio = MolangEngine.evaluate("query.health / query.max_health", ctx);
boolean alive = MolangEngine.evaluateBoolean("query.health > 0", ctx);
```

Unknown queries throw `AddonCompatibilityException` (controlled, catchable) —
never NPEs, never script injection: the grammar is arithmetic, comparisons,
logic, ternary, and scoped identifiers only.

## Script sandbox surface

Scripts never run. If you bridge events yourself, expose only
`ScriptSandbox.SandboxWorld` / `SandboxEntity`, and validate every
add-on-supplied string with `ScriptSandbox.isSafeCommandArgument`.

## Conventions

- Add-on ids are lowercase slugs derived from the archive name.
- Recipe keys are `jae:<addon>_<recipe>` strings; per-addon lists unregister
  cleanly on disable.
- PDC namespaces: `addon_id`, `entity_id` / `item_id`, `block_id`, `npc_id`,
  `type_family`, `geometry`, `version`.
