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
- Every physical backpack has its own saved inventory. The same ender chest can be exposed through more than one server-side wrapper; SMPCore recognizes those wrappers as one storage instead of treating the backpack as a copy. A genuinely copied backpack ID is still separated and its copied payload is neutralized to block duplication while the canonical backpack remains intact.

Never place a backpack inside another backpack. Never try to stack them. Close the backpack before moving, dropping, trading, labeling, upgrading, dying intentionally, or changing servers.

While a backpack is open, the source item is sealed to that session. Contents are written to a disk recovery journal before the GUI is exposed and autosaved while open. If the source moves unexpectedly or the server stops, the next safe recovery returns one canonical copy with the saved contents rather than overwriting it.

Every successful session also keeps a temporary rolling history under `plugins/SMPCore/backpack-history/`. It retains up to five verified states per backpack for 14 days, with a hard cap of 100 snapshots per player. Rapid menu actions are coalesced, unchanged payloads do not write again, and cleanup happens only when that backpack is saved or staff use a recovery command. There is no repeating world or inventory scan.

Staff recovery commands:

- `/backpackadmin list <player> [page]` - list available snapshot IDs, times, and item counts.
- `/backpackadmin view <player> [latest|id]` - show the exact summarized contents.
- `/backpackadmin restore <player> [latest|id]` - prepare a guarded restore.
- `/backpackadmin confirm` or `/backpackadmin cancel` - finish or abandon it within 60 seconds.

The player must be online with the matching physical backpack in their inventory or ender chest. Restores refuse missing or duplicated shells, back up the backpack's current state first, replace the matching bag instead of creating a second copy, force-save the player, and consume the selected snapshot after success.

If you see a backpack safety error, close the menu and stop moving the item. The refusal is protecting the stored contents.

## Custom inventory recovery

The Awakening Table, Bedrock custom anvil, Corruption Table, Reforge station, Runic Loom, Fate Crucible, Mythic Forge, team vaults, and Salvaging Depots record exact copies of items involved in risky inventory moves. This is a staff safety net for a confirmed menu bug, not a normal rollback feature.

Records are event-driven. Rapid clicks are combined for half a second, unchanged batches are skipped, and disk writes run on one bounded background writer. There is no repeating player, world, container, or tick scan. Each player keeps at most 50 records for 14 days, with a 128 MiB hard storage cap. Older records remove themselves when new records are saved or staff inspect the history.

Staff recovery flow:

1. `/itemrecovery list <player> [page]`
2. `/itemrecovery view <player> <latest|id>`
3. Confirm the numbered item is actually missing.
4. Have the player close every inventory, clear their cursor, and leave one completely empty inventory slot.
5. `/itemrecovery restore <player> <id> <item-number>`
6. Review the warning, then `/itemrecovery confirm` within 60 seconds.

Every numbered item is single-use. Delivery is journaled before the item enters the player's inventory and reconciles automatically after an interrupted save or restart. Identical copies recorded within the same five-minute transaction window retire together, preventing the same moved stack from being recovered through adjacent snapshots. Backpacks are refused here and must use `/backpackadmin` so their storage identity cannot be duplicated. The system deliberately does not snapshot button-only menus or duplicate the escrow files already used by gambling and wagering.

## Team vaults and full backpacks

A team vault has 54 slots. Every slot may hold an Expanded Backpack with 54 filled slots, for up to 2,916 stored item slots. Backpacks still cannot contain other backpacks, so storage cannot recurse forever.

Normal full backpacks are supported. SMPCore checks the complete serialized vault before an insertion finishes, keeps database writes off the server thread, and saves the previous vault row as an atomic rollback snapshot. If the live row is malformed, the plugin uses the verified rollback instead of opening an empty vault and overwriting the original data.

Minecraft has a network-packet ceiling even when SQLite has plenty of room. Items with pathological amounts of embedded book, container, or component data are refused before they move into shared storage. The refusal leaves both the item and the last saved vault untouched; ordinary survival items and normal custom gear are far below this guard.

## Drop Safety

Drop Safety is on by default. Important SMPCore items require two matching drop presses within five seconds. The first press is cancelled; the second confirms.

Open `/settings` to toggle it. Confirmed ground items have a 3-second player-pickup lock and are temporarily protected from hopper pickup, merging, and despawning.

## Death chests

On a real death, the plugin searches nearby safe ground and stores drops in a protected death chest for 90 minutes by default.

- Regular-world PvP deaths create a chest too, including deaths caused by a teammate. No-loss duels and Boss Dungeon fights keep inventory instead.
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

Staff can inspect item history with `/itemaudit <player> [item]` and death records with `/deathinventory`. Audit alerts do not delete items automatically. Rare Double Jump books keep their acquisition and consumption trail, while ordinary backpack handoffs are intentionally not recorded as suspicious transfers.
