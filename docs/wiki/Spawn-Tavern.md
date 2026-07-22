# Spawn Tavern

The spawn tavern has drinks, games, daily work, and two quest NPCs.

## Drinks

Right-click Bram the Brewmaster to browse 13 cheap drinks costing 5-15 Essence. The bar is ordered from mild entry drinks to premium drinks, and every listing shows its tier and upgraded morning buff. Drinks are tradable and stack up to 16.

Drinks activate on one right-click. Intoxication stays through milk, death, relogs, and ordinary effect removal. The only cure is being asleep when the server completes a full night skip. Finishing that sleep grants the unique rested buff shown on the drink; the latest drink consumed determines the next morning's buff.

Sneak-right-click Bram to work through his quest for the Cellarmaster's Canteen. It grants Speed I and Haste I for three minutes and has a 30-minute per-player cooldown.

### Gambler's Fare

Bram also sells a Bottomless Lucky Draught for 15 Essence. Each sip grants a 3% chance for a safer bonus slot roll or an extra Crown & Casks draw for 30 seconds. The draught is never consumed, but repeated sips increase temporary nausea. Sleeping clears the nausea, and the draught disappears when carried more than 100 blocks from Bram.

Four foods provide 2.5%, 5%, 7.5%, or 10% Tavern Luck for 30 minutes. Food and draught luck combine but never exceed 10%. Luck never changes the wager or guarantees a win: it only has a small chance to keep the better of two slot rolls or improve the lowest card in a hand.

## Slot Machines

Right-click a registered slot machine and choose a wager of 1-64 Essence, plain Iron Ingots, plain Gold Ingots, or plain Diamonds.

Slots are intentionally difficult: 0.1% pays 25x, 0.4% pays 15x, 1.5% pays 8x, 3% pays 4x, and 7% pays 3x. The other 88% lose the wager. Pushes and 1x results are losses, not refunds. The base hit rate is 12% and the long-run return is 53.5% before a small Tavern Luck bonus.

The reels stop one at a time with a staged animation. Every payout tier has its own matching symbol, while losing reels use varied guaranteed non-matching symbols. After every result, **Spin Again** repeats the same wager without reopening the station; every repeat checks and charges the wager once before it starts. A 1x push does not count as a win for quests or leaderboards.

## Crown & Casks

Every registered station has a short-range label above it. Right-click a game table to choose a matched wager of 1-64 Essence, Iron Ingots, Gold Ingots, or Diamonds. The first player becomes host and locks the wager. There is no countdown: once 2-4 players are ready, the host clicks **Draw Cards** and everyone receives three cards immediately. Players have 15 seconds to choose one. The highest chosen card wins the full pot. Ties refund every contribution, and a sole remaining player wins by forfeit. If the host leaves, hosting transfers to the next seated player. Closing the lobby also releases that player's seat immediately.

## Goblin Hunt

Admins place hidden goblin heads around the map. Every head has a permanent collectible ID, so an admin can break and move it without creating a second reward. Each player can right-click each goblin once for 5 Essence.

Speak with Grikk the Goblin Hunter to unlock the progress menu. Every five findings can be turned in for Mining Luck. The bonus dynamically scales against the current number of placed goblins and reaches a maximum 20% chance to double eligible raw ore drops. For example, 100 active goblins make each five-finding turn-in worth 1%; 200 make each worth 0.5%.

Ordinary player-placed ore does not qualify. A naturally generated ore collected with Silk Touch receives hidden provenance, so it remains eligible when the same ore is placed and mined later with Fortune. This preserves the normal Silk Touch-to-Fortune workflow without allowing crafted or repeatedly replaced ore to generate free copies.

Admin commands:

- `/goblins give [amount]` - gives HeadDatabase ID 89260 as placeable hidden goblins.
- `/goblins count` - shows the current active count.
- `/goblinhunter spawn` - spawns Grikk at your position.
- `/goblinhunter remove` - removes the nearest Grikk.

Win, loss, tie, and cancelled-round screens include **Play Again**, which rejoins the same physical table.

## Darts

Each player gets three throws in a timing GUI. Click Throw when the moving aim marker reaches the center. A bullseye is worth 50 points, then 25, 15, 8, or 2 farther from center.

A perfect 150-point round awards 10 Essence.

The final score screen includes **Play Again** for another three throws without reopening the dartboard.

Real wins are announced only to players within 16 blocks of that game. Slots announce 3x and jackpot wins, Crown & Casks announces the pot winner, and darts announces perfect rounds. Winners and losers receive distinct sound feedback.

## Blackjack

Speak to **Silas the Dealer** to play. Blackjack cannot be started from a command, which keeps the game tied to the physical tavern. `/blackjack claim` exists only to recover an interrupted payout.

The dealer menu shows the wager and rules before anything is taken. If a payout cannot fit, it is kept in recovery storage instead of being dropped or deleted.

Number cards use their shown number, J/Q/K count as 10, and Aces automatically count as 11 or switch to 1 whenever 11 would make the hand bust. Each card's lore shows the value currently included in the total. Silas draws below 17 and stands on every 17, including soft 17. A win returns twice the wager, while a tie returns the original wager.

## Roulette

Speak to **Renn the Croupier** to play European roulette. Choose Essence or a plain stack of coal, raw copper/iron/gold, copper/iron/gold ingots, redstone, lapis, emerald, diamond, quartz, netherite scrap, or netherite ingots. Set a wager from 1 to 64, then choose a straight number or an outside bet. The confirmation screen shows the exact odds and total return before anything is taken.

The wheel has 37 equally likely pockets: green 0 and numbers 1-36. Straight numbers return 36x, red/black, odd/even, and low/high return 2x, and dozens or columns return 3x. Zero loses every outside bet, producing the standard 2.70% European house edge on every offered bet.

Material payouts are journaled before the wheel animation. Closing the menu or disconnecting cannot reroll the result. If a payout does not fit, clear space and use `/roulette claim`.

## Rumor Board

The Rumor Board has two systems:

- **Daily Work** resets each real UTC day: defeat 12 hostile mobs for 40 Essence, mine 24 ores for 45 Essence, and catch 6 fish for 35 Essence. Completed work is claimed from the board.
- **Player Bounties** let players choose an online target and escrow 25, 100, 500, or 1,000 Essence. The payment page also has clear choices for held stacks of iron ingots, gold ingots, diamonds, netherite ingots, any custom orb, or Soul Imprints. Other held item stacks remain supported. The complete held stack becomes the reward, and the creator confirms before anything is taken.

Active bounties survive restarts. Use `/bounties` for a read-only, paginated list with each target's player head. A legitimate non-allied player kill awards every bounty on that target. The creator cannot claim their own posting, and the same killer/victim pair has a 30-minute anti-farming cooldown. Creators can review their postings at the Rumor Board and use a second confirmation screen to cancel one; the entire escrow is returned. Item rewards that cannot fit remain in durable recovery storage for delivery after inventory space is available.

## Rook the Retired Adventurer

Right-click Rook to open his tavern-trial menu. Win once at the slots, win Crown & Casks, and hit a dart bullseye. His Quiet House Coin grants Luck II for 15 minutes with a 24-hour per-player cooldown.

## Admin Setup

Look directly at a block within eight blocks and use:

- `/tavernadmin set slots`
- `/tavernadmin set table`
- `/tavernadmin set darts`
- `/tavernadmin set rumors`
- `/tavernadmin remove <type>`
- `/tavernadmin list`
- `/tavernadmin leaderboard spawn <slots|cards|darts>` - spawns that game's top-10 wins-and-playtime board 2.25 blocks above you and replaces only that game's older board.
- `/tavernadmin leaderboard remove` - removes the nearest tavern leaderboard within six blocks.
- `/tavernadmin sober <player>` - forcibly clears tavern intoxication and nausea if sleep cleanup ever needs staff help.

Each game has one independent champions board showing wins and time played together. Rankings refresh every minute. Game playtime includes time spent waiting in a card lobby and ends safely when a player leaves or disconnects.

Place the NPCs where you are standing:

- `/brewmaster spawn`
- `/adventurer spawn`
- `/goblinhunter spawn`
- `/spawnlife spawn tavern_host` - entrance greeter and directions.
- `/spawnlife spawn tavern_regular` - back-room patron.
- `/spawnlife spawn tavern_tipsy` - back-room tipsy patron.

Use `/brewmaster remove` or `/cardsharp remove` near an NPC to remove it.
Use `/spawnlife remove <type>` within six blocks to remove one of the ambient tavern citizens.
