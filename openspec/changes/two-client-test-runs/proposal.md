# Proposal

## Why

Blockpilot tests one client at a time. Much of what breaks in a mod breaks
between players: a block one player places that another never sees, an
entity that moves for its rider and stands still for everyone else, a
machine whose state reaches only the player who opened it. None of that can
be scripted today, since every run has one client.

## What Changes

- `tools/test_targets.py --clients 2` starts a second dev client of a
  target, with its own game directory and player name, opens the first
  client's world to the LAN and joins the second to it, so a check can
  drive both: each client has its own launcher, and checks that need two
  clients run only where the second was started.
- A first two-client smoke check: a block placed through one client reaches
  the other, and each sees the other's player.
- `docs/dev-environment.md` and the CHANGELOG describe the option.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `dev-integration`: test runs with two clients.

## Impact

- `tools/test_targets.py` and `tools/smoke_test.py`: the second client, its
  join, and the checks that need it.
- Gradle: a second client run per target, generated from the same
  definition as the first, with its own run directory and `--username`.
- `docs/dev-environment.md`, `CHANGELOG.md`.
