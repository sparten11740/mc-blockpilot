# Tasks

## 1. Entity query (game-perception)

- [x] 1.1 Add the read-only `entity <e<id>> [side=server]` to the perception group, reading through `Sides.read` (D2) and answering `type`, `pos`, `yaw`, `pitch`, `vel` and `airborne` as D1 sets them, plus `uuid` on the server side, with `err no_entity` for an id the side doesn't hold; add its help and field docs and the README's perception row; verify on the dev client that `entity e<id>` for a thrown item gives its motion, that `entity e<id> side=server` gives the server's copy with `uuid`, that `sample entity e<id> and entity e<id> side=server every 1t x10` gives ten pairs of rows with the same tick, and that a picked-up item answers `err no_entity`

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
