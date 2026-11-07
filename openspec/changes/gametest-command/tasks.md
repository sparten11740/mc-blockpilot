# Tasks

## 1. Spike (server-bridge)

- [x] 1.1 For 1.21.1 NeoForge, 1.21.1 Fabric and 26.x, find where a test's result and failure message can be read, whether without a mixin, and what enables GameTests (D1); write the findings into the design and narrow the versions covered

## 2. GameTests (server-bridge)

- [ ] 2.1 Add the tests the smoke check runs (D3): on 26.x a data pack with a passing and a failing block-based test, written into the smoke world before joining it again; on 1.21.1 Blockpilot's own two tests in a source set only the dev runs include, registered through `GameTestRegistry.register`, on NeoForge only with GameTests enabled, which its dev runs turn on; add a smoke check for the spec's scenarios, ending with `server test clearall`, and a unit test of the adapter's choice of answer for the Not enabled scenario, which no dev run reaches; verify the data pack's tests load after joining again, and that the check fails with `err syntax` for `gametest` before the change
- [ ] 2.2 Add `gametest run` over the 1.21.1 and 26.x adapters (D2), with its help and `err no_test` in the error table; verify the check passes on every target
- [ ] 2.3 Describe it in the help, `docs/commands.md`, the README and the 1.1.0 section of `CHANGELOG.md`; regenerate the grammar files; verify the grammar checks pass

## 3. Integration checks

- [ ] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, the GameTest check included

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
