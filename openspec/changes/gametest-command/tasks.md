# Tasks

## 1. Spike (server-bridge)

- [ ] 1.1 For 1.21.1 NeoForge, 1.21.1 Fabric and 26.x, find where a test's result and failure message can be read, whether without a mixin, and what enables GameTests (D1); write the findings into the design and narrow the versions covered

## 2. GameTests (server-bridge)

- [ ] 2.1 Add the dev-only fixture for each covered loader, one passing and one failing test with their structure templates, in a source set the published jars leave out (D3); add a smoke check for the spec's scenarios, skipped where there is no fixture; verify it fails with `err syntax` for `gametest` before the change
- [ ] 2.2 Add `gametest run` over each covered version's runner (D2) and its help; verify the check passes on the covered targets and `err unsupported` elsewhere
- [ ] 2.3 Describe it in the help, `docs/commands.md`, the README and the 1.1.0 section of `CHANGELOG.md`; regenerate the grammar files; verify the grammar checks pass

## 3. Integration checks

- [ ] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, the GameTest check skipped where there is no fixture

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
