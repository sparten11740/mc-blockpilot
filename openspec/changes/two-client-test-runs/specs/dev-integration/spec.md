## ADDED Requirements

### Requirement: Test runs with two clients
`tools/test_targets.py --clients 2` SHALL start a second dev client of each
target it runs, with its own game directory and player name, join it to the
first client's world opened to the LAN, and run the checks that need two
clients; without it, those checks SHALL be skipped and reported as skipped.

#### Scenario: A block both players see
- **WHEN** a run with `--clients 2`, both players at the world's spawn, has
  client A run `server setblock 3 -60 3 minecraft:gold_block`, and client B
  then runs `settle; block 3,-60,3; find player 64`
- **THEN** B's `block` reads `gold_block`, and B's `find player` lists A's
  player
