# Player Shops

Player shops let players sell items from a protected chest by attaching a wall sign to it. Buyers right-click the sign, and the plugin safely swaps the buyer's payment for the item from the chest.

Use `/shops` or `/shop` in game to see the short setup reminder.

## Quick Setup

1. Place a chest or trapped chest.
2. Use a double chest if you want more shop storage. Do this before creating the shop.
3. Put at least one copy of the item you want to sell inside the chest.
4. Place a wall sign on the front of the chest.
5. Write the shop format on the sign.
6. Finish editing the sign. If it worked, the sign changes to a green `[Shop]` sign.

Example sign:

```text
[shop]
chest
1
5 diamond
```

This sells 1 of the first item found in the chest for 5 diamonds.

## Sign Lines

- Line 1: `[shop]`
- Line 2: the item being sold.
- Line 3: amount sold per purchase.
- Line 4: price and currency.

Line 2 options:

- `chest`, `item`, `this`, or blank: uses the first real item in the chest.
- A vanilla item id such as `diamond`, `iron_ingot`, `oak_log`, or `netherite_sword`.

Use `chest` for custom items, renamed items, enchanted items, damaged gear, or anything with special metadata. That stores the exact item type from the chest.

## Currencies

Supported payment currencies:

- `diamond`
- `iron`
- `netherite`

Examples:

```text
[shop]
chest
16
3 diamond
```

```text
[shop]
oak_log
32
12 iron
```

```text
[shop]
chest
1
1 netherite
```

Currency items must be plain vanilla items. Custom items that happen to use diamond, iron ingot, or netherite ingot as their material do not count as payment.

## Buying

1. Right-click the shop sign.
2. The plugin checks that the shop has enough stock.
3. The plugin checks that the buyer has enough plain currency.
4. The plugin checks that the buyer has inventory room.
5. The plugin checks that the shop chest has room to receive payment.
6. If every check passes, stock is removed from the shop chest, payment is added to the shop chest, and the bought item is added to the buyer's inventory.

If any check fails, nothing should be moved.

## Restocking

The shop owner can open the shop chest directly and add more of the exact sold item. For custom items or gear, the restocked item must match the item stored by the shop.

Payments collect inside the shop chest. Keep enough free space in the chest for payments, or buyers may see `That shop's payment storage is full.`

## Editing Or Removing A Shop

- To change price, amount, item, or currency, break the shop and recreate it.
- To remove a shop, the owner, OPs, or anyone with `smpcore.shop.admin` can break the sign or shop chest.
- Non-owners cannot open the shop chest directly.
- By default, owners cannot buy from their own shop.

## Protections

Active shop signs and shop chests are protected:

- Non-owners cannot open the shop chest.
- Only the owner, OPs, or `smpcore.shop.admin` can break the shop.
- Explosions do not destroy active shop signs or shop chests.
- Fire does not burn active shop blocks.
- Pistons cannot move active shop blocks.
- Hoppers cannot push items into or pull items out of active shop chests.
- Double chest shops track both chest halves.
- A single chest shop cannot be expanded into a double chest after creation. Break and recreate it if you need more storage.

## Common Problems

- `Attach the shop sign to the front of a chest or double chest.`: the sign is not attached to a chest.
- `Create shops on the front side of the sign.`: edit the front face of the sign, not the back.
- `Line 3 must be the amount sold per purchase.`: line 3 needs a number, such as `1`, `8`, or `64`.
- `Line 4 must be like: 5 diamond, 8 iron, or 1 netherite.`: line 4 needs a positive number and one supported currency.
- `Put the sold item in the chest, then use 'chest' on line 2.`: the plugin could not find a valid item to sell.
- `That chest already has a shop.`: break the existing shop first.
- `That shop is out of stock.`: add more matching stock to the shop chest.
- `Clear inventory space before buying this.`: the buyer does not have enough room after paying.
- `That shop's payment storage is full.`: the shop chest needs space for payment.

## Server Settings

Current default config:

- `player-shops.enabled: true`
- `player-shops.max-amount-per-purchase: 64`
- `player-shops.max-price: 4096`
- `player-shops.allow-owner-purchases: false`

Permissions:

- `smpcore.shop`: use `/shops` and create player shops.
- `smpcore.shop.admin`: manage or remove any player shop.
