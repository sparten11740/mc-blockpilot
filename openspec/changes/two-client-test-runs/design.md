# Design

## Context

- `tools/test_targets.py` starts one dev client per target with Gradle's
  `runClient`, waits for the endpoint, creates a test world and runs the
  smoke test against that client's launcher.
- Opening a singleplayer world to the LAN is Minecraft's own `/publish`,
  which `server` runs with the cheats a test world has; `join server`
  takes an address.

## Goals / Non-Goals

**Goals:**
- Scriptable checks across two clients in one run.

**Non-Goals:**
- Dedicated servers or more than two clients.

## Decisions

### D1: The second client is a second run

The Gradle build generates, from the same definition as each target's
client run, a second one with its own run directory (`run2`) and player
name, so its launcher, endpoint and token are its own. `test_targets.py
--clients 2` starts it as a separate Gradle process with its own project
cache directory once the first client has created its world, so the two
don't contend for one build lock, has the first publish the world with
`server publish <port>` on a fixed port, reads the port `/publish` reports
in case it had to take another, and joins the second with `join server
127.0.0.1:<port>`. Checks that need two clients take both game
directories; without `--clients 2` they are skipped and reported as
skipped, so single-client runs stay as fast as now.

## Risks / Trade-offs

- [Two clients double the load of a run] → Opt-in with `--clients 2`, on
  the targets a change needs.
- [LAN publishing needs a free port] → `/publish`'s reply names the port it
  took, which the run passes on.
