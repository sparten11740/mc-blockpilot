# Proposal

## Why

A script that films a scene can't tell where in the video its steps land.
Lining up an edit, or cutting a clip at the moment a machine starts, takes a
calibration take first: film a bell and a teleport, then find them in the
video. In one such take, server actions landed 0.95 s in. The recording
knows its own clock, the time from its first frame, and `record status`
already reports it as `seconds`, but nothing ties that time to a named
step, and the times are lost among the other replies. An editor, a person
or a script cutting the video, wants them next to the file.

## What Changes

- New `record mark <label>`: notes the video time at which the script
  reached it, and answers with that time and the first video frame at or
  after it. It costs the script no tick.
- `record stop` lists the take's marks as rows, in order. A timed `record
  <duration>` holds the action queue, so its take has no marks.
- The marks also go to `<name>.marks.json` beside `<name>.mp4`, written at
  the first mark and rewritten whole at each one, so they survive the game
  closing or crashing mid-take. A take without marks has no such file.
  `record start` refuses a name whose marks file exists, as it does for the
  video, and a take that fails takes its marks file with it.
- Without a running recording, `record mark` fails with `err no_capture`, as
  `record stop` does. A recording takes at most 1,000 marks.
- `record`'s help, `docs/recording.md` and `docs/commands.md` describe marks
  and say that a server command's change shows a tick or two after the mark
  before it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `video-recording`: marks in a recording, in the reply that ends it and
  in a file beside the video.

## Impact

- `game/record/RecordCommands.java`: the `mark` form, the help and fields,
  and the rows in the stop reply.
- `game/record/Recording.java`: the marks of a take, kept on the client
  thread, written to the marks file and handed to its summary.
- `game/record/RecordCommands.java`: the name check covers the marks file.
- `game/record/FrameSlots.java`: the first frame at or after a time, beside
  the slot arithmetic it reuses, with `FrameSlotsTest`.
- `tools/smoke_test.py`: a check that a change made right after a mark shows
  in the video from the frame the mark names, and not before it, and that
  the marks file holds the take's marks.
- `docs/recording.md`, `docs/commands.md`.
- `docs/grammar.ebnf` and `docs/grammar.xhtml`: `record-command` gains the
  `mark` form, so both are rewritten from the 1.21.1 NeoForge dev client.
- No new error codes.
