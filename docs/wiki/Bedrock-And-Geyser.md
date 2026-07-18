# Bedrock and Geyser

SMPCore supports Bedrock players joining through Geyser and Floodgate. The normal player menus still work, and Bedrock players also get a `Bedrock Controls` button in `/menu`.

## Server Setup

For this Paper 26.2 build:

- Install current Floodgate and a Geyser build that explicitly supports Java 26.2.
- If the selected Geyser build still targets an older Java protocol, install ViaVersion in the connection path.
- If Geyser and Floodgate run on a proxy, enable `send-floodgate-data` and use the same Floodgate `key.pem` on the proxy and backend.
- Keep Floodgate's username prefix, preferably `.`, so Bedrock and Java players with the same name cannot collide. Floodgate replaces spaces with underscores.
- Run `/smpcore bedrock` as an admin after startup. It reports detected integrations and online Bedrock players.

Official references: [Geyser supported versions](https://geysermc.org/wiki/geyser/supported-versions/) and [Floodgate API/backend setup](https://geysermc.org/wiki/floodgate/api/).

## Player Controls

- `/bedrock` opens the crossplay controls menu.
- `/ability` runs the primary ability of the held custom item.
- `/ability alt` runs its alternate, sneak, or secondary action.
- Crouch and press Drop to use the primary held ability without dropping the item.
- Crouch and punch to use the held item's alternate ability.
- Tap a placed anvil to open SMPCore's Bedrock-safe custom crafting. Crouch and tap it for the normal vanilla anvil.
- `/customanvil` only reminds players how to reach the physical anvil now; it no longer opens one anywhere.

These are fallbacks. Normal item use still works when the Bedrock client sends the expected interaction.

Waystones use a visible landing-mode button instead of different left- and right-click actions. The admin boss menu uses the same pattern for spawn and despawn mode.

## Known Visual Differences

Geyser does not convert a Java resource pack into a Bedrock pack. SMPCore therefore builds a separate `SMPCore-bedrock-resource-pack.mcpack` for the models currently mapped on Bedrock: Backpack, Expanded Backpack, and the team-leader crown. The matching Geyser custom-item mappings and pack must both be installed on the connection server for those textures to appear.

Other SMPCore items keep their names, lore, stats, ownership, abilities, and correct icons inside protected menus, but may use their recognizable underlying vanilla model until a Bedrock model is added. A missing cosmetic texture never removes the item or its gameplay data.

Custom player heads need their own Bedrock registration. Staff can hold a head and run `/bedrockskulls register`, or use `/bedrockskulls scan [radius]` for loaded decorative skulls. Restart Geyser after its generated mappings change. Java skins and head textures are still subject to what the Bedrock client and Geyser can render.

Geyser also cannot show Java's glowing outline. SMPCore keeps important targets readable with boss bars, nameplates, holograms, and the private `[ALLY]` marker used by teammate glow. Station, spawner, loot, and rare-drop holograms use an extra Bedrock visibility check: spawn stations remain readable nearby, while private-base holograms use a shorter range and disappear behind walls or floors.

Clickable chat links do not open on Bedrock. `/wiki` prints the full address for Bedrock players instead.

## Quick Test

After installing the bridge:

1. Join with one Java account and one Bedrock account.
2. Check `/menu`, every section, and `/bedrock`.
3. Test a waystone in both landing modes.
4. Test crouch + Drop, crouch + punch, `/ability`, and `/ability alt` with an active relic.
5. Tap a placed anvil and apply a custom enchant book. Crouch-tap the same block and confirm the vanilla anvil opens.
6. Confirm a Salvaging Depot or spawner hologram cannot be seen through a solid floor.
7. Confirm team markers, boss bars, shops, backpacks, and death chests are readable from Bedrock.

If a supported item still shows its vanilla model, reconnect once, verify the Bedrock client accepted the server pack, and ask staff to check the installed `.mcpack` and mappings. Geyser does not generate either file from SMPCore's Java pack automatically.
