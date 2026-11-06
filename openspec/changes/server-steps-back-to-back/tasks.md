# Tasks

## 1. Server steps run back to back (server-bridge)

- [x] 1.1 Add `EngineTest` cases with fake server actions: two that answer in their first server tick both finish in the first server tick after one client tick; one that answers a server tick after it starts, then another, puts exactly one server tick between them; a client query, a `side=server` read and an `if` that holds each end a run and wait for the next client tick, while an `if` that doesn't hold doesn't; verify each fails before the change and the existing server tests (`consecutiveServerQueriesAnswerInOneServerTick`, `aPausedServerFailsWhatWaitsOnItsTicks`, `serverActionsAreCancelledOnTheServerThread`) pass
- [x] 1.2 Extend the gating after a statement in `ScriptJob` over every following statement placed on the server thread whose gate passes now (D1), stopping at the first placed on the client or gated off; verify the new and existing protocol tests pass
- [x] 1.3 Add a smoke step running `server settle=1t time query gametime` three times and one running `server settle=0t time query gametime` three times, checking the results are consecutive and equal; verify on the 1.21.1 NeoForge dev client that both fail before the change and pass after it, and pass on the 26.3 Fabric dev client
- [x] 1.4 Say in the help of `server`'s `settle` option and the `server` row of `docs/commands.md` that `settle=0t` answers in the server tick the command ran in, that consecutive server steps run back to back on the server thread, and that `settle=1t` spaces them one server tick apart (D2); verify `help server` on the dev client shows it and says the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new steps

## Workflow follow-up

- Merge into `main`, then archive this change.
- Pushing needs Jan's go-ahead.
