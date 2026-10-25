# Tasks

## 1. Sub-levels the client misses (sable-sublevels)

- [x] 1.1 Add `side=server` and the `server_only` note to `sable:list` (D1, D2), with its help text and the README's Sable section; verify on the 1.21.1 NeoForge dev client with Sable that, with a ship assembled near spawn and the player teleported far enough that the client drops it, `sable:list <radius>` carries `server_only=1` and `sable:list <radius> side=server` lists the ship, that next to the ship there is no `server_only`, and that the README's new commands run as written

## 2. The hit a click sends (player-control)

- [x] 2.1 Read `use`'s hit on the tick the click is handled (D3) and say in `help use` and the README which hit the server gets; verify on the dev client that `use` at a point above a button's shape reports the hit where the ray met a shape, that a plain `use <pos>` still reports the block, and that the smoke test passes

## 3. Readiness wait (dev-integration)

- [ ] 3.1 End `--wait-ready` at a crash report that appears while it waits (D4), with `MainTest` cases for a new and an old report, and add the wait and the asleep-display note to the README; verify the tests pass on both loaders' nodes

## 4. Integration checks

- [ ] 4.1 Build the four jars and run the smoke test on the 1.21.1 NeoForge dev client; verify all node tests pass and the smoke test passes in text and JSON mode

## Workflow follow-up

- Tell the play-testing session that a new 1.21.1 NeoForge jar is ready, and
  what changed.
- Archive after `add-jar-cli-and-stdio-mcp`, which this change's README
  edits build on.
