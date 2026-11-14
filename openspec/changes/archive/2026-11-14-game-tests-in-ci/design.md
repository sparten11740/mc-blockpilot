# Design

## Context

`tools/test_targets.py` tests targets one after another on the machine it
runs on: for each it builds the target with its unit tests, checks the jar,
starts the dev client with `runClient`, runs the smoke test in text and JSON
mode and the security suite, and closes the client. After all targets it
compares JSON shapes within a loader (from `build/test-targets/<target>/
shapes.txt`, this run's or an earlier one's) and fails a target that skipped
an assertion another target in the same run ran. A run of one target takes
about 9 minutes on an M1 Max. Its docstring says it runs on macOS and Linux,
but so far it has only run on macOS.

The only workflow is `release.yml`, on tags: it builds every target with
Java 17, 21 and 25 (`setup-java`, `setup-gradle`), then uploads to
CurseForge and creates the GitHub release. The repository is public, so
GitHub's standard Linux runners (4 cores, 16 GB) cost nothing.

On the clients: 1.20.1 to 1.21.11 render with OpenGL 3.2 core. 26.3 ships
both LWJGL's OpenGL and Vulkan bindings and has a `preferredGraphicsBackend`
option, set to `"default"`, which picked Vulkan (MoltenVK) on the Mac.
Recording captures sound through an OpenAL Soft loopback device that the
sound engine is moved to; the engine first opens the real device as usual.
`Focus` already lets an agent-driven game act as focused when its window
isn't.

## Goals / Non-Goals

**Goals:**
- The CI run is the same script with the same assertions as a desktop run,
  not a CI-only test.
- A failing target is readable from the run page: the table in the summary,
  the logs as artifacts.

**Non-Goals:**
- Retrying failed assertions or targets automatically; a flaky assertion is
  fixed, not retried around (a person can still re-run failed jobs).
- Screenshots or videos of failures beyond what the suites already save.

## Decisions

### D1: One job per target, then a combining job

A workflow `game-tests.yml` has three stages: a job that lists the targets
(`test_targets.py --list`), a matrix job per target (`fail-fast: false`,
60-minute timeout) that runs `test_targets.py --targets <target>`, and a
final job that downloads every target's `build/test-targets/<target>/` and
runs `test_targets.py --combine`.

Alternatives: all fifteen in one job, which takes over two hours and loses
every target's result to one hung runner; or a fixed subset per push, which
misses exactly the target nobody thought of. Matrix jobs take about as long
as the slowest target.

### D2: Results file and `--combine`

Each target's run writes `build/test-targets/<target>/results.json`: the
held, failed and skipped assertions with their reasons. `--combine` reads the
results of every configured target (or those `--targets` names), fails any
target without one as missing, runs the existing shape and skip comparisons
over them, prints the table and exits as a normal run would. A normal
multi-target run keeps working as before, and now leaves the same files.

The combining job also writes the table to the run's step summary. Its exit
code, not only the target jobs', decides the check, so a cross-target
failure turns the run red.

### D3: Strict skips in CI

`test_targets.py --strict-skips` (set by the workflow) fails a target for
any skipped assertion except the Baritone and two-client ones, naming the
assertion and its reason. Without it a lost ffmpeg or audio device in CI
would skip recording on every target alike, which the cross-target skip
check can't see.

### D4: Display, rendering and sound

The client runs under `xvfb-run` with a 24-bit screen, with Mesa's software
drivers: llvmpipe for OpenGL and lavapipe (`mesa-vulkan-drivers`) for
Vulkan. The workflow installs `xvfb`, `mesa-utils`, `mesa-vulkan-drivers`,
`libopenal`'s runtime needs and `ffmpeg` (Ubuntu's has libx264). For sound,
OpenAL Soft's null output backend (`ALSOFT_DRIVERS=null`) gives the engine
a device that plays nothing, so the engine loads, sounds play and the
loopback capture works.

If 26.x fails on lavapipe, the workflow writes `preferredGraphicsBackend:
"opengl"` into those targets' `run/options.txt` before starting. The first
task finds out which is needed; either way the choice lives in the workflow,
not in the mod.

Found on the first run: no fallback is needed. 1.20.1 to 26.2 draw with
OpenGL on llvmpipe. 26.3 tries OpenGL first, gets no matching GLX visual from
Xvfb ("Couldn't find matching GLX visual") and falls back to Vulkan on
lavapipe by itself, which works.

The first run also showed the frame rate D7 is about: 17 to 45 frames a
second at the default render distance, the 26.x clients slowest. The
workflow therefore seeds each run folder's `options.txt` with
`renderDistance:4`; the flat test world stays in view, and none of the
checks depends on chunks farther away.

Alternatives: a GPU runner (paid, and not needed for correctness); PulseAudio
with a null sink (another daemon to start, where OpenAL Soft's own backend
suffices). If the null backend turns out to lack something the engine needs,
PulseAudio is the fallback.

### D5: Releases don't wait for the game tests

`release.yml` stays as it is: a tag builds and publishes without the game
tests, as Jan decided. A release commit is pushed to main before it is
tagged, so that push's run shows how the game tests went for it.

Alternative: `release.yml` calls `game-tests.yml` through `workflow_call`
and publishes only once it passes. Rejected: Jan doesn't want releases held
back by the game tests, and it would add about half an hour to each
release.

### D6: Triggers and caching

Triggers: pushes to main and `workflow_dispatch` with a `targets` input
(comma-separated, empty for all). No pull request or tag runs: changes land
by pushes to main. A concurrency group per ref cancels a run that a newer
push supersedes, since the newer run tests every commit the older one
would have.

`setup-gradle` caches the Gradle home, which holds Minecraft's assets and
the loaders' decompiled runtimes, per target job; it writes the cache from
main only and reads it everywhere. Together the targets' caches come to
about 7 GB against GitHub's 10 GB limit; a miss costs a few minutes of
downloads, not a failure.

### D7: Bounds that hold under software rendering

The smoke test has assertions tied to frame timing (rebuilds per second in
`perf`, camera moves sampled per frame). llvmpipe draws far fewer frames
than a GPU. The window stays small (the dev default) to keep frame times
down, and assertions that still fail in CI get bounds derived from the
measured frame rate or tick count instead of fixed per-second numbers, so
they check the same thing on a GPU.

### D8: A server step right after the pause screen closes waits

Found by a CI run: `close` shuts the game menu at once, but the integrated
server takes the client's pause only at its next tick, so a server step right
after it read the server as paused and failed with `err paused`. On a Mac the
next request usually comes a few frames later; in CI, frames are slow enough
that it didn't. The server now counts as paused only while the open screen or
overlay still pauses singleplayer, as the game works it out at its next frame;
otherwise the step waits for that tick. A smoke check runs `close` and a
server step in one script, which failed every time before.

## Risks / Trade-offs

- [Linux-only failures nobody has seen, as the script has only run on
  macOS] → the first tasks run one target by hand on a CI runner and fix
  what comes up before the workflow covers all targets.
- [26.x Vulkan on lavapipe fails or is very slow] → the OpenGL fallback in
  D4.
- [Flaky assertions turn CI red at random] → D7, plus the logs as
  artifacts; a flaky assertion found is fixed in its own commit.
- [Fifteen jobs per push make pushes slow to confirm] → they run side by
  side, so the wait is the slowest target, about half an hour; superseded
  runs are cancelled.
- [A release goes out while its commit's game tests fail or still run] →
  accepted (D5); the push's run shows it, and whoever tags can wait for it.
- [Cache eviction past 10 GB] → only slows the runs; D6.

## Migration Plan

There is no container runtime on the development Mac, and GitHub runs a
`workflow_dispatch` only for a workflow already on the default branch, so
while it is brought up the workflow also triggers on pushes to the change's
own branch, until all targets pass. That trigger is removed before merging
to main. Pushing that branch needs Jan's go-ahead. Rolling back is removing
the workflow.

## Open Questions

- Whether 26.x needs the OpenGL fallback (D4), and which assertions need
  CI bounds (D7): both answered by the first CI runs, without changing the
  specs.
