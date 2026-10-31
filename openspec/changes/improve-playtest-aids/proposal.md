# Proposal

## Why

A play-test session turned up one bug and several gaps. `block` reports only
the properties that differ from the block's default, and a predicate reads a
left-out field as `none`. A test of a default value therefore never holds:
`assert block <pos> props.lit==false` fails on an unlit lamp with
`props.lit=none`, so a closed valve or an idle sprinkler can't be checked
directly. The gaps:
- `find`'s relative positions were misread after the player moved. The help
  doesn't say what they are relative to, or that JSON gives them absolute.
- Creative inventory tabs can only be clicked by working out their
  coordinates from a screenshot.
- Checking whether an entity's legs swing takes third-person screenshot
  bursts.
- A placed camera that lands inside a hull block gives a useless shot and
  needs a retry.
- `place`'s errors don't say how to recover.

## What Changes

- `block <pos>` reports every state property, default values included, so
  `props.<name>==<default>` holds when it should. `blocks` keeps naming only
  non-default properties in its palette.
- The `find` help and README say that `~` positions are relative to the
  player's block as the find runs, and that JSON's `at_abs` gives each one
  absolute.
- `screen` lists the creative inventory's tabs on the current page as
  `t<i>:tab "<name>" @x,y,w,h`, the open one with `selected`, so a tab can be
  clicked at the centre of its bounds.
- `entity` adds `walk=<speed>` and `walk_pos=<position>` for a living entity:
  its walk animation's limb speed and phase.
- `camera at <pos> … clear` moves the camera along its view line toward the
  look point, out of any block with a collision shape, and reports how far as
  `cleared=<blocks>`, or `uncleared` when no clear point lies within 8 blocks.
- `place`: `err no_item` adds a hint to give the item first, and a target cell
  that is taken names the cell above when that one is free.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: `block` reports every property; `find`'s positions as
  run; creative tabs in `screen`; walk animation in `entity`.
- `client-observation`: `camera at … clear`.
- `player-control`: `place`'s error hints.

## Impact

- `game/PerceptionCommands.java` (`block`, `find` help, `entity`),
  `game/Screens.java` (creative tabs), `game/observe/ObservationCommands.java`
  (`camera at … clear`), `game/ActionCommands.java` (`place`).
- A new `CreativeModeInventoryScreenAccessor` mixin in the 1.21.1 and 26.1
  source sets, and a `ClientPlatform.Services` method for the tabs on the
  current page, from NeoForge's `getCurrentPage()` and Fabric API's creative
  screen page API.
- README: the perception, observe and interact rows.
