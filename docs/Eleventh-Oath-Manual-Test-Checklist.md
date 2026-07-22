# The Eleventh Oath manual test checklist

## Clean Java player

- Join with no story row and confirm Mira's delayed arrival dialogue plays once.
- Run `/veil`; confirm all eight categories render, locked entries show stable `???`, and the objective points toward Mira.
- Rejoin and confirm the introduction does not replay.
- Use `/veil skip` during a sequence and confirm no later line appears.

## Existing endgame player

- Join with successful historical boss-fight reports and no story row.
- Confirm recorded victories unlock the matching memories without guessing unrelated progress.
- Confirm completed Mayor orders are reflected without consuming materials or granting rewards again.

## Bedrock player

- Open `/veil` and every category; confirm buttons remain visible and cannot be removed.
- Use `/veil text`, `/veil memories`, and `/veil choose` as the non-click fallback.
- Leave a final-choice menu open for 90 seconds and confirm it closes without resolving the choice.

## Boss encounters

- Start one solo fight and one team fight through Malakar.
- Confirm canonical entrance, real phase, one-time low-health, and defeat lines occur in order.
- Confirm only eligible participants receive the memory; spectators and admin test encounters do not.
- Heal the boss above and below 20% and confirm the low-health line never repeats.
- Repeat the kill and confirm normal dialogue remains but the memory cinematic and unlock do not duplicate.
- Disconnect an eligible participant before the kill, restart, and confirm the memory persists for that UUID.

## Final scene

- Defeat the Oathkeeper normally and confirm loot is secured before the choice menu opens.
- Test Mend, Bind, and Sever on separate profiles; confirm each persists after restart and none changes rewards or stats.
- Confirm a second choice is rejected.
- Speak to Silas afterward and confirm the blank-card epilogue unlocks once.

## Items and side quests

- Hold a Soul Imprint for the first time and confirm Orin's line and journal entry unlock once.
- Complete one successful Awakening, one successful Corruption, and one destructive Corruption failure; confirm counters change only after the existing result/recovery process finishes.
- Complete Torren's Redstone Pulse, Rowan's Harvest Moon, Vespera's Moonlit Thesis, Bram's canteen, Rook's trial, and the active goblin hunt; confirm their journal hooks unlock once.

## Reliability

- Restart with profiles dirty and confirm chapter, stage, memories, entries, flags, counters, and alignment survive.
- Run `/veil admin reload` during dialogue and confirm scheduled lines stop cleanly.
- Temporarily invalidate one optional sound or particle in `story-ambient.yml`; confirm the rest of the story keeps working.
- Confirm `/veil admin status <player>` matches the journal after migration and restart.
