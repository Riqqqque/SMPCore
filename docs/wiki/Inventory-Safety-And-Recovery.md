# Inventory Safety and Recovery

SMPCore uses confirmations, escrow, recovery journals, and exact death snapshots to avoid lost items and duplication.

## Backpacks

- Normal Backpack: 27 slots.
- Expanded Backpack: 54 slots.
- Basic recipe: 4 Leather, 4 String, and 1 Chest.
- Upgrade: the Backpack plus 16 Leather and 8 Diamonds. Existing contents stay inside.
- Right-click to open.
- `/backpack label <text>` adds a 1-24 character suffix for organization.
- `/backpack clear` removes the suffix.

Never place a backpack inside another backpack. Never try to stack them. Close the backpack before moving, dropping, trading, labeling, upgrading, dying intentionally, or changing servers.

While a backpack is open, the source item is sealed to that session. Contents are written to a disk recovery journal before the GUI is exposed and autosaved while open. If the source moves unexpectedly or the server stops, the next safe recovery returns one canonical copy with the saved contents rather than overwriting it.

If you see a backpack safety error, close the menu and stop moving the item. The refusal is protecting the stored contents.

## Drop Safety

Drop Safety is on by default. Important SMPCore items require two matching drop presses within five seconds. The first press is cancelled; the second confirms.

Open `/settings` to toggle it. Confirmed ground items have a 3-second player-pickup lock and are temporarily protected from hopper pickup, merging, and despawning.

## Death chests

On a real death, the plugin searches nearby safe ground and stores drops in a protected death chest for 90 minutes by default.

- The location is sent in chat and written on a note.
- Large deaths can use a double chest.
- The chest disappears when empty.
- If no safe location exists, items drop normally instead of replacing blocks.
- Void deaths use a safe fallback search.

Death chests and the admin inventory snapshot are separate safeguards. Restoring a snapshot while the linked chest still contains items is blocked.

## Exact death inventory records

Every real death writes a YAML record under:

`plugins/SMPCore/death-inventories/<player-uuid>/`

It includes the UTC time, world and coordinates, cause, direct and causing entities, death message, XP context, keep-inventory state, linked death chest, and exact serialized copies of normal inventory, armor, offhand, body/saddle slots, crafting inputs, selected hotbar slot, cursor item, custom data, and nested contents. Ender chest contents are not copied because death does not remove them.

Staff recovery flow:

1. `/deathinventory list <player> [page]`
2. `/deathinventory view <player> [latest|id]`
3. Verify the original drops or death chest were not already collected.
4. Put the player online, alive, outside combat, in Survival or Adventure, with every menu closed and cursor/crafting slots empty.
5. `/deathinventory restore <player> [latest|id]`
6. Review the warning, then `/deathinventory confirm` within 60 seconds.

The restore creates and verifies a pre-restore backup before replacing anything. A death snapshot can be restored only once. Keep-inventory deaths and deaths with individually retained items are blocked because restoring them would duplicate items.

## Transactions and full inventories

Protected menus never use decorative items as real rewards. Their buttons are marked and destroyed if removed from the GUI.

- Trades recheck ingredients and space when confirmed.
- Shops keep payments in a separate ledger.
- Bets hold wagers in escrow until settlement.
- Queued boss payments stay with the summoner until the fight actually begins.
- Interrupted payouts remain claimable.
- Menus close or return inserted items on shutdown where needed.

If a transaction reports a pending reward, clear space and use its Claim or Collect action. Repeating the original transaction may create a second unrelated action and is not the recovery method.

## When to contact staff

Contact staff before doing more item movement if:

- A backpack reports invalid or pending recovery data.
- A reward says it is pending but its claim action still fails with free space.
- A death chest is missing before its timer ends.
- A custom item's identity, lore, or modifiers suddenly reset.
- The same tracked legendary appears in two locations.

Staff can inspect item history with `/itemaudit <player> [item]` and death records with `/deathinventory`. Audit alerts do not delete items automatically.
