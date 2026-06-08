# Reliquary and Items

The Reliquary is the central player-facing menu for custom items.

Open it with:

- `/menu`
- `/reliquary`
- `/lrecipe`
- `/lrecipes`

## Reliquary Sections

- `Legendary Relics` - altar-crafted legendary weapons, armor, and combat relics.
- `Mythic Works` - Mythic Forge, Ascendant Core, and Mythic Nexus fusions.
- `Tools and Gear` - backpacks, custom tools, salvage stations, magnets, and normal craftable gear.
- `Utility Relics` - support items such as Ancient Scroll and Talisman of Sustenance.
- `Covenant Armory` - boss trophy gear, full armor sets, standalone armor, utility relics, and materials.

## Utility Relics

- `Orb of the Mystics` - stackable single-use Enderman drop that summons a random legendary altar. The caller has a 1-hour cooldown.
- `Ancient Scroll` - rerolls a player's superpower and avoids giving back the same current power.
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
- `Expanded Backpack` - upgrades a Backpack into 54-slot storage. Recipe: 1 Backpack, 16 leather, and 8 diamonds. The upgrade keeps the same backpack data, so existing contents stay inside when the old backpack is upgraded.
- `Salvaging Depot` - placeable chest station that recycles vanilla armor, tools, and weapons after 6 seconds. Recipe: 1 iron ingot, 2 redstone, 1 chest, and 1 hopper. It returns about 66% of the base materials, reduced by item damage. Hoppers can insert gear, but custom items and processing items are protected.
- `XP Lectern` - placeable utility station that stores player XP and lets players withdraw it later. Recipe: 1 experience bottle, 2 books, 1 lectern, and 1 redstone. The menu supports 1, 5, 10, and all-level deposit/withdraw buttons. Stored XP stays inside if the lectern is broken and moved.
- `Prospector's Pick` - lucky ore mining.
- `Skyhook` - grappling movement with limited uses.
- `Spelunker's Lantern` - held cave light with Night Vision and Haste I.
- `Surveyor's Lens` - right-click ore scan with a cooldown.
- `Mender's Kit` - consumed repair bundle for damaged carried or worn gear.
- `Faraday's Magnet` - craftable player-location compass with limited uses.

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

## Legendary Limits

Exclusive legendary relics are tracked while held by players, stored in ender chests, stored in plugin team vaults, or nested inside backpacks/shulker-style containers that the plugin can safely inspect. If a team stores something like `Siegebreaker Pick` in `/tvault`, the altar system still treats that legendary as existing and will not roll another copy.

## Mythic Nexus Fusions

Mythic fusions are made in the Mythic Forge with two source relics and the required catalyst. Mythics stay unique like other top-end relics.

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
