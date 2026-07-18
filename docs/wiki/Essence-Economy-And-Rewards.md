# Essence, Economy, and Rewards

Essence is SMPCore's main progression currency. Use `/essence` to see your balance and `/menu` → **Custom Stats** for lifetime earnings and progress counters.

## Reliable Essence sources

| Activity | Default reward |
|---|---:|
| Mining normal stone and ores | 1 Essence per 250 weighted mining points |
| Gaining real XP | 1 Essence per 350 XP |
| Killing hostile mobs | 2 Essence per 30 weighted mob points |
| Killing another player | 12 Essence, with a 20-minute same-victim cooldown |
| Finding a hidden goblin | 5 Essence per unique goblin |
| Overseer daily | 25 Essence plus 4 XP Bottles |
| Overseer weekly | 150 Essence, 16 XP Bottles, a Reforge Stone, and +1 Authority |
| Tavern daily work | 35-45 Essence depending on the task |
| Perfect darts round | 5 Essence |

Quests, Boss Mastery ranks, boss progression, bounties, shops, duels, and tavern games provide additional rewards or transfers.

Mining and XP counters are progress bars, not random per-action drops. Progress is saved in batches so ordinary gathering does not write to the database every block.

## Main Essence uses

- Boss Dungeon entry fees.
- Repeated quest and progression costs.
- Beastwarden familiar skill nodes and evolution.
- Market stall purchases.
- Player shops that accept Essence.
- Tavern drinks, food, slots, Crown & Casks, and bounties.
- Duel bets.
- Any future NPC shop that lists an Essence price.

## XP Bottles

XP Bottles are renewable in three ways:

1. Trade with a master Cleric.
2. Store XP in an **XP Lectern**, then bottle it at 10 stored XP plus 1 plain glass bottle per Experience Bottle.
3. Claim Overseer daily and weekly rewards.

The XP Lectern supports batches of 1 or 8 bottles. Bottled XP does not receive class or enchant XP multipliers, so withdrawing and rebottling cannot create free XP.

## Player trading

### Chest shops

Use `/shops` for the sign format. Stock stays in the chest, while payments go into a separate durable ledger. The stock chest can be completely full.

- `/shops balance` shows waiting payments.
- `/shops collect` pays what fits and leaves the rest pending.
- Shops can charge supported vanilla materials or Essence.

### Market stalls

Right-click a stall sale sign twice to buy it. Use `/stall` to manage ownership, `/stall transfer <player>` to offer it to another player, or `/stall sell` to return it for 75% of its listed price after removing player fixtures.

Read [Player Shops](Player-Shops) before building a shop.

## Gambling and wagers

Gambling is optional and never a progression requirement.

- **Slots:** casino odds with a 12% base hit rate, 88% loss rate, and rare 3x-25x payouts.
- **Crown & Casks:** 2-4 players contribute matching wagers; the highest chosen card takes the pot.
- **Blackjack:** played only through Silas the Dealer. `/blackjack claim` recovers an interrupted pending payout.
- **Roulette:** played only through Renn the Croupier. It uses a 37-pocket European wheel with one green zero, equal pocket odds, authentic payouts, and a 2.70% house edge.
- **Spin Bet:** both players lock and review the exact held items before either side confirms.
- **Duels:** fighters and spectators can back a side with Essence; outside viewers can also use safe supported item pools.
- **Bounties:** the Rumor Board escrows the creator's full reward until a valid kill or cancellation.

Use `/spinbet <player>`, then `/spinbet accept <player>` to review both wagers. Either player can deny before the final confirmation. `/spinbet claim` delivers a pending payout after inventory space is cleared.

## Full-inventory behavior

Protected rewards do not silently disappear:

- Essence stays in the account or pending ledger.
- Shop payments remain collectible.
- Unmatched or cancelled bets are refunded.
- Item payouts use pending recovery storage when they cannot fit.
- Crafting and trades check space before charging whenever the result is known in advance.
- Quest rewards use safe give-or-drop handling.

If a payout says it is pending, make room and use the relevant **Claim** or **Collect** action. Do not repeat the original purchase or wager.
