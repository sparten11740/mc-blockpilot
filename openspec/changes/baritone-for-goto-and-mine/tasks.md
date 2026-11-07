# Tasks

## 1. A pathfinder seam (player-control)

- [ ] 1.1 Introduce `Pathfinder` with the built-in implementation over `Navigator` and today's mining walk (D1), with no change of behaviour; verify the unit tests and the movement and mining smoke checks pass on the 1.21.1 NeoForge dev client

## 2. Baritone (player-control)

- [ ] 2.1 Once Jan has approved the source and version, add a run configuration for 1.21.1 Fabric and NeoForge that fetches Baritone's release jar as a run-time-only mod, kept out of the published jars, and record its licence (read from the fetched version) in the README's third-party notes; verify the dev client starts with Baritone and without it
- [ ] 2.2 Add a smoke check for the spec's scenarios (a closed stone room, mining stone by type, without Baritone, stopped, the config default and an unknown name), the Baritone ones skipped without it; verify it fails with `err syntax` for `via=` before the change
- [ ] 2.3 First confirm on both loaders that the mods' class loader finds `baritone.api.BaritoneAPI` and that a proxied listener receives path events, and write what it finds into D2; then add `BaritonePathfinder` (D2, D3), the registry and `via=` option on `goto` and `mine` (D1), and the config default; verify the check passes with Baritone on 1.21.1 Fabric and NeoForge and `err unsupported` without it on every target
- [ ] 2.4 Describe `via=` in the help, `docs/commands.md`, the README and the 1.1.0 section of `CHANGELOG.md`; regenerate the grammar files; verify the grammar checks pass

## 3. Integration checks

- [ ] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
