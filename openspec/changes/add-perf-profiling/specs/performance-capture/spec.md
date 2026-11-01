## ADDED Requirements

### Requirement: Stack samples in a profiled capture
With `profile`, a capture SHALL sample the integrated server thread's stack
about 20 times a second, at uneven intervals, and keep only the samples taken
while the server is inside a tick. The summary SHALL give how many samples were
kept and left out, each mod's share of the kept ones and the most frequent top
frames' shares. The text reply SHALL list the leading ones as `mod` and
`frame` rows.

#### Scenario: A mod's work named
- **WHEN** an agent runs `perf 10s profile` while a mod's code runs on every
  server tick
- **THEN** a `mod` row names that mod with its share of the samples

### Requirement: Attributing a stack sample
A sample SHALL count toward the mod that owns its innermost frame outside the
JDK, Minecraft, the mod loaders and Mixin, passing over frames of libraries no
mod owns; with no such frame, toward `minecraft`, or `java` when no Minecraft
frame is on the stack either. It
SHALL also count toward its top frame, named as class and method and marked
`native` for a native method, with the mod owning that frame's class.

#### Scenario: Native code under a mod
- **WHEN** a sample's top frame is a native method of a mod's class, called
  from that mod's code
- **THEN** the sample counts toward that mod, and its top frame is listed with
  `native`

### Requirement: Profiler sections in a profiled capture
With `profile`, a capture SHALL run the integrated server's metrics recorder,
the one F3+L and `/perf start` use, which records at most the first 10 s, and
give its section tree: each section's share of its parent and of the whole
and its calls per tick, down to 0.5% of the whole, `unspecified` remainders
kept, with the seconds covered and the recorder's report. The text reply
SHALL list the sections at 5% or more as `section` rows by path.

#### Scenario: Sections of a capture
- **WHEN** an agent runs `perf 15s profile`
- **THEN** the reply carries `section` rows such as `root/tick`, each with its
  share, and says the sections cover 10 s

### Requirement: Starting a profiled capture
`perf start … profile` SHALL fail with `err busy` while the server's metrics
recorder already runs, and with `err no_server` without an integrated server.
Without `profile`, a capture SHALL neither sample stacks nor run the metrics
recorder.

#### Scenario: The recorder already runs
- **WHEN** an agent runs `key f3+l`, which starts the recorder in
  singleplayer, and then `perf start x profile`
- **THEN** the result is `err busy` and no capture runs

### Requirement: Timed capture
`perf <duration> [name] [profile]` SHALL start a capture, end it once that much
time has passed on the wall clock, a tick counting as 50 ms, and answer as
`perf stop` does. When `stop` cancels it first, the capture SHALL keep running
until `perf stop` ends it.

#### Scenario: One line
- **WHEN** an agent runs `perf 5s`
- **THEN** the reply is a summary as `perf stop` gives, with a duration of
  about 5 s

#### Scenario: Cancelled
- **WHEN** an agent runs `perf 30s` and `stop` cancels it
- **THEN** `perf status` still reports the capture running

### Requirement: Loaded chunks and sub-levels
At the end of a capture, the server's loaded counts SHALL add the chunks
loaded across its levels, split into `world` chunks and `plots` (chunks in a
sub-level's plot), and with Sable the number of sub-levels loaded. The text
reply SHALL give them in one `loaded` row.

#### Scenario: A sub-level loaded
- **WHEN** a capture ends with one sub-level loaded
- **THEN** the loaded counts carry `plots` above 0 and one sub-level
