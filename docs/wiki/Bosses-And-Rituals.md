# Boss Dungeon

Survival bosses are fought in the shared Boss Dungeon. Players no longer build shrines or summon bosses in normal worlds.

## Entering and summoning

1. Speak to **Malakar the Gatekeeper** at spawn.
2. Enter the protected dungeon.
3. Speak to Malakar inside the arena.
4. Pick a boss and review the materials.
5. Confirm the summon. Essence and materials are consumed only when your queued fight actually begins.

Only one boss can be active at a time. If the arena is busy, confirmed summons enter a queue and keep their Essence and materials until their turn. Visitors can still enter as spectators.

When a queued summon reaches the front, a **10-second boss countdown** begins. Teammates already inside the dungeon join the fight automatically. Online teammates and allied-team members elsewhere receive an invitation and can use its chat button or `/bossjoin accept` during that countdown. The short delay gives everyone time to equip gear and accept before the boss appears.

Anyone else in the dungeon becomes a spectator. A participant who dies or leaves cannot rejoin that fight and watches the survivors instead.

`/bossrituals` opens the same boss catalog. Outside the dungeon it is a cost preview; summoning still requires the Gatekeeper and arena.

## Summon costs

Essence is the main repeatable entry stake. The former shrine materials remain as a smaller themed cost, but their bulk quantities are reduced. Only the summoner pays; teammates, allies, and spectators join free.

| Tier | Boss | Essence | Materials |
|---:|---|---:|---|
| 1 | The Veilbound Marshal | 25 | 1 Bell, 1 Golden Sword, 1 Soul Sand, 2 Gold Blocks |
| 2 | Cindervale Arbalest | 40 | 1 Soul Campfire, 1 Bow, 3 Bone Blocks |
| 3 | The Gloam Matriarch | 60 | 1 Cobweb, 1 Fermented Spider Eye, 1 Moss Block, 2 Black Candles |
| 4 | The Briarveil Regent | 80 | 1 Mangrove Roots, 1 Spore Blossom, 1 Moss Block, 2 Oak Saplings |
| 5 | Thalassa the Drowned Veil | 105 | 1 Conduit, 1 Heart of the Sea, 1 Prismarine, 2 Sea Lanterns |
| 6 | The Argent Confessor | 130 | 1 Anvil, 2 Iron Blocks, 1 Smithing Table |
| 7 | Asterion the Rift Oracle | 160 | 1 End Rod, 1 Eye of Ender, 1 Purpur Block, 2 End Stone Bricks |
| 8 | Morvessa the Runebloom Witch | 195 | 1 Brewing Stand, 1 Dragon's Breath, 1 Amethyst Block, 2 Flowering Azalea Leaves |
| 9 | Noctyr the Veil Warden | 235 | 1 Sculk Shrieker, 1 Echo Shard, 1 Sculk Catalyst, 1 Redstone Block, 2 Soul Lanterns |
| 10 | Corrupted Oathkeeper | 300 | 1 Respawn Anchor, 1 Nether Star, 1 Crying Obsidian, 2 Magma Blocks, 2 Sculk Catalysts |

Only ordinary versions of these materials count. Custom items disguised as a vanilla material cannot be consumed as summon payment.

Losing a started fight consumes the entry payment. If the boss fails to spawn, an administrator interrupts the encounter, or the server shuts down mid-fight, the plugin returns both portions. Offline Essence refunds are saved and delivered on the player's next join.

## Combat difficulty

Boss mechanics become lethal from tier 2 onward. A failed mechanic removes 72% of maximum health at tier 2, then 78%, 84%, 88%, 92%, 96%, and 98% from tier 8 onward. Repeated floor and beam hazards use smaller percentage hits, but staying in them will still kill quickly.

From tier 5 onward, successfully clearing a positional mechanic gives a small reward. Movement checks grant Speed I for four seconds, while escape and sigil checks restore up to two hearts. Oath Rings also require a clean spread before the reward applies.

Every mechanic starts with a title, a short chat explanation, the exact failure damage, an action-bar reminder, and one warning sound. Mechanics that change instructions mid-cast announce the new step once. Repeated action-bar updates do not repeat chat or sounds.

Late bosses also have short casts that seal all healing. Natural regeneration, potions, powers, enchants, relics, and healing from teammates cannot restore health until the cast ends. The seal appears in the title, chat explanation, and action bar.

Extra players increase boss health, attack damage, ability pressure, target counts, and mechanic frequency. The scaling follows the current number of surviving fighters so a larger group cannot erase a boss with raw damage alone.

Class identity still works in normal combat, but boss encounters resist effects that skip their mechanics. Bosses resist health-deleting backstabs, hard crowd control, summon swarms, and Infinity projectile blocking. Time Stop, Domain Expansion, Voidstep, Wayfarer portals, Oath Summon, and Skybound flight cannot move players around an active encounter. Failed mechanics also bypass Phoenix Rebirth and Graveborn's second chance. Druid blessings are unchanged, though healing blessings still obey healing-seal casts.

Every summon opens with a short protected reveal. The boss cannot attack or take damage until the final sound and expanding ring release it. Each boss has its own entrance spiral, attack trail, impact sound, and phase-shift burst; the effects are paced so repeated hits do not stack audio or flood nearby clients.

Every boss also has a different original note-block battle theme. It begins with the encounter, loops without a gap through every phase, and stops when the boss dies, the fight resets, or the listener leaves. Fighters and dungeon spectators hear the same theme through the **Jukebox/Note Blocks** volume setting.

Use `/settings` to mute only the boss music. Mechanic warnings and combat sounds stay enabled so the fight remains readable.

Successful participants also build permanent progress with [Mogrik's Boss Mastery](Boss-Mastery). Each boss has five cumulative ranks with Essence, common materials, utility relics, permanent Huntmarks, and unique gear without giving away rare progression materials.

## Arena rules

- Building, breaking, explosions, natural mob spawning, fire spread, weather, PvP, and random block ticks are disabled.
- The arena is a void world with a safety return if a player falls out.
- The supplied arena world is installed automatically the first time the plugin starts.
- Boss loot and progression are unchanged; only the summoning location and interaction changed.
- A defeated boss creates a protected participant-only chest. It disappears when empty or after two minutes, then everyone returns to spawn.
- Player-dropped items are owner-tagged. Anything still in the arena at cleanup is returned to its owner; unclaimed boss loot returns to the summoner.

## Staff setup

Use `/dungeonkeeper spawn` while standing at the desired spawn location. Aliases: `/dungeonnpc` and `/bosskeeper`.

Set arena points while standing at each desired position:

- `/bossdungeon setentry` - idle arrival and lobby point.
- `/bossdungeon setfight` - participant arrival point.
- `/bossdungeon setspectator` - spectator and eliminated-player point.
- `/bossdungeon setboss` - boss spawn point.
- `/bossdungeon setkeeper` - expected position of the internal summoning NPC.
- `/bossdungeon` - show current points, phase, and queue length.
- `/bossdungeon loadouts` - open the one-click pre-boss test kit GUI. `/bossloadout <boss>` equips a kit directly.

Use `/bossqueue leave` to cancel a queued summon before it starts. After changing the keeper point, remove the old internal NPC with `/dungeonkeeper remove` and place it at the new point with `/dungeonkeeper spawn`.

The plugin creates a second Gatekeeper inside the dungeon automatically. `/bosses spawn` remains available to operators for testing, but survival players cannot use the old world shrine system.

On Paper 26.2, the arena is stored inside the primary world's `dimensions/minecraft/boss_dungeon` folder after its first load. Copy the complete primary world and SMPCore data when moving the season to another server. This preserves the arena and both Gatekeeper locations.
