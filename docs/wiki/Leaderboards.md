# Leaderboards

Leaderboards are persistent server stats saved in the SMPCore database.

Open them with:

- `/leaderboards`
- `/leaderboard`
- `/lb`
- `/topstats`

## Current Boards

- `Player Kills` - player kills credited by Minecraft's killer tracking.
- `Deaths` - player deaths.
- `Boss Kills` - custom boss kills credited when a tracked boss dies.
- `Boss Damage` - total damage dealt to custom bosses.
- `Boss Fights` - custom boss fight participations.
- `Mob Kills` - non-player mob kills.
- `Playtime` - time spent online, saved every minute, when players leave, and refreshed before the playtime board opens.
- `My Boss Reports` - your recent boss fights, outcome, rank, damage, healing received, and whether double drops were active.

## Notes

- Stats survive restarts.
- `/playtime` flushes your current live session first, then shows your saved total.
- The playtime leaderboard flushes online players before loading so it does not lag behind active sessions.
- The menu has back buttons and is linked from `/menu`.
- Boss kills and failures post a server-wide damage report.
- Boss kills are announced publicly so big progression moments feel visible.
- OPs bypass the pre-start lockdown, but regular players cannot use leaderboard commands before `/startsmp`.
