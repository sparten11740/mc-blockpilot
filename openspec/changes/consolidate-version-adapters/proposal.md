# Proposal

## Why

Blockpilot has a clean adapter for mod loaders but none for Minecraft versions,
so every new release spreads through text rules, inline conditions and copied
files. Fix that before 26.4 adds a twelfth, thirteenth and fourteenth target,
while the port to 26.2 and 26.3 has made each cost easy to measure.

Measured on the branch this change stacks on:

- 23 mixins are byte-identical in `src/1.21.1` and `src/26.1`, and 7 more are at
  least 80% the same, so a fix to one lands twice.
- Of 52 rename rules, 17 depend on a variable's name, 18 have one use, 1 has
  none, and a few aren't renames at all.
- The switch from GLFW to SDL touches 6 files, and two GPU classes hold 24 of
  the 55 condition lines in `src/26.1`.
- `test_targets.py` silently skips a target whose `target(` line is wrapped, and
  the run still passes.

## What Changes

No command, reply, jar name or supported version changes. Each item below is a
refactor, a test that keeps it in place, or both.

- Share the identical mixins from `src/main`, and fold in the near-identical
  ones where that stays readable.
- Add narrow adapters for what moves between versions: the open screen, the
  window system (GLFW or SDL) and the GPU API.
- Keep rename rules for real renames: delete the unused rule, turn the ones that
  change a call's shape into conditions, and test that every rule has a use.
- Close the leaks in the loader seam: `Perf` called directly from NeoForge's
  entry point, a loader condition in `CameraControl`, and service methods all
  loaders implement the same way.
- Share what the three build scripts repeat.
- Keep one source of truth for targets, and fail when the other lists disagree
  with it.
- Split `ActionCommands`, move `Perf`'s static state into a capture object,
  stop `protocol` importing from `cli`, and remove the dependency cycles between
  `game` and its subpackages, with `ArchitectureTest` enforcing the new rules.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `.openspec.yaml` sets `skip_specs: true`, because agents see no
difference. The loader changes bring the code in line with the existing
`multi-target-build` requirement "Loader access goes through a platform seam"
and don't change it.

## Impact

- **Code:** `blockpilot/src/{main,1.21.1,26.1,vanilla}`, mostly moves and new
  small classes. `dev.blockpilot.api`, the extension API, doesn't change.
- **Build:** `blockpilot/build.*.gradle.kts`, `stonecutter.gradle.kts` and
  `.github/workflows/release.yml`.
- **Tests and docs:** `ArchitectureTest`, `tools/test_targets.py` and
  `docs/building.md`.
- **Dependencies:** none added.
- **Stacking:** this builds on `add-minecraft-26-2-and-26-3`, now on local
  `main`.
