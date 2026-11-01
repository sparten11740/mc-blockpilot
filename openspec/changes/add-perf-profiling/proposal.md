# Proposal

## Why

A play-test measured 11 TPS on a desktop. The vanilla profiler (F3+L) put
94.6% of the overworld's tick under "unspecified", because the mod doing the
work labels none of it, so it couldn't name the cost. What named it was
`jstack` on the server thread in a hand-written loop, plus hand-written
parsers for the profiler's zip. `perf` already gives MSPT, TPS, slow ticks and
time per block-entity and entity type, but it can't see work done outside
those, such as a physics engine stepping in native code.

## What Changes

- `perf start [name] profile` adds two things to a capture:
  - Stack samples of the server thread, about 20 a second while the server is
    inside a tick. Each sample counts toward the mod whose code is innermost on
    the stack, and toward its top frame, native methods included. The summary
    gives each mod's share and the most frequent top frames.
  - The server's time profiler, the one `/debug start` uses, for the same
    window. The summary gives its section tree, with each section's share of
    the tick, and keeps the profiler's "unspecified" remainders.
- `perf <duration> [name] [profile]` starts a capture, ends it after that long
  and answers as `perf stop` does: `perf 15s profile` in one line.
- At the end of every capture, the loaded counts add chunks split into world
  and sub-level plots and, with Sable, how many sub-levels are loaded.
- Without `profile` a capture costs and reports what it does now, apart from
  those counts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `performance-capture`: profiled captures, the timed form and the extra
  loaded counts.

## Impact

- `game/perf/`: `PerfCommands` (the forms, the rows), `Perf` (the summary), a
  new sampler and attribution with unit tests.
- `MinecraftServerMixin` in both version source sets: marks when the server is
  inside a tick.
- `platform/ClientPlatform`: `modOf` looked up by class name, so stack frames
  can use it.
- README: the performance row.
