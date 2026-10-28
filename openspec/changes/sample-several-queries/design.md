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

### D3. One clock for all

The parser refuses queries whose commands run on different sides, naming
each side. `clock=` must fit them all. The statement is placed and gated as
its first query is. Every query is still gated at each run, so a later query
that can't run gives `err` in its own rows, as a single query does today.

### D4. 1,000 rows in all

The 1,000 limit counts rows. `x<n>` with k queries must have n·k at most
1,000, else `err syntax` names the limit and the product. Beside an action
and without a count, the sample stops after 1,000 / k runs. `samples=` and
`done()` count runs; with one query that is the row count, as today.

## Risks / Trade-offs

- [A sampled query can't take a bare `and`, such as a sub-level named `and`]
  → the UUID still names it. `wait`, `assert` and `if` are unchanged.
- [More rows per reply] → the 1,000-row cap keeps a reply as large as a
  single-query sample can be today.
