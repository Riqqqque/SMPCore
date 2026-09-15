# Teams and SMP Start

## SMP Start Flow

Before `/startsmp`, normal players stay inside a personal barrier 15 blocks from exact spawn and are locked down. The real world border remains 5,000 blocks wide, so the wider spawn and its chunks are never clipped by the waiting area. When staff runs `/startsmp`, the personal barrier clears and the selected PvP grace period starts.

Default intent:

- 15-block personal pre-start barrier around exact spawn.
- Non-OP players cannot break blocks or use plugin commands before start.
- Non-OP players inside the start barrier do not lose hunger.
- Non-OP players inside the start barrier do not take fall damage.
- Hostile mobs are blocked from spawning inside the start barrier.
- OPs and the dimension-lock bypass permission are not given the personal waiting barrier, so staff can finish setup safely.
- The real Overworld border stays 5,000 by 5,000 blocks and centered on world spawn during staging.
- `/startsmp [graceMinutes]` removes the personal barrier and uses that PvP grace length.
- After `/startsmp`, the plugin does not reapply the configured border on restart. Staff can safely use vanilla `/worldborder set ...`, and that live border remains in control.
- Default one-hour post-start grace period.
- PvP is blocked during grace.
- The PvP grace period shows a countdown bossbar.
- The Nether normally opens at the beginning of real-life Day 3, exactly 48 hours after `/startsmp`.
- The End normally opens at the beginning of real-life Day 5, exactly 96 hours after `/startsmp`.
- Staff can configure an exact UTC epoch-millisecond unlock time with `smp-start.nether-unlock-at` or `smp-start.end-unlock-at`. A positive timestamp overrides that dimension's day-based time.
- The current End schedule is July 21, 2026 at 7:00 PM Mountain time. It opens and announces automatically even if nobody is attempting to enter at that moment.
- Portal and plugin teleports into a locked dimension are blocked, so homes or other commands cannot bypass the schedule.
- `/startsmp status` shows the live PvP, Nether, and End countdowns.
- OPs bypass the lockdown tools.

Useful staff commands:

- `/startsmp` - starts with the configured grace time.
- `/startsmp 90` - starts with a 90 minute PvP grace period.
- `/startsmp barrier` - reapplies the pre-start barrier around spawn.
- `/startsmp lock` - re-enables lockdown if staff need to reset before launch.
- `/startsmp reset` - clears the started state.

## Spawn Protection

Spawn has its own permanent anti-grief protection after the SMP starts. By default, the configured spawn world is protected in a 150 block horizontal radius around the world spawn point.

Protected actions include block breaking, block placing, bucket use, fluid flow, fire spread, block burn, explosions, pistons moving blocks into or out of the area, mob spawns, outside mobs walking in, leaf decay and similar natural changes, crop trampling, spawn-world weather lock, stray mobs already inside, endermen and other entity block changes, item frames, paintings, armor stands, and similar decorative entity edits.

OPs and players with `smpcore.spawnprotect.bypass` can build there. Staff can also allow specific non-OP players:

- `/spawnprotect` - show current status.
- `/spawnprotect pos1` and `/spawnprotect pos2` - mark opposite corners for the protected spawn region. Spawn regions protect all Y levels by default.
- `/spawnprotect check` - check whether your current block is protected and whether you bypass it.
- `/spawnprotect see` - show a short particle outline of protected spawn to you only.
- `/spawnprotect clean` - remove loaded stray mobs inside protected spawn.
- `/spawnprotect flag <flag> on|off` - toggle a protection flag. `on` blocks that behavior; `off` allows it. Common flags include `mob-spawns`, `mob-entry`, `natural-decay`, `crop-trample`, and `weather-lock`.
- `/spawnprotect allow <player>` - allow that player to build and edit protected spawn.
- `/spawnprotect remove <player>` - remove their spawn build access.
- `/spawnprotect list` - list allowed builders.
- `/spawnprotect clearregion` - clear the marked region and use radius protection.
- `/spawnprotect radius <blocks>` - change the fallback protected radius.
- `/spawnprotect on` or `/spawnprotect off` - toggle protection.

## Teams

Teams support:

- Team creation and membership.
- Team colors.
- Owner-controlled team renames.
- Owner-controlled team color changes.
- A searchable team browser through `/teams`.
- Team vault storage through `/tvault` or `/teamvault`.
- Team alliances that count as friendly for protections and ally glow without sharing vaults.
- Owner-appointed captains who can invite members, control friendly damage, and sever alliances without gaining alliance-creation, kick, disband, or ownership permissions.
- Teammate damage that is disabled by default. Allied PvP requires both teams to enable it.
- Owner crown unlocks when team size requirements are met.

Useful team commands:

- `/team create "name" [color]` - create a team.
- `/teams [search]` - open the team browser. Searching sorts by best team-name match.
- `/team list` - open the team browser.
- `/team search <name>` - open the team browser filtered to the closest team-name matches.
- `/team colors` - list available colors.
- `/team color <color>` - change your team color. Owner only.
- `/team rename "new name"` - rename your team. Owner only.
- `/team name "new name"` - alias for `/team rename`.
- `/team invite <player>` - invite a player. Owner or captain.
- `/team captain add <member>` - promote a member to captain. Owner only.
- `/team captain remove <member>` - remove a captain. Owner only.
- `/team captain list` - list current captains.
- `/team damage [on|off|toggle|status]` - control teammate and ally damage. Owner or captain.
- `/team ally add <team>` - request an alliance with another team. Owner only.
- `/team ally accept <team>` - accept an incoming alliance request. Owner only.
- `/team ally deny <team>` - deny an incoming alliance request. Owner only.
- `/team ally remove <team>` - end an alliance. Owner or captain.
- `/team allies` - show allies and pending alliance requests.
- `/allysethome <name> [ally team]` - set a shared alliance home. Team leader only.
- `/allyhome <name> [ally team]` - teleport to a shared alliance home.
- `/allyhomes [ally team]` - list the homes shared by your active alliances.
- `/allydelhome <name> [ally team]` - delete a shared alliance home. Team leader only.
- `/team leave` - leave your current team.
- `/team disband` - disband your team. Owner only.
- `/team info` - view team members, owner, color, and allies.
- `/tvault` or `/teamvault` - open your team vault.

Allies are treated as friendly for team-safe plugin systems such as protection checks and private ally glow. They do not get access to your `/tvault`, and your team does not get access to theirs. Team damage starts disabled. Same-team PvP works only after an owner or captain enables it. Damage between allied teams works only while both teams have enabled it, so one team cannot expose the other without permission.

Alliance homes are separate from personal homes and have no count limit. Either team leader may set or delete homes for that exact alliance, and every member of both teams may use them. If either leader ends the alliance, or either team disbands, those shared homes are permanently deleted. They do not return if the teams ally again. The same combat, duel, boss, world-border, dimension-lock, safe-landing, and cross-world checks used by `/home` also protect `/allyhome`.

Player-kill weapon progress never counts against current teammates or allies. Leaving or disbanding a team also keeps every former teammate pair ineligible for one month. A blocked former-teammate kill tells the attacker how much cooldown time remains.

Team hopping is limited separately. Three voluntary team exits within seven days trigger a 24-hour lock during which that player cannot accept a team invite or create another team. Owner-disbanding counts as an exit for the owner; members removed by somebody else's disband are not penalized. Join attempts show the remaining lock time.

## Team Browser

The team browser is opened from `/teams`, `/team list`, `/team search <name>`, or the main `/menu`.

It shows:

- Team owner.
- Member count and online count.
- Team color.
- Player kills.
- Deaths.
- K/D ratio.
- Boss kills.
- Boss damage.
- Total tracked playtime.
- A short member preview.

The browser loads team membership from memory and pulls player stat totals in one batched database read. This keeps the menu lightweight even when many teams exist. Clicking a team sends a quick chat summary. The barrel button opens your own team vault if you are in a team.

## Team Vaults

Team vaults are double chest storage for the team. Saves are serialized per team to avoid write-order bugs. If a team is renamed while the vault is open, the plugin saves and closes the vault first, migrates the stored vault row, and the next `/tvault` opens it under the new name.

The vault must be empty before the last member leaves or the owner disbands the team; this prevents team deletion from destroying stored items.

Unique legendary and mythic relics cannot be stored in team vaults. If one somehow gets into a vault from old data or a weird edge case, it is moved back to the player who opens the vault. If their inventory is full, it stays in the vault until they make space.

## Tab List

Press Tab to see every online, non-vanished player, even when they are in a different world. Entries are ordered by server role and Veil Overseer rank, then show the player's team and name with spacing that keeps long labels readable.

Authority titles are social progression, not combat buffs: Veilmarked at 0, Veil Deputy at 1, Veil Marshal at 3, and Season Warden at 5. Staff at Authority 0 show only their staff title; earned seasonal ranks appear beside it without hiding the player's team.
