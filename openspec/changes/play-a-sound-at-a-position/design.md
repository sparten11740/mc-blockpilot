# Design

## Context

- Every sound the client plays goes through `SoundManager.play` and
  `SoundEngine.play`. `SoundEngineMixin` logs each start there for `sounds`,
  and recordings capture the engine's output.
- The listener is the main camera: `Minecraft.runTick` hands it to
  `SoundManager.updateSource` every frame, and `CameraMixin` has already
  moved it to a placed camera's seat.
- `SoundEngine.calculatePitch` clamps every sound's pitch to 0.5–2 on 1.21.1
  and 26.x alike.
- `SimpleSoundInstance` takes a sound id, category, volume, pitch, position,
  attenuation and whether it is relative to the listener. On both 1.21.1 and
  26.x the engine fades a positioned sound linearly over
  `max(volume, 1) × attenuation_distance` (16 unless sounds.json says
  otherwise), the same rule `playsound` relies on.
- `sounds match=` already checks an id against
  `SoundManager.getAvailableSounds()` and names the closest ones.

## Goals / Non-Goals

**Goals:**
- A sound that starts where the agent says, heard from wherever the camera
  is, on singleplayer and remote servers alike.

**Non-Goals:**
- Making the server's sounds reach a distant camera; that is
  `load-the-world-around-the-camera`.
- Stopping, looping or moving a sound once started.
- Sounds other players hear.

## Decisions

### D1: A plain client sound instance

`sound` is an action, not a query: it changes what is heard and recorded,
so it must not run once per check inside `sample`, `wait` or `assert`, and
like other actions it fails with `err disabled` while agent control is off.
It finishes in the tick it starts.

It builds a `SimpleSoundInstance` with a fresh random seed and hands it
to `SoundManager.play`, on the client thread. Being an ordinary sound, it
gets the category volumes, the sound log, `sounds playing`, the listener at
the camera and the recording without anything added. With `at`: linear
attenuation, not relative. Without: no attenuation, relative, at 0,0,0, as
`SimpleSoundInstance.forUI` does.

Alternatives:
- `playLocalSound` on the client level: the same in the end, but it needs a
  `SoundEvent` holder and gives no say over attenuation for the `ui` form.
- Running `playsound` on the integrated server for the player: it measures
  from the player again and doesn't work on remote servers.

### D2: Ids the client knows

The id check of `sounds match=` moves into one helper both commands call, so
`sound` and `sounds` reject and suggest the same way. A bare name is in
`minecraft:`, as there.

### D3: Positions as elsewhere

`at` resolves like any position: `~` from the player, absolute as given.
With Sable, a plot position plays where the sub-level shows that point when
the sound starts, through the mapping `camera at` uses; the sound doesn't
ride along afterwards.

### D4: Pitch as the engine plays it

`pitch=` takes 0.5 to 2, the range `calculatePitch` clamps to, so the reply
never names a pitch that doesn't play. `playsound`'s 0 to 0.5 would play at
0.5 anyway.

### D5: Muted is read at the start

The reply's `muted` comes from the options at play time: the category's
volume or the master volume at 0. A sound muted then still starts and is
logged, as the game does, so `sounds` lists it and `sounds playing` doesn't.

## Risks / Trade-offs

- [The sound engine has no device, or is paused with the singleplayer pause
  menu] → The reply is still `ok`; the engine drops or holds the sound, and
  `sounds playing` shows whether it plays. The help says so.
- [A mod's sounds.json changes the attenuation distance] → The engine uses
  it, as for that mod's own sounds; the reply gives what was asked.
- [`sound` and `sounds` differ by one letter] → The help of each names the
  other, and `sound` without an id is `err syntax` showing its usage.
