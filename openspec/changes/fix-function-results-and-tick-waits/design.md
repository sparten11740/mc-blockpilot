# Design

## Context

- `server` runs the command through `performPrefixedCommand` with a source
  whose result callback sets `success` and `result`, and answers
  `err command_failed` whenever the callback didn't report success.
- From 1.20.3 on, Minecraft calls that callback when a command finishes or
  throws while running: Brigadier's `ContextChain.runExecutable` reports
  `false, 0` before passing the exception on. It doesn't call it for:
  - a command that can't be parsed: `Commands.finishParsing` sends the error
    through `sendFailure` and runs nothing;
  - an unexpected exception, which `performCommand` reports through
    `sendFailure` as `command.failed`;
  - `function` when the function (or none of a tag's) reaches `return`:
    `FunctionCommand` hands the callback to the function, and only `return`
    calls it.
- `CommandSourceStack.sendFailure` wraps each failure message in a red
  component before handing it to the source; success messages go through
  unwrapped. This holds in 1.21.1's and 26.2's sources; the smoke check
  confirms it on 26.3.
- `ScriptJob` ticks an action in the step that starts it, so an action with
  work to do each tick (`move`, `mouse move`) acts at once. `WaitTicks`
  counts that first tick as one of its N, so it ends N−1 ticks after the step
  before it.
- `server settle=<N>t` already answers N ticks after the command ran.

## Goals / Non-Goals

**Goals:**
- `server function` answers as vanilla sees it: ran, with or without a
  value.
- `wait <N>t` between two steps puts N ticks between them, on both loaders
  and every version.

**Non-Goals:**
- Changing how a command whose callback runs is judged, including one that
  sends a failure message and still returns normally.
- `stable=` and `every=` of `wait … until`, which count checks, and the
  ticks other actions take.

## Decisions

### D1: No callback and no failure message is success

`server` notes whether the callback ran. If it did, the answer is as today.
If it didn't, the capture's failure flag decides: set when a message the
source receives is a red component, the form `sendFailure` gives every
failure. Without one, the answer is `ok success=true` with the feedback as
rows and no `result`. With one, it is `err command_failed` with the last
line that isn't Brigadier's `<--[HERE]` pointer, as today.

Alternatives:
- Parse the command ourselves (`Commands.getParseException`, then
  `performCommand`): it repeats `finishParsing` and misses the
  unexpected-exception path.
- Treat `function` by name: misses `execute … run function` and mods'
  commands that end the same way.
- Report `result=0` with success: it would claim a value the command never
  gave, and a function that returns 0 already answers that.

### D2: A tick wait doesn't count the tick it starts in

`WaitTicks` answers on its N+1st tick, the Nth after the one it started in.
Only the wait changes; every other action still acts in its first step, and
a sample beside a wait still takes its first row before the wait began, so
`sample … every 20t while wait 200t` gains the row at tick 200.

Alternatives:
- Stop `ScriptJob` ticking any action in the step that starts it: every
  action would start a tick late and each sample beside one would shift.
- Start the next step a tick after a wait: it changes when queries after a
  wait read the world, not only how long the wait is.

### D3: The smoke test loads a data pack

The check writes `datapacks/smoke/` into the test world: a `pack.mcmeta`
with only `"pack_format": 48`, and `data/smoke/function/place.mcfunction` and
`seven.mcfunction`. `server reload` turns on packs it finds that aren't
disabled. 1.21.1 reads 48 as its own format, and 26.x reads a lone
`pack_format` below its new scheme as an old pack and still loads it, so one
file serves every target without per-version formats.

## Risks / Trade-offs

- [A function's own commands report to the same source, so a function
  without `return` whose inner command fails loudly answers
  `err command_failed`, though the rest of it ran] → The message names what
  failed, which is what the agent needs, and `?server` carries on past it.
  A function that returns is judged by its value, as today.
- [A mod sends a whole success line in red through the source, from a
  command that reports no result] → It would read as a failure, as it does
  today; nothing gets worse.
- [Scripts tuned to the short wait run a tick longer per `wait`] → The
  release notes say so; the smoke test's waits only need a minimum.
