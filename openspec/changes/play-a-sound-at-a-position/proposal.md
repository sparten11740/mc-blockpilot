# Proposal

## Why

There is no way to play a sound for the client alone. The server's
`playsound` measures from the player, so a placed camera farther than 16
blocks from the player never hears a sound at the place it films: vanilla
drops it, or with a minimum volume moves it next to the player. A film, or a
check of a mod's sound in a recording, needs a sound that starts where the
agent says and is heard from where the camera sits, also on a remote server
where `server` isn't there.

## What Changes

- `sound <id> [at <pos>] [volume=<v>] [pitch=<p>] [source=<category>]`
  plays a sound event on this client only. With `at`, it starts there and
  fades with distance from the listener, which is the camera as rendered, so
  a placed camera hears it from its own seat. Without `at`, it plays as an
  interface sound, the same everywhere.
- `volume=` (default 1) above 1 carries the sound farther, as with
  `playsound`; `pitch=` from 0.5 to 2 (default 1), the range the sound
  engine plays; `source=` a sound category of the running version (default
  `master`).
- `sound` is an action, so it can't run inside `sample`, `wait` or
  `assert`, and fails with `err disabled` while agent control is off.
- The reply names the sound, where it plays (or `ui`), its volume, pitch,
  category and tick, and `muted` when its category or the master volume is
  0, so it won't be heard or recorded.
- An id the client doesn't know fails as it does for `sounds match=`,
  naming the closest ids.
- The sound goes through the client's sound engine like any other, so the
  sound log, `sounds playing` and recordings with sound include it.
- The help and `docs/commands.md` describe `sound`, and `help grammar`,
  `docs/grammar.ebnf` and its diagrams show it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: a command that plays a sound on the client, at a
  position or as an interface sound.

## Impact

- `game/observe/ObservationCommands.java`: the `sound` command, with the
  known-id check that `sounds match=` uses shared between them.
- A version condition where the sound instance's id type differs (1.21.1's
  `ResourceLocation`, 26.x's `Identifier`), if the rename rules don't cover
  it.
- `tools/smoke_test.py`: steps for a positioned sound, an interface sound,
  an unknown id and a muted category.
- `docs/commands.md`: a `sound` row beside `sounds`.
- `docs/grammar.ebnf` and `docs/grammar.xhtml`, regenerated.
- Nothing on the server; on a remote server nothing is sent.
