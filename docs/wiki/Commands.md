# Commands

Use `/help` in game for the short player list. This page groups the important commands by job so you do not need to memorize every alias. Tab completion shows valid subcommands, player names, boss IDs, and other accepted values.

Commands you cannot use are normally staff-only, class-only, or locked until `/startsmp`.

## Main Player Commands

### Menus and information

- `/menu` - main player hub.
- `/settings` - Drop Safety, Spawn Music, and Boss Music.
- `/reliquary` - custom items, recipes, Armory, utilities, and Mythic links.
- `/enchants` - custom enchant guide and boss-enchant recipes.
- `/powerinfo` - class information.
- `/familiar` - summon, dismiss, and inspect unlocked familiars.
- `/leaderboards` - persistent server stat boards.
- `/playtime` - your saved playtime.
- `/wiki` - public wiki link.
- `/changelog` - recent server changes.

Common aliases include `/smpmenu`, `/prefs`, `/lrecipes`, `/familiars`, `/lb`, `/ptime`, `/guide`, and `/changes`.

### Essence, storage, and trading

- `/essence` - current Essence balance.
- `/shops` - chest-shop setup guide and pending-payment commands.
- `/shops balance` - view payments waiting outside stock chests.
- `/shops collect` - collect waiting payments when inventory space is available.
- `/stall` - current rented-stall status.
- `/stall manager <player>` - toggle a trusted stall manager; they can maintain shops but still purchase normally.
- `/stall manager` or `/stall managers` - list your trusted managers.
- `/stall sell` - sell your stall back for 75% after removing player fixtures.
- `/stall transfer <player>` - offer ownership and its shops to an online player.
- `/stall accept` or `/stall deny` - answer a transfer offer.
- `/backpack label <text>` - add a 1-24 character suffix to the held backpack.
- `/backpack clear` - remove the held backpack's suffix.
- `/spinbet <player>` - offer the exact main-hand item shown in the confirmation menu.
- `/spinbet accept <player>` or `/spinbet deny <player>` - answer an item-bet offer.
- `/spinbet cancel` - cancel your unaccepted offer.
- `/spinbet claim` - recover an interrupted item-bet payout.
- `/blackjack claim` - recover an interrupted Blackjack payout; new games start only through Silas.
- `/roulette claim` - recover an interrupted material payout; new spins start only through Renn.
- `/bounties` (`/bountylist`, `/wanted`) - open a read-only, paginated list of every active player bounty.

### Bosses and story

- `/bossrituals` - preview all Boss Dungeon entry costs.
- `/bossjoin accept` or `/bossjoin deny` - answer a teammate or ally invitation during the 10-second countdown.
- `/bossqueue leave` - cancel your queued summon before it starts and before payment is taken.
- `/veil` or `/veil journal` - open The Eleventh Oath journal.
- `/veil objective` - current story objective.
- `/veil memories` - recovered boss memories in order.
- `/veil text` - chat-based journal fallback.
- `/veil skip` - skip the current personal dialogue sequence.
- `/veil choose <mend|bind|sever>` - make the final choice when it becomes available.

### Teams and community

- `/team create "name" [color]` - create a team.
- `/team invite <player>` - owner invitation.
- `/team leave` - leave your team.
- `/team disband` - owner-only team deletion; the vault must be empty.
- `/team info` - members, owner, color, and allies.
- `/team rename "name"` and `/team color <color>` - owner customization.
- `/team ally add|accept|deny|remove <team>` - alliance management.
- `/teams [search]` - searchable team browser.
- `/teamvault` - team storage.
- `/teamglow` - privately outline only your teammates through walls; the setting survives relogs and no extra name label is added.

### Duels

- `/duel` - polished matchmaking, challenge, party, spectate, and betting menu.
- `/duel find` - find a duel with the setup selected in the menu.
- `/duel challenge <player>` - challenge a player using the selected setup.
- `/duel queue <rounds> <open|noheal|melee> [team-size]` - direct 1v1, 2v2, or 3v3 queue command.
- `/duel party` - temporary duo/trio roster menu.
- `/duel party invite|accept|deny|leave|kick|disband|status` - roster commands.
- `/duel accept <player>`, `/duel deny`, `/duel leave` - challenge and match controls.
- `/duel spectate` - watch the current fight from the protected spectator area.
- `/duel bet essence <amount>` - wager any valid Essence amount during the betting window.
- `/duel bet item [amount]` - wager some or all of the held item stack.

Read [Duels and Betting](Duels-And-Betting) before placing a wager.

### Travel and survival

- `/spawn` - exact spawn from any normal dimension, with safe cross-world loading and a short cooldown. Active boss fighters cannot use it to escape an encounter.
- `/wild` (`/rtp`, `/randomtp`) - find safe Overworld ground inside the live world border. It stays away from protected spawn, loads candidate chunks asynchronously, and has a five-minute cooldown after a successful teleport.
- `/warp list` - show every public server warp.
- `/warp <name>` or `/<name>` - travel to a public warp, such as `/tavern`.
- `/sethome`, `/home`, `/homes`, `/delhome` - personal homes with safe cross-world travel. `/home` automatically leaves seats or mounts; combat, duel and boss arenas, world borders, and scheduled dimension locks still apply.
- `/veinminer on|off|status` - toggle or inspect Vein Miner.
- `/veinminer blocks` - personal vein list.
- `/veinminer addblock [block]` and `/veinminer removeblock [block]` - edit that list.
- `/steed summon`, `/steed recall`, `/steed status` - Wildbound Regalia mount controls.
- `/steed quest` - show your current Beastwarden lesson and exact progress.

## Class Commands

Only the matching class can normally use these. Every class command and alias is sealed for active boss fighters. Read [Classes](Superpowers) for costs, cooldowns, and boss restrictions.

- Veil Assassin: `/smokebomb`
- Juggernaut: `/unstoppableforce`
- Nightshade: `/shadow`, `/nightshadevision on|off|toggle|status`
- Deadeye: `/deadeyearrows on|off|toggle|status`
- Stormcaller: `/stormcaller on|off|toggle|status`
- Arcanist: `/arcanebook`
- Oathbound: `/oathsummon <player>`
- Bloodmender: `/bloodsacrifice`, `/curse`
- Shadow Monarch: `/msummon [amount]`, `/msummon despawn`
- Voidwalker: sneak-right-click with an empty main hand to Voidstep; `/voidstep` is a Bedrock/accessibility fallback and `/voidvision` toggles night vision
- Oracle Eye: `/xray`
- Wayfarer: `/travel <x> <y> <z> <dimension>`, `/travel close`
- The Honored One: `/infinity on|off|toggle|status`, `/domainexpansion`

## Staff Commands

### Launch, spawn, and access

- `/launchaccess status|open|lock|allowme` - owner-only launch gate. Opening access does not start the season.
- `/startsmp [graceMinutes]` - begin the season, remove staging lockdown, and start PvP grace.
- `/startsmp status|barrier|lock|reset|preview` - inspect or repair start state.
- `/startsmp unlock nether|end` - open a scheduled dimension early and announce it server-wide. Staff only.
- `/setspawn` - save exact spawn and facing.
- `/spawnprotect` - protection summary.
- `/spawnprotect pos1|pos2` - mark the permanent spawn cuboid.
- `/spawnprotect check|see|flags|list` - inspect protection.
- `/spawnprotect flag <flag> on|off` - change one protection rule.
- `/spawnprotect allow|remove <player>` - builder allowlist.
- `/spawnprotect stick` - give the public-interaction marking stick.
- `/spawnprotect public list` - list exact public-use blocks.
- `/spawnprotect public clear confirm` - clear exact public-use marks.
- `/spawnprotect clean` - remove loaded stray mobs from spawn.
- `/spawnprotect radius <blocks>` or `/spawnprotect clearregion` - fallback-radius controls.
- `/warp create <name>` - create a public warp at your exact position and facing.
- `/warp move <name>` - move an existing public warp to your position and facing.
- `/warp info <name>` and `/warp list` - inspect saved public warps.
- `/warp delete <name>` - permanently remove a public warp and its short command.

### Guide NPCs

Every main NPC root supports `spawn`, `remove`, `list`, and `refresh`:

`/spawnnpc`, `/corruptionwarden`, `/mayor`, `/artificer`, `/dungeonkeeper`, `/brewmaster`, `/adventurer`, `/dealer`, `/croupier`, `/duelmaster`, `/goblinhunter`, `/miner`, `/farmer`, `/witch`, `/overseer`, `/beastwarden`, `/bossbroker`, `/blackmarket`, and `/fisher`.

Run the root without a subcommand for its usage. See [NPC Directory](NPC-Directory) for names and player-facing jobs.

### Boss Dungeon and testing

- `/bossdungeon` - configured points, active phase, and queue state.
- `/bossdungeon setentry|setfight|setspectator|setboss|setkeeper` - save points while standing inside the dungeon.
- `/bossdungeon tp <point>` - jump to a saved point.
- `/bossdungeon test <boss>` - no-cost, no-loot encounter test.
- `/bossdungeon join|spectate|reset` - enter or safely clear a test.
- `/bossloadout [boss]` - realistic pre-boss gear; `/bossloadout clear` removes only generated kit items.
- `/bosses` - direct boss control GUI.
- `/bosses spawn|despawn <boss>`, `/bosses clearall`, `/bosses status` - tracked boss controls.
- `/bossmasteryadmin setkills <player> <boss> <amount>` or `reset <player>` - mastery testing.

### Duel arena and tavern

- `/duel admin set <lobby|fighter1|fighter1b|fighter1c|fighter2|fighter2b|fighter2c|spectator|corner1|corner2>` - arena points.
- `/duel admin status|forcestop` - verify or safely stop a duel.
- `/duel admin leaderboard wins|bets` - place the two physical boards.
- `/duel admin removeleaderboard` - remove the nearest duel board.
- `/tavernadmin set <slots|table|darts|rumors>` - register the targeted station block.
- `/tavernadmin remove <type>` and `/tavernadmin list` - station maintenance.
- `/tavernadmin leaderboard spawn <slots|cards|darts>` - place one game's physical board.
- `/tavernadmin leaderboard remove` - remove the nearest tavern board.
- `/tavernadmin sober <player>` - clear tavern intoxication and nausea without granting a morning drink buff.

### Market stalls and shops

- `/stall admin wand` - corner-selection wand.
- `/stall admin create <id> <price>` - create the selected stall using the sign you are looking at.
- `/stall admin setprice <id> <price>` - update price and sign.
- `/stall admin list` - owners, fixtures, and template hashes.
- `/stall admin restore <id>` - preview, then repeat within 15 seconds to restore.
- `/stall admin snapshot <id> confirm` - deliberately replace one launch template.
- `/stall admin snapshotall confirm` - replace every eligible unowned template.
- `/stall admin remove <id>` - remove an empty, unowned stall definition.

### Items, Essence, and recovery

- `/customitem give <item> [player]` - custom utility, boss trophy, and Veil items.
- `/customitem give veinwake_pick <player>` - give an audited Veinwake Pick to an online player.
- `/backpackadmin list|view|restore ...` and `/backpackadmin confirm|cancel` - inspect and safely restore bounded backpack snapshots.
- `/itemrecovery list|view|restore ...` and `/itemrecovery confirm|cancel` - inspect and restore one verified item from a risky custom-inventory record.
- `/legendary give <item> [player]` - legendary test item.
- `/reforger spawn|remove|list|refresh` and `/reforger stone [player]` - Brannik and test stones.
- `/priest spawn|remove|list|refresh` - Father Aldren.
- `/corruptionstation give [player]` and `/corruptionstation list` - Corruption Anchors.
- `/essenceadmin balance|progress <player>` - inspect an account.
- `/essenceadmin give|take|set <player> <amount>` - audited balance changes.
- `/essenceadmin reset <player> confirm` - destructive account reset.
- `/itemaudit <player> [item]` - item origin and anomaly history.
- `/deathinventory list <player> [page]` - exact saved death snapshots.
- `/deathinventory view <player> [snapshot]` - read-only inspection.
- `/deathinventory restore <player> [snapshot]`, then `/deathinventory confirm` - guarded restore.
- `/deathinventory cancel` - abandon the pending restore.
- `/familiaradmin list|give|take ...` - test any current familiar.
- `/beastwardenadmin complete|reset|armor|progress|preview ...` - Beastwarden testing and progress diagnostics.

### World systems and diagnostics

- `/spawner info|reset` - targeted custom spawner inspection.
- `/goblins give [amount]` and `/goblins count` - hidden collectibles.
- `/goblins see [on|off|refresh]` - privately mark every loaded goblin head through walls for the executing admin.
- `/goblins audit` - check every registered goblin against the live world; add `prune` to remove overwritten or fully enclosed heads and update the active total.
- `/spawnlife spawn|remove|list <type>` and `/spawnlife refresh` - ambient characters.
- `/bedrockskulls register|scan [radius]|status` - Geyser custom-head mappings.
- `/smpcore bedrock` - Floodgate, Geyser, ViaVersion, and online Bedrock status.
- `/veil admin reload|status|setchapter|setstage|flag|unlock|lock|replay|reset|debug|migrate ...` - story maintenance.

### General staff utilities

SMPCore also provides permission-gated moderation and convenience commands including `/fly`, `/speed`, `/heal`, `/feed`, `/god`, `/vanish`, `/freeze`, `/unfreeze`, `/invsee`, `/enderchest`, `/nick`, `/announce`, `/smite`, `/day`, `/night`, `/sun`, `/storm`, `/gmc`, `/gms`, `/gma`, and `/gmsp`.

Permissions are defined in `paper-plugin.yml`. The [Admin Setup Checklist](Admin-Setup-Checklist) gives the safest order for configuring a fresh server.
