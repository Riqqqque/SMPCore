# Bosses and Rituals

Bosses can be spawned by staff through `/bosses`, but survival players use shrine rituals from `/bossrituals`.

Every custom boss has a boss bar, a live hologram, tracked cleanup, custom particles and sounds, phase mechanics, public kill announcements, server-wide damage leaderboards, personal after-action reports, and boss trophy drops for Covenant recipes.

## How Rituals Work

1. Build the shrine exactly as shown below or in `/bossrituals`.
2. Hold the listed catalyst item.
3. Right-click the focus block or any shrine block touching the focus.
4. If the shrine is valid, the catalyst is consumed and the shrine blocks disappear.
5. If the shrine is invalid, chat tells you what piece is wrong or missing.

Important rules:

- Aurelion the Rift Seraph only answers in the End.
- Successful rituals delete the shrine on use, so do not build it out of blocks you are not ready to spend.
- If a ritual fizzles because the area unloads or the boss cannot form, the catalyst is refunded.
- Stronger bosses may create arena pressure. Edge camping and high-ground cheese are punished.
- If everyone in the fight area dies or leaves after the fight starts, the boss despawns and the fight counts as a failure.
- Boss rewards spawn in a labeled loot chest at or near the death spot. The plugin first looks for a safe space, then force-clears a nearby non-protected block if needed. It will not overwrite containers, existing boss loot, portals, bedrock/barriers, command blocks, structure blocks, or jigsaw blocks. Natural item drops are only used as the final emergency fallback.
- Every boss victory is guaranteed to produce at least one reward item. Random bonus drops can fail, but the base reward cannot.
- Boss drops are doubled from 4-6 PM America/Denver by default. The server announces when double loot starts, warns before it ends, and announces when it ends.

## Ritual Cheat Sheet

| Boss | Focus | Catalyst | Main Reward Path |
| --- | --- | --- | --- |
| Yule the Minion | Bell | Golden Sword | Gilded Skull, Oathbound Plate |
| Kael the Ashen | Soul Campfire | Bow | Solar Ember, Titan Gear |
| Vesper the Widow Queen | Cobweb | Fermented Spider Eye | Widow Silk, Verdant Heart |
| Voralith the Crimson Warden | Sculk Shrieker | Echo Shard | Crimson Rib, Sculk Heart, Dominion Core |
| Aurelion the Rift Seraph | End Rod | Eye of Ender | Rift Lens, Void Halo, Awakening Table chance |
| Nereida the Abyss Mother | Conduit | Heart of the Sea | Abyssal Pearl, Tideheart |
| The Iron Saint | Anvil | Iron Block | Titan Gear, Saint Alloy |
| Mirewood the Root Tyrant | Mangrove Roots | Spore Blossom | Living Bark, Verdant Heart |

## How to Read the Shrine Diagrams

The diagrams below are top-down views of each shrine. North is the top of the diagram.

- `Y=0` is the ground or base layer.
- `Y=1` is the layer directly above the center base block.
- `F` is the focus block. Right-click this block, or any shrine block touching it, while holding the catalyst.
- `.` means the slot should be empty or does not matter for the ritual.
- The center stack must be vertical: the `F` block sits directly above the center base block unless the diagram says otherwise.

## Ritual Layouts

### Yule the Minion - Gilded Muster

Yule is the first bruiser boss. He is fast, wears gold armor, and phase two gains Strength, heavier knockback, and Yule's Thralls.

Build:

- Put Soul Sand on the ground.
- Place a Bell directly on top of the Soul Sand.
- Put Gold Blocks touching the Soul Sand on north, south, east, and west.
- Hold a Golden Sword and right-click the Bell or any shrine block.

Visualization:

```text
Legend: F = Bell, S = Soul Sand, G = Gold Block

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. G .
G S G
. G .

Center side view
Y=1  F  Bell
Y=0  S  Soul Sand
```

Final shape:

```text
      Bell
       |
  Gold Soul Sand Gold
       |
      Gold
```

Rewards: 1 Gilded Skull, 35% Oathbound Plate. XP: 225.

### Kael the Ashen - Ashen Wake

Kael is a skeleton marksman that punishes open sight lines. Phase two adds faster burning control shots.

Build:

- Put a Bone Block on the ground.
- Place a Soul Campfire directly on top of that Bone Block.
- Put Bone Blocks touching the center Bone Block on north, south, east, and west.
- Hold a Bow and right-click the Soul Campfire or any shrine block.

Visualization:

```text
Legend: F = Soul Campfire, B = Bone Block

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. B .
B B B
. B .

Center side view
Y=1  F  Soul Campfire
Y=0  B  Bone Block
```

Final shape:

```text
        Soul Campfire
             |
  Bone Block Bone Block Bone Block
             |
        Bone Block
```

Rewards: 2 Solar Ember, 25% Titan Gear. XP: 300.

### Vesper the Widow Queen - Widow's Bloom

Vesper is a spider boss built around leap pressure, poison, and dragging strikes. Phase two is faster and punishes spacing harder.

Build:

- Put a Moss Block on the ground.
- Place a Cobweb directly on top of the Moss Block.
- Put Black Candles on the same height as the Cobweb, touching it north, south, east, and west.
- Hold a Fermented Spider Eye and right-click the Cobweb or any shrine block.

Visualization:

```text
Legend: F = Cobweb, C = Black Candle, M = Moss Block

Y=1 top layer
. C .
C F C
. C .

Y=0 base layer
. . .
. M .
. . .

Center side view
Y=1  F  Cobweb
Y=0  M  Moss Block
```

Final shape:

```text
       Black Candle
            |
Black Candle Cobweb Black Candle
            |
       Black Candle

Moss Block is directly under the Cobweb.
```

Rewards: 2 Widow Silk, 25% Verdant Heart. XP: 320.

### Voralith the Crimson Warden - Crimson Dominion Gate

Voralith is the hardest Warden boss. He uses darkness, dominion pulses, resonance blasts, and heavy melee punishment.

Build:

- Put Reinforced Deepslate on the ground.
- Place a Sculk Shrieker directly on top of the Reinforced Deepslate.
- Put Sculk Catalysts touching the Shrieker on north and south.
- Put Redstone Blocks touching the Shrieker on east and west.
- Put Soul Lanterns on all four diagonal corners from the Shrieker.
- Hold an Echo Shard and right-click the Shrieker or any shrine block.

Visualization:

```text
Legend:
F = Sculk Shrieker
D = Reinforced Deepslate
C = Sculk Catalyst
R = Redstone Block
L = Soul Lantern

Y=1 top layer
L C L
R F R
L C L

Y=0 base layer
. . .
. D .
. . .

Center side view
Y=1  F  Sculk Shrieker
Y=0  D  Reinforced Deepslate
```

Important: the Sculk Catalysts are north and south of the Shrieker. The Redstone Blocks are east and west. The four Soul Lanterns are diagonal corners on the same height as the Shrieker.

Rewards: Dominion Core, 2 Crimson Rib, 1 Sculk Heart. The Dominion Core repairs Crimson Dominion in an anvil. XP: 950.

### Aurelion the Rift Seraph - Rift Coronation

Aurelion is an End-only Enderman boss that bends distance into a weapon. Phase two increases rift pressure and displacement.

Build:

- This ritual only works in the End.
- Put a Purpur Block on the ground.
- Place an End Rod directly on top of the Purpur Block.
- Put End Stone Bricks touching the Purpur Block on north, south, east, and west.
- Hold an Eye of Ender and right-click the End Rod or any shrine block.

Visualization:

```text
Legend: F = End Rod, P = Purpur Block, E = End Stone Bricks

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. E .
E P E
. E .

Center side view
Y=1  F  End Rod
Y=0  P  Purpur Block
```

Final shape:

```text
           End Rod
              |
End Stone Bricks Purpur Block End Stone Bricks
              |
      End Stone Bricks
```

Rewards: 2 Rift Lens, 30% Void Halo, 50% Awakening Table. XP: 600.

Awakening Table note: this boss is the normal player source for Awakening Tables. The chance is controlled by `awakening-table.rift-seraph-drop-chance` in `config.yml`.

### Nereida the Abyss Mother - Abyssal Baptism

Nereida is a drowned boss that turns water and rain into pressure. Phase two gains stronger waves and water-based regeneration.

Build:

- Put a Prismarine block on the ground.
- Place a Conduit directly on top of the Prismarine.
- Put Sea Lanterns touching the Prismarine on north, south, east, and west.
- Hold a Heart of the Sea and right-click the Conduit or any shrine block.

Visualization:

```text
Legend: F = Conduit, P = Prismarine, S = Sea Lantern

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. S .
S P S
. S .

Center side view
Y=1  F  Conduit
Y=0  P  Prismarine
```

Final shape:

```text
       Conduit
          |
Sea Lantern Prismarine Sea Lantern
          |
     Sea Lantern
```

This shrine works on land or underwater.

Rewards: 2 Abyssal Pearl, 30% Tideheart. XP: 475.

### The Iron Saint - Iron Litany

The Iron Saint is a slow, heavy Iron Golem boss. Phase two adds slam pulses and Weakness pressure.

Build:

- Put a Smithing Table on the ground.
- Place an Anvil directly on top of the Smithing Table.
- Put Iron Blocks touching the Smithing Table on north, south, east, and west.
- Hold an Iron Block and right-click the Anvil or any shrine block.

Visualization:

```text
Legend: F = Anvil, S = Smithing Table, I = Iron Block

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. I .
I S I
. I .

Center side view
Y=1  F  Anvil
Y=0  S  Smithing Table
```

Final shape:

```text
         Anvil
           |
Iron Block Smithing Table Iron Block
           |
      Iron Block
```

Rewards: 2 Titan Gear, 30% Saint Alloy. XP: 700.

### Mirewood the Root Tyrant - Root Tyrant's Wake

Mirewood is a root-bound husk boss that slows players and regenerates in phase two.

Build:

- Put a Moss Block on the ground.
- Place Mangrove Roots directly on top of the Moss Block.
- Put Oak Saplings touching the Moss Block on north, south, east, and west.
- Hold a Spore Blossom and right-click the Mangrove Roots or any shrine block.

Visualization:

```text
Legend: F = Mangrove Roots, M = Moss Block, O = Oak Sapling

Y=1 top layer
. . .
. F .
. . .

Y=0 base layer
. O .
O M O
. O .

Center side view
Y=1  F  Mangrove Roots
Y=0  M  Moss Block
```

Final shape:

```text
       Mangrove Roots
             |
Oak Sapling Moss Block Oak Sapling
             |
       Oak Sapling
```

Rewards: 2 Living Bark, 30% Verdant Heart. XP: 440.

## Boss Brews

Boss materials can be brewed into stronger potions in a normal Brewing Stand.

How to brew:

- Open `/bossbrews`, `/bosspotions`, or `/brews` to view the guide.
- Use an Awkward Potion, Splash Awkward Potion, or Lingering Awkward Potion in the bottle slot.
- Put the listed boss material in the ingredient slot.
- The output keeps the same potion form and gains the boss-brew effects.

Current brews:

- Sunforged Ichor: Solar Ember. Fire Resistance I for 20:00, Speed III for 8:00, Haste III for 8:00, Strength I for 8:00.
- Dominion Blood: Crimson Rib or Sculk Heart. Strength III for 6:00, Resistance II for 8:00, Absorption IV for 6:00, Regeneration II for 2:00.
- Rift Draught: Rift Lens or Void Halo. Speed IV for 5:00, Jump Boost III for 5:00, Slow Falling I for 8:00, Invisibility I for 2:00.
- Abyssal Tonic: Abyssal Pearl or Tideheart. Water Breathing I for 30:00, Conduit Power I for 10:00, Dolphin's Grace III for 8:00, Night Vision I for 15:00.
- Verdant Elixir: Living Bark or Verdant Heart. Regeneration III for 3:00, Absorption III for 8:00, Saturation I for 0:20.
- Saint's Resolve: Gilded Skull, Oathbound Plate, Titan Gear, or Saint Alloy. Resistance III for 6:00, Absorption IV for 8:00, Health Boost II for 8:00, Regeneration II for 2:00.
- Widowstep Vial: Widow Silk. Invisibility I for 5:00, Speed III for 5:00, Jump Boost III for 5:00, Strength I for 3:00.

## Troubleshooting Rituals

- If nothing happens, make sure you are right-clicking with the catalyst in your main hand.
- If chat says the catalyst is wrong, the shrine focus was recognized but you used the wrong item.
- If chat says a piece is missing, compare your shrine to the layout above and check block heights.
- If the shrine remains after activation, the ritual did not successfully start.
- If Aurelion refuses to spawn, move the ritual to the End.
- If a boss or visual gets stuck, staff can use `/bosses clearall`.
