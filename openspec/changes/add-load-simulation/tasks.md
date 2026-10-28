# Tasks

## 1. Server and client load (load-simulation)

- [x] 1.1 Add the `load` action with its forms, report and limits (D1, D6), the server load parked at the head of `tickChildren` through a mixin for both versions (D2), and its end on `load off`, a 0, agent control off, leaving the world, the game closing and the server stopping (D5); add unit tests for the time drawn within the jitter and for the report; add help and the README's performance row; verify on the dev client that `load server 60; perf start; wait 200t; perf stop` reports MSPT near 60 and TPS near 16, that `load server 30 jitter=20` spreads MSPT, and that leaving and joining the world ends it
- [x] 1.2 Add the client load in the frame mixin for both versions (D3); verify on the dev client that `load client 50` brings `perf` FPS under 20 and `load client 0` restores it

## 2. Network delay (load-simulation)

- [ ] 2.1 Add the delaying handler with ordered release per direction and an accessor for the connection's channel in both versions (D4), installed by `load net` and passing packets straight on at 0; add unit tests for the release times (order kept under jitter, straight on at 0); verify on the dev client that after `load net 200` a block placed with `server settle=0t setblock …` reaches the client about 4 to 5 ticks later (with `wait block … until id==… ` reporting `elapsed`), against 0 to 1 tick without it, and that the game stays connected for a minute at `load net 2000`

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
