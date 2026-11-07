# Proposal

## Why

The client's sound, message and particle logs outlive the world they were
logged in, while their ticks are the world's game time, which a new world
starts again near 0. After an agent leaves a world that ran to tick 3,900
and makes a new one under the same name, `sounds since=400` lists the old
world's sounds from ticks 3,753 to 3,911 beside the new world's, ticks in
the reply's future. A check that runs `use` and then reads `sounds since=`
takes an old world's sound for one that just played. `joined=` doesn't
help: it shows only when `since=` predates the join.

## What Changes

- Entering a world starts the sound, message and particle logs anew, so
  `since=`, the window a reply covers and `from=` take in only that world's
  entries: a singleplayer world, also one left a moment before, or a server.
- The logs are cleared rather than their entries tagged with a world number,
  as every query answers the same either way and nothing else has to know
  the number.
- Between leaving a world and entering the next, the logs keep the last
  world's entries, so what it said on the way out can still be read.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: the logs start anew in each world.

## Impact

- `game/observe/TickLog.java` (`clear`) and `Observations.java`, which
  clears its logs when the player enters a world.
- `tools/smoke_test.py`, `docs/commands.md` and the CHANGELOG.
