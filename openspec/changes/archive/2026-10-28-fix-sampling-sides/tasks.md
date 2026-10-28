# Tasks

## 1. Sampling across sides (command-language)

- [x] 1.1 Add `Statement.Call.runsOnServer()` and use it in `checkClock`, `Sample.lead`, the sampler's clock and trip, and server-clock waits (D1), and let `Sides.onServer` and `tryServer` run at once on the server thread (D2); add parser and engine tests (`clock=server` fits a `side=server` query, a mixed sample reads it in the run's trip); verify on the dev client that `sample sable:info <sub> brief side=server every 1t x20 clock=server` and `sample entity e<id> side=server every 1t x10 clock=server` answer, and that `sample entity e<id> and entity e<id> side=server and nbt e<id> path=Pos every 1t x10` reads both server rows of a run at one tick
- [x] 1.2 Add `GameBridge.clientTicks()` and the `client_tick` field on client-clock rows (D3), and `GameBridge.serverLoad()` with `held_client=` and the help notes of `sample` and `load` (D4), with the README's meta row; add engine tests (client rows carry a rising client tick, server-clock rows none, `held_client` counts runs under server load and is left out without); verify on the dev client that `sample entity e<id> every 1t x40` rows rise by one in `c<n>`, and that `load server 60; sample entity e<id> and entity e<id> side=server every 1t x20` carries `held_client=20`

## 2. Integration checks

- [x] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
