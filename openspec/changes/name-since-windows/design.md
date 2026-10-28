# Design

## Context

- `Since.tick` turns `since=` into a first tick: a duration counts back from
  the current tick, a bare number is the tick itself. Without `since=`,
  `sounds` and `messages` cover their whole log and `particles` the last 20
  ticks.
- Sounds (the last 2,000) and messages (the last 500) live in a `TickLog`,
  which knows the tick of its oldest entry and reports `from=` when it has
  dropped entries a window asked for.
- Particle counts are kept per tick for the last 1,200 ticks that had any, in
  `Observations`. So when particles are sparse, what the log holds reaches
  back further than 1,200 ticks. Nothing clears these logs when the player
  changes worlds, and game time is per world.
- Replies already carry the current `tick`.
- `BlockpilotClient` notices on its client tick when the player leaves a
  world, to put back the options agents changed.

## Goals / Non-Goals

**Goals:**
- A reply shows which ticks it covered, so a misread `since=` shows at once.

**Non-Goals:**
- Changing what a bare number means. Scripts pass the `tick` of earlier
  replies, which must keep working.
- `log since=`, which takes a log mark (a sequence number), not a tick.

## Decisions

### D1. `window=t<first>..t<now>`

`sounds`, `messages` and `particles` add a `window` field: the first tick
covered and the current tick, printed `window=t<first>..t<now>`, and in JSON
an object `{from, to}` of the two ticks. The first tick is the asked one
(from `since=`, or the default), unless the log has dropped entries from
there on: then it is the oldest tick the log still holds, the tick `from=`
already reports for sounds and messages. A log that dropped nothing covers its
quiet early ticks too, so its window starts at the asked tick even before its
oldest entry. Without `since=`, sounds and messages start at their oldest
entry, or at the current tick when the log is empty. `Since` gets a small pure
function for this, which the tests drive. `TickLog` answers the tick of its
oldest entry, and the particle counts learn which ticks they dropped, as
`TickLog` does.

The existing `from=` stays as it is: it tells that the log dropped entries
the window asked for, which the window alone doesn't say.

### D2. `joined=t<tick>`

The game notes the game tick when the player enters a world (the first client
tick with a level after none). When `since=` is a bare number below that
tick, the four queries add `joined=t<tick>`, printed with the reminder
`(since=<n> is a tick; <n>t counts back)`. A duration never triggers it, and
neither does a bare tick from this world. The answer still covers the tick
given, so a deliberate look further back keeps working.

## Risks / Trade-offs

- [Replies get one or two fields longer] → `window` is one short token, and
  `joined` appears only when the tick predates the world.
- [A world joined, left and joined again records the latest join] → a tick
  from the first visit then counts as before joining. The reminder is a
  hint, and the answer is unchanged.
