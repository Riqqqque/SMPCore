# Classes

Players receive one hidden class on first join. The class is meant to be discovered through gameplay. If the class has a command, the player privately receives a hint.

Use `/powerinfo` to read about classes. Staff can assign classes with `/setpower <player> <class>`. Ancient Scroll rerolls a player's class, even if they already have one, and avoids giving back the same current class. Java players can right-click it; Bedrock players can tap or swing it if their client does not send a normal item-use packet.

An `Ancient Scroll` can also be upgraded in the Awakening Table with 1 Nether Star. If the awakening succeeds, it becomes an `Awakened Ancient Scroll`. Using that upgraded scroll opens a menu where the player can choose any real class instead of rolling randomly. Picking the player's current class is blocked so the scroll is not wasted. Choosing `The Honored One` kills the player once as the cost. If the scroll awakening fails, the scroll is destroyed.

The `/powerinfo` menu is sorted from most common to rarest.

## Class Chances

- `Juggernaut` - 7.41%. Tank identity with extra health, crouch knockback resistance, `/unstoppableforce`, fall slams, and 80% less fall damage.
- `Verdant` - 7.41%. Crop, wood, healing, and Wand of Mother Nature gameplay.
- `Titan` - 6.17%. Larger body, extra hearts, heavy melee damage, and lower knockback.
- `Veil Assassin` - 5.76%. Eight-heart assassin with complete crouch concealment, full-speed crouching and Speed IV while veiled, backstabs, no netherite armor, and `/smokebomb`. Complete concealment hides the player model, equipment, nameplate, and ally marker while leaving them listed in Tab.
- `Berserker` - 5.76%. Strength fighter with low-health speed and regeneration.
- `Prospector` - 5.35%. Mining class with haste, underground night vision, 25% extra ore chances, and extra health.
- `Nightshade` - 4.53%. `/shadow toggle`, toggleable night vision, poison hits, invisible gear hiding, and speed while hidden.
- `Deadeye` - 4.12%. Stronger ranged mark pressure, toggleable arrow preservation, and anti-stealth utility.
- `Frostborn` - 4.12%. Cold-biome buffs, snowball weakness, stronger chill combat pressure, and defensive retaliation.
- `Sentinel` - 4.12%. Stronger defensive guard identity.
- `Druid` - 3.70%. Right-click Druid's Grimoire to pick a positive blessing for self and nearby teammates.
- `Arcanist` - 3.29%. XP, enchanting, luck, durability, and `/arcanebook` book upgrades.
- `Oathbound` - 3.29%. Keeps solo speed, buffs near teammates, and can be summoned by teammates with `/oathsummon`.
- `Runesmith` - 3.29%. Haste II, better durability protection, and stronger boss-kill repairs.
- `Shadow Monarch` - 3.29%. Stores killed hostile mobs only. `/msummon` summons 1 stored hostile mob, `/msummon <amount>` summons more, and `/msummon despawn` unsummons active mobs. Summons have 40 HP, boosted damage, armor, resistance, and knockback defense.
- `The World` - 3.29%. Bound clock that stops time in a radius.
- `Graveborn` - 2.88%. Undead resistance, undead-fueled second chances, and stronger buffs around player deaths.
- `Riftwarden` - 2.88%. Stronger boss-hunter with Slow Falling near custom bosses, plus Resistance near bosses or in the End.
- `Stormcaller` - 2.88%. Faster storm and lightning combat identity, with extra axe and mace PvP buffs. Use `/stormcaller off` if you want to disable strike procs.
- `Voidwalker` - 2.88%. `/voidstep` and `/voidvision`, with a shorter blink cooldown and toggleable night vision.
- `Bloodmender` - 2.47%. Stronger sustain fighter with `/bloodsacrifice` team healing and `/curse` armor pressure.
- `Oracle Eye` - 2.47%. `/xray` highlights players, entities, and ores for 2.5 minutes and warns near diamonds or ancient debris.
- `Skybound` - 2.06%. Creative-style flight burst with a cooldown.
- `Wayfarer` - 2.06%. `/travel <x> <y> <z> <dimension>` and `/travel close` for portal control.
- `Ashen Soul` - 1.65%. Fire-themed survival and death-prevention flavor.
- `Tideborn` - 1.65%. Strong water buffs, underwater breathing, and normal underwater mining.
- `Mortal` - 1.23%. No special class abilities until rerolled with Ancient Scroll.
- `The Honored One` - 0.01%. Toggleable `/infinity` projectile immunity, stopped hostile projectiles, and `/domainexpansion` with a 15-second sculk domain and 10-minute cooldown.

## Bound Class Items

- `World Clock` belongs to The World.
- `Wand of Mother Nature` belongs to Verdant.
- `Druid's Grimoire` belongs to Druid.

Bound class items are preserved through death and stay tied to their owner. The Wand of Mother Nature is limited to one per Verdant player and should not be stored as stacks.

## Boss Encounter Rules

Classes keep their normal PvP and world behavior, but boss fights resist shortcuts that erase mechanics. Bosses resist Veil Assassin's health-based backstab, hard Chill effects, summon swarm damage, Infinity projectile blocking, and Time Stop. Boss-specific damage bonuses remain, but use lower encounter values shown in `/powerinfo`.

Domain Expansion, Voidstep, Wayfarer portals, Oath Summon, and Skybound flight cannot move fighters around an active encounter. Phoenix Rebirth and Graveborn's second chance still work against normal lethal damage, but not a failed boss mechanic. Blood Sacrifice does not consume health when a healing seal blocks every target. Druid is unchanged; its healing blessings simply obey the same healing seals as every other heal.
