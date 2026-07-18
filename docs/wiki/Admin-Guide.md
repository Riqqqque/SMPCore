# Admin Guide

For a fresh or migrated server, follow the [Admin Setup Checklist](Admin-Setup-Checklist) in order. It covers exact spawn, launch access, the SMP start, every core NPC, Boss Dungeon points, the duel arena, tavern stations, market stalls, collectibles, Bedrock checks, and the final smoke test.

Quick references:

- [Commands](Commands) - categorized player and staff command reference.
- [NPC Directory](NPC-Directory) - every spawnable NPC and its purpose.
- [Inventory Safety and Recovery](Inventory-Safety-And-Recovery) - backpacks, death chests, exact death snapshots, and transaction recovery.
- [Player Shops](Player-Shops) - chest shops, stall creation, templates, and restore rules.
- [Boss Dungeon](Bosses-And-Rituals) - costs, combat rules, queue, arena, and test controls.
- [Bedrock and Geyser](Bedrock-And-Geyser) - connection, controls, packs, mappings, and verification.

## The Eleventh Oath

The story layer uses the existing NPC, boss, quest, item, and dungeon IDs. It never replaces their normal menus, objectives, costs, loot, or recovery rules.

The editable files are copied to the SMPCore plugin data folder on first start:

- `story.yml` - feature switches, dialogue presentation, and mappings from existing NPC IDs.
- `story-dialogue.yml` - priority, trigger, conditions, lines, cooldown, and actions for NPC and event dialogue.
- `story-codex.yml` - the eight journal categories and their entries.
- `story-bosses.yml` - boss entrance, phase, low-health, defeat, and memory lines by internal boss ID.
- `story-ambient.yml` - safe personal sound and particle effects for memory unlocks.

Run `/veil admin reload` after editing them. An invalid dialogue reference is rejected with an actionable console warning; unrelated story integrations stay enabled.

Existing players are migrated from successful rows in the existing boss-fight reports. The migration does not guess kills that were never recorded and does not replay Mayor rewards. Use `/veil admin migrate <player>` to check the history again.

To add dialogue, copy a node in `story-dialogue.yml`, give it a unique ID, select a supported trigger/context, and reference only journal entries that exist in `story-codex.yml`. One-time nodes are persisted per player. Repeatable nodes should have a cooldown.

Permissions:

- `smpcore.story` - player journal and story commands.
- `smpcore.story.admin` - story inspection, migration, reload, replay, and state changes.

See [The Eleventh Oath](The-Eleventh-Oath) for the spoiler-light player page and `docs/Eleventh-Oath-Manual-Test-Checklist.md` for the release checklist.

## Boss Control

- `/bosses` opens the admin boss GUI.
- Choose Spawn or Despawn mode, then click a boss.
- `/bosses clearall` removes every tracked custom boss.
- `/bosses status` prints active boss counts.
- `/bossloadout` opens ten one-click kits built from gear available before each boss fight.
- `/bossloadout <boss>` equips a kit directly. `/bossloadout clear` removes only generated test gear.
- Replaced armor, offhand items, and used hotbar slots move into the inventory. If it is full, they drop at the admin's feet.

## Bedrock Checks

- `/smpcore bedrock` reports Floodgate, Geyser, ViaVersion, and detected online Bedrock players.
- Paper 26.2 needs ViaVersion in the current Geyser connection path.
- See [Bedrock and Geyser](Bedrock-And-Geyser) for the deployment and gameplay checklist.

## Spawner Control

- `/spawner info` inspects the targeted spawner within 8 blocks.
- `/spawner reset` resets the targeted spawner to default modifiers.
- Creative pick-block on custom spawners requires `smpcore.spawner.admin`.

## Item Control

- `/legendary give <item> [player]` gives legendary items.
- `/customitem give <item> [player]` gives non-legendary custom items, boss trophies, Veil relics, and utility items.
- `/corruptionstation give [player]` gives a Corruption Anchor. Place it where players should corrupt items.
- `/corruptionstation list` shows placed Corruption Anchors.
- `/reforger spawn` places Brannik at your location.
- `/reforger remove` removes the nearest Brannik within 6 blocks.
- `/reforger stone [player]` gives a Reforge Stone for testing.
- `/priest spawn` places Father Aldren at your location.
- `/priest remove` removes the nearest Father Aldren within 6 blocks.
- `/artificer spawn` places Orin the Artificer at your location. His player menu explains reforging, awakening, corruption, all orb types, the Runic Loom, Fate Crucible, Mythic Forge, Salvaging Depot, XP Lectern, and the recommended upgrade order.
- `/artificer remove` removes the nearest Orin within 6 blocks. `/artificer list` shows every placed Orin.
- `/itemaudit <player> [item]` checks item origin logs.
- OPs and players with `smpcore.staff` receive live audit alerts for suspicious tracked custom item activity.
- `Duplicate tracked item` means the same tracked ID was found in more than one place.
- `Moved without a known handoff` means the item changed owner during a scan without a matching drop, pickup, craft, or admin give record.

## Spawn Protection

- `/setspawn` sets the exact server spawn, including facing direction. World spawn spread is forced off.

- `/spawnprotect` shows the marked region, active flags, fallback radius, and allowed builders.
- `/spawnprotect pos1` and `/spawnprotect pos2` mark opposite corners of the protected spawn region. The region protects every Y level by default.
- `/spawnprotect check` shows whether the block you are standing in is protected and whether you bypass it.
- `/spawnprotect see` shows a short particle outline of the protected spawn area to you only.
- `/spawnprotect clean` removes loaded stray mobs inside protected spawn.
- `/spawnprotect flag <flag> on|off` toggles a protection flag. `on` means spawn blocks that behavior. `off` means spawn allows it.
- Common flags: `mob-spawns` blocks mob spawning, `mob-entry` keeps outside mobs out, `natural-decay` blocks leaf decay and similar changes, `crop-trample` protects spawn farms, and `weather-lock` keeps the spawn world clear.
- `/spawnprotect allow <player>` lets a non-OP player build and edit protected spawn.
- `/spawnprotect remove <player>` removes that access.
- `/spawnprotect clearregion` clears the marked region and falls back to radius protection.
- `/spawnprotect radius <blocks>` changes the fallback radius. Default is 150 blocks.
- `/spawnprotect on` or `/spawnprotect off` toggles the system.
- `/spawnprotect stick` gives the public-interaction marking stick. Use it on a protected block that normal players should be allowed to operate.
- `/spawnprotect public list` shows all exact public-use marks. `/spawnprotect public clear confirm` removes them.

Corruption Anchors, Awakening Tables, Runic Looms, and Fate Crucibles are automatically public-use when they are inside protected spawn. Private utilities such as Salvaging Depots, XP Lecterns, and Agricultural Pylons still need an exact public-use mark if staff intentionally places one at spawn.

## Veil Relic Admin Examples

- `/customitem give ashen_verdict Rique`
- `/customitem give crimson_guard_chestplate Rique`
- `/customitem give bloodbound_banner Rique`
- `/customitem give rift_lens Rique`

## Tools and Gear Admin Examples

- `/customitem give spelunkers_lantern Rique`
- `/customitem give surveyors_lens Rique`
- `/customitem give menders_kit Rique`
- `/customitem give xp_lectern Rique`
- `/customitem give corruption_anchor Rique`

## Duel Arena

- Configure both primary fighter spawns, the spectator view, and opposite arena corners with `/duel admin set ...`. Optional `fighter1b`, `fighter1c`, `fighter2b`, and `fighter2c` points give duos and trios exact starts; otherwise the plugin derives nearby team positions.
- Run `/duel admin status` before opening matchmaking. The optional lobby point is not required.
- Spawn Cassian with `/duelmaster spawn` and place the two physical boards with `/duel admin leaderboard wins` and `/duel admin leaderboard bets`.
- `/duel admin forcestop` restores fighters, spectators, arena blocks, and outstanding bets without recording a winner.
- Duel state uses `duel-arena.yml`, `duel-recoveries.yml`, `duel-escrow.yml`, and `duel-leaderboards.yml` under the SMPCore data folder. Keep these files with plugin data during a server move.

## Market Stalls

1. `/stall admin wand`
2. Left-click one corner and right-click the opposite corner.
3. Look directly at the sale sign inside the selection.
4. `/stall admin create <id> <price>`
5. Verify the rewritten sign, then `/stall admin snapshot <id> confirm` after every container and chest shop is clear.

Use `/stall admin list` to record template hashes. `/stall admin restore <id>` previews a restore and must be repeated within 15 seconds. A changed container holding any item blocks restoration, and templates never copy inventories.

## Exact Death Recovery

Use the protected restore flow, not manual inventory editing:

1. `/deathinventory list <player> [page]`
2. `/deathinventory view <player> [latest|id]`
3. Verify the linked death chest and original drops were not collected.
4. `/deathinventory restore <player> [latest|id]`
5. Read the checks, then `/deathinventory confirm` within 60 seconds.

The target must be online, alive, outside combat and boss fights, in Survival or Adventure, with every GUI, cursor, and crafting slot clear. A pre-restore backup is written first, and a snapshot can be restored only once.

## Recovery Tips

- If a boss hologram or bar looks stale, use `/bosses clearall`.
- Place Malakar at spawn with `/dungeonkeeper spawn`. Players use him to enter the shared Boss Dungeon; `/bossrituals` previews costs.
- Configure the arena with `/bossdungeon setentry`, `setfight`, `setspectator`, `setboss`, and `setkeeper`, then run `/bossdungeon` to verify every point.
- Include the complete primary world and SMPCore data when moving this season to production. Paper 26.2 stores the arena under `dimensions/minecraft/boss_dungeon` inside that primary world.
- If a custom item appears duplicated, use `/itemaudit`.
- Audit alerts do not delete items. They only notify staff and point to the matching `/itemaudit` command.
- If the world start state needs to be redone, use `/startsmp reset`.
- Launch access and the SMP start are separate. Use `/launchaccess open` when players may join, then use `/startsmp` only when the season should actually begin.
