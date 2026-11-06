# Proposal

## Why

Two common script steps don't do what they say, and scripts that time
things, such as one filming a scene, trip over both:
- `server function <id>` answers `err command_failed` with `result=0` when
  the function ran but has no `return`. `server` takes success only from the
  command's result callback, and Minecraft doesn't call it for a function
  that returns nothing. The error text is the success line, `Running function
  <id>`, and the script stops there unless the step starts with `?`.
- `wait <N>t` waits one tick short, so `wait 1t` between two steps waits
  none. Forty `wait 1t` in a row took 0.14 s, and four `wait 10t` took
  1.95 s, about 36 ticks. A script that moves something one step per tick
  can't be written.

## What Changes

- A server command whose result callback never runs and that reported no
  failure succeeds: `ok success=true`, with no `result`, as it has none. One
  that reported a failure, such as a parse error, still answers
  `err command_failed`. A command whose callback runs is judged as today.
- `wait <N>t` and `wait <N>s` let N ticks pass between the step before and
  the step after. `wait 0t` still passes no tick.
- `sample … while wait <N>t` takes one more sample, at the tick the wait
  ends, as a sample beside an action already runs to the end of it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: a command with no result succeeds unless it failed.
- `player-control`: `wait <Nt|Ns>` lets exactly that many ticks pass.
- `command-language`: the scenario for sampling across a wait counts the
  sample at its end.

## Impact

- `game/ServerCommands.java`: `server` and its help.
- `protocol/Engine.java`: `WaitTicks`.
- `EngineTest`: `waitForTicks` and `aSampleRunsBesideAWait`.
- `tools/smoke_test.py`: a check that loads a data pack with a function
  without `return` and one with it, and a step that times `wait 1t`.
- `docs/commands.md`: the `server` and `wait` rows.
- Scripts that relied on the short wait now run a tick longer per `wait`.
