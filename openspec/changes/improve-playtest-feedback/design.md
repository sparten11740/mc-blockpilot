# Design

## Context

Relevant current state:

- A mixin on `SoundEngine.play` logs each start into `Observations`, at the
  point where the sound event has resolved and before the engine checks the
  volume. So muted sounds are logged, but sounds the engine can't resolve
  are not.
- The engine keeps the sounds it plays in a private map from instance to
  channel handle. Each tick it drops the entries whose channel has stopped,
  20 ticks after their start at the earliest, and those whose category
  volume is 0. It never adds a sound whose volume comes out at 0, unless the
  sound may start silent, or any sound while the master volume is 0. The
  map and the `soundEngine` field on `SoundManager` have the same names and
  types in 1.21.1 and 26.1.
- `SoundManager.getAvailableSounds()` gives the sound events the loaded
  sounds.json files define, in both versions. The engine plays only those.
- The sound log (2,000) and the message log (500) are rings that drop their
  oldest entry when full, and they keep no record of what they dropped.
- `wait <query> until` re-runs its query every `every=` ticks. It treats an
  error result like any result whose predicates don't hold, until
  `timeout=`.
- The CLI sets `--timeout` as the socket's read timeout. `Main` turns the
  resulting `SocketTimeoutException` into "blockpilot: timed out". Closing
  the connection makes the endpoint end the session, which cancels that
  session's scripts.
- `KeyNames.closest` ranks names by edit distance for `key`'s "closest:"
  hint.

## Goals / Non-Goals

**Goals:**
- `sounds playing` reports what the engine plays, not a guess from starts
  and lengths.
- The new fields and errors come from state the client already has; no new
  per-tick work beyond one map entry per sound start.

**Non-Goals:**
- Reporting a sound's effective gain (category and master volume applied);
  `vol` stays the instance's own volume, as in the log.
- `from=` on `particles`, whose window is bounded in ticks (1,200) and
  documented as such.
- A launcher timeout that adapts to the script's own `timeout=` values; the
  CLI doesn't parse scripts.

## Decisions

### D1. Playing sounds come from the engine's channel map

Two accessor mixins in each version source set read the sound manager's
engine and the engine's instance-to-channel map. `sounds playing` lists the
entries whose channel handle hasn't stopped, so a finished sound that waits
out its 20-tick delete delay is left out.

Alternative: keep the started instances and ask `SoundManager.isActive` for
each. That reads the same map in the end, so it holds more state for
nothing.

Alternative: count a tickable sound as playing until its own `isStopped()`.
This doesn't cover non-tickable sounds and doesn't know when a channel ends.

### D2. Start ticks in a weak identity map

The existing play hook also records each instance's first start tick in a
weak map. With `putIfAbsent`, a sound the engine loops by hand (looping with
a delay, so it calls `play` again) keeps its first start. Played seconds are
the current tick minus the start, on the one clock that every `since=`
uses. While the game is frozen or paused the sound plays on, but the count
stops; the help says played time is game time. A playing sound with no
recorded start (none can be expected) shows no `played`.

### D3. Reading on the client thread

Queries run on the client thread, which also ticks the sound engine, so the
map doesn't change while `sounds playing` copies it.

### D4. Dropped-entry tracking for `from=`

Each ring remembers the tick of the newest entry it has dropped. When a
query's `since` is at or before that tick (always, without `since=`, once
anything was dropped), entries from its window are gone. The reply then
carries `from=<tick of the oldest entry kept>`. `match=` and `kind=` don't
change the test, because the dropped entries' ids and kinds aren't kept.

### D5. Unknown sound ids

A `match=` without `*` becomes a full id as today (`minecraft:` when it has
no namespace) and is looked up in `getAvailableSounds()`. A miss throws
`syntax` with `unknown sound <id>; closest: <up to 3 ids>; or
match=*<path>*`. The candidates are the known ids that contain the asked
path, or all known ids when none does, ranked by `KeyNames.closest`. Since
the log only holds resolved events, the check can't reject an id the log
could match.

### D6. A wait ends on `err syntax`

`WaitUntil` returns the query's result at once when it is an error with code
`syntax`. Gate errors (`not_in_world` and the like) and other errors keep
the wait re-running, so a wait through a world join still works.

Alternative: end on any error. That would break waits that start before the
world is ready, so it was rejected.

### D7. The CLI catches the reply's read timeout itself

`ShellClient` catches `SocketTimeoutException` around the read of the
script's reply and prints the message the spec gives, with `--timeout` in
seconds. The reads for the token, the status and `fmt json` keep `Main`'s
generic message; those answer at once from a live game.

### D8. Camera help only

The far-view hint is a sentence in `help camera` and the README's observe
row. It adds no screenshot options, because `option` already sets and
restores both settings.

## Risks / Trade-offs

- [A mod replaces or wraps the sound engine, as some audio mods do] → the
  accessors target vanilla fields that both versions share. A mod that
  swapped the engine class would break far more than this. The log keeps
  working either way.
- [`played` lags behind real time while the game is frozen or paused] → the
  help says it is game time. Tests that freeze the game can still compare
  start ticks.
- [A syntax error that would go away later, such as an id a resource reload
  is about to add] → the wait fails with the reason at once, which still
  beats a silent timeout.
- [`from=` can't take `match=` into account] → it says the window is
  incomplete, not that a match was dropped. That is the honest reading.
