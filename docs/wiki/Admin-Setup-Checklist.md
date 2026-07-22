# Admin Setup Checklist

This is the practical launch and rebuild order. Use [Admin Guide](Admin-Guide) for deeper recovery notes.

## 1. Verify the server

- Paper and API line: Minecraft/Paper 26.2.
- Java runtime: Java 25.
- Required runtime plugins for the intended setup: Citizens, Geyser, Floodgate, ViaVersion, HeadDatabase, GSit, FastAsyncWorldEdit, Terralith, and RuinedImage where used by the server.
- Confirm the SMPCore jar version with `/smpcore` or the startup log.
- Confirm the Java resource-pack URL and SHA-1 in both AMP's `server.properties` and `MinecraftModule.kvp`.
- Join once from Java and Bedrock before opening access.

## 2. Set exact spawn and protection

1. Stand at the desired arrival point and face the correct direction.
2. Run `/setspawn`.
3. Mark the protected corners with `/spawnprotect pos1` and `/spawnprotect pos2`.
4. Run `/spawnprotect check`, `/spawnprotect see`, and `/spawnprotect flags`.
5. Use `/spawnprotect stick`, then confirm the same block twice for any exact public button or container that is not automatically public.
6. Run `/spawnprotect clean` and verify turtle eggs, farmland, decorations, mobs, fluids, fire, explosions, pistons, and weather behave correctly.

Corruption Anchors, Awakening Tables, Runic Looms, and Fate Crucibles are automatically public-use in protected spawn. XP Lecterns, Salvaging Depots, and other private-base utilities require an exact public-interaction mark when intentionally placed at spawn.

## 3. Place core NPCs

At minimum:

```text
/spawnnpc spawn
/mayor spawn
/artificer spawn
/reforger spawn
/priest spawn
/dungeonkeeper spawn
/overseer spawn
/miner spawn
/farmer spawn
/witch spawn
/fisher spawn
/goblinhunter spawn
/beastwarden spawn
/bossbroker spawn
/blackmarket spawn
```

Place tavern and duel NPCs as needed:

```text
/brewmaster spawn
/adventurer spawn
/dealer spawn
/croupier spawn
/duelmaster spawn
```

Run `/<npc> list` after placement and `/spawnlife list` for ambient characters. See [NPC Directory](NPC-Directory) for the full list.

## 4. Boss Dungeon

Inside the dungeon, stand at and set each point:

```text
/bossdungeon setentry
/bossdungeon setfight
/bossdungeon setspectator
/bossdungeon setboss
/bossdungeon setkeeper
```

Then:

1. Run `/bossdungeon` and confirm all five points.
2. Put the internal Malakar at the keeper point.
3. Run `/bossdungeon test yule_the_minion`.
4. Use `/bossdungeon join`, `/bossdungeon spectate`, and `/bossdungeon reset` to verify recovery.
5. Run `/bossloadout <boss>` for each progression tier as needed.
6. Confirm the 10-second countdown, team auto-join, invitations, death spectating, loot chest, two-minute return, and arena projectile/item cleanup.

Test mode consumes no entry cost and creates no collectible boss loot.

## 5. Duel arena

Set primary points and full arena corners:

```text
/duel admin set fighter1
/duel admin set fighter2
/duel admin set spectator
/duel admin set corner1
/duel admin set corner2
```

Optional exact duo/trio points: `fighter1b`, `fighter1c`, `fighter2b`, and `fighter2c`.

Run `/duel admin status`, then spawn physical boards with:

```text
/duel admin leaderboard wins
/duel admin leaderboard bets
```

Test TNT, wind charges, cobwebs, crystals, anchors, water, lava, round timeout, death restore, spectator limits, and `/duel admin forcestop`.

## 6. Tavern

Look at the station block within eight blocks:

```text
/tavernadmin set slots
/tavernadmin set table
/tavernadmin set darts
/tavernadmin set rumors
```

Spawn independent hologram boards:

```text
/tavernadmin leaderboard spawn slots
/tavernadmin leaderboard spawn cards
/tavernadmin leaderboard spawn darts
/tavernadmin leaderboard spawn roulette
```

Run `/tavernadmin list` and test repeated play, pending payouts, disconnect cleanup, card host transfer, dart anti-autoclick timing, blackjack dealer distance, and local win announcements.

## 7. Public warps

Stand at the exact arrival point, face the direction players should face, and create the warp:

```text
/warp create Tavern
```

Players can immediately use `/tavern` or `/warp tavern`. Use `/warp move Tavern` after repositioning, `/warp info Tavern` to verify it, and `/warp delete Tavern` to remove it. Warp names cannot replace another server command.

## 8. Market stalls

For each stall:

1. Run `/stall admin wand`.
2. Left-click the first corner and right-click the opposite corner with the wand.
3. Look directly at the sale sign inside that selection.
4. Run `/stall admin create <id> <price>`.
5. Confirm the sign reads `Stall for <price> Essence` and `Right-Click`.
6. Clear every container and active chest shop.
7. Save the launch template with `/stall admin snapshot <id> confirm`.
8. Run `/stall admin list` and record the template hash.
9. Preview `/stall admin restore <id>` without confirming, then test one disposable stall restore.

Back up `market-stall-templates.yml` with the launch world and SMPCore data.

## 9. Spawn life and collectibles

Run `/goblins audit` after major spawn building changes. If the report finds overwritten or fully enclosed heads, use `/goblins audit prune`; the active hunt total and Mining Luck scaling update immediately.

- Place ambient NPCs with `/spawnlife spawn <type>`.
- Give hidden goblins with `/goblins give [amount]`; every placed head receives a unique collectible ID.
- Run `/goblins count` after placement.
- Test Biscuit fetch from start to return-home cleanup.
- Test Bone/Cod/Salmon feeding cooldowns.
- Run `/bedrockskulls scan [radius]` around custom decorative heads, then restart only when the bridge configuration requires it.

## 10. Launch gate and SMP start

Launch access and season start are separate.

1. `/launchaccess lock` keeps joining limited to configured owners.
2. `/launchaccess status` verifies the gate.
3. `/startsmp lock` enables the 15-block personal waiting barrier.
4. `/startsmp barrier` reapplies it if needed.
5. Confirm the real Overworld border is already 5,000 blocks wide and visible.
6. `/launchaccess open` allows the public to join while keeping them staged.
7. Run `/startsmp [graceMinutes]` only at the actual launch moment.

`/startsmp` sends the Season of the Veil introduction to everyone, removes the personal barrier, begins PvP grace, schedules the Nether for real Day 3, and the End for real Day 5. A positive `smp-start.nether-unlock-at` or `smp-start.end-unlock-at` UTC epoch-millisecond value overrides the matching day-based time. Scheduled openings announce themselves automatically.

For the current season, `smp-start.end-unlock-at` is `1784682000000`: July 21, 2026 at 7:00 PM America/Denver, or July 22 at 1:00 AM UTC.

If launch pacing changes, `/startsmp unlock nether` or `/startsmp unlock end` permanently opens that dimension for the current season and announces it to everyone. Repeating the command does not repeat the announcement.

## 11. Final smoke test

- `/menu`, every submenu, Back buttons, and live Essence values.
- `/settings` music toggles and Drop Safety.
- Java and Bedrock resource packs, skulls, menus, abilities, and anvil access.
- Every core NPC opens once without duplicate holograms.
- `/reliquary`, direct crafting, custom enchants, all major stations, backpacks, shops, stalls, and spawners.
- One normal death, death chest, and read-only `/deathinventory view`.
- One early boss test, one duel, one tavern game, and one team vault open/save.
- `/smpcore bedrock`, `/bossdungeon`, `/duel admin status`, `/spawnprotect`, `/startsmp status`, and `/launchaccess status` show no warnings.
- Review the latest server log after the test session.

Do not use a live restore, reset, force-stop, or SMP start command merely as a read-only test.
