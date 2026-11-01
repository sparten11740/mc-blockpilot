# Design

## Context

- `perf start`/`perf stop` collect through hooks that return at once unless
  `Perf.active`. `perf stop`'s `Stop` action submits one task to the server
  thread for the loaded block-entity and entity counts, then `Perf.stop`
  builds the summary, saves it and replies with an overview line plus `top`
  rows.
- `ClientPlatform.modOf(Class)` finds the mod whose files hold a class, on
  both loaders, and caches the answer per class. Particle sources use it.
- `MinecraftServer` has `startTimeProfiler()`, `stopTimeProfiler()` (returning
  `ProfileResults`) and `isTimeProfilerRunning()`, unchanged between 1.21.1
  and 26.1. `ProfileResults.getTimes(path)` gives a section's children as
  `ResultField`s (share of parent, share of the whole, count), with the
  section itself first. Stopping must happen on the server thread.
- `MinecraftServerMixin` (identical in both version source sets) already
  injects into `tickChildren`. `tickServer` brackets a whole tick in both
  versions.
- Sable's companion finds a sub-level by chunk (`getContaining`), which
  `SubLevels.at` wraps, and `near(level, EVERYWHERE)` lists them all.

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
time profiler records every section push and pop. Both change the MSPT a
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

### D4: Sections from the server's own profiler

With `profile`, the capture calls `startTimeProfiler()` at start. It refuses
first if `isTimeProfilerRunning()` is already true, so a `/debug` profile in
progress is never cut short. At stop, the task that already runs on the
server thread also calls `stopTimeProfiler()` and walks `getTimes` from
`root`. It skips each list's first entry (the section itself), keeps entries
at 0.5% of the whole or more, and goes at most 8 levels deep. The tree goes
into `server.sections` as nested objects with `name`, `share` (of the
parent), `of_tick` (of the whole) and `per_tick` (calls per tick). The text
rows show the sections at 5% of the whole or more by path, `root/tick/…`.

### D5: The timed form

`perf <duration> [name] [profile]` is a form whose first slot is a duration.
Its action starts the capture, waits until `ticks × 50 ms` of wall-clock time
has passed, then runs the same steps as `Stop`. `stop` cancelling it leaves
the capture running, so nothing measured is lost, and `perf stop` still ends
it.

### D6: Loaded chunks and sub-levels

The stop task counts each level's visible chunk holders, as it already walks
them for block entities, and sorts each chunk into `plots` when `SubLevels.at`
finds a sub-level at its origin, else into `world`. With Sable it adds
`sub_levels` from `near(level, EVERYWHERE)` per level. These go into
`server.loaded` beside the existing counts, and into one `loaded` text row.

## Risks / Trade-offs

- [Frames show intermediary names on Fabric 1.21.1, where the game runs
  remapped] → Mod attribution still works by class. 1.21.1 NeoForge and 26.1
  run with readable names.
- [Work inside a mod's Mixin handler counts toward the class it is merged
  into, usually Minecraft's] → The frames below it, in the mod's own classes,
  usually carry the cost. The help says how samples are attributed.
- [A short capture has few samples] → The summary gives the sample count. 10 s
  gives about 200.
