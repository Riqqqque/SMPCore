# Waystones

Waystones are player-built travel points. Each player must discover a waystone before they can teleport to it.

## Build a waystone

A waystone is a small structure, not a crafting-table recipe.

You need:

- 1 Lodestone
- 1 Stone Brick Wall
- 1 Glowstone
- 1 sign of any wood type

Build it in this exact order from bottom to top:

```text
        Glowstone
    Stone Brick Wall  <- attach the sign here
        Lodestone
```

1. Place the Lodestone on the ground.
2. Place the Stone Brick Wall directly on top of it.
3. Place the Glowstone directly on top of the wall.
4. Attach the sign to any side of the Stone Brick Wall.
5. Put a unique waystone name on the sign's first line. Names can be up to 32 characters.
6. Right-click the sign to register and discover the waystone.

Leave two blocks of clear standing space beside the structure so travelers have a safe place to arrive.

Renaming the sign does not rename an existing waystone. Restore its original name, or break and rebuild the structure with the new name.

## Discover and travel

1. Right-click a valid waystone sign once to discover it.
2. Right-click any waystone you already know to open your personal destination menu.
3. Select a destination. Use the landing button to choose the safest nearby block or the top of the destination's Glowstone.

The menu is paginated automatically when you discover more than 45 waystones. Java and Bedrock players use the same visible buttons.

Discoveries are saved per player. Knowing one waystone does not automatically unlock every other one, and teammates must discover destinations for themselves.

## Travel safety

- The destination structure must still exist and have its named sign.
- Distant chunks load asynchronously before the structure and landing area are checked.
- Teleports stay inside the active world border and avoid unsafe landing blocks.
- Cross-world travel clears seat and mount state when possible and safely retries one rejected teleport.
- The Nether and End cannot be entered through a waystone before their scheduled unlock, unless the player has the staff bypass.
- Waystones cannot be used during player combat, duels, or active boss encounters.
- `/back` is updated only after a successful waystone teleport.

If a waystone was destroyed, it is removed from the registry and from player discovery lists. Rebuild and rediscover it to use that location again.

## Troubleshooting

- **The structure is not recognized:** Check the Lodestone, Stone Brick Wall, and Glowstone order, then make sure the sign has a name on its first line.
- **The name conflicts:** Another registered waystone already uses that name, or the structure was renamed without being rebuilt.
- **No safe landing spot:** Clear two blocks of standing space beside the structure or above its Glowstone.
- **The destination is locked:** Wait for the Nether or End unlock, leave combat, or finish the current duel or boss fight.
- **The destination disappeared:** A required block or named sign was broken. Rebuild and discover it again.
