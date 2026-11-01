# Design

## Context

- `perf start`/`perf stop` collect through hooks that return at once unless
  `Perf.active`. `perf stop`'s `Stop` action submits one task to the server
  thread for the loaded block-entity and entity counts, then `Perf.stop`
  builds the summary, saves it and replies with an overview line plus `top`
  rows.
- `ClientPlatform.modOf(Class)` finds the mod whose files hold a class, on
  both loaders, and caches the answer per class. Particle sources use it.
- `MinecraftServer`'s `startTimeProfiler()` (`/debug start`) times ticks only:
  its results list no sections. The sections come from the metrics recorder
  (`/perf start`, F3+L): `startRecordingMetrics(results, report)`,
  `finishRecordingMetrics()` and `isRecordingMetrics()`, unchanged between
  1.21.1 and 26.1. The recorder ends itself after
  `PROFILING_MAX_DURATION_SECONDS` (10). At the end of a server tick it hands
  over its `ProfileResults`, then writes its report to a temporary folder and
  hands over that folder.
  `ProfileResults.getTimes(path)` gives a section and then its children as
  `ResultField`s (share of parent, share of the whole, count).
- `MinecraftServerMixin` (identical in both version source sets) already
  injects into `tickChildren`. `tickServer` brackets a whole tick in both
  versions.
- Sable's companion finds a sub-level by chunk (`getContaining`), which
  `SubLevels.at` wraps, and `SubLevels.all` lists them. Its plot chunks are
  served by position, outside the chunk map's full chunks.

## Goals / Non-Goals

**Goals:**
- Name the mod behind server tick time that no profiler section labels,
  native code included, in a capture that compares across runs.

**Non-Goals:**
- Reading F3+L zips from a game without Blockpilot.
- Client-thread sampling or allocation profiling.
- Undoing safepoint bias: samples land where the JVM can stop the thread, so
  shares indicate rather than measure.

## Decisions

### D1: One `profile` keyword for both costly parts

Sampling makes the server thread stop briefly about 20 times a second, and the
metrics recorder records every section push and pop. Both change the MSPT a
capture measures, so they run only when asked. The loaded counts cost one pass
over the chunks at stop, so every capture gets them.

Alternative: separate keywords for samples and sections. That means more
grammar for a choice that rarely matters.

### D2: A sampler thread, kept to ticks

A daemon thread sleeps a random 35 to 65 ms between samples, 50 ms on
average. A fixed 50 ms would line up with the 20-TPS tick and keep hitting
the same phase. Each sample calls `getStackTrace()` on the server's running
thread. It is kept only when a volatile flag, set by `MinecraftServerMixin`
at `tickServer`'s head and cleared at its return, says the server is inside a
tick. Samples outside a tick are only counted.

### D3: Attribution is a pure function over frames

`StackSamples` takes the frames, top first, with a lookup from class name to
mod. It returns the sample's mod and top frame, and keeps the counts and the
rankings, so the rules have unit tests. The mod is the first frame from the
top whose class name starts with none of the prefixes of the JDK, Minecraft,
the loaders or Mixin, and whose mod is known. Library classes with no mod are
passed over toward their caller. With no such frame the sample is
`minecraft`, or `java` without any Minecraft frame. Blockpilot's own classes
count as a mod, so its cost shows.

`ClientPlatform` gains `modOf(String className)` with a cache keyed by name,
and `modOf(Class)` goes through it. The loader services then take a class
name, which is all they used.

### D4: Sections from the server's metrics recorder

With `profile`, the capture starts the recorder on the server thread, handing
it a future for the results and one for the report path. It refuses first if
`isRecordingMetrics()` is already true, so a recording in progress is never
cut short. The recorder covers the capture's first 10 s, by its own limit. At
stop, the task that already runs on the server thread calls
`finishRecordingMetrics()` if the recorder still runs. The stop then waits, as
it waits for the counts, until the results arrive at the end of the next
server tick. It walks `getTimes` from `root`, skipping each list's first entry
(the section itself), keeps entries at 0.5% of the whole or more, and goes at
most 8 levels deep. The tree goes
into `server.sections` as nested objects with `name`, `share` (of the
parent), `of_whole` and `per_tick` (calls per tick). The text rows show the
sections at 5% of the whole or more by path, `root/tick/…`. `sections_s`
gives the seconds covered. The recorder writes its report, the same files as
an F3+L zip's server part, to a temporary folder. The capture ends before the
stop waits for that folder, so the wait doesn't stretch the measured window.
The folder then moves beside the summary as `<name>-profile`, which
`profile_report` names. A `sections` row gives both.

Alternative: install a profiler of our own on the server thread for the whole
window. 1.21.1 keeps the profiler in a server field and 26.1 behind a
thread-local, so that would mean two hooks to maintain.

### D5: The timed form

`perf <duration> [name] [profile]` is a form whose first slot is a duration.
Its action starts the capture, waits until `ticks × 50 ms` of wall-clock time
has passed, then runs the same steps as `Stop`. `stop` cancelling it leaves
the capture running, so nothing measured is lost, and `perf stop` still ends
it.

### D6: Loaded chunks and sub-levels

Sable keeps plot chunks out of the chunk map's full chunks and hands them out
by position through `getChunkNow`, so the stop task counts them per
sub-level. For each sub-level from `SubLevels.all`, it asks `getChunkNow` for
every chunk under its plot bounds and counts each distinct chunk once as
`plots`, with its block entities. The visible chunk
holders give the `world` chunks: those whose latest chunk is full, passing
over holders in a plot, which Sable registers too and which the plot count
already covers. With Sable, `sub_levels` counts the sub-levels. These go into
`server.loaded` beside the existing counts, and into one `loaded` text row.

## Risks / Trade-offs

- [Frames show intermediary names on Fabric 1.21.1, where the game runs
  remapped] → Mod attribution still works by class. 1.21.1 NeoForge and 26.1
  run with readable names.
- [Work inside a mod's Mixin handler counts toward the class it is merged
  into, usually Minecraft's] → The frames below it, in the mod's own classes,
  usually carry the cost. The help says how samples are attributed.
- [A short capture has few samples] → The summary gives the sample count. 10 s
  gives about 200, kept only for the part of it the server spends in ticks.
- [Plot bounds grow while a sub-level is turned, so `plots` can include plot
  chunks it doesn't fill] → They are chunks Sable holds for it all the same.
