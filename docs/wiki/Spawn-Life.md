# Spawn Life

Staff can place small ambient characters anywhere around spawn with `/spawnlife spawn <type>`. They are protected Citizens NPCs, persist through restarts, and use short two-line nameplates.

## Available Characters

- `dog` - Biscuit. Right-click him for a fetch stick, then drop it within 28 blocks. He runs to it, picks it up, returns it, and walks back to his starting point.
- `cat` - Miso the Mouser.
- `fox` - Pip the Fox.
- `parrot` - Buttons the Parrot.
- `illusioner` - the Crooked One. Intended for a hidden corner; each player gets his full encounter and two-minute nausea curse once.
- `baker` - Elowen, with ordinary bakery and weather chatter.
- `mason` - Jory, with building and bell chatter.
- `courier` - Nell, with roads, deliveries, and Season 5 chatter.
- `dockhand` - Oren, with harbor and fog chatter.
- `seamstress` - Maeve, with clothing and trade chatter.
- `tavern_host` - Tamsin. Place her at the tavern entrance; she directs guests toward the tables, bar, drinks, and games.
- `tavern_regular` - Nessa. A back-room regular with jokes about cards, darts, slots, and her tab.
- `tavern_tipsy` - Garrick. A visibly rougher patron whose dialogue insists the floor and hallway are moving.

Animals make occasional local sounds when a player is nearby. Citizens are quieter. Right-click dialogue is short and individually throttled so repeated clicks do not spam chat.

Hold a normal bone and right-click Biscuit, or hold cod/salmon and right-click Miso, to feed them. The food is consumed only on a successful interaction. Each player can feed each animal once per minute; hearts, sounds, and a short reaction confirm it.

## Fetch Safety

Biscuit's stick belongs to the player who requested it. It cannot stack, be crafted, or be stored in containers. Only one throw can run for a player and one for a dog at a time. Unreachable throws time out, return the stick, and send Biscuit home. If the player's inventory is full, the stick is returned at their feet for that player.

Use `/spawnlife remove <type>` next to a character to remove it, `/spawnlife list [type]` to inspect placements, and `/spawnlife refresh` after changing NPC data.

## Complete Placement Command List

### Tavern

- `/spawnlife spawn tavern_host`
- `/spawnlife spawn tavern_regular`
- `/spawnlife spawn tavern_tipsy`

### Animals and Fetch

- `/spawnlife spawn dog`
- `/spawnlife spawn cat`
- `/spawnlife spawn fox`
- `/spawnlife spawn parrot`

### Town Citizens

- `/spawnlife spawn baker`
- `/spawnlife spawn mason`
- `/spawnlife spawn courier`
- `/spawnlife spawn dockhand`
- `/spawnlife spawn seamstress`

### Hidden Encounter

- `/spawnlife spawn illusioner`

Stand exactly where the character should appear and face the direction they should look before running a spawn command. Use `/spawnlife remove <type>` while standing within six blocks if a placement needs to be redone.
