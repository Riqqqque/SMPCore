# Gear Upgrades and Stations

Speak to **Orin the Artificer** for the same information in a Bedrock-friendly GUI. This page explains what each system does and when to use it.

## Safe upgrade order

| Step | System | Why now |
|---:|---|---|
| 1 | Craft and smith | Choose the permanent base item first. |
| 2 | Enchant | Add vanilla and custom enchants while the item is editable. |
| 3 | Reforge | A new prefix replaces the old one; corrupted gear cannot be reforged. |
| 4 | Direct orbs | Add the chosen stat or boss aggro before the final lock. |
| 5 | Runic Loom | Raise an existing enchant while the item is still editable. |
| 6A | Awakening | Rare final upgrade; awakened gear cannot be corrupted. |
| 6B | Corruption | Alternative final gamble that permanently seals survivors. |

Choose step 6A **or** 6B, not both.

## Reforging

Right-click **Brannik** and insert one eligible weapon, tool, or armor piece plus one Reforge Stone.

- Recipe: 1 Smooth Stone and 4 Amethyst Shards.
- One stone is consumed per roll.
- The roll chooses one of 15 prefixes, from strong to harmful.
- A new roll replaces the previous prefix.
- Enchants, durability, custom identity, model, and other compatible modifiers remain intact.
- Corrupted gear is sealed and cannot be reforged.

## Direct-use orbs

Use the orb on the target item in your inventory. The menu previews the result before applying it.

- **Warden's Lure Orb:** armor only; adds +5 custom-boss aggro. One per armor piece.
- **Veilshift Orb:** armor, weapons, and tools; rolls one bonus stat. A later Veilshift Orb replaces the previous orb stat.
- **Runebloom Orb:** fuel for the Runic Loom rather than a direct item click.
- **Orb of the Mystics:** a rare Enderman drop that summons one random legendary altar. It is single-use and the caller has a one-hour cooldown.

## Runic Loom

The Runic Loom raises one enchant already on an item.

1. Insert armor, a tool, or a weapon with at least four existing enchants.
2. Insert 1 Runebloom Orb.
3. Choose the exact eligible enchant.
4. The successful roll adds +1 or +2, clamped to the loom's safe cap.

Corrupted gear and enchants already at the runic cap are rejected. The normal Runebloom Orb recipe is 1 Awakening Shard, 2 Riftglass Lenses, 32 XP Bottles, and 32 Lapis Lazuli. Morvessa also has a 5% drop chance.

## Awakening Table

Awakening is a rare permanent upgrade with real failure risk.

- Normal gear consumes 1 Awakening Shard per attempt.
- Base success chance: 5%.
- Failure removes half of the item's remaining durability.
- If a failure leaves the item below 15% durability, it shatters.
- Repair fully before every attempt.
- A successful awakened item can never be corrupted.

An Ancient Scroll uses 1 Nether Star instead. Success creates an Awakened Ancient Scroll that lets its owner choose a class. Failure destroys the scroll.

Asterion is the normal source of Awakening Shards and Awakening Tables. Use `/bossrituals` for current drop previews.

## Corruption Anchor

Corruption is the final high-risk item path. Insert one eligible unlocked item and one catalyst, then confirm the five-second ritual.

**Corrupted Essence:**

- 25%: x3 or x4 eligible stats.
- 25%: -25% eligible stats.
- 25%: -50% eligible stats.
- 25%: item destroyed.

**Corruption Stone:**

- 50%: x2 eligible stats.
- 50%: sealed with no stat increase.
- Cannot be used on legendary or mythic relics.

Every surviving corrupted result is permanently sealed from anvils, enchanting, smithing, grindstones, reforging, awakening, and later item transformations. Awakened items are rejected before the ritual starts.

## Soul Imprint

The name stays obfuscated until a player legitimately discovers one. The Corrupted Oathkeeper has a 0.5% chance to drop it.

Use the imprint on the same eligible finished item three times. It creates a copy that cannot be imprinted or corrupted again. The discovery is announced once with a title and sound; the first server acquisition is also announced publicly.

Because this is a powerful copy operation, only use it on the final item you actually want duplicated.

## Fate Crucible

The Fate Crucible accepts eligible Veil orbs and Soul Imprints.

- It commits the entire inserted stack.
- 50% success: the stack doubles.
- 50% failure: the stack is destroyed.
- Split the stack before inserting it if you do not want to risk all of it.
- No item, class, drink, or timing trick changes the odds.

## Mythic Forge

Insert two exact compatible legendary relics and 1 Ascendant Core. Use `/mythics` to view valid pairs.

Both source legendaries and the core are consumed. The sources are permanently retired from future altar rolls, and the forge refuses an output that has already reached its server uniqueness limit.

## Utility stations

### XP Lectern

- Deposits or withdraws 1, 5, 10, or all levels.
- Bottles stored XP at 10 XP plus 1 glass bottle per Experience Bottle.
- Stored XP stays in the item when the lectern is broken and moved.

### Salvaging Depot

- Accepts ordinary salvageable armor, weapons, and tools.
- Gives a 10-second cancel window, then locks for 6 seconds of processing.
- Returns about 66% of base materials, reduced by damage.
- Rejects relics, legendaries, class items, backpacks, and stations.
- Holograms use a short range and do not reveal private bases through floors.

### Agricultural Pylon

Protects nearby farmland from player and mob trampling. It does not create crops or duplicate harvests.

## Spawn interaction

Corruption Anchors, Awakening Tables, Runic Looms, and Fate Crucibles are automatically usable by normal players inside protected spawn. Private-base utilities such as Salvaging Depots and XP Lecterns are not automatically public unless staff marks that exact block.
