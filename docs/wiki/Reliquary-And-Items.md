# Reliquary and Items

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
- `Covenant Armory` - boss trophy gear, full armor sets, standalone armor, utility relics, and materials.

## Utility Relics

- `Awakening Table` - dropped by Aurelion the Rift Seraph. The default drop chance is 50%, controlled by `awakening-table.rift-seraph-drop-chance` in `config.yml`. Use `/bossrituals`, open Aurelion the Rift Seraph, and build the End-only Rift Coronation shrine to summon the boss.
- `Orb of the Mystics` - stackable single-use Enderman drop that summons a random legendary altar. The caller has a 1-hour cooldown.
- `Ancient Scroll` - rerolls a player's superpower and avoids giving back the same current power. It can be upgraded in an Awakening Table with 1 Nether Star. A successful upgraded scroll lets the player choose a non-Mortal power from a menu; a failed upgrade destroys the scroll.
- `Talisman of Sustenance` - passive inventory talisman that restores health and hunger over time.

## Item Rarity Theme

- `Common` - basic custom utility.
- `Uncommon` - early special gear.
- `Rare` - useful but not endgame-defining.
- `Epic` - strong utility, armor pieces, and build-defining tools.
- `Legendary` - major boss-linked power.
- `Mythic` - top-end relics, rare boss trophies, or high-investment gear.

Each rarity uses a consistent color theme across names, lore, drops, and menu presentation.

## Tools and Gear

This section is for normal craftable gear, not Covenant boss weapons.

- `Backpack` - portable 27-slot storage.
- `Expanded Backpack` - upgrades a Backpack into 54-slot storage through the Reliquary trade button or a crafting table. Recipe: 1 Backpack, 16 leather, and 8 diamonds. The upgrade keeps the same backpack data, so existing contents stay inside when the old backpack is upgraded.
- `Salvaging Depot` - placeable chest station that recycles armor, tools, and weapons, including leather, chainmail, gold, iron, copper, diamond, netherite, bows, shields, tridents, maces, elytra, horse armor, wolf armor, carrot-on-a-stick, warped-fungus-on-a-stick, and ordinary custom tools. Items queue for 10 seconds first, so you can pull out an accidental item before it locks; once locked, salvaging takes 6 seconds. Recipe: 1 iron ingot, 2 redstone, 1 chest, and 1 hopper. It returns about 66% of the base materials, reduced by item damage, and even last-hit durable gear still returns at least one scrap material. Unique relics, legendaries, backpacks, stations, and power items are protected from salvage. Its hologram is capped to nearby viewing distance and should not render through solid blocks. Hoppers can insert gear, but salvageable inputs stay in the depot until they are queued or processed; raw output materials can still be pulled out normally.
- `Agricultural Pylon` - placeable farm station that protects nearby farmland from player, mob, and jump trampling. Recipe: 4 bone meal, 2 wheat, 2 copper ingots, and 1 lantern. By default it protects roughly a 10x10x10 area around the pylon, and the radius can be changed in `config.yml`.
- `XP Lectern` - placeable utility station that stores player XP and lets players withdraw it later. Recipe: 1 experience bottle, 2 books, 1 lectern, and 1 redstone. The menu supports 1, 5, 10, and all-level deposit/withdraw buttons. Stored XP stays inside if the lectern is broken and moved.
- `Prospector's Pick` - lucky ore mining.
- `Skyhook` - grappling movement with limited uses.
- `Spelunker's Lantern` - held cave light with Night Vision and Haste I.
- `Surveyor's Lens` - right-click ore scan with a cooldown.
- `Mender's Kit` - consumed repair bundle for damaged carried or worn gear.
- `Faraday's Magnet` - craftable utility magnet. Shift-right-click while holding it to toggle item pulling within 10 blocks.

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
- Public ritual progression.

## Legendary Relic Notes

- `Mjolnir` - every hit calls lightning and adds bonus mace damage. When Thunder Strike is off cooldown, the hit also deals configurable true damage that ignores armor. By default this is 3 hearts of true damage every 12 seconds.

## Legendary Limits

Exclusive legendary relics are one-of-one. They are tracked while held by players, nested inside backpacks/shulker-style containers that the plugin can safely inspect, found in loaded world containers, dropped as items, or displayed in item frames. They cannot be stored in ender chests or team vaults. If an older protected relic is found there, the plugin moves it back to the player opening that storage and saves the cleaned vault when needed. Once one is created or discovered, that legendary ID is permanently locked out of future altar rolls, even if the tracked copy later moves around or is missed by a storage scan. If too many copies are found anyway, the over-cap relic set is removed and the recipe materials are refunded.

## Mythic Nexus Fusions

Mythic fusions are made in the Mythic Forge with two source relics and the required catalyst. Mythics stay unique like other top-end relics. The two source legendaries are permanently retired from future altar rolls once the mythic is created. Open `/mythics` and click any fusion to see the exact source relics, Ascendant Core requirement, and forge steps.

- `Gilded Sovereign` = Emerald Blade + Divine Axe Rhitta.
- `Soulrender` = Wither Blade + Executioner Blade.
- `Nightfall` = Blink Dagger + Hypnosis Staff.
- `Crimson Dominion` = Hard Hitter + Warden Blade.
- `Paradox Reaver` = Riftreaver + Hourglass Blade.
- `Tempest Trident` = Frost Scythe + Trident of Percy.
- `Stormfall Maul` = Thor's Hammer + Dash Mace.

## Main Menus

- `/menu` is the shortest hub command.
- `/reliquary` is the full item guide.
- `/mythics` opens Mythic Nexus fusions.
- `/bossrituals` opens boss shrine instructions.
- `/enchants` opens the custom enchant menu.

If the Reliquary, Mythic Nexus, or Covenant Armory is opened from `/menu`, its Back buttons return to the menu path that opened it. Direct commands such as `/reliquary` and `/mythics` still work as standalone shortcuts.
