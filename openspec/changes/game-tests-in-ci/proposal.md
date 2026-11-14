# Proposal

## Why

The game tests (`tools/test_targets.py`) run only by hand, on one Mac, and a
full run of all fifteen targets takes long enough that changes are usually
checked on a few targets only. A target that breaks in a change made for
another is found late, sometimes after a release: unit tests and a build
can't see a mixin that fails to apply or a command that answers differently
in the game.

## What Changes

- A GitHub Actions workflow runs the game tests on Linux on every push to
  main, and by hand for chosen targets. Each target runs in its own job, so
  the fifteen run side by side; the list comes from `settings.gradle.kts`,
  so a new target is picked up without editing the workflow.
- The clients run under a virtual display with software rendering (Mesa's
  OpenGL and Vulkan drivers), with ffmpeg and an audio device that plays
  nothing, so recording and recording with sound run in CI as on a Mac
  instead of being skipped.
- The test script saves each target's results and gains a way to combine
  results saved on several machines, so the checks across targets (JSON
  shapes of one loader, an assertion skipped on one target but run on
  another) still hold when the targets ran in separate jobs.
- Each target's logs (build, client, each suite) are kept as artifacts of the
  run, and the run's summary shows the table of targets.
- Assertions that depend on timing or a GPU and prove flaky under software
  rendering are made to hold there, without loosening what they check on a
  real GPU.
- docs/building.md describes the CI runs.

Out of scope: holding a release back until the game tests pass (a release
builds and publishes as now; the run for its commit's push to main shows how
the game tests went), pull request runs, the Baritone runs and the
two-client checks (both stay skipped, as in a run without `--baritone` and
`--clients 2`), Windows and macOS runners, and a CI check for mods that use
Blockpilot (docs/testing.md already shows how a mod starts the game in CI).

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: the game tests run in CI on every push to main, and
  results saved on several machines combine into one report.

## Impact

- **CI:** a new workflow `.github/workflows/game-tests.yml`; `release.yml`
  stays as it is.
- **Test script:** `tools/test_targets.py` writes a results file per target
  and gains a combining mode; `tools/smoke_test.py` may get CI-safe bounds for
  timing assertions.
- **Runner cost:** fifteen Linux jobs of roughly 15 to 30 minutes each per
  push to main; free for a public repository. Caches of Minecraft's assets
  and the loaders' runtimes keep downloads down, within GitHub's 10 GB cache
  limit as far as it goes.
- **Mod code:** none expected; a fix found by the first CI runs, if any, goes
  in its own commit.
