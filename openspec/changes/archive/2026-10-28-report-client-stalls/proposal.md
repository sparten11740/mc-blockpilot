# Proposal

## Why

Every Blockpilot command starts on the game's client thread. When that thread
stops ticking for a reason outside Blockpilot, every request just waits.
A command block that floods chat can do that: hundreds of thousands of lines a
tick, each logged and added to the chat screen. The CLI gives up after its
300 s timeout and advises a larger `--timeout`, which is wrong here. The agent
can't tell that the game is stuck, or why. Even `stop` and a `server` command
to undo the cause wait forever. Blockpilot's own buffers stay bounded, but
nothing reports the stall.

## What Changes

- The endpoint notices when the client thread hasn't finished a tick for 30 s
  while no Blockpilot command holds it. It then answers every request waiting
  on the client with `err stalled`, saying for how long and how many chat
  lines arrived meanwhile, and cancels its script. Requests that arrive while
  the stall lasts get the same answer within a second.
- A world load inside a Blockpilot command, which holds the client thread on
  purpose, is not a stall.
- `help errors` explains `stalled`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `agent-endpoint`: requests are answered, not left waiting, when the client
  thread stalls.

## Impact

- `protocol/Engine.java` (a watchdog thread and the client's tick and step
  times), `protocol/ScriptJob.java` (cancelling with an error argument),
  `protocol/GameBridge.java` (a hint on what the client is busy with),
  `protocol/Errors.java`, with engine tests.
- `game/BlockpilotClient.java`: counting chat lines since the last client
  tick.
- README: the paragraph on the CLI's exit codes and timeout.
