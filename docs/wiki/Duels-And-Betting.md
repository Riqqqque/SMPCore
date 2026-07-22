# Duels and Betting

Veilward duels are low-risk arena fights. The plugin saves each fighter's exact inventory, armor, durability, effects, XP, game mode, and return location before entry. Consumables and temporary arena damage are reset between rounds, and the saved state returns when the match ends.

Every fighter starts every round at exactly 20 health. Max-health bonuses are temporarily normalized so extra hearts cannot decide a duel, then the player's original health, max-health behavior, and absorption return after the match.

Rounds also start at 20 hunger, 5 saturation, and zero carried exhaustion. After the countdown, sprinting, fighting, natural regeneration, and eating use normal vanilla hunger rules. The player's original hunger, saturation, and exhaustion return after the duel.

## Playing

- Use `/duel` or talk to Cassian the Fightmaster.
- Pick first to 1, 2, or 3, a ruleset, and a 1v1, 2v2, or 3v3 team size, then click **Find Duel**.
- Click **Challenge Player** to send the same setup directly to another online player or matching party. `/duel challenge <player>` does the same thing.
- Use the Duel Party menu or `/duel party invite <player>` to form an exact two- or three-player roster. The captain queues or challenges for the party.
- Open PvP allows normal PvP tools and healing.
- No Healing blocks healing during active rounds.
- Melee Only accepts direct melee damage only.
- Every round lasts 2 minutes 30 seconds. A knocked-out teammate spectates until the next round. Eliminating the whole opposing team wins the round; a timeout compares team damage dealt, then average remaining team health.
- Use `/duel spectate` to watch the active match and `/duel leave` to return. Spectators can move up to 29 blocks from the configured viewing point, but cannot fly underground or leave the viewing area.

Elytras, external storage, item dropping, arena escapes, and unrelated commands are disabled during a match. TNT, wind charges, cobwebs, crystals, anchors, buckets, and ordinary temporary PvP blocks can be used. TNT still hurts fighters but never damages the arena. Water, lava, temporary blocks, projectiles, and explosives are cleared after every round.

## Betting

Use `/duel bet` during the 15-second pre-fight window. Select a side, adjust the Essence amount, and lock it. `/duel bet essence <amount>` accepts any exact amount up to the player's current balance. Fighters may only back their own team; other players can back either side.

Viewers outside the arena may also hold a normal item stack and click **Bet Held Stack**, or use `/duel bet item <amount>`. Backpacks, custom relics, bundles, and filled containers are blocked. Each exact item forms its own pool, so dirt never competes with diamonds and an unmatched item wager is refunded. Arena fighters and spectators use Essence only because their exact inventory is restored after the match.

Winning bettors split each matching pool in proportion to their stake. If nobody backed one side, the match is cancelled, or the server safely stops the duel, stakes are refunded. Essence over the balance cap and item payouts without an empty slot remain safely pending instead of dropping or disappearing.

## Admin Setup

Stand at each point and run:

- `/duel admin set fighter1`
- `/duel admin set fighter1b` and `/duel admin set fighter1c` (optional)
- `/duel admin set fighter2`
- `/duel admin set fighter2b` and `/duel admin set fighter2c` (optional)
- `/duel admin set spectator`
- `/duel admin set corner1`
- `/duel admin set corner2`

`corner1` and `corner2` must enclose the complete playable arena and its height. The extra team spawns are optional; without them the plugin offsets teammates sideways from the two primary fighter points. Set them when the arena layout needs exact starting positions. `/duel admin set lobby` remains optional.

Use `/duel admin status` to verify setup and `/duel admin forcestop` to safely cancel a match. Spawn Cassian with `/duelmaster spawn`.

The duel menu does not contain leaderboard buttons. Spawn the hologram boards at your location with `/duel admin leaderboard wins` and `/duel admin leaderboard bets`. Remove the nearest one within eight blocks with `/duel admin removeleaderboard`.
