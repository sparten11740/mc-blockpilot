# Tasks

## 1. A pathfinder seam (player-control)

- [x] 1.1 Introduce the `Pathfinder` port, `BuiltInPathfinder` over `Navigator`, and `Pathfinders` picking the first available adapter in order of preference (D1), with `goto` and `mine`'s walk into reach depending only on the port, `mine` passing its reach of 3 through unchanged, and no change of behaviour; add unit tests for the choice with fake adapters (the first available wins, an unavailable one is skipped, the built-in one as the last resort); verify the unit tests and the movement and mining smoke checks pass on the 1.21.1 NeoForge dev client
- [x] 1.2 Have `mine` by type walk closer once, through the port, to a block in reach that the crosshair can't hit before passing over it (D1); add a smoke step for a gold block sealed in stone, which the built-in walk passes over with `err no_block gold_block`; verify the step passes on the 1.21.1 NeoForge dev client

## 2. Baritone (player-control)

- [x] 2.1 Add a run configuration for the NeoForge and Fabric targets that fetches the Baritone build for its Minecraft version from cabaletta's GitHub releases (the source Jan approved on 2026-11-07; v1.11.3 for 1.21.1, v1.18.0 for 26.1, v1.19.0 for 26.2, v1.20.0 for 26.3), as a run-time-only mod kept out of the published jars, checked against the release's checksums, and record its licence (read from the fetched version) in the README's third-party notes; verify the dev client starts with Baritone and without it
- [x] 2.2 Add a smoke check for the spec's scenarios (into a closed stone room, mining gold blocks behind a wall, no way there through bedrock, stopped), run in the Baritone run configuration and skipped elsewhere; verify it fails before the change, as the built-in pathfinder answers `err no_path` for the room
- [x] 2.3 First confirm on both loaders that the mods' class loader finds `baritone.api.BaritoneAPI` and that a proxied listener receives path events, and write what it finds into D2; then add `BaritonePathfinder` (D2, D3) at the head of the preference list and `by=` in the replies; verify the check passes with Baritone on the 1.21.1 and 26.3 NeoForge and Fabric targets, which stand for the eight that can load it, and the regular runs keep the built-in pathfinder
- [x] 2.4 Say in the help of `goto` and `mine`, `docs/commands.md`, the README and the 1.1.0 section of `CHANGELOG.md` that they go through Baritone when it is installed, that its settings decide whether it breaks or places blocks, and that turning off Baritone's `allowBreak` and `allowPlace` gives a walk that alters nothing

## 3. Integration checks

- [x] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
