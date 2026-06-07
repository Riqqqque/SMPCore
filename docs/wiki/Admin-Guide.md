# Admin Guide

## Boss Control

- `/bosses` opens the admin boss GUI.
- Left-click a boss to spawn it.
- Right-click a boss to despawn all copies of that boss.
- `/bosses clearall` removes every tracked custom boss.
- `/bosses status` prints active boss counts.

## Spawner Control

- `/spawner info` inspects the targeted spawner within 8 blocks.
- `/spawner reset` resets the targeted spawner to default modifiers.
- Creative pick-block on custom spawners requires `smpcore.spawner.admin`.

## Item Control

- `/legendary give <item> [player]` gives legendary items.
- `/customitem give <item> [player]` gives non-legendary custom items, boss trophies, Covenant relics, and utility items.
- `/itemaudit <player> [item]` checks item origin logs.
- OPs and players with `smpcore.staff` receive live audit alerts for suspicious tracked custom item activity.

## Covenant Relic Admin Examples

- `/customitem give ashen_verdict Rique`
- `/customitem give crimson_guard_chestplate Rique`
- `/customitem give bloodbound_banner Rique`
- `/customitem give rift_lens Rique`

## Tools and Gear Admin Examples

- `/customitem give spelunkers_lantern Rique`
- `/customitem give surveyors_lens Rique`
- `/customitem give menders_kit Rique`

## Recovery Tips

- If a boss hologram or bar looks stale, use `/bosses clearall`.
- If players report missing boss ritual steps, send them to `/bossrituals`.
- If a custom item appears duplicated, use `/itemaudit`.
- Audit alerts do not delete items. They only notify staff and point to the matching `/itemaudit` command.
- If the world start state needs to be redone, use `/startsmp reset`.
