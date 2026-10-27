# Proposal

## Why

Play-testing a mod's audio and long-range effects turned up answers that an
agent can misread. `sounds` only logs starts, so nothing says whether a
60-second looping track is still playing, which is what a fade-out test
needs. Its log keeps the last 2,000 starts, and once it has dropped some, an
empty answer looks the same as silence. A `match=` with a bare name such as
`techno` means `minecraft:techno` and quietly matches nothing. The CLI said
only "timed out" when its own `--timeout` cut a longer `wait` short, without
naming the limit. Framing an effect that spans hundreds of blocks took several
tries to find the options that widen the view.

## What Changes

- A new `sounds playing [since=] [match=] [limit=]` lists the sounds the
  client's sound engine is playing now: id, category, position (or `ui`),
  volume, pitch, `loop` for a looping sound, the tick it started and how long
  it has played. `wait sounds playing match=<pattern> until count==0` then
  waits for a sound to end. A sound the engine never starts, because its
  category or the master volume is 0, is not listed; the log still records
  its start.
- `sounds` and `messages` add `from=<tick>`, the tick of the oldest entry
  their log still keeps, when the log has dropped entries from the window the
  query asks for.
- A `sounds` `match=` without `*` that names no sound event the client knows
  fails with `err syntax unknown sound <id>`, naming the closest known ids and
  the `*<name>*` form, as `find` does for an unknown block and `key` for an
  unknown key.
- `wait <query> until …` ends at once when its query fails with `err syntax`,
  which no re-run can change, instead of re-running it until the timeout. A
  misspelt sound id, block or key in a wait then fails straight away.
- When no reply comes within `--timeout`, the CLI says how many seconds it
  waited, that the script was cancelled, and that a longer script needs a
  larger `--timeout`, instead of a bare "timed out".
- `help camera` names `option fov 110` and `option renderClouds off` for
  taking in an effect that spans hundreds of blocks.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: sounds playing now, log coverage (`from=`) for sounds
  and messages, and unknown sound ids in `match=`.
- `command-language`: a wait ends at once on a query syntax error.
- `dev-integration`: the CLI's message when no reply comes within
  `--timeout`.

## Impact

- `game/observe/ObservationCommands.java` (`sounds` form and help, `messages`,
  `camera` help), `game/observe/Observations.java` (start ticks, dropped-entry
  tracking), new accessor mixins for the sound manager's engine and the
  engine's channels in both version source sets, and both
  `blockpilot.mixins.json`.
- `protocol/Engine.java` (the wait's query result) and its tests.
- `cli/ShellClient.java` and `MainTest`.
- README: the observe row and the CLI exit-code paragraph.
