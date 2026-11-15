# Tasks

## 1. Tracking from the camera (sable-sublevels)

- [ ] 1.1 Add the tracking mixin (D1) to the 1.21.1 mixin config; verify on the 1.21.1 NeoForge client with Sable 2.0.6 that before it a ship spawned next to an away camera 2000 blocks off is on the server only (`sable:info` answers `no_sublevel` on the client) and not in the frame, and after it on both and in the frame; verify a ship the server unloaded while the camera was gone comes back on both sides with the camera
- [ ] 1.2 Say it in `help camera`, docs/commands.md, docs/sable.md and the CHANGELOG under 1.4.0

## 2. Checks

- [ ] 2.1 Compile all targets, run the unit tests on the 1.21.1 targets and the test script on 1.21.1 NeoForge and Fabric without Sable; verify all pass

## Workflow follow-up

- Archive this change.
- Pushing needs the maintainer's go-ahead.
