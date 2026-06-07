# Custom Enchants

Open the enchant guide with `/enchants` or from `/menu`.

Custom enchants use normal gameplay surfaces where possible: enchant table rolls, enchanted books, anvils, grindstones, loot chests, and recipes. The lore is intentionally simple so they read close to vanilla enchants.

## How To Get Them

- Enchant table enchants can appear from normal enchanting on valid gear.
- Custom enchant books can be applied in an anvil and consume XP levels.
- Matching custom enchant books can be combined in an anvil. Two books of the same level upgrade by one level, up to that enchant's max level.
- A matching custom enchant book can also upgrade an item that already has the same level, up to that enchant's max level.
- Same-type tools and armor can be combined in an anvil to merge their SMPCore custom enchants when the right-hand item adds something new.
- Grindstones remove custom enchants safely.
- Double Jump is loot-only from Ancient City chests by default.
- Boss-crafted books are made with Covenant boss materials and are shown in `/enchants`.

## Enchant Table Enchants

- Replenish I: hoe enchant. Replants supported crops when harvested.
- Delicate I: tool and weapon enchant. Protects immature crops and harvests stacked plants without destroying the root.
- Telekinesis I: tool and weapon enchant. Mining and mob drops go straight into your inventory when space is available. Overflow drops normally.
- Smelting Touch I: pickaxe enchant. Smelts mined drops when a real furnace or blast recipe exists. Netherrack is intentionally ignored.
- Wise I-III: pickaxe, sword, and hoe enchant. Gives +15%, +30%, or +40% XP from all sources while held. Breaking or right-click harvesting crops with it drops at least 2 XP.
- Dash I: sword and axe enchant. Right-click or sneak-left-click to dash forward. Current cooldown is 15 seconds.
- Frostbite I-II: melee enchant. Hits can briefly slow enemies. Higher levels improve the chance and chill strength.
- Harvesting I-III: hoe enchant. Mature crops can produce one extra crop drop.
- Bulwark I-III: armor enchant. Worn pieces reduce incoming damage, capped at 30% total reduction.
- Reinforced I-III: gear enchant. Items can ignore durability damage.
- Essence Capture I: tool and weapon enchant. Eligible mob kills have a 1% chance to drop that mob's spawn egg.

Essence Capture exclusions:

- Players.
- Custom bosses.
- Ender Dragon.
- Wither.
- Warden.
- Elder Guardian.

## Loot-Only Enchant

- Double Jump I: boot enchant. Found in Ancient City loot with a 23% chest chance by default.
- Double jumping costs 4 hunger by default and does not rely on saturation.
- The jump launches the player upward and forward.

## Boss-Crafted Enchant Books

These books do not appear from the enchant table. They are stronger because they require Covenant boss materials.

Click the enchant in `/enchants` to view the exact recipe in-game.

### Kingslayer I

Effect: melee weapons deal 18% more damage to tracked custom bosses.

Recipe:

```text
Crimson Rib  | Blaze Rod | Crimson Rib
Blaze Rod    | Book      | Blaze Rod
Sculk Heart  | Nether Star | Sculk Heart
```

### Soul Siphon I

Effect: melee hits heal a small capped amount based on damage dealt. The cap keeps it PvP-safe.

Recipe:

```text
Verdant Heart | Ghast Tear   | Verdant Heart
Crimson Rib   | Book         | Crimson Rib
Soul Sand     | Golden Apple | Soul Sand
```

### Echoing I

Effect: melee hits can mark the target with Glowing, apply Weakness, and lightly knock them back with a sonic burst.

Recipe:

```text
Titan Gear     | Echo Shard | Titan Gear
Amethyst Shard | Echo Shard | Amethyst Shard
Sculk Heart    | Book       | Sculk Heart
```

## Admin Book Commands

- `/dashbook`
- `/doublejumpbook`
- `/smeltingtouchbook`
- `/wisebook`
- `/telekinesisbook`
- `/delicatebook`
- `/replenishbook`

## Safety Rules

- Custom enchant books apply through anvils and consume XP levels.
- Custom-enchanted vanilla tools and armor, including Replenish hoes, keep their SMPCore enchants when upgraded in a smithing table. Actual custom relics are still blocked from vanilla crafting and smithing.
- Custom enchants can be removed safely in a grindstone.
- Dash cooldowns are stored on the player, so relogging does not bypass them.
- Boss-crafted enchant books require real Covenant boss materials.
- Boss-crafted enchant books are tracked by the item audit system.
