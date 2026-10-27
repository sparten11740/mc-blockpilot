# Design

## Context

`sable:list` asks Sable for the sub-levels the client level holds, through
`SubLevels.near`. `sable:find` already reads the server's copy through
`Sides.read`, which runs the same read on the server thread against the
integrated server's level and is gated on cheats. `use` reads its reply's hit
with `ActionCommands.hit()`, which reports `minecraft.hitResult`. The CLI's
readiness wait polls `endpoint.json` once a second. See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- An agent learns from `sable:list` alone that the server holds sub-levels
  the client lacks.
- `use` reports exactly the hit its click was sent with.

**Non-Goals:**
- Repairing a client that missed a sub-level; rejoining the world does that.
- Detecting a client that fails before any mod loads. Nothing of
  Blockpilot's runs then; the README names the usual cause.

## Decisions

### D1. `side=server` through `Sides.read`

`sable:list` takes the `side` option like `sable:find` and runs its read
through `Sides.read`. It captures the player's position on the client thread
first, since the read runs on the server thread. The rows are the same on
both sides.

### D2. The `server_only` note costs one server read, only when allowed

Without `side`, after the client's rows, and only in singleplayer with cheats
on, `sable:list` reads the server's sub-levels within the same radius in one
`Sides`-style server-thread read. It adds `server_only=<n>` for the UUIDs the
client lacks, and leaves the field out at zero. If that read fails or times
out, the list goes out without the note: the client's answer never depends on
it.

*Alternative:* the note without cheats. That would report server state to a
survival player, which the cheats gate on `side=server` exists to prevent.

### D3. `use` reads its hit on the tick the click is handled

The game handles a pressed key during the next client tick, with the hit in
place at that moment. Blockpilot's actions tick at the end of that same tick,
before the next frame picks the hit again. So reading `hitResult` on the
action tick after the press gives the hit that click used. The hit read at
press time could be a tick older, which shows on a moving sub-level. The
press-on-the-right-tick path for blocks aboard already reads its hit this way.
The reply still comes on the same tick as before.

### D4. A crash report ends the readiness wait

The CLI notes when the wait starts. Each time it polls `endpoint.json`, it
lists `<gameDir>/crash-reports/*.txt` and stops at the first one modified
since then: exit 2, `blockpilot: the game crashed while starting: <path>`.
This covers any crash after Minecraft's crash handler is in place, such as a
mod failing during load. It doesn't cover a loader that shows its own error
window instead of writing a report.

## Risks / Trade-offs

- [The note's server read adds latency to `sable:list`] → one read on the
  server thread in singleplayer only; capped by `Sides`' 5 second limit, and
  skipped without cheats.
- [A crash report written for another instance in the same directory] → only
  reports newer than the wait's start count, and two games never share a
  game directory.
