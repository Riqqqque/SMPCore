# Player Shops

The market stall's built-in sale sign is reserved for buying the stall. It cannot be turned into or purchased as a chest shop. Stall owners may remove and replace wall-mounted shop signs attached directly to their chests.

Player shops sell items from a protected chest. The stock stays in the chest, while buyer payments are held safely outside it until the owner collects them.

Use `/shops` or `/shop` in game for the short setup guide.

## Quick Setup

1. Place a chest or trapped chest. Make it a double chest now if you want more stock space.
2. Put at least one copy of the item you want to sell inside.
3. Shift-place a normal wall sign directly onto the chest.
4. Enter these four lines:

```text
[shop]
chest
1
5 diamond
```

That sells one copy of the first item in the chest for five diamonds.

Inside a rented market stall, signs can only be attached directly to a chest or trapped chest. Freestanding and hanging signs are blocked.

## Sign Lines

- Line 1: `[shop]`
- Line 2: `chest` for the first item in the chest, or a vanilla item id.
- Line 3: amount sold per purchase, from 1 to 64 by default.
- Line 4: price followed by the currency.

Use `chest` for custom, renamed, enchanted, or damaged items. It records the exact sample item. Legendary items cannot be sold through player shops.

## Currencies

Shops accept plain vanilla coal, copper ingots, iron ingots, gold ingots, redstone, lapis, emeralds, diamonds, netherite ingots, or Essence.

Examples:

```text
5 coal
12 iron
8 emerald
25 essence
```

Custom items that reuse one of those vanilla materials do not count as payment.

## Stock And Payments

- Restock by putting more of the exact sold item into the chest.
- The stock chest may be completely full. Payments never use its slots.
- Use `/shops balance` to see waiting payments.
- Use `/shops collect` to collect them.
- If the player's inventory is full, collection stops safely and leaves the remainder in the payment ledger.

## Buying

Right-click the shop sign. The purchase only completes if the shop has stock, the buyer has enough currency, and the bought items fit after payment is removed. If a check fails, nothing moves.

## Rented Market Stalls

Right-click an available stall's sale sign twice to purchase it with Essence. One player may own one stall at a time; both direct purchases and accepted transfers enforce this limit immediately before ownership changes.

Inside an owned stall, players may:

- Place chests and trapped chests in empty spaces.
- Attach ordinary wall signs directly to those chests.
- Break their placed shop signs and storage.
- Remove existing chests, trapped chests, barrels, lanterns, soul lanterns, furnaces, smokers, blast furnaces, crafting tables, bookshelves, chiseled bookshelves, flower pots, and decorated pots.

Players cannot modify cherry, pale, spruce, shelf, or trapdoor blocks. They cannot place signs anywhere except directly on shop chests. The rest of the premade stall stays protected from breaking, fire, explosions, flowing liquids, pistons, mobs, and hoppers.

Only the stall owner or a shop admin can edit ordinary signs in an owned stall. Active shop signs and the stall's built-in sale sign are server-managed and cannot be rewritten, dyed, made glowing, or waxed; break and recreate an active player shop to change it.

Premade removable decor does not drop an item when broken, so restoring a stall cannot duplicate its furnishings. Premade containers must be empty before removal. Chests and signs placed by the owner still drop normally.

Useful commands:

- `/stall` - show your stall and setup reminder.
- `/stall transfer <player>` - offer the stall and its active shops to another player.
- `/stall sell` - sell it back for 75% of its listed price after removing player fixtures.
- `/shops` - show the chest-shop sign format.

## Admin Restore Tools

Every stall has a structure-only launch template in `market-stall-templates.yml`. Templates store block data, sign text, sign settings, and decorated-pot sherds. They deliberately do not store inventory contents, which prevents item restoration from becoming a duplication source.

- `/stall admin restore <id>` - show the change count; run it again within 15 seconds to restore that stall.
- `/stall admin snapshot <id> confirm` - intentionally replace one launch template.
- `/stall admin snapshotall confirm` - replace all eligible unowned templates.
- `/stall admin list` - show ownership, fixture count, and template hash.

Restore closes active chest shops in the region and resets changed blocks. It refuses to run if a container that would be replaced still contains items. Empty the named stall's changed chests, barrels, furnaces, smokers, bookshelves, or pots and retry. Unchanged stock containers are not overwritten.

Do not edit the compressed template payload by hand. Keep a backup of `market-stall-templates.yml` with the launch world backup.

## Admin Shops

Admins with `smpcore.shop.admin` can create infinite server shops by using `[adminshop]` or `[ashop]` on line 1. The other lines use the normal format. Admin shops do not consume stock, and buyer payments are removed from the game.

## Editing Or Removing A Shop

- Break the sign and recreate it to change the item, amount, price, or currency.
- The owner, OPs, and players with `smpcore.shop.admin` can break a shop.
- Non-owners cannot open its stock chest.
- A single chest cannot be expanded after the shop is created. Break the sign, make the double chest, and recreate the shop.

## Common Problems

- `Attach the shop sign to the front of a chest or double chest.`: the wall sign is not attached directly to a chest.
- `Inside your stall, attach a wall sign directly to a chest or trapped chest.`: the sign is outside your stall, not attached, or belongs to another stall.
- `Line 3 must be the amount sold per purchase.`: use a number such as `1`, `8`, or `64`.
- `Line 4 must be a price and supported currency.`: use a value such as `5 diamond` or `25 essence`.
- `Put the sold item in the chest, then use 'chest' on line 2.`: no valid sample item was found.
- `That shop is out of stock.`: add more copies of the stored item.
- `Clear inventory space before buying this.`: the purchase result will not fit safely.

## Server Settings And Permissions

Default settings:

- `player-shops.enabled: true`
- `player-shops.max-amount-per-purchase: 64`
- `player-shops.max-price: 4096`
- `player-shops.allow-owner-purchases: false`

Permissions:

- `smpcore.shop`: use `/shops` and create player shops.
- `smpcore.shop.admin`: manage any chest shop and create infinite admin shops.
- `smpcore.stall.use`: buy and manage a market stall.
- `smpcore.stall.admin`: create, price, snapshot, restore, and remove market stalls.
