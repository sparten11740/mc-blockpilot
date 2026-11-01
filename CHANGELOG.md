# Changelog

## Unreleased

- Blockpilot is licensed under Apache-2.0 instead of MIT.

## 1.0.0-alpha.0 (2026-11-01)

The first release. Blockpilot lets AI agents and scripts observe and control Minecraft through a fast, token-efficient
local interface, to play the game or play-test a mod. It comes for NeoForge and Fabric on Minecraft 1.21.1 and 26.1.2,
with `blockpilot-api` 2.0.0 for mods that add their own commands.

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

Blockpilot is licensed under MIT.
