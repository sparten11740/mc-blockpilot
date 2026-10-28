# Design

## Context

- `Engine.tickClient` runs at the end of every client tick and steps the
  scripts. `submit` also steps query-only scripts at once through
  `runOnClient`. Both run on the client thread.
- Some commands hold the client thread on purpose: a world load waits inside
  the command while the integrated server starts. Tasks the client runs then
  must not re-enter that script.
- Replies are written when a script's future completes. The network layer
  never waits for the client, so a reply can be sent from any thread.
- `ScriptJob.cancel(code)` is already called off the client thread, for
  instance when a session closes. It fails the current statement with the
  code and skips the rest.
- Chat lines reach Blockpilot on the client thread, one call each, before the
  chat screen takes them.

## Goals / Non-Goals

**Goals:**
- An agent learns within about 30 s that the game is stuck, and why when chat
  is the cause.

**Non-Goals:**
- Ending the flood or running commands while the client is stuck. That needs
  a way to the server that skips the client's gate, which is a separate
  decision.
- Limiting chat itself. Minecraft logs and shows every line before any mod
  could drop it.

## Decisions

### D1. What a stall is

The client is stalled when its last finished tick is 30 s old and no
Blockpilot step runs on the client thread. The engine keeps two volatile
times: the end of the last client tick, and the start of the client step
running now, or 0. The step mark covers `tickClient` and the steps `submit`
starts, so a world load inside a command counts as busy, not stalled.

30 s is well past garbage-collection pauses and chunk-generation hitches, and
well short of the CLI's 300 s.

### D2. A watchdog thread

The engine starts one daemon thread that checks once a second. While the
client is stalled, it cancels every queued and query-only script with
`err stalled <why>`. A request sent during the stall is cancelled at the next
check. A server-side action still running is cancelled too: its script can't
go on past it without the client. The check reads only the two times and the
script lists, so it never waits on a game thread. Tests call the check
directly with a time.

### D3. Cancelling with a reason

`ScriptJob.cancel` takes the result to fail the current statement with. The
code-only form stays for `stop` and closing sessions. The reason reads
`the client hasn't ticked for <n>s`, followed by the game's hint when there
is one.

### D4. The chat hint

`GameBridge.stallHint()` returns what the client did since its last tick, or
null. The game counts chat lines received since the end of the last client
tick, in a counter the chat hook adds to and `tickClient` resets. When that
count is above zero the hint reads `<n> chat lines arrived since`. Other
causes, such as a mod looping on the client thread, give no hint.

## Risks / Trade-offs

- [A game that really is busy for over 30 s outside Blockpilot, saving a huge
  world after the player quits for instance, fails pending requests] → it
  says why. The agent can retry once the client ticks again.
- [Cancelling from the watchdog thread] → as when a session closes. A client
  action's cancel can run off the client thread, which every action already
  allows for `stop` from another session.
