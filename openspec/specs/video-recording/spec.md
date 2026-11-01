# video-recording Specification

## Purpose

Records the game's frames and sound to a video file from a script, so mod
authors can make showcase videos and clips without a screen recorder.

## Requirements

### Requirement: Recording window
`record start [name]` SHALL start a recording and `record stop` SHALL end it,
save the file and answer once it is saved. Only one recording runs at a time:
starting a second fails with `err busy`, and stopping without one fails with
`err no_capture`. `record status` reports whether one runs, its name and for
how long. A recording outlasts the connection that started it.

#### Scenario: Start and stop from separate calls
- **WHEN** an agent runs `record start lever` with the CLI, then
  `use ~0,0,2`, then `record stop`, each as its own call
- **THEN** the last call answers with the saved file, and the video shows the
  lever being used

#### Scenario: Second recording
- **WHEN** a recording runs and an agent runs `record start other`
- **THEN** the result is `err busy` naming the running recording

### Requirement: Recording summary
The reply to `record stop` SHALL give the file, the length in seconds, the
width and height, the frame rate, the number of frames, how many of them
repeat the frame before because the game had not rendered a new one,
whether it has sound, and the H.264 encoder used.

#### Scenario: Slow frames counted
- **WHEN** a recording at `fps=60` runs while the game renders 40 frames a
  second
- **THEN** the stop reply reports about a third of the frames as repeated

### Requirement: Timed recording
`record <duration> [name]`, with the options `record start` takes, SHALL
start a recording, end it after that long on the wall clock and answer as
`record stop` does. Cancelling the wait with `stop` SHALL leave the
recording running until `record stop`.

#### Scenario: Ten seconds in one line
- **WHEN** an agent runs `use ~0,0,2; record 10s gears`
- **THEN** the reply comes after about 10 s with the file `gears.mp4` and a
  length of about 10 s

### Requirement: Recording files
A recording SHALL be saved as `<game dir>/blockpilot/recordings/<name>.mp4`.
Names use letters, digits, `_`, `.` and `-`, up to 64, and don't start with
`.`. Without a name it is `recording-<yyyyMMdd-HHmmss>`. A name whose file
exists SHALL fail with `err exists`, so a take is never overwritten.

#### Scenario: Name taken
- **WHEN** `recordings/lever.mp4` exists and an agent runs
  `record start lever`
- **THEN** the result is `err exists` naming the file, and nothing records

#### Scenario: Bad name
- **WHEN** an agent runs `record start ../lever`
- **THEN** the result is `err syntax` saying what names may contain

### Requirement: Video format
The file SHALL be an MP4 with H.264 video in 4:2:0 colour and, unless the
recording is silent, AAC stereo audio at 48 kHz, as common players and
video sites accept.

#### Scenario: Plays as uploaded
- **WHEN** a recording made with sound is inspected with `ffprobe`
- **THEN** it has one `h264` video stream with pixel format `yuv420p` and one
  `aac` audio stream with two channels at 48000 Hz

### Requirement: Frames from the framebuffer
Frames SHALL come from the game's own framebuffer, so other windows covering
the game, or the game in the background, don't show in them. They include the
GUI the game draws unless `hud=off` is given.

#### Scenario: Window in the background
- **WHEN** an agent records with the game window behind another window
- **THEN** the video shows the game, not the window in front of it

### Requirement: Video size
`width=<px>` SHALL set the video's width, by default the frame's width up to
1920. It is never wider than the frame, and the height keeps the frame's
aspect ratio. Width and height are rounded down to even numbers. A width
below 16 SHALL fail with `err syntax`.

#### Scenario: Default size on a large window
- **WHEN** the framebuffer is 3456×2160 and an agent runs `record start`
- **THEN** the video is 1920×1200

#### Scenario: Wider than the frame
- **WHEN** the framebuffer is 1280×720 and an agent runs
  `record start width=1920`
- **THEN** the video is 1280×720

### Requirement: Frame rate and timing
`fps=<n>`, from 1 to 120, SHALL set the frame rate, by default 60. Frame `k`
SHALL show the newest frame the game rendered by `k/fps` seconds after the
start, repeating it when no newer one was rendered, so the video lasts as
long as the recording did on the wall clock.

#### Scenario: Game hitches
- **WHEN** the game stops rendering for half a second during a recording at
  `fps=60`
- **THEN** the video holds the last frame for half a second, about 30 frames,
  and its length still matches the wall clock

### Requirement: No idle frame-rate limit while recording
From 26.1 on, while a recording runs, the game's frame-rate limit for a
player without input SHALL NOT apply. The `maxFps` option still does.

#### Scenario: Long take without input
- **WHEN** on 26.1 nobody has touched the keyboard or mouse for two minutes
  and a recording at `fps=60` runs with `maxFps` at 120
- **THEN** the game keeps rendering at up to 120 frames a second instead of
  30

### Requirement: The game keeps running while recording
While a recording runs, the game SHALL NOT pause when its window loses
focus, as while an agent is connected, even when no connection is open.

#### Scenario: Long take in the background
- **WHEN** an agent runs `record start` in one call, the window is in the
  background and no call follows for five minutes
- **THEN** the video shows the world going on, not the pause menu

### Requirement: Sound in the recording
The audio track SHALL hold the sound the game mixes, at the game's volume
settings, heard from the camera, so from a placed camera's position when
`camera at` set one. It SHALL be in plain stereo, even when directional
audio is on, and in sync with the video.

#### Scenario: Placed camera hears the block
- **WHEN** an agent runs `camera at ~10,2,0 look ~0,0,0`, records, and a
  note block plays to the left of the camera
- **THEN** the note is heard on the left in the video, as loud as at that
  distance from the camera

#### Scenario: Muted category
- **WHEN** an agent runs `option soundCategory_music 0` before recording
- **THEN** the recording has no music, while other sounds are in it

### Requirement: Sound keeps playing while recording
While a recording with sound runs, the game SHALL keep playing its sound on
its sound device, the same mix as recorded.

#### Scenario: Watching a take
- **WHEN** a person watches the game while a recording runs
- **THEN** they hear the game's sound as without a recording, in plain stereo

### Requirement: Sounds across the start and end
Sounds that loop or follow an entity, such as a minecart's rolling, SHALL
keep playing across the start and the end of a recording with sound.
One-shot sounds and music playing at that moment end.

#### Scenario: Minecart rolling
- **WHEN** a minecart rolls near the player and an agent runs `record start`
- **THEN** its rolling sound is in the recording

### Requirement: Silent recordings
`audio=off` SHALL record without an audio track and leave the game's sound
device as it is, so no sound is cut at the start or the end.

#### Scenario: Clip for an animated image
- **WHEN** an agent runs `record 5s clip audio=off`
- **THEN** the file has a video stream and no audio stream, and the reply
  says it has no sound

### Requirement: Sound that can't be captured
When a recording with sound is asked for but the game's sound can't be
captured, because the sound engine is off or the sound library lacks what
capturing needs, `record start` SHALL fail with `err unsupported` giving the
reason and pointing to `audio=off`.

#### Scenario: Sound engine off
- **WHEN** the game started without a sound device and an agent runs
  `record start`
- **THEN** the result is `err unsupported` saying the sound engine is off and
  that `audio=off` records without sound

### Requirement: The encoder
Recording SHALL use `ffmpeg`, taken from the `ffmpeg` path in
`config/blockpilot/config.json` when set, else the first found on the path
or in the usual install locations of the platform. Without one, or without
an H.264 encoder in it, `record start` SHALL fail with `err unsupported`
naming where it looked and the config key.

#### Scenario: Homebrew install with a bare path
- **WHEN** the game was started by a launcher whose path lacks
  `/opt/homebrew/bin`, where ffmpeg is installed
- **THEN** `record start` finds it and records

#### Scenario: No ffmpeg
- **WHEN** no ffmpeg is installed and an agent runs `record start`
- **THEN** the result is `err unsupported` listing the places searched and
  the `ffmpeg` key of the config file

### Requirement: Encoder failure
When the encoder fails during a recording, for example because the disk is
full, the recording SHALL end. `record status` and `record stop` SHALL then
fail with `err record_failed` and the encoder's last message, and the
partial file SHALL be removed.

#### Scenario: Disk full
- **WHEN** the disk fills up while a recording runs and an agent then runs
  `record stop`
- **THEN** the result is `err record_failed` with ffmpeg's message, and no
  recording runs any more

### Requirement: GUI hidden for a recording
`hud=off` SHALL hide the GUI, as the F1 switch does, from the first frame of
the recording to its end, and then show it again unless it was hidden
before.

#### Scenario: Clean shot of a machine
- **WHEN** an agent runs `record 8s machine hud=off`
- **THEN** no frame shows the hotbar or the crosshair, and the GUI is back
  after the reply

### Requirement: What ends a recording
Only `record stop`, the end of a timed recording, an encoder failure and the
game closing SHALL end a recording. Closing the game SHALL save it first.
Leaving a world, closing the connection that started it and turning agent
control off SHALL NOT end it.

#### Scenario: Game closed while recording
- **WHEN** a recording runs and the player closes the game
- **THEN** the file is saved, playable and complete up to the close, before
  the game exits

#### Scenario: Leaving the world
- **WHEN** an agent runs `record start; leave; wait 2s; record stop`
- **THEN** the video goes on from the world to the title screen
