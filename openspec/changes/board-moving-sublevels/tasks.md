# Tasks

## 1. Boarding a moving sub-level (sable-sublevels)

- [ ] 1.1 Make `sable:tp` place the player on the spot through the client's copy each tick until the client shows them carried within 0.6 blocks of it, stopping creative flight, answering with the player's world position as `at`, and failing after 100 ticks with the new `err not_aboard` (D1, D2), with its help, `help errors` and the README's With Sable section; verify on the dev client that after `sable:tp <sub> lever` on a raft pushed to about 50 blocks per second, `sable:plot here ~0,0,0` is within half a block of the reply's `plot` and `status` shows `aboard`, and that `sable:tp` onto a parked sub-level still answers within a few ticks
- [ ] 1.2 Add the watcher that emits `! sable:boarded <sub>` and `! sable:left <sub>` when the sub-level carrying the player changes, called each client tick before the engine's steps, quiet on joining and leaving worlds (D3), with unit tests for the transitions and the README; verify on the dev client that `sable:tp` onto a raft carries `! sable:boarded` and that walking off its deck delivers `! sable:left`

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
