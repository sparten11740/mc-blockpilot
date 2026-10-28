# Tasks

## 1. The entity query (game-perception)

- [x] 1.1 Accept `me` in `entity`'s slot on both sides and add `ref` and `age` to its replies (D1, D2), with its help and the README's perception row; verify on the dev client that `entity me` and `entity me side=server` name the player with the same `e<id>`, and that a summoned item's `age` grows by about 20 per second
- [x] 1.2 Count the entity updates the client applies per kind with mixins on the client's packet listener for both versions, clear them at the end of each client tick, and add `net` to client replies (D3); add unit tests for the counter (kinds, clearing, left out at zero); verify on the dev client that `sample entity e<id> every 1t x20` of a moving mob shows `net` on the rows where updates arrived

## 2. Integration checks

- [x] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
