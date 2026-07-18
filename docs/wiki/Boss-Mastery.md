# Boss Mastery

Mogrik the Bossbroker runs a permanent boss-hunting ledger. Place him with `/bossbroker spawn`, then players can speak to him to see every boss and claim earned rewards.

## How progress works

- Every eligible participant in a successful arena fight gets one victory for that boss.
- Admin test encounters, summons without a real victory, and spectators do not count.
- Existing recorded victories are imported when the system first starts.
- Each boss has five cumulative ranks at 1, 3, 6, 10, and 15 victories.
- Ranks must be claimed in order. A full inventory leaves the reward waiting instead of dropping or consuming the claim.

Ranks pay Essence, common boss materials, a guaranteed utility relic, a permanent Huntmark, and a unique mastery item. Mogrik never awards rare progression materials. The Oathkeeper's material rank pays Essence instead of Corrupted Essence.

## Huntmarks

Rank IV for each boss awards that boss's Huntmark. Claiming it permanently adds 4% damage against that specific boss. The compass is a keepsake; the bonus remains active while it is stored and duplicate marks do not stack.

Players who claimed an older Rank IV automatically receive its Huntmark the next time they open that boss's ledger page. The permanent bonus is based on claimed mastery progress, so it does not depend on keeping the item in an inventory.

The ten final items follow the boss path, including the four-piece **Bossbroker's Pursuit** armor set and the final **Eleventh Bell** trinket.

## Bossbroker's Pursuit

The Gloam Matriarch through Argent Confessor mastery paths award the hood, coat, leggings, and boots. Wearing all four gives 15% more damage against custom bosses and reduces ordinary boss attacks by 12%. Failed boss mechanics ignore the defense bonus.

## The Eleventh Bell

Hold the Bell in either hand and sneak-right-click near at least two hostile mobs. It marks up to eight for 15 seconds. Hitting one echoes 25% of that damage, capped at five hearts, to the other marks. The echo cannot kill by itself and never targets players, custom bosses, boss minions, NPCs, or familiars. Cooldown: 180 seconds.

## Staff tools

- `/bossbroker spawn|remove|list|refresh` - place or manage Mogrik. Aliases: `/bossbrokernpc`, `/mogrik`.
- `/bossmasteryadmin setkills <player> <boss> <amount>` - set test progress.
- `/bossmasteryadmin reset <player>` - clear one player's mastery ledger.

Mastery saves use an atomic file replacement and keep `boss-mastery.yml.previous` as the last rollback copy.
