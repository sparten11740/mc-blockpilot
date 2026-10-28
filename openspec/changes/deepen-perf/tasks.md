# Tasks

## 1. Server tick spikes and timeline (performance-capture)

- [x] 1.1 Add `p99` to `server.mspt`, `server.spikes` and the file-only `server.timeline` keyed by game tick (D1, D5), with the text overview's MSPT and spike list; add unit tests for the spikes (over 50 ms, longest first, at most 20) and for leaving the timeline out of the reply; verify on the dev client with `load server 30 jitter=30` (or a `server` command that takes long) that a capture lists spikes whose ticks match `t<n>` of server-clock sample rows, and that the file has the timeline

## 2. Block changes by block (performance-capture)

- [x] 2.1 Add the `blockChanged` mixin for both versions and the counting with block entity type and Sable sub-level cached per section (D2), with `server.block_changes` in the summary and the text overview; add unit tests for the ranking; verify on the dev client that a capture while a repeating command block toggles a block (or a redstone clock runs) lists that block with its rate, and that changes aboard a sub-level carry its name

## 3. Particle sources (performance-capture)

- [ ] 3.1 Add `ClientPlatform.modOf` for NeoForge and Fabric (D4) and the sampled stack walk at client particle creation with `particles.sources` in the summary and text overview (D3); add unit tests for picking the source frame (skipped prefixes, the packet listener as `server`); verify on the dev client that a capture near a campfire or a mod's particle source names it, and that particles from a `server particle …` command count as `server`
- [ ] 3.2 Add the `sendParticles` mixin for both versions with the same walk on the server thread (D3); verify on the dev client that particles sent by server code are attributed to their sender with `via` `server_side`

## 4. Integration checks

- [ ] 4.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
