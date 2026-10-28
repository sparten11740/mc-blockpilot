# Design

## Context

- `join world` opens the save through the vanilla world-open flow and
  answers once the player is in the world and no loading screen shows. The
  integrated server starts on its own thread and ticks from then on, while
  the client still loads terrain.
- In vanilla, nothing ticks before the server has started: loading the
  levels and preparing the spawn area only load chunks. The server-started
  event on both loaders fires after that and before the first tick.
- `tick freeze` sets the server's tick rate manager frozen and marks the
  change as Blockpilot's, and the server-stopping hook undoes it, so a world
  is never saved frozen because of Blockpilot.
- Leaving a singleplayer world halts the integrated server, and the client
  waits until it has shut down. Shutting down fires the server-stopping
  hook first, with no ticks after it, and then saves, writing the overworld's
  game time to level.dat.
- `join world <folder>` takes the folder as free text to the end of the
  line, since folder names hold spaces. Options go before free text, as in
  `server as=player <command>`.
- Sable runs its sub-levels, their physics, loading them from the save and
  sending them to players from one container tick, which it skips while the
  server is frozen. After a frozen join neither the server nor the client
  has any sub-levels until a tick runs. Vanilla never freezes players, so the
  client still moves the player.

## Goals / Non-Goals

**Goals:**
- The first reading after a frozen join shows the save's game time and
  everything at its saved state.
- `saved_at` is exactly the game time in level.dat.

**Non-Goals:**
- Freezing from `-Dblockpilot.autojoin`; `leave` plus a frozen `join` covers
  reloads within one game.
- Making Sable send sub-levels while frozen.

## Decisions

### D1: Freeze from the server-started hook

`join world tick=frozen` notes the folder as a pending freeze before it opens
the save. A new server-started hook (NeoForge's `ServerStartedEvent`,
Fabric's `SERVER_STARTED`) freezes the server's tick rate manager when the
started server's folder is the pending one, marks the change as Blockpilot's
as `tick freeze` does, and clears the pending freeze. The join also clears it
when it answers, fails or is cancelled, so a join that never gets a server
leaves nothing behind.

Alternatives: freezing at the end of the first server tick is a tick late. A
mixin into the server's loop would do no better than the loaders' event.

### D2: `tick=frozen` as an option

The freeze is the option `tick=frozen`, written before the folder:
`join world tick=frozen New World`. A bare `frozen` keyword would collide
with folder names that start with the word, since the folder is free text.
`join server` with the option fails with `err syntax` before leaving the
current game.

### D3: `saved_at` from the server-stopping hook

The server-stopping hook records the overworld's game time. `leave` and
`quit` clear that record before they leave and add `saved_at=t<tick>` from it
once the world is gone (JSON `saved_at`, a number). Leaving a multiplayer
server stops no local server, so there is no record and no field.

Alternative: reading level.dat after the save would need the save's path and
an NBT read for the same number.

## Risks / Trade-offs

- [The client's loading might wait on a server tick and never end while
  frozen] → The dev-client check joins frozen and expects the usual answer;
  vanilla keeps sending chunks and handling the connection while frozen.
- [After a frozen join, Sable sub-levels are missing on both sides, and the
  player can fall where a deck would be] → The README says so. On the dev
  client the server's copy came with the first `tick step 1`, one tick on
  from the save with its saved velocity, and the client's with the second.
  The exact saved pose can't be read, since Sable steps a sub-level in the
  tick that loads it.
