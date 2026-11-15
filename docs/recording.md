# Recording videos

`record` makes a video of the game, such as a mod's showcase for its project page or a clip for a bug report. A script
can set the shot up and take it again until it is right:

```sh
"<game dir>/blockpilot/blockpilot" "camera at ~6,3,6 look ~0,1,0; option soundCategory_music 0; record 10s showcase hud=off"
```

The frames come from the game's framebuffer, so the window can be covered or in the background, and the video has the
window's shape: a 16:9 video needs a 16:9 window. `repeated=` in the reply counts the frames the game had not rendered
anew in time; when it is high, lower `fps=` or `width=`. With vsync on, the game renders no faster than the display
refreshes, and macOS may hold a window in the background to 60 Hz, so a recording at 60 fps repeats frames; `option
enableVsync off` lets it render faster for the take. While recording, the lower frame rate from 26.1 on for a player who
hasn't touched the keyboard or mouse for a minute doesn't apply.

The sound is the game's own mix: at the game's volumes, so `option soundCategory_music 0` keeps the music out of a take
until `option reset`, and heard from the camera, so from a placed camera's position. It is plain stereo, even with
Directional Audio on, since a video plays on any speakers, and the game keeps playing it through the speakers while it
records. Starting and stopping a recording with sound reopens the game's sound device: sounds that loop or follow an
entity, such as a minecart's rolling, carry on, but one-shot sounds and the music playing at that moment end, so start a
take a moment before the action. `audio=off` records without sound and leaves the device alone. The command-line client
gives up on a call after 300 s (`--timeout`), so for a longer take run `record start` and `record stop` as separate
calls.

`record script [name]`, with the options `record start` takes, starts a take that ends with its script: after the last
step, at an error that stops the script, or when `stop` or real input cancels it, so a failing step never leaves a
recording running into the next take. The script's reply comes once the file is saved, with the summary `record stop`
would give as the event `! record …`; a `record stop` in the script ends the take earlier.

```sh
"<game dir>/blockpilot/blockpilot" "camera at ~6,3,6 look ~0,1,0; record script door hud=off; use ~0,0,2; wait 2s"
```

A shot list made with `camera play` films the same way: the script starts the take and the shots, does its work while
the camera plays them, and ends with `camera wait`, so the take ends with the last shot.

```sh
"<game dir>/blockpilot/blockpilot" "record script launch hud=off; camera play at ~0,4,-12 look ~0,2,0 | hold 20t | \
  move ~12,6,0 look ~0,2,0 over 60t ease=inout | follow sub:rocket at ~0,4,-16 | hold 100t; use ~0,0,2; camera wait"
```

`record mark <label>` notes the video time the script reached it at, so an edit knows where the script's steps land in
the take. It answers `ok mark door 2.15s frame=129`: the time from the video's first frame (0 before it, the clock
`record status` counts), and the first video frame from that time, and it costs the script no tick. A change the client
makes right after the mark shows from that frame or the next; a server command's change shows a tick or two later, once
the client has it, so to mark what the camera sees, mark after a `wait … until` that reads it. `record stop` lists the
take's marks as rows, and each mark rewrites `<name>.marks.json` beside the video, so the marks so far survive a game
that closes or crashes mid-take:

```json
{"video": "take.mp4", "marks": [{"label": "door", "seconds": 2.15, "frame": 129}]}
```

A script can turn the file into a video description's chapters, a `0:00` line and then one such as `0:02 The door opens`
per mark. `record start` refuses a name whose marks file exists, so a marks file always describes its own video, and a
take that fails takes its marks file with it. A recording takes at most 1,000 marks, and a timed `record <duration>`
holds the queue of actions until it ends, so its take has none.

Slow motion comes from the game's tick rate: lower it for the take and record at a normal frame rate, and the game draws
new frames between its ticks, so the motion stays smooth at a quarter of the speed. `tick rate` needs the integrated
server.

```sh
"<game dir>/blockpilot/blockpilot" "tick rate 5; camera move ~8,3,8 look ~0,1,0 over 40t background; record 8s blast fps=60 audio=off; tick rate 20"
```

Everything that counts ticks slows with the game: entities, particles, a camera move (the 40 ticks above take 8 s) and
`wait`. The length of a timed `record` and the times `record mark` gives go by the wall clock, so `record 8s` lasts 8 s
and shows 2 s of the game. In practice sounds play at their normal speed, so record without them and add them in the
edit.

Testing a visual effect, an explosion, a lamp or a spinning part, works best with captures that compare from run to run.
Fix the camera with `camera at … look …`, so every run sees the same frame; slow the game with `tick rate 5` for frames
between the ticks; take clean frames, `option hud clean` or `hud=clean` on `screenshot` and `record`, so no HUD, no
other mod's overlay and none of the player's effects get in; and put the scene back between runs with `snapshot save`
and `snapshot restore`. Start the capture on the effect itself rather than after counted ticks: `wait sounds
match=mymod:* since=1t until count>0` answers as its sound starts. Then judge the motion from a burst sheet, a video or
numbers: `screenshot burst=12 every=2t sheet hud=clean` takes each drawn frame once, `flicker 2s lamp=@~0,1,3 r=0.4`
gives a part's brightness in every frame and its rhythm in hertz, which tells a lamp strobing at 10 Hz from one that
fades, and `particles since=40t match=mymod:*` counts what the effect spawned.

```sh
"<game dir>/blockpilot/blockpilot" "snapshot restore before; camera at ~6,3,6 look ~0,1,0; option hud clean; tick rate 5; \
  use ~0,0,2; wait sounds match=mymod:* since=1t until count>0; screenshot burst=12 every=2t sheet; flicker 2s \
  lamp=@~0,1,3 r=0.4; tick rate 20; option reset"
```

A project gallery takes images, not videos; an animated GIF cut from a recording shows there. GIFs grow fast, so keep it
to a few seconds and a small size (with libwebp in ffmpeg, `showcase.webp` instead makes a smaller animated WebP):

```sh
ffmpeg -t 6 -i "<game dir>/blockpilot/recordings/showcase.mp4" -vf "fps=12,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" -loop 0 showcase.gif
```

Recording needs [ffmpeg](https://ffmpeg.org/) with an H.264 encoder (libx264, as in most builds, or the platform's own:
VideoToolbox on macOS, Media Foundation on Windows): `brew install ffmpeg` on macOS, `winget install ffmpeg` on Windows,
the distribution's package on Linux. A game started from a launcher doesn't get the shell's `PATH`, so besides the
`PATH` Blockpilot looks in the usual install folders: `/opt/homebrew/bin`, `/usr/local/bin` and `/opt/local/bin` on
macOS; `/usr/bin`, `/usr/local/bin` and `/snap/bin` on Linux; WinGet's links, Scoop's shims, Chocolatey's `bin` and
`C:\ffmpeg\bin` on Windows. Anywhere else, set its path as `"ffmpeg"` in `config/blockpilot/config.json`. Without one,
`record start` fails with `err unsupported` naming where it looked. If ffmpeg fails while recording, for example on a
full disk, the recording ends, `record status` and `record stop` answer `err record_failed` with ffmpeg's last lines,
and the partial file is removed.

[Back to the README](../README.md)
