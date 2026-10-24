# Tasks

## 1. Logs (client-observation)

- [ ] 1.1 Add the sound log (SoundEngine.play mixin after resolve, 2,000-entry ring, client tick) and the `sounds [since] [match] [limit]` query with `tick`; verify a played sound appears with id, category, position, volume, pitch, and that `wait sounds … until count>0` returns when it plays
- [ ] 1.2 Add the message log (chat hook, Gui overlay/title/subtitle mixins, 500-entry ring) with `messages [since] [kind]` and actionbar/title/subtitle events; verify with `title @p actionbar …` and `title @p title …` through `server`
- [ ] 1.3 Add particle counting (ParticleEngine.add mixin, per-tick counts for 1,200 ticks) and `particles [since]`; verify with `server particle minecraft:crit ~ ~1 ~ 0 0 0 0 40`

## 2. Visual capture (client-observation)

- [ ] 2.1 Add `screenshot burst=<n> every=<Nt> [width]` as a framebuffer-capturing action returning files and ticks; verify 4 frames 10 ticks apart are saved and listed
- [ ] 2.2 Add camera placement (`camera at <pos> [look <pos|e<id>>]`, `camera reset`, reset on leaving the world) through a Camera.setup mixin; verify a screenshot from a placed camera shows the player from outside
- [ ] 2.3 Report rendered camera angles in `status` (cam_yaw/cam_pitch, plus cam_roll on NeoForge via ComputeCameraAngles at lowest priority); verify `cam_roll` appears while a placed camera or mod roll is active

## 3. Sampling

- [ ] 3.1 Add `sample <query> every <Nt> x<n> [clock=client|server]` to the engine (rows with tick + query fields, max 1,000, server clock only for server queries); verify with unit tests on the fake game and `sample status every 1t x20` in game

## 4. Performance capture (performance-capture)

- [ ] 4.1 Add the Perf accumulators and `perf start [name] | stop | status` with `err busy` / `err no_capture`; verify with a unit test on the summary builder
- [ ] 4.2 Instrument server ticks (MSPT from tick times, block-entity and entity tick time per type, counts); verify with a capture over a world with ticking block entities
- [ ] 4.3 Instrument the client (frame times, FPS and 1% low, block-entity renderer time per type, section rebuilds, heap and GC); verify with a capture while looking at chests
- [ ] 4.4 Instrument the network (packets and bytes per type and direction, custom payload ids); verify payload counts appear during a capture
- [ ] 4.5 Save `<gameDir>/blockpilot/perf/<name>.json` and render the compact text overview; verify both and that idle instrumentation does no timing

## 5. Integration

- [ ] 5.1 Update the README command reference and tell the Aerolarge agent what landed; verify `openspec validate add-playtest-observability --strict` passes
