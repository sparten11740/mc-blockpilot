# Tasks

## 1. Naming the window (client-observation)

- [ ] 1.1 Give `TickLog` and the particle counts the tick of their oldest entry, add the window computation to `Since` and the `window` field to `sounds`, `messages` and `particles` (D1), with their help, field docs and the README's observe row; add unit tests for the window (since= later than the oldest tick, earlier than it, a duration, no since=, an empty log); verify on the dev client that `particles since=100` names the window the log holds, that `particles since=100t` gives `t<now-100>..t<now>`, that `particles` alone gives the last 20 ticks, and that `sounds since=3s` and `messages` name theirs
- [ ] 1.2 Note the tick the player joins a world and add `joined=t<tick>` with its reminder to `sounds`, `sounds playing`, `messages` and `particles` when a bare `since=` predates it (D2); add unit tests for when it appears (bare tick before joining, bare tick after, a duration); verify on the dev client that `sounds since=100` carries `joined=` and `sounds since=<tick of a reply>` does not

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
