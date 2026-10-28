# Tasks

## 1. Reporting a stalled client (agent-endpoint)

- [x] 1.1 Track the client's last tick and the running client step in the engine (D1), add the watchdog thread and its check (D2), cancel scripts with a reason (D3), add `GameBridge.stallHint()` with the chat count since the last client tick (D4), add `stalled` to `help errors` and the README's exit-code paragraph; add engine tests (a stall cancels waiting and running scripts with the reason and hint, a request during the stall is answered at the next check, a client step that holds the thread is not a stall, ticking again ends it); verify on the dev client, in a throwaway world, that a one-off chat burst big enough to hold the client for over 30 s (nested `execute as @e … run tellraw @a …` from a `server` command) answers a waiting `wait 100t` with `err stalled … chat lines arrived since` about 30 s after the client's last tick, that `stop` sent then is answered within a second with `err stalled`, that requests work again once the burst is through, and that `create world … normal` still answers `ok`

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass
