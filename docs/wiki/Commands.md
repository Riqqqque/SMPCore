# Commands

## Player Commands

- `/menu`, `/smpmenu` - opens the main player menu with Reliquary, mythics, bosses, powers, leaderboards, team vault, enchants, and wiki links.
- `/reliquary`, `/lrecipe`, `/lrecipes` - opens the item and recipe guide.
- `/mythics`, `/mythicfusions`, `/mythicnexus`, `/nexus` - opens Mythic Nexus recipes.
- `/enchants` - opens the custom enchant guide menu.
- `/bossrituals`, `/bossritual`, `/rituals` - opens the boss ritual guide.
- `/bossbrews`, `/bosspotions`, `/brews` - opens the boss-material potion guide.
- `/leaderboards`, `/leaderboard`, `/lb`, `/topstats` - opens persistent server leaderboards.
- `/wiki`, `/guide`, `/smpwiki` - shows the clickable wiki link.
- `/team` - team commands. Leaders can use `/team color <color>` and `/team rename "new name"`.
- `/tvault`, `/teamvault` - opens team vault storage.
- `/powerinfo` - opens superpower information.
- `/stormcaller on|off|toggle|status` - Stormcaller-only lightning strike toggle.
- `/home`, `/sethome`, `/delhome`, `/homes` - home system.
- `/spawn` - OP-only spawn command on this server.
- `/back` - OP-only back command on this server.
- `/veinminer` - vein miner toggle if enabled.

## Admin Commands

- `/bosses` - boss control GUI.
- `/bosses spawn <boss>` - spawn a boss at your location.
- `/bosses despawn <boss>` - remove active copies of a specific boss.
- `/bosses clearall` - remove all tracked custom bosses.
- `/spawner info` - inspect the spawner you are looking at within 8 blocks.
- `/spawner reset` - reset modifiers on the spawner you are looking at within 8 blocks.
- `/spawnermgr` - alias for `/spawner`.
- `/legendary give <item> [player]` - give legendary items.
- `/customitem give <item> [player]` - give non-legendary custom items, Covenant items, boss trophies, and utility relics.
- `/itemaudit <player> [item]` - view custom item origin and anomaly logs.
- `/day` - sets the current world to day.
- `/night` - sets the current world to night.
- `/sun` - clears weather.
- `/storm` - starts a storm.
- `/setpower <player> <power>` - assign a superpower.
- `/admin reward <player>` - give a reward soul lantern.
- `/admin reward revoke <player>` - revoke reward lantern usage privilege.
- `/announce <message>` - console-safe server announcement.
- `/startsmp [graceMinutes]` - expands the world border and starts grace timing. The optional number controls PvP grace for this launch.
- `/startsmp reset` - resets the start state.
- `/startsmp lock` - puts the world back into the pre-start lockdown.
- `/startsmp barrier` - reapplies the configured pre-start barrier around spawn.
- `/unban <player>` - profile unban helper.

Permissions are defined in `src/main/resources/paper-plugin.yml`.
