# Death Inventory Recovery

SMPCore records every real player death under:

`plugins/SMPCore/death-inventories/<player-uuid>/`

Each YAML file shows the player, UTC time, exact death location, damage type, direct and causing entities, death message, XP context, keep-inventory flags, linked death chest, and a readable summary of every inventory slot. The `nbt-base64` fields are the authoritative Paper raw-NBT copy. They preserve custom names, lore, attributes, enchants, data components, custom item tags, nested contents, armor, offhand, body/saddle slots, the four personal crafting inputs, selected hotbar slot, and cursor item. The derived crafting-result slot is intentionally excluded because it is created from the inputs rather than owned separately. Ender-chest contents are not copied because death does not remove them.

## Commands

- `/deathinventory list <player> [page]`
- `/deathinventory view <player> [latest|id]`
- `/deathinventory restore <player> [latest|id]`
- `/deathinventory confirm`
- `/deathinventory cancel`

Aliases: `/deathinv` and `/invrestore`.

Permission: `smpcore.admin.deathinventory` (operator by default).

## Safe restore procedure

1. Use `list` and `view` to verify the death, cause, time, location, state, and linked death chest.
2. Confirm that the original drops or death-chest contents were not already collected.
3. The target must be online, alive, in Survival or Adventure, outside combat and boss fights, with no container/menu open and no cursor item.
4. Run `restore`. This creates a 60-second confirmation without changing items.
5. Run `confirm`. SMPCore rechecks the target and snapshot, writes an exact pre-restore backup, marks the operation in progress, replaces all inventory groups, saves player data, verifies the resulting fingerprint, and then marks the death snapshot restored.

A death snapshot is restorable only once. Keep-inventory deaths and deaths with individually retained items are deliberately blocked because a full restore would duplicate those items. A linked death chest that still contains items also blocks restoration.

If applying the inventory fails, SMPCore restores and verifies the player's pre-restore inventory. If the server stops mid-restore, the next login compares the live inventory with both saved copies and either completes the audit state, reopens the unused death snapshot, or marks it `REVIEW_REQUIRED` without applying anything automatically.

Pre-restore backups are retained beside the death record and are never silently deleted.
