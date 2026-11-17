# Tasks

## 1. References in the protocol layer (extension-api, sable-sublevels)

- [x] 1.1 Keep the column and reason of `Parser.SyntaxError` apart and have `CommandRegistry.parse` report the reason alone (design 4); verify new unit tests for `parse` (a good step, an unknown command, a bad slot reported without `col N:`) pass
- [x] 1.2 Turn `protocol/EntityNames` into `References` with a `References.Lookup` (`resolveEntity`, `resolveSubLevel`) that `GameBridge` extends, resolving `sub:<name|here>` to `sub:<uuid>` once per run wherever names are resolved now; implement `resolveSubLevel` in `BlockpilotClient` through `SableSupport` (`no_sublevel`, `ambiguous`, `unsupported`); verify unit tests with a fake lookup cover a name, `here`, a UUID passed through, the three errors and a text slot keeping its `sub:`
- [ ] 1.3 Add `Args.subLevel(name)` returning the `UUID` and throwing on an unresolved reference, update `ArgType.SUB_LEVEL`'s Javadoc, give the API fixture a sub-level slot read with `subLevel`, and move docs/extending.md to 2.2.0 with `sub:` slots and `Args.subLevel` documented; verify the unit tests and the fixture's build pass

## 2. Camera forms and shot lists (client-observation, sable-sublevels)

- [ ] 2.1 Extend `check_camera_play` in tools/smoke_test.py with the spec's "A bad shot after good ones", "A rejected play leaves the one playing", "A camera command that fails", "A shot that can't start" and "The error is told once", plus `camera play hold 5t | follow sub:nosuchship` answering `err unsupported` at once without Sable; verify the new checks fail before the change
- [ ] 2.2 Split `CameraCommands` into a `Form` table with check, start and mode (design 1), deriving the shot forms, the `ease=` forms, their error texts, option descriptions and target lists from it, with `camera` checking before it ends a play and forms reading `sub:` through `Args.subLevel`; verify the existing camera smoke checks still pass
- [ ] 2.3 Add `ShotList.parse` (design 2) and read each shot through `References.resolve(registry.parse(step), lookup)` and its form's check, started in shot mode with no added words; remove the `col` regex and `ObservationCommands.EntityNames`; verify unit tests for `ShotList` (holds, a bad hold, an empty shot, spacing round pipes, an unknown form) and 2.1's up-front checks pass
- [ ] 2.4 Rework `CameraTimeline` (design 3): publish after the first shot starts, turn any exception into the failure (`err internal`, logged), clear it in `stop`, make the state volatile with the re-check before a shot, and time through a clock interface; verify unit tests (first shot failing, a later failure, a runtime error, an error told once, a stop between tick and start) and all of 2.1 pass
- [ ] 2.5 Return null from `CameraControl.drawn` and `subLevelOrigin` for a sub-level no longer on the client, and answer `camera move` with its resolved end point (design 9); verify by compiling, and with the hand checks in 6.2
- [ ] 2.6 Update the camera help (fields `shots` and `over` for move, orbit and play, `sub:` references resolved as the command arrives) and the camera section of docs/commands.md; verify `help camera` in the dev client reads so

## 3. HUD captures and clean frames (client-observation)

- [ ] 3.1 Extend `check_hud_clean` in tools/smoke_test.py with the spec's "Clean as an option value", "Reading the option during a clean recording", "Restoring after a change during a capture", "The option keeps its value" and "Blind and clean, still unable to sprint", plus `screenshot hud=clean` and `record 1s hud=clean` leaving the HUD as before; verify the new checks fail before the change
- [ ] 3.2 Layer the HUD in `HudVisibility` (design 5) behind a small HUD interface, route `ClientOptions.Hud` and its snapshot through the option layer, give `ObservationCommands` and `Recording` a capture handle that re-applies its mode each tick, and remove `State`, `Capture.apply` and `Recording.hideHud`; verify unit tests (nesting, an option change during a capture, F1 during a clean capture, the end restoring the layer) and 3.1's option checks pass
- [ ] 3.3 Hold the FOV effects through `Options.fovEffectScale()` with a fixed 0 instance while clean and read the field through an accessor in `ClientOptions` (design 6); verify "The option keeps its value" passes and options.txt keeps the player's value after a clean capture and a menu save
- [ ] 3.4 Scope `CleanEffectsMixin` outside `Minecraft.tick()` with a tick flag and zero the nausea intensity after each tick while clean before 1.21.11 (design 7); verify the sprint check passes, and by hand on 1.20.1 and 1.21.11 that nausea vanishes at once in a clean screenshot
- [ ] 3.5 Add the `@Pseudo` mixin on Forge's `ForgeGui.render` to the shared mixin config (design 8); verify it applies on the 1.20.1 Forge dev client with no mixin warning and that the clean checks pass there
- [ ] 3.6 Update the `option` help to `hud on|off|clean`, the `screenshot` help's tile label (`#<n> t<tick> c<drawn>`) and its `drawn` and `late` fields, and docs/commands.md for `option hud clean`; verify `help option` and `help screenshot` read so

## 4. Flicker, particles and the client tick (client-observation)

- [ ] 4.1 Start `Flicker.strongest` at two cycles over the span, build each part's rhythm fields once, catch any exception in the per-frame reader, and add `no_frames` to `Errors.CODES` (design 10); verify unit tests (one step reads none, a 5 Hz square wave is found, under four frames reads none), `help errors` listing `no_frames`, and the flicker smoke check still reading `5hz` and `none`
- [ ] 4.2 Count `drawn=` on the client tick counter behind `client_tick` (design 11) and say so in the screenshot help; verify a smoke check for "Drawn on the samples' clock" passes
- [ ] 4.3 Drop the redundant `namespaced()` before `glob` in `particles` and describe `total` as counting the matched types; verify `check_particles_match` also checks that `total` equals the flames' count

## 5. Cleanup

- [ ] 5.1 Remove the doubled and stale doc comments (`SableSupport.subLevels`, `ContactSheet.label`, `CommandRegistry.Entry`, `CameraFollow`), the copies of `Numbers.round` and `TICKS_PER_SECOND`, the `instanceof` dispatch on region kinds in `pixels` and `flicker`, the help string fragments and the extra `SableCommands.resolve` overloads; verify the unit tests pass and every target compiles
- [ ] 5.2 Add `roll=` and `Args.subLevel` to the 1.4.0 section of CHANGELOG.md, with no "Fixed" entries for unreleased features, and bring README in line with `option hud clean` and `camera play`; verify by reading both against the specs

## 6. Integration checks

- [ ] 6.1 Compile all 15 targets and run `tools/test_targets.py` on 1.21.1-fabric (shared code), 1.20.1-forge (Forge HUD hook, nausea before 1.21.11, the 1.20 HUD signature), 1.21.11-neoforge (nausea through the blend factor) and 26.2-fabric (the Hud class tree); verify every run passes
- [ ] 6.2 Check by hand with Sable on 1.21.1: "Here as the command arrives", "The ship goes", "A slot that takes a sub-level" through the API fixture, a `camera move` to a plot position answering its end as given, and `camera follow sub:ship turning` answering `err syntax`; verify each behaves as specified

## Workflow follow-up

- Archive this change, syncing the three delta specs and dropping tasks.md.
- Pushing needs the maintainer's go-ahead; nothing is released until the
  maintainer says so.
