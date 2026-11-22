# Tasks

## 1. Test worlds (test-worlds)

- [x] 1.1 Write the marker before `createFreshLevel`, and let `create world` replace a marked folder without `level.dat` (D1); verify with a smoke step that makes such a folder by hand (marker and `datapacks/` only), checks `create world` replaces it, and that a marked folder with `level.dat` still fails with `err exists`
- [x] 1.2 Say in `create world`'s and `delete world`'s help and in `docs/commands.md` what happens to a creation cut short; verify `help create` shows it

## 2. Quit goes next (game-session)

- [x] 2.1 Cancel other sessions' jobs ahead of a script that starts with `quit`, on the client thread (D2); verify with `EngineTest` cases: another session's running action ends with `err cancelled` and `quit` runs, the same session's earlier action finishes first, and a job holding the client thread is cancelled through its pending cancel
- [x] 2.2 Say it in `quit`'s help and the `quit` row of `docs/commands.md`; verify `help quit` shows it

## 3. Names (performance-capture, video-recording, test-worlds)

- [x] 3.1 Allow `+` in capture, recording and snapshot names and name the rejected name in each error (D4); verify with unit or smoke checks that `perf 2t own+bits` saves `own+bits.json` and `perf start ../x` fails naming `../x`

## 4. Chat log (client-observation)

- [x] 4.1 Add the common chat window mixin for both method shapes, the `showLocal` flag, and drop the logging from the NeoForge, Forge and Fabric listeners and the vanilla agent's mixins, keeping their judging (D3); verify all 11 targets compile
- [x] 4.2 Add a smoke check that a line added on the client (a client-side command reply or `displayClientMessage`) shows once in `messages kind=chat`, that a server command's feedback still shows once, and that action-bar text isn't `chat`; verify it passes on 1.20.1 Forge, 1.21.1 NeoForge, a 26.x Fabric and a 26.x vanilla target

## 5. Integration checks

- [x] 5.1 Run `tools/test_targets.py` on the targets in 4.2, telling Luminel before starting dev clients; verify each passes

## Workflow follow-up

- Add the fixes to the 1.4.2 section of `CHANGELOG.md`.
- Merge into `main`, archive this change and remove the worktree.
- Pushing needs the maintainer's go-ahead.
