# Familiars and Gathering

Open `/familiar` or `/menu` → **Familiars** to manage every unlocked familiar.

## Shared familiar rules

- Only one familiar can be active at a time.
- Selecting another familiar dismisses the current one first.
- All familiars use the same smooth follow, hover, bob, teleport-catchup, and normalized look behavior.
- Java and Bedrock players both see familiar bodies. Bedrock uses a Geyser-safe version of the same registered familiar head and movement.
- Right-clicking your familiar gives a character-specific reaction or opens its useful interaction.
- Familiar bonuses apply only while that familiar is active.
- Kael's training tree and evolution are saved separately for each familiar.

## Familiar list

| Familiar | Unlock | Core perk |
|---|---|---|
| Veil Wisp | Finish Mayor Bah's six orders | Earn normal Essence 50% faster; one shared 51% boss-drop double roll, +1% per extra active Wisp |
| Miner Familiar | Finish Torren's eight ore shipments | 10% chance for eligible raw ore to triple |
| Tiller | Finish Rowan's eighth commission | Extra crop and ordinary food-craft chances |
| Morrow | Finish Vespera's Moonlit Thesis | Longer brewed potion duration and a small valid splash/lingering upgrade chance |

The **Bond** branch of Kael's tree improves the active familiar's original perk. Evolution permanently improves that core perk by roughly 5%.

## Mayor Bah and the Veil Wisp

Complete Mayor Bah's six orders in sequence. Material turn-ins use ordinary items; boss wins count only when you legitimately participate.

| Order | Requirement | Essence |
|---:|---|---:|
| 1 | 32 Sculk and 16 Amethyst Shards | 75 |
| 2 | Defeat the Veilbound Marshal | 125 |
| 3 | 1 Solar Ember and 1 Widow Silk | 175 |
| 4 | Defeat Asterion | 250 |
| 5 | 1 Titan Gear and 1 Living Bark | 350 |
| 6 | Defeat the Corrupted Oathkeeper and bring 1 Corrupted Essence | 500 |

The final order unlocks the Veil Wisp. Its Essence bonus applies to the normal mining, hostile-mob, XP, and PvP sources while it is active. Boss doubling uses one shared roll per fight, not one separate roll per player.

## Mining bonuses

Mining Luck, the Miner Familiar, and Mining Fever roll separately and add bonus copies. They do not multiply each other exponentially.

- **Mining Luck:** up to 20% chance for one extra copy of eligible raw ore.
- **Miner Familiar:** base 10% chance for two extra copies.
- **Mining Fever:** sneak-right-click the Veinwake Pick anywhere, including while looking into the air, for 45 seconds of a 50% chance for two extra copies. Cooldown: 6 minutes. Using it before it is ready shows the active and cooldown timers.
- **Vein Miner:** use `/veinminer on`, then sneak while mining. The Veinwake Pick works with chained ores, including Fortune, Silk Touch, Smelting Touch, Telekinesis, and the natural/placed-ore safeguards.

If more than one bonus triggers, each adds its own copies. The action bar names every proc.

### Silk Touch ore

Natural ore collected with Silk Touch receives a hidden provenance marker. If that exact marked ore is placed and later mined for raw drops, gathering bonuses remain eligible. Newly crafted, admin-given, or ordinary player-placed ore does not receive that marker, so repeatedly placing ore cannot create an infinite loop.

Pistons preserve the tracked state when moving an eligible placed ore. Explosions clear it safely.

## Torren the Miner

Turn in these shipments in order: 64 Coal, 48 Raw Copper, 32 Raw Iron, 24 Raw Gold, 32 Redstone, 24 Lapis Lazuli, 12 Emeralds, and 8 Diamonds.

- After Raw Gold: **Veinwake Pick**, an unbreakable diamond pick with Mining Fever.
- After Diamonds: **Miner Familiar**.

Torren does not own a special mine. Every stage works throughout the normal world.

## Hidden goblins and Grikk

- Each placed goblin can be discovered once per player for 5 Essence.
- Every five discoveries can be turned in to Grikk.
- Mining Luck scales against the current active goblin count and reaches 20% after turning in findings for all of them.
- Finding every currently active goblin grants **Goblin Slayer**, +2% damage to players and mobs while the complete-hunt condition remains met.
- If staff adds more goblins later, the total and required completion update automatically.

Use `/goblins` after speaking to Grikk.

## Rowan and Tiller

Rowan has twelve farming and cooking commissions:

- Commission 4 awards **Furrowkeeper**.
- Commission 8 unlocks **Tiller**.
- The final chapter unlocks **Hearty Harvest**, allowing eligible Tiller-crafted food to receive improved hunger and saturation.

Read [Farmer and Witch Quests](Farmer-And-Witch-Quests) for the chapter breakdown.

## Vespera and Morrow

Vespera teaches potion progression through eight lessons. The final Moonlit Thesis unlocks:

- **Morrow**.
- Permanent boss-material brewing access.
- The `/bossbrews` guide.

Hoppers cannot bypass the boss-brew unlock.

## Corin the Fisher

The first conversation gives one free Oak Boat. Catch 5 Cod, 3 Salmon, and 2 Pufferfish in order. The catches remain in your inventory; progress counts from the fishing event.

Finishing awards **Shoreline Companion** with Luck of the Sea I, Lure I, and Unbreaking II.

## Veil Overseer and Authority

The Overseer rotates one UTC daily directive:

- Mine 32 eligible natural ores.
- Defeat 20 hostile mobs.
- Help defeat one progression boss.

Daily reward: 25 Essence and 4 XP Bottles. Claim five dailies in one Monday-based UTC week for 150 Essence, 16 XP Bottles, 1 Reforge Stone, and +1 Authority.

Authority is seasonal reputation displayed in the tab list, not combat power:

- 0: Veilmarked
- 1: Veil Deputy
- 3: Veil Marshal
- 5: Season Warden

## Beastwarden training

Kael's eight lessons award the Wildbound Regalia and unlock a separate 50-node tree for every familiar. The tree branches are Instinct, Endurance, Pace, Bond, and Fortune. Use [Beastwarden Training](Beastwarden-Training) for the armor, steed, evolution, and tree details.
