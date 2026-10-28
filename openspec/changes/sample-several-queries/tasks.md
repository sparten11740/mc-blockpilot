# Tasks

## 1. Sampling several queries (command-language)

- [ ] 1.1 Parse `sample <query> [and <query>…]` through `sample-query` in `Grammar.CORE`, with the MODIFIED Grammar requirement added to this change's delta in the same commit (D1), check that all queries share one clock (D3) and that n·k is at most 1,000 (D4), hold the queries in `Statement.Sample`, and run them in the sampler on the same ticks with one row per query, named by `query` after the tick when there are several (D2), counting runs for `samples=` and the count; update `sample`'s usage and help, the README's meta row, and add parser and engine tests (several queries, a bare `and` splitting them, mixed clocks, the row cap, rows per tick in order, one query unchanged, beside a `while` action); verify `GrammarTest` passes and, on the dev client, that `sample sable:info <sub> brief and status every 20t x3` gives six rows in pairs with the same tick, that `sample status and nbt e<id> every 1t x5` fails with `err syntax` naming both sides, and that `sample status and status every 1t x600` fails naming the 1,000-row limit

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
