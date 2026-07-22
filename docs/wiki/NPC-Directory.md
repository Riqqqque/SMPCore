# NPC Directory

Right-click an NPC once to open its menu or receive its current conversation. Repeated clicks are debounced, and most NPCs do not repeat full dialogue every time.

## Guides and progression

| NPC | What they do | Staff placement command |
|---|---|---|
| Mira the Guide | Season overview, changelog, wiki, and first steps | `/spawnnpc spawn` |
| Mayor Bah | Six season orders and the Veil Wisp | `/mayor spawn` |
| Orin the Artificer | Complete gear-modification and station help | `/artificer spawn` |
| Brannik the Reforger | Applies a random prefix using Reforge Stones | `/reforger spawn` |
| Father Aldren | Priest services and Boss Ward progression | `/priest spawn` |
| Veyr | Corruption warnings and Anchor guidance | `/corruptionwarden spawn` |
| Veil Overseer | Daily directives, weekly cache, and Authority | `/overseer spawn` |

## Bosses and combat

| NPC | What they do | Staff placement command |
|---|---|---|
| Malakar the Gatekeeper | Enters the Boss Dungeon and manages boss summons | `/dungeonkeeper spawn` |
| Mogrik the Bossbroker | Five mastery ranks for every progression boss | `/bossbroker spawn` |
| Sable the Curio Broker | Placeable souvenirs from bosses already defeated | `/blackmarket spawn` |
| Cassian the Fightmaster | Duel setup, parties, matchmaking, betting, and spectating | `/duelmaster spawn` |

## Gathering, familiars, and quests

| NPC | What they do | Staff placement command |
|---|---|---|
| Grikk the Goblin Hunter | Hidden-goblin progress, Mining Luck, and completion bonus | `/goblinhunter spawn` |
| Torren the Miner | Ore shipments, Veinwake Pick, and Miner Familiar | `/miner spawn` |
| Rowan the Farmer | Farming/cooking commissions, Furrowkeeper, and Tiller | `/farmer spawn` |
| Vespera the Hedge-Witch | Potion lessons, Morrow, and boss-brew access | `/witch spawn` |
| Kael the Beastwarden | Wildbound Regalia, steeds, familiar trees, and evolution | `/beastwarden spawn` |
| Corin the Fisher | One free boat and a short fishing quest | `/fisher spawn` |

## Tavern

| NPC | What they do | Staff placement command |
|---|---|---|
| Bram the Brewmaster | Drinks, rested buffs, Tavern Luck food, and a quest | `/brewmaster spawn` |
| Rook the Retired Adventurer | Tavern trial and Quiet House Coin | `/adventurer spawn` |
| Silas the Dealer | The only access point for blackjack | `/dealer spawn` |
| Renn the Croupier | European roulette with number and outside bets | `/croupier spawn` |
| Tamsin the Host | Tavern entrance directions | `/spawnlife spawn tavern_host` |
| Nessa the Regular | Ambient tavern conversation | `/spawnlife spawn tavern_regular` |
| Garrick the Tipsy | Ambient tavern conversation | `/spawnlife spawn tavern_tipsy` |

## Spawn life

| Character | Interaction | Staff placement command |
|---|---|---|
| Biscuit | Fetch menu; accepts a Bone once per minute and returns home after fetch | `/spawnlife spawn dog` |
| Miso the Mouser | Ambient cat; accepts Cod or Salmon once per minute | `/spawnlife spawn cat` |
| Pip the Fox | Ambient reactions | `/spawnlife spawn fox` |
| Buttons the Parrot | Ambient reactions | `/spawnlife spawn parrot` |
| The Crooked One | Hidden one-time conversation and a temporary nausea curse | `/spawnlife spawn illusioner` |
| Elowen the Baker | Town and story conversation | `/spawnlife spawn baker` |
| Jory the Mason | Town and story conversation | `/spawnlife spawn mason` |
| Nell the Courier | Town and story conversation | `/spawnlife spawn courier` |
| Oren the Dockhand | Town and story conversation | `/spawnlife spawn dockhand` |
| Maeve the Seamstress | Town and story conversation | `/spawnlife spawn seamstress` |

## NPC management pattern

Dedicated NPC commands use the same four actions:

- `/<npc> spawn` - place the NPC at your exact location and facing.
- `/<npc> remove` - remove the nearest matching NPC within six blocks.
- `/<npc> list` - list current placements.
- `/<npc> refresh` - refresh Citizens skin, equipment, nameplate, and interaction data.

Use the command shown in the tables as `<npc>`. Ambient characters instead use `/spawnlife spawn|remove|list <type>` and `/spawnlife refresh`.

NPC placements live in Citizens and SMPCore data. Copy both plugin data sets with the world when moving the server.
