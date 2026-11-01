# Recording videos

`record` makes a video of the game, such as a mod's showcase for its project page or a clip for a bug report. A script
can set the shot up and take it again until it is right:

```sh
run/blockpilot/blockpilot "camera at ~6,3,6 look ~0,1,0; option soundCategory_music 0; record 10s showcase hud=off"
```

The frames come from the game's framebuffer, so the window can be covered or in the background, and the video has the
window's shape: a 16:9 video needs a 16:9 window. `repeated=` in the reply counts the frames the game had not rendered
anew in time; when it is high, lower `fps=` or `width=`. With vsync on, the game renders no faster than the display
refreshes, and macOS may hold a window in the background to 60 Hz, so a recording at 60 fps repeats frames; `option
enableVsync off` lets it render faster for the take. While recording, 26.1's lower frame rate for a player who hasn't
touched the keyboard or mouse for a minute doesn't apply.

The sound is the game's own mix: at the game's volumes, so `option soundCategory_music 0` keeps the music out of a take
until `option reset`, and heard from the camera, so from a placed camera's position. It is plain stereo, even with
Directional Audio on, since a video plays on any speakers, and the game keeps playing it through the speakers while it
records. Starting and stopping a recording with sound reopens the game's sound device: sounds that loop or follow an
entity, such as a minecart's rolling, carry on, but one-shot sounds and the music playing at that moment end, so start a
take a moment before the action. `audio=off` records without sound and leaves the device alone. The command-line client
gives up on a call after 300 s (`--timeout`), so for a longer take run `record start` and `record stop` as separate
calls.

A project gallery takes images, not videos; an animated GIF cut from a recording shows there. GIFs grow fast, so keep it
to a few seconds and a small size (with libwebp in ffmpeg, `showcase.webp` instead makes a smaller animated WebP):

```sh
ffmpeg -t 6 -i run/blockpilot/recordings/showcase.mp4 -vf "fps=12,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" -loop 0 showcase.gif
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
