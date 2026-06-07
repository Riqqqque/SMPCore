# Spawners

Spawners have a custom upgrade system. Players with `smpcore.spawner.use` can preserve, stack, and modify spawners. Staff with `smpcore.spawner.admin` can inspect and reset them.

Open the in-game modifier guide by right-clicking a spawner with an empty hand or with an item that is not a modifier.

## Player Actions

- Spawn Egg: changes the spawner mob type and consumes the egg.
- Sugar: increases spawn speed. Default max is 32 sugar for up to 16x speed.
- Redstone Dust: toggles redstone control mode. By default, powered redstone disables the spawner.
- Eye of Ender: enables AI nerf if the server config allows it. Spawned mobs stop targeting and pathfinding, but still keep normal physics interactions.
- Nether Quartz: resets sugar, redstone control, and AI nerf. Stack size is kept.
- Spawner item: stacks same-type spawners together. Default max stack is x64.

## Breaking And Placing

- A Silk Touch tool preserves the spawner only when spawner Silk Touch is enabled and the player has `smpcore.spawner.use`.
- Preserved spawner items keep mob type, stack size, sugar count, redstone mode, and AI nerf state.
- Breaking without valid Silk Touch drops XP instead of the spawner. XP is `15 + 5` per extra stacked spawner.
- Creative mode does not create survival spawner drops.
- Placing a custom spawner item restores its saved data.
- Explosions clean up tracked spawner data so stale holograms/state do not linger.

## Admin Commands

- `/spawner info` - look at a spawner within 8 blocks and show type, stack, sugar, speed, redstone, and AI nerf data.
- `/spawner reset` - look at a spawner within 8 blocks and reset it to default modifiers.
- `/spawnermgr` - alias for `/spawner`.

## Creative Pick Block

Creative pick-block on a custom spawner is restricted to `smpcore.spawner.admin`.

When allowed, it gives a custom spawner item that preserves the tracked data instead of a plain vanilla spawner.

## Current Defaults

- Silk Touch spawners: enabled.
- Max stack: 64.
- Sugar max: 32.
- Max speed multiplier: 16x.
- AI nerf: enabled.
- Redstone control: powered redstone disables the spawner.
- Minimum spawn delay floor: 40 ticks.
- Stacked spawn count cap: 32.
- Nearby entity cap: 96.

## Common Problems

- If a spawn egg does nothing, the player may not have `smpcore.spawner.use`.
- If stacking fails, the spawner types must match and the result cannot exceed x64 by default.
- If mobs stand still after spawning, AI nerf is enabled on that spawner.
- If a redstone-controlled spawner stops spawning, check whether it is powered.
- If a spawner drops XP instead of itself, check Silk Touch, permission, game mode, and config.
