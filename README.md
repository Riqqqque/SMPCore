# SMPCore

Core Paper plugin for Ethereal SMP. SMPCore handles server essentials, custom progression systems, custom items, bosses, classes, and the built-in resource pack.

## Features

- Custom legendary, mythic, awakened, and rare items.
- Expanded Armory of the Veil season gear with boss-linked weapons, armor sets, utility relics, and trophy materials.
- Legendary altar, mythic forge, awakening table, and recipe menus.
- Custom bosses with boss bars, holograms, phases, attacks, and tracked cleanup.
- Hidden class system with admin assignment tools.
- Team system with shared team vaults.
- Backpacks, waystones, death chests, homes, spawn tools, and admin utilities.
- Custom enchants, custom tools, loot rules, cooldowns, and item audit logging.
- Built-in resource pack build task.
- SQLite persistence with migration-safe startup behavior.

## Requirements

- Paper `26.2`
- Java `25`
- Gradle wrapper included in this repo
- Optional: current Geyser, Floodgate, and ViaVersion for Bedrock players on Paper 26.2

See [Bedrock and Geyser](docs/wiki/Bedrock-And-Geyser.md) for the bridge checklist, visual fallbacks, ability gestures, and placed-anvil access.

## Build

On Windows:

```powershell
.\gradlew.bat build
```

On Linux:

```bash
./gradlew build
```

Build outputs:

- Plugin jar: `build/libs/SMPCore-<version>-linux-x64.jar`
- Resource pack zip: `build/resourcepack/SMPCore-resource-pack.zip`

### Publishing the resource pack

Resource-pack publishing is intentionally opt-in. Configure these values in the runtime environment, not in the repository:

- `PUBLISH_RESOURCE_PACK=1`
- `RESOURCE_PACK_SSH_TARGET` - an SSH config alias or `user@host`
- `RESOURCE_PACK_REMOTE_PATH` - absolute path to the hosted zip
- `RESOURCE_PACK_PUBLIC_URL` - public HTTPS URL for the pack

SSH key authentication is used by default. Interactive authentication is disabled unless `RESOURCE_PACK_ALLOW_INTERACTIVE_AUTH=1` is explicitly set for that run.

Run the normal build first, then publish the exact artifact it produced:

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat publishResourcePack --console=plain
```

The publisher validates the local archive, uploads to a temporary file, validates it remotely, keeps one `.rollback` copy, atomically replaces the hosted zip, and verifies a cache-busted public download before reporting success.

## Install

1. Build the plugin with the Gradle wrapper.
2. Put `build/libs/SMPCore-<version>-linux-x64.jar` in the server `plugins` folder.
3. Put the resource pack zip wherever the server serves packs from.
4. Restart the server.
5. Review and update `plugins/SMPCore/config.yml`.

## Useful Commands

- `/smpcore reload`
- `/reliquary` (`/lrecipe`, `/lrecipes`)
- `/legendary give`
- `/customitem give`
- `/itemaudit`
- `/bosses`
- `/bossrituals` (dungeon boss and summon-cost preview)
- `/wiki`
- `/bedrock`, `/ability`, `/customanvil`
- `/team`, `/tvault`, `/teamvault`
- `/powerinfo`, `/setpower`
- `/admin reward`
- `/announce`

Admin-only commands are protected through permissions in `src/main/resources/paper-plugin.yml`.

## Development Notes

- Always run `.\gradlew.bat build` after plugin or resource pack edits.
- Keep generated `.gradle/`, `bin/`, and `build/` output out of commits.
- Keep data migrations safe. Existing player, team, item, waystone, boss, and audit data should not be dropped on read failures.
- Do not guess Paper/Bukkit/Floodgate behavior. Check local code or docs before changing API-sensitive logic.
- Keep item and inventory logic defensive against dupes, disconnects, death handling, hoppers, dispensers, and stale state.

## Project Layout

- `src/main/java/me/rique/smpcore` - plugin source
- `src/main/resources` - plugin config and Paper metadata
- `src/main/resourcepack` - built-in resource pack source
- `docs/wiki` - GitHub Wiki source pages
- `build.gradle.kts` - Gradle build and resource pack task

## Author

Rique
