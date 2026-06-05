# Custom Enchants

Open the enchant guide with `/enchants` or from `/menu`.

Custom enchants use normal gameplay surfaces where possible: enchant table rolls, enchanted books, anvils, grindstones, loot chests, and recipes. The lore is intentionally simple so they read like vanilla enchants.

## Enchant Table Enchants

- `Replenish I` - hoe enchant. Replants supported crops when harvested.
- `Delicate I` - tool and weapon enchant. Protects immature crops and harvests stacked plants without destroying the root.
- `Telekinesis I` - tool and weapon enchant. Mining and mob drops go straight into your inventory when space is available.
- `Smelting Touch I` - pickaxe enchant. Smelts mined drops when a real furnace/blast recipe exists. Netherrack is intentionally ignored.
- `Wise I-III` - pickaxe, sword, and hoe enchant. Gives more XP while held and adds crop XP.
- `Dash I` - sword and axe enchant. Right-click or sneak-left-click to dash forward.
- `Frostbite I-II` - melee enchant. Hits can slow enemies for a short time.
- `Harvesting I-III` - hoe enchant. Mature crops can produce one extra crop drop.
- `Bulwark I-III` - armor enchant. Worn pieces reduce incoming damage, capped at 30% total.
- `Reinforced I-III` - gear enchant. Items can ignore durability damage.

## Loot-Only Enchant

- `Double Jump I` - boot enchant. Found in Ancient City loot. Double jump uses hunger and does not rely on saturation.

## Boss-Crafted Enchant Books

These books do not appear from the enchant table. They are stronger because they require Covenant boss materials.

### Kingslayer I

Effect: melee weapons deal 18% more damage to tracked custom bosses.

Recipe:

- 1 Enchanted Book.
- 1 Nether Star.
- 2 Sculk Hearts.
- 2 Crimson Ribs.
- 3 Blaze Rods.

### Soul Siphon I

Effect: melee hits heal a small capped amount based on damage dealt. The cap keeps it PvP-safe.

Recipe:

- 1 Enchanted Book.
- 1 Golden Apple.
- 2 Verdant Hearts.
- 2 Crimson Ribs.
- 2 Soul Sand.
- 1 Ghast Tear.

### Echoing I

Effect: melee hits can mark the target with Glowing, apply Weakness, and lightly knock them back with a sonic burst.

Recipe:

- 1 Enchanted Book.
- 2 Titan Gear.
- 2 Sculk Hearts.
- 2 Echo Shards.
- 2 Amethyst Shards.

## Admin Book Commands

- `/dashbook`
- `/doublejumpbook`
- `/smeltingtouchbook`
- `/wisebook`
- `/telekinesisbook`
- `/delicatebook`
- `/replenishbook`

## Safety Rules

- Custom enchants can be removed safely in a grindstone.
- Custom enchant books apply through anvils and consume XP levels.
- Dash cooldowns are stored on the player, so relogging does not bypass them.
- Boss-crafted enchant books are tracked by the item audit system.
