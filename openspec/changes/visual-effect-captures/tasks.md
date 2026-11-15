# Tasks

## 1. Particles (client-observation)

- [ ] 1.1 Add `particles match=<pattern>`, as `sounds match=`; verify with a smoke check that it keeps only the matching types

## 2. Bursts (client-observation)

- [ ] 2.1 Capture each burst frame once, waiting for a new frame when none was drawn since the last, and add `drawn=` and `late=` to each frame and the drawn time to sheet labels; verify the smoke checks of bursts and sheets pass, and that under a load that stalls the client no two frames of a burst share a drawn time

## 3. Clean (client-observation)

- [ ] 3.1 Add the clean HUD setting: `option hud clean` and `hud=clean` on `screenshot` and `record`; hide the HUD pass on every version and the player's effects while a frame is drawn; verify on 1.21.1 NeoForge and 26.3 Fabric that a clean screenshot under night vision and blindness reads as one without them, and the HUD is gone

## 4. Flicker (client-observation)

- [ ] 4.1 Add `flicker <region>… over <duration>` with per-frame brightness and each region's dominant frequency; verify on a lamp switched by a clock at a known rate that the frequency matches, and that a steady region reads no flicker

## 5. Docs and checks

- [ ] 5.1 Write the visual-effects recipe in docs/recording.md, the `@s` note in `help server`, and the new commands in docs/commands.md, the README and the CHANGELOG under 1.4.0
- [ ] 5.2 Compile all targets, run the unit tests and the test script on 1.20.1 Forge, 1.21.1 NeoForge, 1.21.11 Fabric and 26.3 Fabric; verify all pass

## Workflow follow-up

- Archive this change.
- Pushing needs the maintainer's go-ahead.
