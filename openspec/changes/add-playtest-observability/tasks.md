# Tasks

## 1. Logs (client-observation)

- [x] 1.1 Add the sound log (SoundEngine.play mixin after resolve, 2,000-entry ring, client tick) and the `sounds [since] [match] [limit]` query with `tick`; verify a played sound appears with id, category, position, volume, pitch, and that `wait sounds … until count>0` returns when it plays; verified: a `playsound` shows up with id, category, relative position, volume, pitch and tick, and `wait sounds match=*explode* … until count>0` returned 1.9 s after a 2 s TNT fuse was lit
- [x] 1.2 Add the message log (chat hook, Gui overlay/title/subtitle mixins, 500-entry ring) with `messages [since] [kind]` and actionbar/title/subtitle events; verify with `title @p actionbar …` and `title @p title …` through `server`; verified with actionbar, title and subtitle through `server`: all three listed with ticks and delivered as `!` events (repeats within 2 s are folded)
- [x] 1.3 Add particle counting (ParticleEngine.add mixin, per-tick counts for 1,200 ticks) and `particles [since]`; verify with `server particle minecraft:crit ~ ~1 ~ 0 0 0 0 40`; verified: `particles` reports `crit x40` with `total=40`

## 2. Visual capture (client-observation)

- [x] 2.1 Add `screenshot burst=<n> every=<Nt> [width]` as a framebuffer-capturing action returning files and ticks; verify 4 frames 10 ticks apart are saved and listed; verified: 4 frames listed, 10 ticks apart
- [x] 2.2 Add camera placement (`camera at <pos> [look <pos|e<id>>]`, `camera reset`, reset on leaving the world) through a Camera.setup mixin; verify a screenshot from a placed camera shows the player from outside; verified: a screenshot from `camera at ~5,3,5 look ~0,1,0` shows the player from outside with the GUI hidden, and leaving the world resets the camera
- [x] 2.3 Report rendered camera angles in `status` (cam_yaw/cam_pitch, plus cam_roll on NeoForge via ComputeCameraAngles at lowest priority); verify `cam_roll` appears while a placed camera or mod roll is active; verified: `status` reports `cam:<yaw>/<pitch>/<roll>` (JSON `cam_yaw`, `cam_pitch`, `cam_roll`) while a placed camera is active; a non-zero roll needs a mod that rolls the camera

## 3. Sampling

- [x] 3.1 Add `sample <query> every <Nt> x<n> [clock=client|server]` to the engine (rows with tick + query fields, max 1,000, server clock only for server queries); verify with unit tests on the fake game and `sample status every 1t x20` in game; verified with `EngineTest` and `sample status every 1t x20` (20 rows, one tick apart)

## 4. Performance capture (performance-capture)

- [x] 4.1 Add the Perf accumulators and `perf start [name] | stop | status` with `err busy` / `err no_capture`; verify with a unit test on the summary builder; verified `err busy` and `err no_capture` in game, and the summary builder with `PerfTest`
- [x] 4.2 Instrument server ticks (MSPT from tick times, block-entity and entity tick time per type, counts); verify with a capture over a world with ticking block entities; verified: 25 hoppers show as `server be hopper` with ms per tick, plus per-type entity times and loaded counts
- [x] 4.3 Instrument the client (frame times, FPS and 1% low, block-entity renderer time per type, section rebuilds, heap and GC); verify with a capture while looking at chests; verified while facing 25 chests: fps, 1% low, frame times, `renderer chest` ms per frame, section rebuilds, heap and GC
- [x] 4.4 Instrument the network (packets and bytes per type and direction, custom payload ids); verify payload counts appear during a capture; verified: `custom_payload/neoforge:custom_time_packet` and vanilla packet types appear per direction with counts and bytes
- [x] 4.5 Save `<gameDir>/blockpilot/perf/<name>.json` and render the compact text overview; verify both and that idle instrumentation does no timing; verified: `perf/obs.json` saved and the text overview printed; every timing hook returns early unless `Perf.active`

## 5. Integration

- [x] 5.1 Update the README command reference and tell the Aerolarge agent what landed; verify `openspec validate add-playtest-observability --strict` passes; verified: the README lists the observe and performance commands, the Aerolarge agent was told after each batch, and validation passes

## 6. Second round (Aerolarge feedback)

- [ ] 6.1 Add `option` (list, get, set, `reset`; the options.txt-named table over `OptionInstance` plus `hud`; enum/boolean/number parsing with the validator's range; originals recorded on first change; no `Options.save`) and wait after a change until the section compile queue stays empty for 3 ticks (`settled=` / `unsettled pending=`); verify in game that `option graphicsMode fast` rebuilds the terrain in Fast, `option renderDistance 99` names the range, and `option reset` restores the previous values
- [ ] 6.2 Restore changed options on `leave`, on world exits Blockpilot didn't start, on the agent-control key and at shutdown, keep them across `join`, snapshot restore and test-world creation, and reset the placed camera on the key too; verify in game: quitting through the pause menu and pressing F8 each bring the options back, and a snapshot restore keeps them
- [ ] 6.3 Add `crop=` to `screenshot` and `sheet` for bursts (grid, `#n t<tick>` labels in a pixel font, at most 1,536 px wide), with the layout and font in game-free code; verify with a unit test of the layout and labels, and in game that an 8-frame sheet with a crop shows labelled, cropped tiles
- [ ] 6.4 Add the `pixels` query (fraction and `@<pos>` regions, `r=`, defaults `all` and `centre`, mean rgb and luma, max, p90, `err offscreen`) with the per-frame projection capture on `LevelRenderer.renderLevel`; verify the statistics and projection with unit tests, and in game that a glowstone block probed with `@` is brighter than the stone around it and that a point behind the camera is offscreen
- [ ] 6.5 Extend perf captures with particles (type tag, tick and render time per type, live counts), entity renderer time per type and, on NeoForge, time per render-level stage, and add them to the text overview; verify with a capture during a particle burst and with mobs on screen, on NeoForge and Fabric
- [ ] 6.6 Update the README and help, tell the Aerolarge agent what landed, and run `openspec validate add-playtest-observability --strict`; verify it passes
