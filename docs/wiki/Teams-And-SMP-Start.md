# Teams and SMP Start

## SMP Start Flow

Before `/startsmp`, the configured world stays inside the configured start border and non-OP players are locked down. When staff runs `/startsmp`, the border expands and the selected PvP grace period starts.

Default intent:

- Tiny pre-start border.
- Non-OP players cannot break blocks or use plugin commands before start.
- Non-OP players inside the start barrier do not lose hunger.
- Non-OP players inside the start barrier do not take fall damage.
- Hostile mobs are blocked from spawning inside the start barrier.
- `/startsmp` expands to the real border.
- `/startsmp [graceMinutes]` expands the border and uses that PvP grace length.
- After `/startsmp`, the plugin does not reapply the configured border on restart. Staff can safely use vanilla `/worldborder set ...`, and that live border remains in control.
- Default one-hour post-start grace period.
- PvP is blocked during grace.
- The PvP grace period shows a countdown bossbar.
- OPs bypass the lockdown tools.

Useful staff commands:

- `/startsmp` - starts with the configured grace time.
- `/startsmp 90` - starts with a 90 minute PvP grace period.
- `/startsmp barrier` - reapplies the pre-start barrier around spawn.
- `/startsmp lock` - re-enables lockdown if staff need to reset before launch.
- `/startsmp reset` - clears the started state.

## Teams

Teams support:

- Team creation and membership.
- Team colors.
- Owner-controlled team renames.
- Owner-controlled team color changes.
- A searchable team browser through `/teams`.
- Team vault storage through `/tvault` or `/teamvault`.
- Team alliances that count as friendly for protections and ally glow without sharing vaults.
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
- `/team invite <player>` - invite a player. Owner only.
- `/team ally add <team>` - request an alliance with another team. Owner only.
- `/team ally accept <team>` - accept an incoming alliance request. Owner only.
- `/team ally deny <team>` - deny an incoming alliance request. Owner only.
- `/team ally remove <team>` - end an alliance. Owner only.
- `/team allies` - show allies and pending alliance requests.
- `/team leave` - leave your current team.
- `/team disband` - disband your team. Owner only.
- `/team info` - view team members, owner, color, and allies.
- `/tvault` or `/teamvault` - open your team vault.

Allies are treated as friendly for team-safe plugin systems such as protection checks and private ally glow. They do not get access to your `/tvault`, and your team does not get access to theirs.

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

Unique legendary and mythic relics cannot be stored in team vaults. If one somehow gets into a vault from old data or a weird edge case, it is moved back to the player who opens the vault. If their inventory is full, it drops safely at their feet instead of being deleted.
