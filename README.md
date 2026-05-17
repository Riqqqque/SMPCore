# SMPCore

Core Paper plugin for Ethereal SMP. SMPCore handles server essentials, custom progression systems, custom items, bosses, powers, and the built-in resource pack.

## Features

- Custom legendary, mythic, awakened, and rare items.
- Legendary altar, mythic forge, awakening table, and recipe menus.
- Custom bosses with boss bars, holograms, phases, attacks, and tracked cleanup.
- Hidden superpower system with admin assignment tools.
- Team system with shared team vaults.
- Backpacks, waystones, death chests, homes, spawn tools, and admin utilities.
- Custom enchants, custom tools, loot rules, cooldowns, and item audit logging.
- Built-in resource pack build task.
- SQLite persistence with migration-safe startup behavior.

## Requirements

- Paper `26.1.2`
- Java `25`
- Gradle wrapper included in this repo
- Optional: Floodgate/Geyser for Bedrock compatibility paths

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

- Plugin jar: `build/libs/SMPCore-1.0.0.jar`
- Resource pack zip: `build/resourcepack/SMPCore-resource-pack.zip`

## Install

1. Build the plugin with the Gradle wrapper.
2. Put `build/libs/SMPCore-1.0.0.jar` in the server `plugins` folder.
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
- `build.gradle.kts` - Gradle build and resource pack task

## Author

Rique
