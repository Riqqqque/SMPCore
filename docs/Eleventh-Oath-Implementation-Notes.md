# The Eleventh Oath implementation notes

## Existing systems reused

- `SMPCore` remains the lifecycle owner. The story service is registered beside the existing quest, boss, NPC, item, and crossplay managers.
- `DatabaseManager` remains the only SQLite access point. Story profiles use its existing asynchronous executor and a versioned row per player.
- `BossManager` remains authoritative for encounter participants, phases, defeat rewards, loot chests, and admin tests. Story hooks only add dialogue, memories, journal entries, and the final choice after normal rewards are secured.
- `GuideNpcManager`, `ReforgeManager`, and `PriestManager` keep their existing menus. Story dialogue is an additional non-blocking interaction and never replaces access to those menus.
- Mayor, miner, farmer, witch, goblin, overseer, and tavern progression keep their current objectives and rewards. Their completions only report narrative milestones to the story service.
- Awakening, Corruption, Soul Imprints, familiars, Essence, reforges, relics, and classes remain mechanically unchanged. The story records successful events and explains them through journal entries.
- The existing Paper lifecycle command API is used for `/veil`; `MenuDupeGuardListener` continues to provide the global inventory safety net.
- Bedrock detection and the normal inventory-menu conventions are reused. Every final choice also has the `/veil choose` text-command fallback.
- Existing boss-fight reports are the only source used to migrate historical victories. No past completion is guessed.

## Implementation approach

1. Add a versioned, asynchronously persisted `StoryProfile` with idempotent sets for memories, codex entries, dialogue nodes, and event keys.
2. Load canonical story content from dedicated YAML resources. Invalid optional mappings warn and fall back without disabling the rest of SMPCore.
3. Add `/veil` journal, objective, memories, text fallback, skip, final choice, and staff administration commands.
4. Connect canonical boss entrance, phase, low-health, defeat, and participant memory lines to the real encounter lifecycle. Admin test encounters never grant story progress.
5. Add short stage-aware NPC lines while leaving all existing NPC menus immediately available.
6. Trigger restrained personal titles, sounds, and particles at memory milestones. No ambient effect requires a configured world location.
7. Preserve alignment as narrative-only state. Mend, Bind, and Sever never change combat, drops, stats, or quest rewards.
8. Add pure unit coverage for selection, cooldowns, idempotence, migrations, choices, and duplicate event protection, plus an operator checklist.

## Compatibility decisions

- Boss order stays governed by the current dungeon. Out-of-order victories unlock the correct memory and the journal presents them in canonical order.
- Disconnected eligible participants are still persisted because the boss manager supplies participant UUIDs; cinematics wait until a later online interaction.
- Existing players are migrated from successful recorded boss-fight participation. Other historical actions are not inferred.
- Story content does not give inventory items, so full inventories cannot lose narrative rewards.
- Player-private alignment is never broadcast.
