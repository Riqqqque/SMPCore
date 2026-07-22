# Reliquary and Items

## The First Dragon's Sigil

The first player to claim the season's Dragon Egg may receive **The First Dragon's Sigil** from staff. It is a bound Mythic Relic with a unique Java and Bedrock texture.

Right-clicking activates **Firstflight**:

- Launches the champion forward and upward.
- Grants Slow Falling for 12 seconds, Fire Resistance for 20 seconds, and one protected landing for 15 seconds.
- Deals 6 hearts of damage and strong knockback to ordinary hostile mobs within eight blocks.
- Never damages or moves players, passive mobs, boss entities, or boss minions.
- Cannot activate during a boss fight, dungeon encounter, or duel.
- Cooldown: 3 minutes.

Staff grant it with `/customitem give first_dragon_sigil <player>`. Revoke it with `/customitem revoke first_dragon_sigil <player>` or `/customitem take first_dragon_sigil <player>`. Revocation disables stored copies bound to that player as well as removing visible copies from their inventory and ender chest.

Staff command: `/customitem give first_dragon_sigil <player>`

The Reliquary is the central player-facing menu for custom items.

Open it with:

- `/menu`
- `/reliquary`
- `/lrecipe`
- `/lrecipes`

Recipe detail pages have a `Craft From Inventory` button for normal custom items and utility relics. It consumes the shown materials from the player's inventory on the server and gives the result directly. Java players can still use the normal crafting table recipes where those exist, but this button is the safest path for Bedrock/Geyser players because it does not depend on the Bedrock client recipe book.

## Reliquary Sections

- `Legendary Relics` - altar-crafted legendary weapons, armor, and combat relics.
- `Mythic Works` - Mythic Forge, Ascendant Core, and Mythic Nexus fusions.
- `Tools and Gear` - backpacks, custom tools, salvage stations, magnets, and normal craftable gear.
- `Utility Relics` - support items such as Ancient Scroll and Talisman of Sustenance.
- `Armory of the Veil` - boss trophy gear, full armor sets, standalone armor, utility relics, and materials.

## Utility Relics

- `Awakening Table` - dropped by Asterion the Rift Oracle. The default drop chance is 50%, controlled by `awakening-table.rift-seraph-drop-chance` in `config.yml`. Summon Asterion through Malakar inside the Boss Dungeon.
- `Orb of the Mystics` - stackable single-use Enderman drop that summons a random legendary altar. The caller has a 1-hour cooldown.
- `Ancient Scroll` - rerolls a player's class and avoids giving back the same current class. It can be upgraded in an Awakening Table with 1 Nether Star. A successful upgraded scroll lets the player choose a non-Mortal class from a menu; choosing The Honored One kills the player once as the cost. A failed upgrade destroys the scroll.
- `Talisman of Sustenance` - passive inventory talisman that restores health and hunger over time.

## Item Rarity Theme

- `Common` - basic custom utility.
- `Uncommon` - early special gear.
- `Rare` - useful but not endgame-defining.
- `Epic` - strong utility, armor pieces, and build-defining tools.
- `Legendary` - major boss-linked effect.
- `Mythic` - top-end relics, rare boss trophies, or high-investment gear.

Each rarity uses a consistent color theme across names, lore, drops, and menu presentation.

## Tools and Gear

This section is for normal craftable gear, not Veil boss weapons.

- `Backpack` - portable 27-slot storage.
- `Expanded Backpack` - upgrades a Backpack into 54-slot storage through the Reliquary trade button or a crafting table. Recipe: 1 Backpack, 16 leather, and 8 diamonds. The upgrade keeps the same backpack data, so existing contents stay inside when the old backpack is upgraded.
- Hold a backpack and use `/backpack label <text>` to add a 1-24 character organization suffix without changing its identity. `/backpack clear` removes the suffix. Each backpack has independent saved contents, and a backpack cannot contain another backpack.
- `Salvaging Depot` - placeable chest station that recycles armor, tools, and weapons, including leather, chainmail, gold, iron, copper, diamond, netherite, bows, shields, tridents, maces, elytra, horse armor, wolf armor, carrot-on-a-stick, warped-fungus-on-a-stick, and ordinary custom tools. Place two depots together to make one protected 54-slot Large Salvaging Depot; normal chests and a third depot cannot connect. Items queue for 10 seconds first, so you can pull out an accidental item before it locks; once locked, salvaging takes 6 seconds. Recipe: 1 iron ingot, 2 redstone, 1 chest, and 1 hopper. It returns about 66% of the base materials, reduced by item damage, and even last-hit durable gear still returns at least one scrap material. Unique relics, legendaries, backpacks, stations, and class items are protected from salvage. Its hologram is capped to nearby viewing distance and should not render through solid blocks. Hoppers can insert gear, but salvageable inputs stay in the depot until they are queued or processed; raw output materials can still be pulled out normally.
- `Agricultural Pylon` - placeable farm station that protects nearby farmland from player, mob, and jump trampling. Recipe: 4 bone meal, 2 wheat, 2 copper ingots, and 1 lantern. By default it protects roughly a 10x10x10 area around the pylon, and the radius can be changed in `config.yml`.
- `XP Lectern` - placeable utility station that stores player XP and lets players withdraw it later. Recipe: 1 experience bottle, 2 books, 1 lectern, and 1 redstone. The menu supports 1, 5, 10, and all-level deposit/withdraw buttons. It can also consume 10 stored XP and 1 plain glass bottle for each Experience Bottle, with buttons for batches of 1 or 8. Bottled XP does not receive class or enchant XP multipliers, so it cannot be cycled for free XP. Stored XP stays inside if the lectern is broken and moved.
- `Prospector's Pick` - lucky ore mining.
- `Skyhook` - grappling movement with limited uses.
- `Spelunker's Lantern` - held cave light with Night Vision and Haste I.
- `Surveyor's Lens` - right-click ore scan with a cooldown.
- `Mender's Kit` - consumed repair bundle for damaged carried or worn gear.
- `Faraday's Magnet` - craftable utility magnet. Shift-right-click while holding it to toggle item pulling within 10 blocks.

For the complete upgrade order, orb rules, failure chances, Soul Imprints, Fate Crucible, Runic Loom, and public spawn stations, read [Gear Upgrades and Stations](Gear-Upgrades-And-Stations).

## Reforging

Spawn staff can place `Brannik` with `/reforger spawn`.

Right-click the dwarf to reforge one tool, weapon, or armor piece with one `Reforge Stone`. Reforges preserve the item's custom data, enchants, damage, and model data. The result randomly rolls one of 15 good-to-bad prefixes.

Recipes:

- `Reforge Stone` - 1 Smooth Stone in the center with 4 Amethyst Shards around it.

## Corruption

Admins can place a `Corruption Anchor` with `/corruptionstation give`.

Right-click the anchor to risk one item with one catalyst. Any single unlocked item can be used.

`Corrupted Essence` is the high-risk Oathkeeper catalyst:

- 25%: the item gains x3 or x4 stats.
- 25%: the item loses 25% stats.
- 25%: the item loses 50% stats.
- 25%: the item is destroyed.

`Corruption Stone` is the lower-tier craftable catalyst:

- 50%: the item gains x2 stats.
- 50%: the item keeps its stats.
- It cannot be used on legendary or mythic relics.

After an item is corrupted, it cannot be enchanted, reforged, crafted into another item, smithing-upgraded, grindstoned, or changed in an anvil. Successful corruption also makes breakable items unbreakable.

## Extra Survival Recipes

- `Leather from Rotten Flesh` - fill all 9 crafting grid slots with rotten flesh to craft 1 leather. This is a normal shaped recipe, so vanilla crafting tables and autocrafters can use it.
- `Bell` - craft with 1 iron ingot on top, iron/copper/iron across the middle, and 1 gold ingot on the bottom. This gives players a normal survival path for Bell-based Reliquary recipes.

## PvP Balance Philosophy

Custom items are allowed to feel powerful, but the plugin tries to avoid one-click fight deletion. Strong effects usually have one or more limits:

- Cooldowns.
- Short duration.
- Conditional trigger.
- Full armor set requirement.
- Boss trophy cost.
- Public dungeon boss progression.

## Legendary Relic Notes

- `Mjolnir` - every hit calls lightning and adds bonus mace damage. When Thunder Strike is off cooldown, the hit also deals configurable true damage that ignores armor. By default this is 3 hearts of true damage every 12 seconds.
- `Warden Blade` - its listed 8 damage is the unmodified base hit. Sharpness, Strength, reforges, Veilshift Orbs, awakening, and corruption stack normally. Sound Wave remains a separate 4.5-heart true-damage ability.

## Legendary Limits

Exclusive legendary relics are one-of-one. They are tracked while held by players, nested inside backpacks/shulker-style containers that the plugin can safely inspect, found in loaded world containers, dropped as items, or displayed in item frames. They cannot be stored in ender chests or team vaults. If an older protected relic is found there, the plugin moves it back to the player opening that storage and saves the cleaned vault when needed. Once one is created or discovered, that legendary ID is permanently locked out of future altar rolls, even if the tracked copy later moves around or is missed by a storage scan. If too many copies are found anyway, excess copies are removed without a material refund.

## Mythic Nexus Fusions

Mythic fusions are made in the Mythic Forge with two source relics and the required catalyst. Mythics stay unique like other top-end relics. The two source legendaries are permanently retired from future altar rolls once the mythic is created. Open `/mythics` and click any fusion to see the exact source relics, Ascendant Core requirement, and forge steps.

- `Gilded Verdict` = Emerald Blade + Divine Axe Rhitta.
- `Soulrender` = Wither Blade + Executioner Blade.
- `Nightfall` = Blink Dagger + Hypnosis Staff.
- `Veil Dominion` = Hard Hitter + Warden Blade.
- `Paradox Reaver` = Riftreaver + Hourglass Blade.
- `Tempest Trident` = Frost Scythe + Trident of Percy.
- `Stormfall Maul` = Thor's Hammer + Dash Mace.

## Main Menus

- `/menu` is the shortest hub command.
- `/reliquary` is the full item guide.
- `/mythics` opens Mythic Nexus fusions.
- `/bossrituals` opens the dungeon boss and cost preview.
- `/enchants` opens the custom enchant menu.

If the Reliquary, Mythic Nexus, or Armory of the Veil is opened from `/menu`, its Back buttons return to the menu path that opened it. Direct commands such as `/reliquary` and `/mythics` still work as standalone shortcuts.
