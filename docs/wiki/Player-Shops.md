# Player Shops

Player shops let players sell items from a protected chest by attaching a wall sign to it.

## Creating A Shop

Place a chest, or a double chest if you want more storage, put the item you want to sell inside, then place a wall sign on the chest. Make the chest the size you want before creating the shop.

Sign format:

```text
[shop]
chest
1
5 diamond
```

- Line 1 must be `[shop]`.
- Line 2 can be `chest`, `item`, or `this` to use the first real item in the chest.
- Line 2 can also be a vanilla item id like `diamond` or `iron_ingot`.
- Line 3 is the amount sold per purchase.
- Line 4 is the price and currency.

Supported currencies:

- `diamond` - plain vanilla diamonds
- `iron` - plain vanilla iron ingots
- `netherite` - plain vanilla netherite ingots

Custom items that happen to use one of those materials do not count as payment.

## Buying

Right-click the shop sign. The plugin checks stock, payment, and inventory space before moving anything.

If the buyer has enough currency and room, the item moves into their inventory and the payment moves into the shop chest.

## Protections

- Non-owners cannot open the shop chest directly.
- Only the owner, OPs, or `smpcore.shop.admin` can break a shop.
- Explosions, fire, and pistons do not destroy or move active shop signs or shop chests.
- A double chest shop tracks both chest halves.
- Hoppers cannot push into or pull out of active shop chests.
- Active shops cannot be expanded from single chest to double chest. Break and recreate the shop if you need to change its storage size.
- Shop signs store the exact item being sold, including item name and metadata, when `chest` is used.

## Commands

- `/shops`
- `/shop`

These commands show the setup format in game.
