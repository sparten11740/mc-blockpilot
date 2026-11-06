# Tasks

## 1. Marks in a recording (video-recording)

- [x] 1.1 Add the first frame at or after a time beside the slot arithmetic in `FrameSlots` (D2); verify `FrameSlotsTest` covers 0, a time on a slot's start, one just after it, and fps 20 and 60
- [x] 1.2 Add `record mark <label>` (D1, D3, D4): marks kept on `Recording` on the client thread, the answer `ok mark <label> <seconds>s frame=<k>`, 0 before the first frame, `err no_capture` with no recording or one finishing, `err record_failed` after an encoder failure as `record status` gives it, `err too_large` past 1,000 marks; verify on the 1.21.1 NeoForge dev client that `record mark x` with no recording answers `err no_capture`, and `record start t audio=off; record mark first` answers `ok mark first 0s frame=0`
- [x] 1.3 List the marks as rows of `Stop`'s reply, in the order made, keyed by label with `seconds` and `frame` (D3); verify on the 1.21.1 NeoForge dev client that the spec's two scenarios for the reply give their rows, including marks made in separate calls, and that a timed `record 1s` reply has no rows
- [x] 1.4 Write `<name>.marks.json` at each mark (D5): `{"video": "<name>.mp4", "marks": [{"label", "seconds", "frame"}]}`, written to `.<name>.marks.json.tmp` and moved over the file atomically on the client thread, `seconds` rounded as in the reply, no file for a take without marks, a failed write logged and retried at the next mark; verify on the 1.21.1 NeoForge dev client that the file appears after the first mark while the take runs and matches the mark replies after the second, that `record 1s` leaves none, and that after `record start; record mark open; wait 20t; record mark close; quit` the saved MP4 has `close` in its marks file, and after the same script with the game process killed (`kill -9`) instead of `quit`, the marks file still parses and lists both
- [x] 1.5 Make `record start` fail with `err exists` naming `<name>.marks.json` when it exists, and delete a failed take's marks file with its other files (D5); verify on the 1.21.1 NeoForge dev client that a leftover `take.marks.json` without `take.mp4` makes `record start take` fail and records nothing, and that a take whose ffmpeg is killed mid-way (`pkill -f '.take.video.mkv'`) after a mark leaves neither file
- [x] 1.6 Add a smoke check from the spec's scenario "A change right after a mark": `option hud off`, a 20 fps silent take of 256 px, `record mark hud; option hud on`, then extract frames `k − 1`, `k + 1` and `k + 3` with ffmpeg and compare the hotbar band's mean as `check_hud_hides` does, and check that the marks file beside the video lists `hud` with the reply's time and frame; verify it passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients, and fails when the mark is moved after `option hud on; wait 5t`
- [x] 1.7 Rewrite `docs/grammar.ebnf` with `python3 tools/grammar.py --game-dir blockpilot/versions/1.21.1-neoforge/run` and redraw `docs/grammar.xhtml` with `./gradlew grammarDiagrams`; verify `record-command` in both has the `mark` form, `GrammarFileTest` passes, and the smoke test's `check_grammar_file` passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 1.8 Describe marks in `record`'s help and fields, the `record` row of `docs/commands.md` and `docs/recording.md`: what a mark times, the frame, that a server command's change shows a tick or two later, that marks come back in the `record stop` reply and in `<name>.marks.json` beside the video (from which a script can write a video description's `0:00` timestamps), that `record start` refuses a name whose marks file exists, and that a timed take can't take marks; verify `help record` on the dev client shows it and says the same as the spec

## 2. Integration checks

- [x] 2.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new check and its marks file, and that the JSON shapes of the `record` steps still match within each loader

## Workflow follow-up

- Merge into `main`, then archive this change.
- Pushing needs Jan's go-ahead.
