# Sable sub-levels

Sable builds sub-levels: structures such as ships whose blocks live in a *plot*, a grid far out in the same dimension
(around x and z 20,000,000), and are drawn wherever the sub-level's pose puts them. With Sable installed, Blockpilot
works with them:

- A position in a plot prints as itself, followed by the sub-level and where that block is in the world, e.g.
  `aim:stone_button 20481032,129,20481033 sub=ship world=10.5,-58.5,6.5`. It pastes back into `block`, `blocks`, `nbt`,
  `face`, `use`, `mine`, `place` and `camera`. `status` shows `aboard:<name>` while the player stands on or rides a
  sub-level, and `! sable:boarded <name>` and `! sable:left <name>` arrive when that changes (falling or flying off, or
  the sub-level unloading under the player, counts as leaving; JSON adds the UUID as `id`).
- `block <plot position> light` gives the plot's own light for that cell and adds `world_sky=` and `world_block_light=`,
  the light at the world cell where the block is drawn, to compare with.
- `face` and `use` at a plot position aim where the sub-level is on each tick, so they work on a moving ship, where a
  world position is left behind. As everywhere, `use` fails with `err obstructed <id> <position>` when something else is
  in the way; `use <pos> any` clicks it anyway, for multi-block panels whose mod takes a click on any of their blocks. A
  camera placed at, or looking at, a plot position rides along, and so do a `camera move`'s point, via points and look,
  so a path bends with a moving ship. `goto` refuses plot positions; `sable:tp` is the way aboard, and after `err
  obstructed`, `sable:tp` to the same position moves the player to the nearest spot from which it is in sight, if there
  is one (else it answers `hidden`).
- The `sable:` commands take a sub-level as its UUID, its name, or `here` (the one the player stands on or rides, else
  the one under the crosshair):
  - `sable:list` lists the sub-levels within 128 blocks, nearest first. In singleplayer with cheats on it adds
    `server_only=<n>` when the integrated server holds sub-levels in that radius that the client hasn't received (after
    a lost packet, say), and `sable:list side=server` lists the server's.
  - `sable:info here` gives the pose, velocity, height above ground (`agl`), bounds and whether the player rides it;
    `sable:info ship brief` only heading, pitch, roll, speed, climb rate, world y (`y`) and `agl`, e.g. for `sample
    sable:info ship brief every 10t x20` over a take-off. `agl` is measured from the lowest corner of the lowest block,
    however the sub-level is turned, to the ground straight under it. With `side=server` it reads the integrated
    server's copy (cheats on), also of a sub-level the client hasn't received, and measures `agl` in the server's level,
    so `sample sable:info ship brief side=server and <server-side query> every 20t x30` reads one copy in both rows. The
    size in `sable:list` and the plot bounds are the world bounds turned back into the plot, so they grow while the
    sub-level is turned.
  - `sable:find ship *door* 3` finds blocks aboard, with plot and world positions; with `side=server` from the server's
    copy, also on a sub-level the client hasn't received. They come nearest to the player first, nearest to `near=<pos>`
    (a world or plot position), or with `sort=-z` along the sub-level's own plot axes (`-` for largest first), so
    `sable:find ship *booster* 1 sort=-z` is the one furthest toward plot +z however the ship is turned. The limit cuts
    the ordered list, and `count=` gives all matches when it left some out. The block may carry states, as in `find`:
    `sable:find ship *_slab[type=top]`; so may the block `sable:tp` boards next to.
  - `sable:tp 20481032,129,20481033` puts the player on a free spot next to that block aboard (cheats on): the nearest
    from which the block is in sight, so inside a hull rather than on it, else the nearest with `hidden`. `dist` is how
    far the player's eyes are from the block. It answers once the client shows the player standing on that spot, also on
    a ship under way: Sable's client runs sub-levels a tick or so behind the server, so the player is put on the spot as
    the client has the ship. When the client doesn't show that within 5 s, it fails with `err not_aboard` and what the
    client shows. `sable:tp ship *seat*` does the same next to the matching block aboard nearest the player, named in
    the reply as `block` and `target`; it reads the server's copy, so it also boards a ship that has flown out of the
    client's sight, without carrying a plot position along.
  - `sable:world 20481032.5,129.5,20481033.5` converts plot to world, and `sable:plot ship 10.5,-58.5,6.5` world to
    plot.
- Right after `join world tick=frozen` neither side has any sub-levels: Sable loads them from the save, and sends them
  to the player, only on ticks that run. After `tick step 1` the server's copy is there (`side=server`), one tick on
  from the save as the game time is, with its saved velocity; after a second `tick step 1` the client's is too. Until
  then the client has no deck to stand on, and players don't freeze, so a player saved aboard can fall.

Blockpilot talks to Sable only through its published [companion API](https://github.com/ryanhcode/sable-companion) and
bundles nothing of it. Without Sable nothing changes and nothing has to be installed; `help sable` says why the `sable:`
commands are missing. Companion versions 1.x from 1.6 are supported (Sable 2.0.5 and 2.0.6 ship 1.6.0). With any other
version the integration stays off, and the log and `help sable` name the version found. Tested with Sable 2.0.6
(companion 1.6.0), Create 6.0.10 and Create Aeronautics 1.3.2 on NeoForge 1.21.1. Sable has no release for 26.x yet, so
the 26.x jars are built without the integration; with a Sable installed they run as without it, and `help sable` says
the build has no Sable integration.

[Back to the README](../README.md)
