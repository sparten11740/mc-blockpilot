# Design

## Context

- Screenshots read the main render target at a frame boundary
  (`FrameMixin` → `Screenshots.onFrame`). On 26.1, `FrameCapture.read` maps
  the copy at once, which waits for the GPU, then copies pixel by pixel in
  Java. On 1.21.1 it is `Screenshot.takeScreenshot`, a blocking
  `glReadPixels`. Both are fine for a screenshot and too slow for every
  frame.
- 26.1's `CommandEncoder.copyTextureToBuffer` takes a callback that runs
  once the GPU has finished the copy. The game's own `Screenshot` maps the
  buffer in that callback, so nothing waits.
- The game's sound goes through `com.mojang.blaze3d.audio.Library`. Its
  `init` opens an OpenAL device (`alcOpenDevice`) and creates the context.
  `SoundEngine.reload` runs `destroy` (stop all, clean up the library) and
  then `init` again. LWJGL's OpenAL is OpenAL Soft, which has loopback
  devices (`ALC_SOFT_loopback`): the program asks for the mix with
  `alcRenderSamplesSOFT` instead of a sound card pulling it.
- The listener follows the main camera once per frame
  (`soundManager.updateSource(gameRenderer.getMainCamera())` in `runTick`).
  `CameraMixin` moves that camera for `camera at`, so a placed camera is
  already the listener.
- 1.21.1's `SoundEngine` compares the preferred device with
  `Library.getCurrentDeviceName()` once a second and reloads when they
  differ. 26.1 compares the device list and names when the list changes.
- 26.1's `FramerateLimitTracker` caps the frame rate at 30 after a minute
  without input and at 10 after ten minutes. 1.21.1 has no such cap.
- `Focus.setKeepActive` keeps the game from pausing while an agent counts as
  present (`AgentPresence`, a minute after the last connection).
- `perf` is the model for a capture that outlasts connections: global state,
  `start`/`stop`/`status`, and `perf <duration>` as an `Action` that ticks
  until the wall clock says so.
- Minecraft launched from a launcher on macOS gets a bare `PATH`
  (`/usr/bin:/bin:/usr/sbin:/sbin`), so Homebrew's `/opt/homebrew/bin` isn't
  on it.

## Goals / Non-Goals

**Goals:**
- A video that can be uploaded as it is, from one command.
- The exact game mix as the soundtrack, in sync.
- No cost to players who never record, and no new runtime dependency besides
  `ffmpeg` for `record`.

**Non-Goals:**
- Camera paths (orbits, dollies): a separate change. `camera at` stays
  static.
- Fixed-step rendering, where the game slows down and each frame is
  rendered at an exact time. The single clock (D5) leaves room for it.
- Resizing the window or cropping: the video has the window's shape. A
  16:9 video needs a 16:9 window.
- Animated GIF or WebP output. The README shows the one `ffmpeg` command
  that turns a recording into one.
- Recording the microphone, other windows or other programs' sound.

## Decisions

### D1: A `record` command shaped like `perf`

`record start|stop|status` and `record <duration>`, with a global recording
that outlasts connections, as captures do. The CLI opens a connection per
call, so start and stop come from separate connections. `record <duration>`
is an `Action` like `perf`'s `Timed`. Cancelled, it leaves the recording
running.

Unlike `perf`, a taken name fails with `err exists`. A take is costly to
redo, and a summary is not.

Errors reuse the codes with the same meaning, their descriptions widened to
recordings: `busy`, `no_capture`, `exists`, `unsupported`. One new code,
`record_failed`, for an encoder that died (D3).

Alternative: recording as an option of `screenshot`. It would tie a
minutes-long capture to one script and its timeout.

### D2: ffmpeg found, not bundled

`record` runs the user's `ffmpeg`. It is looked for in this order:
1. The `ffmpeg` key of `config/blockpilot/config.json`.
2. The `PATH` entries.
3. The platform's usual places:
   - macOS: `/opt/homebrew/bin`, `/usr/local/bin`, `/opt/local/bin`.
   - Linux: `/usr/bin`, `/usr/local/bin`, `/snap/bin`.
   - Windows: the WinGet links folder, `scoop\shims`, Chocolatey's `bin`
     and `C:\ffmpeg\bin`.

A pure `FfmpegLookup` takes the environment and a file check as arguments,
so tests cover the order without a real ffmpeg.

`ffmpeg -hide_banner -encoders` is read once per game session. The encoder
is `libx264` (`-preset veryfast -crf 18`) when present. Otherwise it is the
platform's hardware H.264 encoder: `h264_videotoolbox` on macOS, `h264_mf`
on Windows. Without either, `record start` fails with `err unsupported`.

Alternatives:
- Bundling ffmpeg: 50–80 MB per platform, and GPL for libx264.
- Pure-Java encoding: JPEG frames in AVI or MOV give files ten times the
  size that video sites and browsers don't all take. A Java H.264 encoder
  (jcodec) is too slow for 1080p in real time.

### D3: Video and audio to temporary files, muxed at the end

While recording:
- Raw RGBA frames go to ffmpeg's standard input, which encodes them into
  `recordings/.<name>.video.mkv`.
- The audio goes to `recordings/.<name>.wav`, written by Java.

`record stop` then runs a second ffmpeg that copies the video and encodes
the WAV to AAC into `<name>.mp4`, with `-movflags +faststart` for web
players. Then it deletes both temporary files. Names can't start with `.`,
so the temporary files never clash with a recording.

- Matroska for the temporary video because it stays readable when ffmpeg
  is cut off. An MP4 without its index would not.
- A thread drains ffmpeg's error output and keeps the last lines. If ffmpeg
  exits early, the recording ends, the temporary files are deleted, and the
  next `record status` or `record stop` answers `err record_failed` with
  those lines.

Alternative: one ffmpeg reading audio from a second input. `ProcessBuilder`
gives a process one input pipe only. A named pipe isn't portable to Windows
from Java. A loopback TCP input works, but ffmpeg before 6.0 reads its
inputs in turn and can stall on one.

### D4: Frames scaled on the GPU and read back without waiting

Each frame boundary while recording, the finished frame is drawn scaled
into a recording texture of the output size. That texture is copied into
the next of three buffers. A buffer is read once the GPU signals its copy
done, one or two frames later. Its bytes go to the writer thread as they
are: RGBA, rows bottom up. ffmpeg flips them (`vflip`) and converts them to
`yuv420p`, so Java touches no pixel.

- 26.1: a render pass with the game's blit pipeline into the recording
  texture, then `copyTextureToBuffer` with a callback, as `Screenshot`
  does.
- 1.21.1: `glBlitFramebuffer` with linear filtering into a framebuffer of
  the output size, then `glReadPixels` into a pixel buffer object with a
  fence, mapped once the fence has signalled.
- When the scale factor is above 2, the frame is halved in steps first, so
  the linear filter doesn't skip pixels.
- The code goes in version files next to `FrameCapture`
  (`FrameReadback`).

Alternatives:
- `FrameCapture.read` per frame: a GPU stall plus about 10 ms of Java per
  1080p frame.
- Full-size readback scaled by ffmpeg: a 3456×2160 Retina frame is 30 MB,
  1.8 GB/s at 60 fps through a pipe.

### D5: One clock for frames and samples

The clock starts when the first frame is captured. A frame is stamped with
the time of the boundary at which it was finished. A pure `FrameSlots`
decides which frame fills each output slot `k`: the newest frame stamped at
or before `k/fps`, repeated when no newer one exists. It counts the
repeats. The audio thread renders up to `elapsed × 48000` samples on the
same clock. Both tracks start at zero on one clock, so they stay in sync
without timestamps in the streams.

When the writer falls behind, a full queue (four frames) makes the client
thread wait at the next boundary. The game then slows down, and later slots
repeat a frame. Nothing is dropped, and one number in the summary, the
repeats, covers both a slow game and a slow encoder.

At the end, the audio stops at the time of the last video slot, so both
tracks are equally long.

A later fixed-step mode would drive this clock from game time instead of
the wall clock. The loopback mixer only advances when samples are rendered,
so the sound would follow.

### D6: The sound from an OpenAL Soft loopback device

A mixin on `Library.init` opens a loopback device while a recording with
sound wants one. It creates the context with the loopback's required
format: stereo, 16-bit, 48 kHz, HRTF off. Everything else in `Library`
runs unchanged: sources, distance model, channel pools. So does any other
mod's use of the context, such as EFX.

`record start` sets the flag and reloads the sound engine on the client
thread. `record stop` clears it and reloads again. If OpenAL lacks
`ALC_SOFT_loopback`, or the sound engine isn't running, `record start`
fails with `err unsupported` before anything is switched.

Each loop of the audio thread renders the samples the clock is owed with
`alcRenderSamplesSOFT`, about every 10 ms. It appends them to the WAV and
hands them to the monitor (D7).

Plain stereo because a video plays on any speakers. HRTF is made for
headphones and sounds wrong elsewhere.

Alternatives:
- Rebuilding the mix from the sound log: Blockpilot logs every sound start
  with its position, so the `.ogg` files could be mixed again. It is only
  an approximation, though: no moving sources without per-tick positions,
  and not OpenAL's own panning.
- Capturing from the operating system (ScreenCaptureKit, WASAPI loopback,
  PulseAudio monitors): native code per platform, and a permission prompt
  on macOS.

### D7: Playing the mix on the real device

The audio thread opens the device the game would have opened (the
preferred one, else the system default) with a context of its own. That
context is current on the audio thread only, through
`ALC_EXT_thread_local_context`, so the game's current context is never
touched. One streaming source plays a queue of 10 ms buffers. The queue is
kept 40–120 ms ahead. When the device's clock drifts from the wall clock
and the queue leaves that band, a buffer is dropped or a short silence is
queued. That only affects what is heard, never the recording.

While the loopback is open, the mixin makes `Library` report the monitor
device's name and connection state. So:
- 1.21.1's once-a-second check sees no change of device.
- A lost speaker device makes the engine reload as it would without a
  recording. The reload opens the loopback again and monitors the new
  device.

Alternative: `javax.sound.sampled`. It may be missing from a trimmed game
runtime, and it ignores the sound device chosen in the game.

### D8: Loops kept across the switch

Before each reload, the recorder notes the playing sound instances that
loop or tick (`isLooping()`, `TickableSoundInstance`). It plays them again
afterwards. Without this, an entity's loop that starts when the entity is
first seen, such as a minecart's rolling or a bee's buzz, would stay silent
until the entity is seen again. One-shot sounds and music end. The music
manager starts the next piece after its usual delay.

### D9: HUD, indicator and pausing

- `hud=off` sets `options.hideGui` at the first frame and puts the value
  back at the end, unless it was already hidden.
- `onHudRender` doesn't draw the indicator while a recording runs.
- `Focus.setKeepActive` also holds while a recording runs, so the game
  doesn't pause in the background once agents have gone quiet.

### D10: No idle frame-rate cap while recording (26.1)

A mixin on `FramerateLimitTracker.getThrottleReason` answers `NONE` instead
of `SHORT_AFK` or `LONG_AFK` while a recording runs. An iconified window
still throttles: a minimized game shouldn't be recorded. The repeats in the
summary then show it.

### D11: Threads

- Client thread: frame boundaries, issuing copies, collecting finished
  ones, the reloads.
- Writer thread: frames to ffmpeg's input.
- Audio thread: rendering, the WAV, monitoring.
- One thread drains ffmpeg's error output.

`record stop` is an `Action` that ticks until a future completes. That
future flushes the pending copies, closes the video input, waits for
ffmpeg, switches the sound back and muxes. The game keeps rendering
meanwhile. A shutdown hook in the client's close path runs the same stop
and waits for it before the game exits.

## Risks / Trade-offs

- [1080p60 is about 500 MB/s of raw frames plus an x264 encode] → Frames
  are scaled on the GPU, the encoder preset is fast, and back-pressure slows
  the game rather than losing frames. The repeats tell an agent to lower
  `fps=` or `width=`.
- [Reloading the sound engine cuts one-shot sounds and music, and hitches
  for a moment] → Loops are replayed (D8). The README says to start a take
  a moment before the action. `audio=off` avoids the switch.
- [Another mod that changes `Library.init` may clash with the mixin] → The
  mixin only replaces opening the device and the context's attributes, and
  it is active only while a recording with sound runs.
- [The monitor drifts from the wall clock over long takes] → The queue band
  in D7. Only what is heard is affected.
- [The game crashes mid-recording] → The temporary Matroska video and the
  WAV stay behind as dot files, readable by ffmpeg. They aren't recovered
  automatically.
- [26.1's render backend changes in a later version] → Readback is confined
  to `FrameReadback` per version, like `FrameCapture`.
- [Hardware encoders give larger files at the same quality than libx264] →
  libx264 comes first. The summary names the encoder used.
- [The CLI's 300 s timeout cuts a long `record <duration>`] → The help says
  to use `record start` and `record stop` for long takes, as for `perf`.

## Migration Plan

Nothing to migrate: a new command, an optional config key and new option
names. Players who never run `record` keep the game's audio path untouched.
