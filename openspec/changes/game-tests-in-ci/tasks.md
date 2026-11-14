# Tasks

## 1. Test script (multi-target-build)

- [x] 1.1 Write `build/test-targets/<target>/results.json` for each target (held, failed and skipped assertions with reasons) and add `--combine`, which reads the results of every configured target or those `--targets` names, fails a target without results as missing, runs the shape and skip comparisons and prints the table (D2); verify by hand on results from a local run of `1.21.1-neoforge` and `26.3-fabric`: combining both passes, a shape line edited in one fails naming the query and both targets, a skip edited into one fails that target, and a deleted results file fails as missing
- [x] 1.2 Add `--strict-skips`, failing a target for a skipped assertion other than the Baritone and two-client ones (D3); verify on `1.21.1-neoforge` with ffmpeg hidden from the PATH that recording is skipped without the flag and fails the target with it
- [x] 1.3 Describe `--combine` and `--strict-skips` in the script's docstring and docs/building.md

## 2. Workflow (multi-target-build)

- [ ] 2.1 Write `.github/workflows/game-tests.yml`: the target list job, a matrix job per target under `xvfb-run` with Mesa's llvmpipe and lavapipe, ffmpeg and `ALSOFT_DRIVERS=null`, running `test_targets.py --targets <target> --strict-skips` and uploading its `build/test-targets/<target>/`, and the combining job writing the table to the step summary; triggers on pushes to main and `workflow_dispatch` with `targets`, plus pushes to this change's branch while it is brought up; a concurrency group per ref; `setup-gradle` caching; actions pinned by SHA as in `release.yml` (D1, D4, D6)
- [ ] 2.2 Push the branch (needs Jan's go-ahead), which runs the workflow through its branch trigger; bring up the targets until each client reaches a world, fixing Linux-only failures in their own commits; record in the design whether 26.x needs the OpenGL fallback (D4)
- [ ] 2.3 Make the assertions that fail only under software rendering hold there (D7), each in its own commit; verify each still passes on the Mac on `1.21.1-neoforge` and `26.3-fabric`
- [ ] 2.4 Verify every target passes in two CI runs in a row, recording with sound included, and that the run keeps each target's logs
- [ ] 2.5 Verify the failure path on a throwaway commit pushed to the branch that breaks one assertion on `1.20.1-forge` only: the check fails, the summary names the target and the assertion, the other targets pass; drop the commit
- [ ] 2.6 Remove the branch trigger and merge to main (pushing main needs Jan's go-ahead); verify the push runs every target and `release.yml` is unchanged, and that a by-hand run for `26.3-fabric` runs only that target

## Workflow follow-up

- Archive this change.
- Pushing the branch and main needs Jan's go-ahead.
