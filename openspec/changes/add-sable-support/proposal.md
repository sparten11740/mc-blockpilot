# Proposal

## Why

Mods built on Sable, such as ships and other assembled structures that fly or
drive, keep a sub-level's blocks in a plot far from where they are seen.
Blockpilot only knows the world. An agent play-testing such a mod gets plot
coordinates printed as offsets from the player, can't `find` anything aboard,
aims `use` at a world point the sub-level has moved away from, and has to hunt
blocks by hand. Play-testing also turned up seven small gaps elsewhere, which
this change fixes too.

Sable is an optional mod. Blockpilot SHALL keep installing, loading and
behaving exactly as now without it, on every loader, and an incompatible Sable
SHALL never stop the game from starting.

## What Changes

- With Sable installed, the built-in commands understand sub-levels:
  - A position inside a plot is printed as itself, with the sub-level's name and
    its world position, instead of as an offset from the player. `status`'s aim
    on a block aboard is the first case.
  - `block`, `face`, `use` and `camera` accept plot positions. Aiming follows
    the sub-level as it moves, and `use` reports what it hit when something is
    in the way.
- New `sable:` commands, registered through the extension API only when Sable
  is installed and compatible:
  - `sable:list` lists the sub-levels near the player.
  - `sable:info` gives one sub-level's pose, velocity and bounds.
  - `sable:world` and `sable:plot` convert positions between plot and world.
  - `sable:find` finds blocks aboard a sub-level.
  - `sable:tp` puts the player next to a block aboard.
- Without Sable, or with an incompatible version, none of this exists. `help`
  says why, and every other command works as before.
- Play-test fixes:
  - Key names are accepted with underscores (`left_shift`), as help shows them.
  - `sample … while` also takes a `wait` (`while wait 200t`).
  - `camera at` reports the block it sits in, and warns when that block is
    solid.
  - `blocks … grid` prints one block name per cell instead of run lengths.
  - The CLI prints a one-line stderr summary when steps failed or were skipped.
    Its exit code already says so.
  - While singleplayer is paused, commands that need server ticks fail at once
    with `err paused` instead of blocking the action queue behind them.
  - A predicate on a field the reply leaves out reads it as `none`, as the
    timeout report already prints it: `wait status until state!=menu` now
    returns once the player is in a world.

## Capabilities

### New Capabilities
- `sable-sublevels`: how Blockpilot works with Sable sub-levels when Sable is
  installed. That covers plot positions in replies and arguments, aiming and
  cameras that follow a sub-level, the `sable:` commands, and how Blockpilot
  behaves without Sable or with an incompatible version.

### Modified Capabilities
- `player-control`: key names also accepted with underscores
  (`left_shift`).
- `command-language`: `sample … while` also takes a `wait`, in the grammar and
  in the sampling requirement; a field left out reads as `none` in
  predicates.
- `client-observation`: `camera at` reports the block at the camera and warns
  inside a solid one.
- `game-perception`: `blocks … grid` lays a region out one name per cell.
- `server-bridge` (from `add-test-harness`): server work fails with
  `err paused` while the game is paused.

## Impact

- **Code:**
  - A new compat package for Sable, which is the only code that touches Sable's
    API.
  - A small sub-level interface the built-in commands use, which does nothing
    without Sable.
  - Changes to position printing, aiming, the camera placement, the key lookup,
    the sampler, the region dump and the CLI.
- **Build:** a compile-only dependency on Sable's public companion API
  (`dev.ryanhcode.sable-companion`, from `maven.ryanhcode.dev`). Nothing of
  Sable's is bundled, and the mod metadata declares no Sable dependency that the
  loader enforces.
- **Testing:**
  - Unit tests for the sub-level interface's arithmetic and for the isolation
    rule (nothing outside the compat package refers to Sable).
  - Smoke tests without Sable on both loaders.
  - A play-test in a dev client with Sable and a mod that builds sub-levels.
- **Docs:** the README's command table and a section on Sable.
