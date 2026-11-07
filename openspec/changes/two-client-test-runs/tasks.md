# Tasks

## 1. Two clients (dev-integration)

- [ ] 1.1 Generate a second client run per target from the same definition as the first, with its own run directory and the fixed player name `blockpilot2` (D1); verify both clients of the 1.21.1 NeoForge target start side by side from separate Gradle processes
- [ ] 1.2 Teach `tools/test_targets.py --clients 2` to start the second client, publish the first one's world and join the second to it; add the two-client check for the spec's scenario, skipped and reported as skipped without `--clients 2`; verify it passes on the 1.21.1 NeoForge and 26.3 Fabric targets
- [ ] 1.3 Describe `--clients 2` in `docs/dev-environment.md` and the 1.1.0 section of `CHANGELOG.md`

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets, and with `--clients 2` on the 1.21.1 NeoForge and 26.3 Fabric targets; verify every run passes

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.
