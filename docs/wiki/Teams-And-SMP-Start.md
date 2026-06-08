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
- Team vault storage through `/tvault` or `/teamvault`.
- Teammate protection hooks for compatible abilities.
- Owner crown unlocks when team size requirements are met.

Useful team commands:

- `/team create "name" [color]` - create a team.
- `/team colors` - list available colors.
- `/team color <color>` - change your team color. Owner only.
- `/team rename "new name"` - rename your team. Owner only.
- `/team name "new name"` - alias for `/team rename`.
- `/team invite <player>` - invite a player. Owner only.
- `/team leave` - leave your current team.
- `/team disband` - disband your team. Owner only.
- `/team info` - view team members, owner, and color.
- `/tvault` or `/teamvault` - open your team vault.

## Team Vaults

Team vaults are double chest storage for the team. Saves are serialized per team to avoid write-order bugs. If a team is renamed while the vault is open, the plugin saves and closes the vault first, migrates the stored vault row, and the next `/tvault` opens it under the new name.
