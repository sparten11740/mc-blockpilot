# Tasks

## 1. Loaded mods per loader (client-identity)

- [ ] 1.1 Add `Services.mods()` and implement it for NeoForge, Forge, Fabric and the vanilla agent (D3); verify all 11 targets compile

## 2. The queries (client-identity)

- [ ] 2.1 Add `client` with the versions, the mod count and the GPU details per version (D4); verify with a smoke check that it names `minecraft=`, the loader, `blockpilot=`, `backend=opengl` and a GPU on every target, and `asked=` from 26.2
- [ ] 2.2 Add `mods [match=]` with the background hashing at load and the `changed` check (D2); verify with unit tests of the hashing and the change check, and a smoke check that `wait mods until hashing==0` returns and Blockpilot's own row matches its jar or names its folder
- [ ] 2.3 Add both to `docs/commands.md`, the README command table and their help texts; verify `help client` and `help mods` show them

## 3. Readiness file (dev-integration)

- [ ] 3.1 Write the loaded mods into `endpoint.json` (id, version, file); verify with a smoke check that reads the file and finds Blockpilot's entry

## 4. Integration checks

- [ ] 4.1 Run `tools/test_targets.py` on 1.20.1 Forge, 1.21.1 NeoForge, 1.21.11 Fabric, 26.1 NeoForge, 26.3 Fabric and 26.3 vanilla (each loader, and each of the three GPU shapes), telling Luminel before starting dev clients; verify each passes

## Workflow follow-up

- Add the feature to a 1.5.0 section of `CHANGELOG.md`.
- Merge into `main`, archive this change and remove the worktree.
- Pushing needs the maintainer's go-ahead.
