# Security

Bedrock Add-Ons are **untrusted input**. JavaAddonEngine treats them that way:

## Archive handling

- Zip-Slip and path traversal rejected (canonical-path check per entry,
  plus `..`, absolute-path, and drive-letter pre-filters).
- File-count and total-byte caps enforced during streaming extraction
  (`security.max-files`, `security.max-addon-size-mb`); oversized entries
  abort the import, never fill the disk.
- Only `.mcaddon` / `.mcpack` / `.zip` accepted.

## Content validation

- Every definition file JSON-validated; broken files are skipped with an
  UNSUPPORTED report entry, never parsed half-way.
- `manifest.json` UUIDs validated (`UUID.fromString`); dependencies
  recorded as missing, never resolved over the network.
- Textures PNG-signature-checked so corrupt files can't ship in packs.
- Script files size-capped (500 KB) and count-capped (200) at analysis.

## Execution sandbox

- Add-on JavaScript is **analyzed, never executed**: no engine, no eval,
  no JVM access. `eval`, `Function()`, `fs`, `child_process`, network
  globals, and timer globals are classified UNSUPPORTED and blocked.
- `.mcfunction` dispatch is allowlist-based (FULL + PARTIAL command sets);
  unknown commands return null and are skipped with a warning. Lines are
  length-capped, newline/backtick/`$`-filtered, and dispatched as console
  commands — no shell, no chaining tricks.
- NPC button commands and GUI actions go through the same dispatcher.

## Server surface

- The optional pack HTTP server serves only `.zip` from `packs/`,
  rejects traversal, and binds only where configured (default loopback).
- SQLite/MySQL access is prepared statements only; no string-built SQL.
- No hardcoded secrets; `storage.jdbc-url` is operator config.
- Permissions default closed except `javaaddonengine.use`.

## Reporting an issue

Include the add-on's `report.txt` detail lines and the server log WARN —
both carry the file/line needed to reproduce.
