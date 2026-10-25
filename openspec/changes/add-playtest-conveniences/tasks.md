# Tasks

## 1. Quit (game-session, dev-integration)

- [x] 1.1 Add `quit`: leave a loaded world as `leave` does, answer `ok pid=<pid>`, then stop the game a few ticks later through its own stop; help, example and the README command table; verify in game on NeoForge and Fabric that `quit` from a world saves it (a block placed just before is there after the next start), the process exits by itself, and `endpoint.json` is gone
- [x] 1.2 Make the CLI wait for the pid in a `quit` reply (text `ok pid=<n>`, JSON `pid`) until the process is gone or `--timeout` runs out, exit 0 or 2 with the pid on stderr, and document it in the README's CLI section; verify by running `tools/blockpilot.py "quit"` against a dev client (returns once the process is gone, exit 0) and from the title screen with `--timeout 0.5`, long enough for the reply but too short for the game to close (exit 2, pid on stderr)

## 2. Points within a block (player-control, game-perception)

- [x] 2.1 Add the game-free block-frame helper (north-facing model frame, quarter turns by a horizontal facing, both directions) with unit tests for all four facings, an unturned block and the round trip
- [x] 2.2 Add `at <u,v,w>` to `face` and `use` (coordinates 0 to 1, else `err syntax`), aiming at that point of the block for world and plot positions; help and README; verify in game that `face <furnace> at 0.75,0.75,0` aims at the upper left of the front (as seen from in front) of a furnace facing north and of one facing east (the crosshair is on the front face both times), that `use <lever> at 0.5,0.1,0.5` toggles a lever, and that `use <plot position> at …` works on a block aboard a moving sub-level
- [x] 2.3 Add `hit=<u,v,w>` (JSON `hit`) to the crosshair report of `status` and the aim of `face`/`use` replies; verify in game that aiming at a point by hand, reading `hit=` and passing it to `use <pos> at` lands on the same point, on a turned block and aboard a sub-level

## 3. Messages and screenshots (client-observation, game-perception)

- [x] 3.1 Add `match=<pattern>` to `messages` (whole text, any case, `*` for any run) with a unit test of the pattern rule and the help; verify in game that `wait messages since=<tick> match=*engaged* until count>0 timeout=30s` returns on the matching chat line and not on an earlier other one
- [x] 3.2 Make the `screenshot` action and the MCP tool capture a frame rendered after the request, keeping burst spacing; verify in game that `use <lever>; screenshot` shows the lever flipped and `chat hello; screenshot` shows the chat line (both arrive from the server a tick later), and that bursts, sheets and `hud=off` still pass the smoke test

## 4. Flight readout (sable-sublevels)

- [x] 4.1 Add `agl` to `sable:info` and the `brief` form (`yaw`, `pitch`, `roll`, `speed`, `climb`, `agl`) with help and README; verify in a dev client with Sable that a ship on the ground reads `agl=0`, and that `sample sable:info <ship> brief every 10t x6` during a climb shows `agl` growing

## 5. Integration checks

- [ ] 5.1 Run `tools/smoke_test.py` in text and JSON mode on NeoForge and Fabric; verify every step passes and the JSON shapes match between the loaders
- [ ] 5.2 Build the jar and give it to the agents play-testing mods with what changed; verify they confirm quit, points within a block and the message pattern work in their loop, and fold their feedback in

## Workflow follow-up

- Archive after `add-test-harness` and `add-sable-support`, whose
  capabilities this change adds to.
