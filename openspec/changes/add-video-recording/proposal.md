# Proposal

## Why

Mod authors show their mods with short videos: a trailer on YouTube embedded
in the project description, and silent animated clips in the gallery.
Recording one today takes an outside tool:
- OBS or the system's screen recorder capture the window as it appears on
  screen. The window must stay visible, and the cursor, the HUD and
  Blockpilot's agent indicator end up in the video.
- Replay Mod renders without sound, so the audio has to be recorded apart
  and lined up by hand.

Blockpilot already reads frames from the game's own framebuffer, places
cameras and drives the player from scripts. What's missing is a way to turn
that into a video with the game's sound. With it, an agent can script a take
(camera, the interaction to show, the mix) and record it again until it is
right, with the window in the background.

## What Changes

- New `record` command, shaped like `perf`:
  - `record start [name] [width=<px>] [fps=<n>] [hud=off] [audio=off]` starts
    a recording.
  - `record stop` ends it and answers with the file, its length and size,
    and how many frames were repeated because the game rendered too slowly.
  - `record status` says whether one runs.
  - `record <duration> [name] …` records for that long in one line.
- The video is an MP4 (H.264 video, AAC audio) saved under
  `blockpilot/recordings/<name>.mp4`. Its frames come from the game's
  framebuffer, so the window can be covered or in the background.
- The sound is exactly what the game mixes, captured from OpenAL rather than
  rebuilt from the sound log. It is heard from the camera's position, so
  from a placed camera's viewpoint when `camera at` set one. While a
  recording runs, the game still plays its sound through the speakers.
- Encoding needs `ffmpeg`, found on the path, in the usual install
  locations, or at a path set in `config/blockpilot/config.json`. Only
  `record` needs it. Without it, `record start` fails with
  `err unsupported` saying how to provide it.
- While a recording runs, Blockpilot's agent indicator stays out of the
  frame.
- `option` takes the sound volumes (`soundCategory_master`,
  `soundCategory_music`, …), so a script can set a take's mix, for example
  with music off. They are restored like every other agent change.

## Capabilities

### New Capabilities
- `video-recording`: recording the game's frames and sound to a video file:
  the `record` command, the file it writes, the sound it captures, its
  timing and the encoder it needs.

### Modified Capabilities
- `client-observation`: the sound volume options in `option`.
- `player-control`: the agent activity indicator stays out of the frame
  while a recording runs.

## Impact

- New `game/record` package: the command, the recording clock, the ffmpeg
  process and its lookup, and the WAV file the audio goes to before it is
  muxed. Version-specific frame readback (`src/1.21.1`, `src/26.1`) next to
  `FrameCapture`.
- Audio: a mixin on `com.mojang.blaze3d.audio.Library` per version, which
  opens an OpenAL Soft loopback device while a recording runs, and a thread
  that renders its mix, writes it and plays it on the real device.
- `FrameMixin` (frame boundaries), `BlockpilotClient.onHudRender` (the
  indicator), `AgentPresence` (no pausing while recording), `ClientOptions`
  (sound volumes), `protocol/Config` (the `ffmpeg` path), `protocol/Errors`
  (`busy`, `no_capture`, `exists` and `unsupported` cover recordings too; a
  new `record_failed`). On 26.1, a mixin on the game's frame-rate limiter.
- External dependency: `ffmpeg`, for `record` only. Nothing else changes for
  players who never record.
- Tests: the clock's frame slots, the WAV writer, the ffmpeg lookup and its
  arguments. The smoke test gets a short recording, skipped when ffmpeg is
  missing.
- README: the observe row, a section on recording and the `ffmpeg` config
  key; `help record` and `help errors`.
