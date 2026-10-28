# Tasks

## 1. Sampling several queries (command-language)

- [x] 1.1 Parse `sample <query> [and <query>…]` through `sample-query` in `Grammar.CORE`, with the MODIFIED Grammar requirement added to this change's delta in the same commit (D1), run a sample of mixed sides on the client clock with its server-side queries read in one hop through a new `GameBridge` call, refusing `clock=server` for it (D3), check that n·k is at most 1,000 (D4), hold the queries in `Statement.Sample`, and run them in the sampler on the same ticks with one row per query, named by `query` after the tick when there are several (D2), counting runs for `samples=` and the count, with a job making up the server steps it missed while the client waited on the server; update `sample`'s usage and help, the README's meta row, and add parser and engine tests (several queries, a bare `and` splitting them, mixed sides with the server ones read in one hop, `clock=server` refused for mixed sides, the row cap, rows per tick in order, one query unchanged, beside a `while` action, a server-side action keeping its ticks beside a mixed sample); verify `GrammarTest` passes and, on the dev client, that `sample sable:info <sub> brief and status every 20t x3` gives six rows in pairs with the same tick, that `sample status and nbt e<id> every 1t x5` gives five pairs of rows with the same tick in each pair, that the same with `clock=server` fails with `err syntax`, and that `sample status and status every 1t x600` fails naming the 1,000-row limit

## 2. The server's copy of a sub-level (sable-sublevels)

- [x] 2.1 Give `sable:info` the `side` option, reading through `Sides.read` with the sub-level resolved as `sable:find` resolves it (D5), with `help sable:info`, the README's sable row and With Sable section; verify on the dev client that `sable:info <sub> side=server` at rest gives the client's pose and `agl`, that a sub-level `sable:list side=server` shows but the client hasn't received answers with `side=server` while the plain form fails with `err no_sublevel`, and that `sample sable:info <sub> brief side=server and nbt e<id> every 20t x3` gives three pairs of rows

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
