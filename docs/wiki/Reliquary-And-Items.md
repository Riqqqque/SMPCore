# Reliquary and Items

The Legendaries wing shows live server availability. Each legendary is marked as available or already crafted, and claimed entries show the team currently associated with the claim. If that team no longer exists, the original player or an unknown/disbanded label is shown instead.

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
- `Tools and Gear` - backpacks, custom tools, stations, support items, and every seasonal utility relic. Larger catalogs use clear Previous and Next page controls.
- `Custom Enchants` - every custom enchant, compatible item, effect, level, and craftable book recipe.
- `Armory of the Veil` - boss trophy weapons, full armor sets, standalone armor, and progression materials.

## Utility Relics

- `Awakening Table` - dropped by Asterion the Rift Oracle. The default drop chance is 50%, controlled by `awakening-table.rift-seraph-drop-chance` in `config.yml`. Summon Asterion through Malakar inside the Boss Dungeon.
- `Orb of the Mystics` - stackable single-use Enderman drop that summons a random legendary altar. The caller has a 1-hour cooldown.
- `Ancient Scroll` - rerolls a player's class and avoids giving back the same current class. It can be upgraded in an Awakening Table with 1 Nether Star. A successful upgraded scroll lets the player choose a non-Mortal class from a menu; choosing The Honored One kills the player once as the cost. A failed upgrade destroys the scroll.
- `Talisman of Sustenance` - passive inventory talisman that restores health and hunger over time.
- `Velocity Nullifier` - reusable inventory relic that completely absorbs one incoming mace smash, including its damage and impact, then recharges for 15 seconds. Ordinary mace swings do not use it, and extra copies share the same cooldown. Craft it with 4 Amethyst Shards in the corners, 4 Copper Ingots on the sides, and 1 Wind Charge in the center. The recipe is also available from the Reliquary for Bedrock-safe crafting.

## Item Rarity Theme

- `Common` - basic custom utility.
- `Uncommon` - early special gear.
- `Rare` - useful but not endgame-defining.
- `Epic` - strong utility, armor pieces, and build-defining tools.
- `Legendary` - major boss-linked effect.
- `Mythic` - top-end relics, rare boss trophies, or high-investment gear.

Each rarity uses a consistent color theme across names, lore, drops, and menu presentation.

## Tools and Gear

This section combines normal craftable gear, support items, and seasonal utility relics. Veil boss weapons and armor remain in the Armory.

- `Backpack` - portable 27-slot storage.
- `Expanded Backpack` - upgrades a Backpack into 54-slot storage through the Reliquary trade button or a crafting table. Recipe: 1 Backpack, 16 leather, and 8 diamonds. The upgrade keeps the same backpack data, so existing contents stay inside when the old backpack is upgraded.
- Hold a backpack and use `/backpack label <text>` to add a 1-24 character organization suffix without changing its identity. `/backpack clear` removes the suffix. Each backpack has independent saved contents, and a backpack cannot contain another backpack.
- `Salvaging Depot` - placeable chest station that recycles armor, tools, and weapons, including leather, chainmail, gold, iron, copper, diamond, netherite, bows, shields, tridents, maces, elytra, horse armor, wolf armor, carrot-on-a-stick, warped-fungus-on-a-stick, and ordinary custom tools. Place two depots together to make one protected 54-slot Large Salvaging Depot; the plugin joins them even if the client initially leaves two single chests, while normal chests and a third depot cannot connect. Items queue for 10 seconds first, so you can pull out an accidental item before it locks; once locked, salvaging takes 6 seconds. Recipe: 1 iron ingot, 2 redstone, 1 chest, and 1 hopper. It returns about 66% of the base materials, reduced by item damage, and even last-hit durable gear still returns at least one scrap material. Unique relics, legendaries, backpacks, stations, and class items are protected from salvage. Its hologram is capped to nearby viewing distance and should not render through solid blocks. Sneak-right-click a placed depot to choose salvageable-only hopper input, ordinary accept-all storage, guarded plain-junk disposal, and optional results-only output into a hopper directly below. Guarded Trash gives a 10-second cancel window and rejects valuable, custom, named, enchanted, data-bearing, and unstackable items instead of deleting them.
- `Agricultural Pylon` - placeable farm station that protects nearby farmland from player, mob, and jump trampling. Recipe: 4 bone meal, 2 wheat, 2 copper ingots, and 1 lantern. By default it protects roughly a 10x10x10 area around the pylon, and the radius can be changed in `config.yml`.
- `XP Lectern` - placeable utility station that stores player XP and lets players withdraw it later. Recipe: 1 experience bottle, 2 books, 1 lectern, and 1 redstone. The menu supports 1, 5, 10, and all-level deposit/withdraw buttons. It can also consume 10 stored XP and 1 plain glass bottle for each Experience Bottle, with buttons for batches of 1 or 8. Bottled XP does not receive class or enchant XP multipliers, so it cannot be cycled for free XP. Stored XP stays inside if the lectern is broken and moved.
- `Prospector's Pick` - lucky ore mining.
- `Skyhook` - grappling movement with limited uses.
- `Spelunker's Lantern` - held cave light with Night Vision and Haste I.
- `Surveyor's Lens` - right-click ore scan with a cooldown.
- `Mender's Kit` - hold the kit in one hand and a damaged item in the other, then use the kit to repair 35% of that item's maximum durability. One successful repair consumes the kit; invalid or fully repaired targets do not.
- `Restock Token` - a villager killed by fire or lava drops one. Sneak-right-click a villager with the token to reset every trade's uses, demand, and special price. It is consumed only after a villager with valid trades is restocked.
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
- `Cobweb` - place 5 String in an X shape to craft 1 Cobweb.

## Villager Transport and Ownership

- Sneak-right-click an unclaimed villager to pick it up as one non-stackable villager head. Its name, profession, level, trades, biome, and other data stay attached to that item.
- Place the villager head against a safe block to put the same villager back into the world.
- Sneak-right-click with any shovel to claim a villager or open its owner menu. Owners can control trading and add trusted trading partners.
- Sneak-right-click with shears to anchor or release a villager.
- Pickup and claiming have a 2-second cooldown. Automated villager hoppers and dispenser placement are disabled.
- Infinite stock keeps offers available without consuming uses, and successful trades still drop their normal player XP.

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
- `Midas Sword`, `Life Stealer`, and `Strength Sword` - player-kill upgrades count only eligible non-team kills. Current teammates, allies, and recent former teammates cannot provide progress. A blocked former-teammate kill displays the remaining one-month cooldown. Life Stealer also requires a new unique eligible victim for each permanent step.

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

- `/menu` is the shortest hub command. Profile, Reliquary, Bosses, Familiars, Teams, and Leaderboards are direct destinations.
- `/reliquary` is the full item guide and the single menu home for Armory gear, Custom Enchants, Tools and Gear, Legendaries, and Mythics.
- `/mythics` opens Mythic Nexus fusions.
- `/bossrituals` opens the dungeon boss and cost preview.
- `/enchants` opens the custom enchant menu.

Mythic Nexus appears in the Reliquary's Mythic Works wing instead of being repeated in the Bosses menu. If the Reliquary, Mythic Nexus, Armory, or Custom Enchants is opened from `/menu`, its Back buttons preserve that menu path. Direct commands such as `/reliquary`, `/mythics`, and `/enchants` still work as standalone shortcuts.
