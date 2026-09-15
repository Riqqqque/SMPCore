# Boss Dungeon

Survival bosses are fought in the shared Boss Dungeon. Players no longer build shrines or summon bosses in normal worlds.

You can eat normally anywhere in the dungeon, including while aiming at the protected arena floor or walls. Mechanics that explicitly seal healing still prevent health recovery for their announced duration, but they do not stop food consumption or hunger restoration.

## Entering and summoning

1. Speak to **Malakar the Gatekeeper** at spawn.
2. Enter the protected dungeon.
3. Speak to Malakar inside the arena.
4. Pick a boss and review the materials.
5. Confirm the summon. Essence and materials are consumed only when your queued fight actually begins.

Only one boss can be active at a time. If the arena is busy, confirmed summons enter a queue and keep their Essence and materials until their turn. Visitors can still enter as spectators.

When a queued summon reaches the front, a **10-second boss countdown** begins. Teammates already inside the dungeon join the fight automatically. Online teammates and allied-team members elsewhere receive an invitation and can use its chat button or `/bossjoin accept` during that countdown. The short delay gives everyone time to equip gear and accept before the boss appears.

Anyone else in the dungeon becomes a spectator. A participant who dies or leaves cannot rejoin that fight and watches the survivors instead.

Spectators use a protected free-camera viewing area above the arena. Crouch to descend and jump to rise. The camera can move down close to the fight floor without entering it, while the boundary still blocks the void, unloaded edges, and entity-camera mode. Spectators cannot attach or teleport to fighters through Minecraft's spectator target menu. Reaching an edge holds the camera there instead of snapping it back to the starting point. Use `/spawn` to leave.

`/bossrituals` opens the same boss catalog. Outside the dungeon it is a cost preview; summoning still requires the Gatekeeper and arena.

Malakar's entrance menu also includes **Boss Mechanics**. The field guide lists every boss in progression order, the phase where each major mechanic appears, and the action players should take to clear it.

## Summon costs

Every boss costs Essence plus exactly two themed items: one ritual focus and one catalyst. Only the summoner pays; teammates, allies, and spectators join free.

| Tier | Boss | Essence | Materials |
|---:|---|---:|---|
| 1 | The Veilbound Marshal | 25 | 1 Bell, 1 Golden Sword |
| 2 | Cindervale Arbalest | 40 | 1 Soul Campfire, 1 Bow |
| 3 | The Gloam Matriarch | 60 | 1 Cobweb, 1 Fermented Spider Eye |
| 4 | The Briarveil Regent | 80 | 1 Mangrove Roots, 1 Spore Blossom |
| 5 | Thalassa the Drowned Veil | 105 | 1 Prismarine Bricks, 1 Water Bucket |
| 6 | The Argent Confessor | 130 | 1 Anvil, 1 Iron Block |
| 7 | Asterion the Rift Oracle | 160 | 1 End Rod, 1 Eye of Ender |
| 8 | Morvessa the Runebloom Witch | 195 | 1 Brewing Stand, 1 Dragon's Breath |
| 9 | Noctyr the Veil Warden | 235 | 1 Sculk Shrieker, 1 Echo Shard |
| 10 | Corrupted Oathkeeper | 300 | 1 Respawn Anchor, 1 Nether Star |

Only ordinary versions of these materials count. Custom items disguised as a vanilla material cannot be consumed as summon payment.

Losing a started fight consumes the entry payment. If the boss fails to spawn, an administrator interrupts the encounter, or the server shuts down mid-fight, the plugin returns both portions. Offline Essence refunds are saved and delivered on the player's next join.

## Combat difficulty

Boss mechanics become lethal from tier 2 onward. A failed mechanic removes 72% of maximum health at tier 2, then 78%, 84%, 88%, 92%, 96%, and 98% from tier 8 onward. Repeated floor and beam hazards use smaller percentage hits, but staying in them will still kill quickly. The Corrupted Oathkeeper uses its own staged ring damage described below.

From tier 5 onward, successfully clearing a positional mechanic gives a small reward. Movement checks grant Speed I for four seconds, while escape and sigil checks restore up to two hearts. Oath Rings also require a clean spread before the reward applies.

Every mechanic starts with a title, a short chat explanation, the exact failure damage, an action-bar reminder, and one warning sound. Mechanics that change instructions mid-cast announce the new step once. Repeated action-bar updates do not repeat chat or sounds.

The Veilbound Marshal's blue marker is an isolation check: the marked player stays inside and is immune to their own circle, while every other player must leave it. Ashen Crossfire follows the same ownership rule for each red marker, and overlapping failures can only punish one player once per cast. Widow's Claim trail pools become dangerous after they land, giving the hunted player time to keep moving.

Clean execution creates short damage openings without reducing any boss's base health or attack. Blue Isolation grants 10% for four seconds, Ashen Crossfire 12% for 4.5 seconds, Webbreak 15% for five seconds, Root Wards 18% for 5.5 seconds, and Tidal Divide 20% for 5.5 seconds. The Argent Confessor and tier 7+ fights keep their stronger existing openings.

The Argent Confessor gives seven seconds to break Saint's Stagger and five seconds to stop attacking during Counterstance. Both action bars show the remaining time. Breaking the guard early opens the damage phase immediately, and either successful check exposes the Confessor to 25% more damage for five to six seconds.

Morvessa can maintain two to five Runebloom Familiars based on party size. She replaces only missing familiars after the summon cooldown, and every familiar expires after 45 seconds. Voralith's Dominion Pulse only hits active fighters inside its visible radius.

The Corrupted Oathkeeper is still the hardest fight, but its final phase now rewards execution instead of add cleanup. Corrupted Brand begins in phase 2, stays fixed where it was cast, leaves a real escape lane at the arena edge, and gives at least 2.2 seconds to leave its purple circle. Ember waves have a hard cap, expire on their own, and never split. Breaking one exposes the Oathkeeper to 15% more damage in phase 2 or 25% more damage in phase 3.

When the fight ends or the dungeon is reset, every Corrupted Ember and other boss-owned minion is removed before the arena can accept another encounter.

Oath Rings give 3.5 seconds for the first call. Phase 3 reverses the call once and gives another 3 seconds. A missed phase 3 ring removes 58% of maximum health, so one mistake is survivable but missing both calls is fatal. A perfect double-ring clear still exposes the Oathkeeper to 40% more damage for seven seconds. Phase 3 keeps its health, damage, speed, and two-ring pressure, but no longer stacks Resistance II or begins another ring sequence almost immediately.

Late bosses also have short casts that seal all healing. Natural regeneration, potions, powers, enchants, relics, and healing from teammates cannot restore health until the cast ends. The seal appears in the title, chat explanation, and action bar.

Extra players increase boss health, attack damage, ability pressure, target counts, and mechanic frequency. The scaling follows the current number of surviving fighters so a larger group cannot erase a boss with raw damage alone.

Class identity still works in normal combat, but boss encounters resist effects that skip their mechanics. Bosses resist health-deleting backstabs, hard crowd control, summon swarms, and Infinity projectile blocking. Time Stop, Domain Expansion, Voidstep, Wayfarer portals, Oath Summon, and Skybound flight cannot move players around an active encounter. Failed mechanics also bypass Phoenix Rebirth and Graveborn's second chance. Druid blessings are unchanged, though healing blessings still obey healing-seal casts.

Every summon opens with a short protected reveal. The boss cannot attack or take damage until the final sound and expanding ring release it. Each boss has its own entrance spiral, attack trail, impact sound, and phase-shift burst; the effects are paced so repeated hits do not stack audio or flood nearby clients.

Every boss also has a different original note-block battle theme. It begins with the encounter, loops without a gap through every phase, and stops when the boss dies, the fight resets, or the listener leaves. Fighters and dungeon spectators hear the same theme through the **Jukebox/Note Blocks** volume setting.

Use `/settings` to mute only the boss music. Mechanic warnings and combat sounds stay enabled so the fight remains readable.

Successful participants also build permanent progress with [Mogrik's Boss Mastery](Boss-Mastery). Each boss has five cumulative ranks with Essence, common materials, utility relics, permanent Huntmarks, and unique gear without giving away rare progression materials.

The shared reward chest now grows with the group that actually fought. Each additional meaningful combat contributor adds 25% to guaranteed boss progression materials, up to 2x materials with five contributors. Chance-based gear, tables, orbs, Soul Imprints, and other rare rolls keep their normal chance and quantity, so extra spectators or idle players cannot multiply jackpot loot.

Every completed boss also makes one independent **2% armor roll**. A successful roll puts exactly one random piece from the next available boss-armor set into the shared chest. It is never doubled by party scaling or the Veil Wisp. The armor path is:

| Defeated boss tier | Possible armor set |
|---:|---|
| 1-3 | Gloam Court |
| 4-5 | Depthveil Pact |
| 6 | Cinder Confessor |
| 7 | Riftveil Step |
| 8-9 | Nocturne Guard |
| 10 | Eclipse Mantle |

The rare find stays secret until a surviving participant successfully opens the chest. At that point the server announces the player who revealed it and the exact piece.

## Arena rules

- Building, breaking, explosions, natural mob spawning, fire spread, weather, PvP, and random block ticks are disabled.
- The arena is a void world with a safety return if a player falls out.
- The supplied arena world is installed automatically the first time the plugin starts.
- Guaranteed progression materials scale with meaningful combat participation; rare-drop rolls do not.
- A defeated boss creates a protected participant-only chest. It disappears when empty or after two minutes, then everyone returns to spawn.
- Player-dropped items are owner-tagged. Anything still in the arena at cleanup is returned to its owner; unclaimed boss loot returns to the summoner.
- If the chest cannot be placed, overflows, or a player's inventory is full, the affected reward stays owner-locked and delivery retries automatically. It is never converted into a public arena drop.

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
