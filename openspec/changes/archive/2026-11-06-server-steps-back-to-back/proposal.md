# Proposal

## Why

A script can't drive the integrated server one tick at a time, say to move a
mob a block per tick while a camera films it. Every step checks its gate on
the client thread first, and only a run of queries is gated in one go. So
each step after a server action waits for the next client tick, then runs on
the server tick after that:
- With the default `settle=2t`, sixty `server tp …` steps took 9.55 s, about
  three ticks each.
- With `settle=0t`, a step still waits for the hand-off from the server
  thread to the client and back. Where a client tick doesn't fall between two
  server ticks, as under load, a server tick goes by without a step, so the
  steps drift from the server's ticks.

The workaround was a generated data pack with one function per tick,
scheduled from a start function.

## What Changes

- A run of consecutive steps of commands that run on the server thread,
  such as `nbt`, `server` and `tick`, is gated in one go on the client before
  its first step, as a run of queries is today.
  Then it runs back to back on the server thread: each step starts in the
  server tick the step before it answered in.
- `settle=` thereby spaces server commands exactly: `server settle=0t …`
  steps run in one server tick, and `server settle=1t …` steps one server
  tick apart.
- The spec's "`settle=0t` answers on the next tick" becomes what it does:
  it answers at the end of the server tick the command ran in.
- The run ends at the first other step, which runs on the next client tick
  as today: a client step, a `side=server` read, which runs from the client,
  or an `if` that holds.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: consecutive server steps run back to back, and
  `settle=0t` answers in the tick the command ran in.

## Impact

- `protocol/ScriptJob.java`: the gating that `gateQueriesAfter` does for
  queries covers every statement that runs on the server thread.
- `game/ServerCommands.java`: the help of `server`'s `settle` option.
- `EngineTest`: server actions back to back in one server tick, one server
  tick apart, and a client step after them.
- `tools/smoke_test.py`: a step reading `time query gametime` three times at
  `settle=1t` and at `settle=0t`.
- `docs/commands.md`: the `server` row.
- No change to the grammar, the replies or the error codes. Scripts with
  several server steps in a row finish sooner.
