# Changelog

## 1.1.0 (unreleased)

For filming: a camera that glides, follows, circles, zooms and tilts, one that films far from the player, entities taken
by name, sounds where the camera hears them, and marks for the edit. `blockpilot-api` becomes 2.1.0, an addition, so
integrations built against 2.0.0 keep working.

- **Camera moves:** `camera move <pos> [via <pos>]… [look <pos|e<id>|ahead>] over <duration> [ease=]` glides the camera
  to a point, placed anew in every frame, through up to 32 via points on one smooth curve at a constant speed. It keeps
  its target, turns to a new one or looks ahead, and `ease=in|out|inout` shapes its speed. With `background` it glides
  while the script goes on, so characters can move and sounds play during the shot, and `camera wait` waits for its end.
  `stop` leaves it where the last frame drew it. `status` names a placed camera's position, and an entity look target is
  followed where each frame draws it.
- **Following and orbiting:** `camera follow <entity> [at <~dx,dy,dz>] [turning]` keeps the camera's framing on a
  moving entity, turning with it over its shoulder if asked, and `camera orbit <pos|entity> [by <degrees>] over
  <duration>` circles a point or a moving entity, timed like a move, with `ease=` and `background`.
- **Camera lens:** `fov=` and `roll=` give a placed camera its own field of view, from 1 to 170 degrees, and tilt;
  `camera at` and `follow` set them, `move` and `orbit` glide to them, so a zoom can tighten along a move.
- **A camera away from the player:** `camera at … away` has the integrated server load, send and play the world
  around the camera, so a set a thousand blocks off can be filmed while the player stands still, and `look` and `find`
  search around it.
- **Sounds:** `sound <id> [at <pos>]` plays a sound on this client alone, heard from the camera, in singleplayer and on
  servers alike.
- **Entities by name:** every command that takes `e<id>` also takes `@<name>`, the entity with that tag or custom name,
  the nearest of several, so a script can summon a tagged actor and point the camera at it without reading its id.
- **Slow motion:** `docs/recording.md` shows how to film it with `tick rate`, and what slows with the game.
- **A take that ends with its script:** `record script [name]` records until its script ends, however it ends, and
  the reply carries the take's summary as `! record …`, so a failing step no longer leaves a recording running.
- **Recording marks:** `record mark <label>` notes the video time and frame a script reached; `record stop` lists the
  marks, and `<name>.marks.json` keeps them beside the video.
- **Fewer fields in a sample:** `sample … fields=a,b` keeps only those fields in each row, so a wide readout stays
  readable.
- **Server steps:** consecutive server steps run back to back on the server's ticks, so `settle=1t` spaces commands
  exactly one server tick apart and `settle=0t` runs them in one.
- **Fixes:** `server function` of a function without `return` answers `ok`, not `err command_failed`. `wait <N>t`
  waits N ticks, where it waited one fewer, so scripts that allowed for that run a tick longer per wait. On 26.x,
  `screenshot` now names a placed camera's position. A command line the launcher can't read prints one line saying
  what is wrong, not its whole usage text first.
- **Extension API:** `Form.repeated(limit, group)` declares a part that may come several times, read back with
  `Args.values` and `Args.positions`. An `ArgType.ENTITY` slot takes `@<name>` too, looked up before the handler
  runs, so the handler still reads an id; `Lexicon.isEntityName` tells a name. `CommandContext.atEnd(action)` runs an
  action when the script ends, however it ends, before its reply.

## 1.0.0 (2026-11-05)

The first stable release, and it adds Minecraft 26.2 and 26.3. Blockpilot now comes for NeoForge and Fabric on
Minecraft 1.21.1, 26.1.2, 26.2 and 26.3, and as a Java agent for plain Minecraft 26.1.2, 26.2 and 26.3, one jar per
version. `blockpilot-api` stays at 2.0.0.

- **Mining:** `mine` counts a block as mined only if the player dug it. A target that changed by itself, such as dirt
  that grass spread onto, or an empty cell, used to count as mined. Now `mine` at a position digs what is there or
  gives `err no_block`, and `mine` by type goes on to the next block.
- **Capture on 26.1.2:** screenshots, pixel probes and the last frames of a recording wait for the GPU's copy, where
  some graphics drivers used to hand them an older frame, and recordings no longer show uneven motion.
- **Keys:** key names are the running game's, so 26.3 has no `f25` and adds names such as `ac_home`.

## 1.0.0-alpha.0 (2026-11-01)

The first release. Blockpilot lets AI agents and scripts observe and control Minecraft through a fast, token-efficient
local interface, to play the game or play-test a mod. It comes for NeoForge and Fabric on Minecraft 1.21.1 and 26.1.2,
and as a Java agent for plain Minecraft 26.1.2 without a mod loader, with `blockpilot-api` 2.0.0 for mods that add their
own commands.

- **Interfaces:** MCP over stdio through the launcher the game writes to `<game dir>/blockpilot/blockpilot`, which
  outlives restarts of the game; MCP over Streamable HTTP; a line protocol; and a command-line client. All of them
  listen on loopback only and need a token.
- **Command language:** scripts of commands, waits and assertions on any query, sampling while an action runs, events,
  and JSON output. Its grammar is in `docs/grammar.ebnf`, with railroad diagrams.
- **Perception:** player status, the blocks and entities around, finding blocks by id, tag, pattern or block state,
  block and region reads (also server-side), light, inventories, screens and NBT.
- **Movement and interaction:** path finding, moving and turning, mining, placing, using, attacking, crafting,
  containers, raw keys, mouse and key chords, and flight. Real input interrupts agent actions, and F8 turns agent
  control off.
- **Observation:** screenshots, bursts and contact sheets, pixel probes, placed cameras, client options including sound
  volumes, waiting for chunks to settle, and logs of sounds, messages, particles and the game log.
- **Video recording:** `record` saves an MP4 with the game's sound from its framebuffer, with the window in the
  background if need be. It needs ffmpeg.
- **Play-testing:** test worlds, snapshots, the integrated server's commands with their output, tick freezing and
  stepping, performance captures with stack sampling, and simulated server, client and network load.
- **Mods:** sub-levels with Sable, and NeoForge mod configs.

Blockpilot is licensed under Apache-2.0.
