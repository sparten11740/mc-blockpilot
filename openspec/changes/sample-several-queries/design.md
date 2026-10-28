# Design

## Context

- `Parser.sample` reads one query up to the stop words (`ok`, `until`,
  `every`, `then` or a test shape), then `every`, the duration, the count,
  `clock=` and `while`. `checkClock` matches `clock=` against the side the
  query's command runs on.
- `Statement.Sample` holds one `Call`. `ScriptJob` places and gates the
  statement by that query's spec, and runs a `while` action beside it.
- `Engine.Sampler` runs the query on its clock every N ticks. Each run adds a
  row: `tick`, then `err` when the query failed, its fields, and `count` when
  it gave rows. `done()` counts rows against the count.
- `Grammar.CORE` is the EBNF that `help grammar` prints. `GrammarTest`
  requires it to equal the EBNF of the active change that modifies the
  command-language spec, else the main spec's.
- A command declared `onServer()` runs on the integrated server's thread and
  needs the integrated server and cheats; the gate answers `no_server` or
  `no_cheats`. The protocol layer has no way to run work on the server thread
  from the client's tick; it only steps server-side scripts from the server's
  tick hook.
- `Sides.onServer` (game layer) submits a read to the integrated server and
  waits up to 5 s. `sable:find`, `sable:list` and `sable:tp` use it for
  `side=server`, so their handlers still run on the client's tick.
- `sable:info` resolves the sub-level and reads pose, velocity, `agl` and
  `rides` from the client only.

## Goals / Non-Goals

**Goals:**
- Several readouts sampled on the same ticks in one line, read back as rows
  that line up by tick.

**Non-Goals:**
- Merging the queries' fields into one row per tick. Two queries can share
  field names (`speed`, `y`), and prefixes would change the names that
  predicates and readers already use.
- Several queries for `wait`, `assert` or `if`. Their predicates read one
  result.
- Running each query on its own side's clock and grouping the rows by tick
  afterwards. That splits the sampler across two threads and must match the
  client's game time against the server's, which can be a tick apart, for
  little gain over D3.

## Decisions

### D1. `and` joins the queries, inside `sample` only

A sampled query also stops at a bare `and`, through its own production
`sample-query`. The plain `query` of `wait`, `assert` and `if` is unchanged.
`and` reads as it does between predicates. A read-only command rarely needs
a bare `and`; only a word slot, such as a sub-level's name, could take one
(see Risks). The grammar's `sample` becomes:

```ebnf
sample       ::= "sample" blank+ sample-query
                 ( blank+ "and" blank+ sample-query )*
                 blank+ "every" blank+ duration
                 ( blank+ count ( blank+ clock )? ( blank+ beside )?
                 | ( blank+ clock )? blank+ beside )
sample-query ::= ( command-name - construct )
                 ( blank+ ( token - ( stop | "and" ) ) )*
```

`GrammarTest` compares `Grammar.CORE` with the grammar of the active change
that modifies the command-language spec. So the MODIFIED Grammar requirement
joins this change's delta in the commit that changes `Grammar.CORE`, as for
`sample … while`, and the build stays green until then.

Alternatives: `;` already ends a statement; `,` is part of positions; `&` is
kept out of predicate values and reads as a shell operator; `while sample …`
would put the second sample on its own schedule, not the same ticks.

### D2. One row per query, named after the tick

Each run adds one row per query, in the order written. With several queries
each row carries the field `query`, the query's command name, printed bare
right after the tick: `t758038 sable:info yaw0 …`. Two queries of the same
command are told apart by their order. A sample of one query leaves `query`
out, so its replies read as today.

### D3. Mixed sides on the client clock

When all queries run on one side the sample runs on that side's clock, as
today, and `clock=` must fit it. When they mix sides, the sample runs on the
client's clock, and `clock=server` is `err syntax` naming a client-side query.
On each run the sampler runs the client-side queries on the client thread.
It reads all the server-side ones in a single hop to the server thread, so
they see one moment of the server. The sampler reaches the server through a
new `GameBridge` call that runs a task on the integrated server's thread and
waits for it up to 5 s, as `Sides.onServer` does. A hop that fails or times
out gives each server-side query's row an `err`. Every query is gated at each
run as today, so without the integrated server the server-side rows carry
`err no_server` (or `no_cheats`). The statement's placement uses the client
when sides mix, else its first query's side.

Rows carry the tick of the client's run, as `gameTime()` reports it. In
singleplayer that is the integrated server's game time, so the server rows
read the moment their label names, or the next server tick at most.

### D4. 1,000 rows in all

The 1,000 limit counts rows. `x<n>` with k queries must have n·k at most
1,000, else `err syntax` names the limit and the product. Beside an action
and without a count, the sample stops after 1,000 / k runs. `samples=` and
`done()` count runs; with one query that is the row count, as today.

### D5. `sable:info side=server`

`sable:info` takes the `side` option as `sable:find` does and reads through
`Sides.read`. The sub-level is resolved as `sable:find` resolves it: `here`
on the client, a UUID or name on the side read. With `side=server` the pose,
velocity and bounds come from the server's `SubLevels.Info`, `agl` from
`LowestPoint` and the ground scan in the server's level, and `rides` from the
server's player. The handler still runs on the client's tick, so in a mixed
sample it reads the server in a hop of its own, beside the sampler's.

## Risks / Trade-offs

- [A sampled query can't take a bare `and`, such as a sub-level named `and`]
  → the UUID still names it. `wait`, `assert` and `if` are unchanged.
- [More rows per reply] → the 1,000-row cap keeps a reply as large as a
  single-query sample can be today.
- [The client waits on the server once per run] → as `side=server` reads do
  today, with the same 5 s limit. A server read is a short task between
  server ticks.
- [`sable:info side=server` hops on its own, so in one run the server can
  tick between its read and the sampler's batch] → the two reads lie at most
  a tick apart. Both read the copy the server simulates.
