# Proposal

## Why

Sampling a body on both sides showed three faults:

- `sample sable:info ship brief side=server every 1t clock=server` is refused
  because `sable:info` runs on the client. With `side=server` it reads the
  server, so the server clock fits it. In a sample that mixes sides, such a
  query also reads the server on a trip of its own, beside the run's shared
  one, so two server readouts of one run can be a tick apart.
- Client-clock rows are labelled with the integrated server's game time as
  the client thread reads it. The two threads tick out of phase, and the
  phase drifts, so labels repeat and skip. After a restart, 100 rows carried
  only 63 to 67 different labels, and the agent had to sort rows by key.
- Every read of the server from the client waits for the server thread.
  Under `load server` the client then stalls for most of a server tick and
  catches up in bursts. A smoothness measurement sampled that way shows
  bunched updates that real lag doesn't produce, and nothing says so.

## What Changes

- A query given `side=server` counts as a server-side query wherever a
  `sample` or a `wait` picks its clock: `clock=server` fits it and runs it
  on the server thread. In a sample that mixes sides it is read in the run's
  one trip to the server, with the other server-side queries.
- Rows taken on the client clock add the client's own tick count,
  `c<n>` (`client_tick`). It rises by one per client tick, so rows sort and
  pair reliably. `tick` stays the one game clock.
- A client-clock sample that read the server while `load server` was set says
  so in its result, `held_client=<runs>`. The help of `sample` and `load`
  explains that such reads hold the client until the server's tick ends.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: `side=server` reads as server-side queries in samples
  and waits, the client tick on client-clock rows, and the held-client note.

## Impact

- `protocol/Statement.java` (whether a call runs on the server),
  `Parser.java`, `Engine.java` (sampler, waits), `GameBridge.java`, with
  their tests.
- `game/Sides.java` (a read already on the server thread runs at once),
  `game/BlockpilotClient.java` (client ticks, the server load), `Construct`
  and `load` help.
- README: the meta row.
