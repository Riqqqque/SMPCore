# Commands

## Player Commands

- `/menu`, `/smpmenu` - opens the main player menu with Reliquary, mythics, bosses, powers, teams, leaderboards, settings, enchants, and wiki links.
- `/settings`, `/playersettings`, `/prefs` - opens personal settings, including important-item drop safety.
- `/reliquary`, `/lrecipe`, `/lrecipes` - opens the item and recipe guide.
- `/mythics`, `/mythicfusions`, `/mythicnexus`, `/nexus` - opens Mythic Nexus recipes.
- `/enchants` - opens the custom enchant guide menu.
- `/bossrituals`, `/bossritual`, `/rituals` - opens the boss ritual guide.
- `/bossbrews`, `/bosspotions`, `/brews` - opens the boss-material potion guide.
- `/leaderboards`, `/leaderboard`, `/lb`, `/topstats` - opens persistent server leaderboards.
- `/wiki`, `/guide`, `/smpwiki` - shows the clickable wiki link.
- `/shops`, `/shop` - shows how to create chest-based player shops. OPs and `smpcore.shop.admin` also see admin-shop setup help.
- `/team` - team management commands. Leaders can use `/team color <color>`, `/team rename "new name"`, and `/team ally ...`.
- `/teams [search]`, `/team list`, `/team search <name>` - opens the searchable team browser with member counts, online counts, deaths, kills, boss stats, and playtime.
- `/tvault`, `/teamvault` - opens team vault storage.
- `/team ally add <team>` - request an alliance with another team. Owner only.
- `/team ally accept <team>` or `/team ally deny <team>` - accept or deny an alliance request. Owner only.
- `/team ally remove <team>` - end an alliance. Owner only.
- `/team allies` - view current allied teams and pending alliance requests.
- `/teamglow`, `/allyglow`, `/teammateglow` - privately highlights teammates and allied teams for your client with a glowing ally marker.
- `/powerinfo` - opens superpower information.
- `/stormcaller on|off|toggle|status` - Stormcaller-only lightning strike toggle.
- `/home`, `/sethome`, `/delhome`, `/homes` - home system.
- `/spawn` - OP-only spawn command on this server.
- `/back` - OP-only back command on this server.
- `/veinminer` - vein miner toggle if enabled.
- `/veinminer addblock [block]` or `/veinminer add block [block]` - add the block you are looking at, holding, or typing to your personal veinminer list.
- `/veinminer removeblock [block]` or `/veinminer remove block [block]` - remove a block from your personal veinminer list.
- `/veinminer blocks` - list your personal veinminer blocks.
- `/msummon` - Sovereign-only command to summon 1 stored hostile mob.
- `/msummon <amount>` - Sovereign-only command to summon multiple stored hostile mobs.
- `/msummon despawn` - Sovereign-only command to unsummon active mobs.

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
- `/smite <player>` - strikes a player with harmless lightning effects.
- `/freeze <player> [on|off]` - freezes or toggles an online player.
- `/unfreeze <player>` - unfreezes an online player.
- `/viewteamvault <team>`, `/teamvaultsee <team>`, `/tvaultsee <team>` - owner-only safe team vault inspector.
- `/spawnprotect` - show spawn protection status.
- `/spawnprotect allow <player>` - let a non-OP player build and edit inside protected spawn.
- `/spawnprotect remove <player>` - remove a player from the spawn build allowlist.
- `/spawnprotect radius <blocks>` - change the protected spawn radius.
- `/spawnprotect on` or `/spawnprotect off` - enable or disable spawn protection.
- `/startsmp [graceMinutes]` - expands the world border and starts grace timing. The optional number controls PvP grace for this launch.
- `/startsmp reset` - resets the start state.
- `/startsmp lock` - puts the world back into the pre-start lockdown.
- `/startsmp barrier` - reapplies the configured pre-start barrier around spawn.
- `/unban <player>` - profile unban helper.

Permissions are defined in `src/main/resources/paper-plugin.yml`.
