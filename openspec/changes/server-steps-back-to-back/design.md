# Design

## Context

- `ScriptJob.advance` checks a statement's gate on the client thread before
  it runs (`gatedThrough < index`), because the gate reads client state:
  agent control, the player and level, the integrated server, cheats.
- `gateQueriesAfter` extends one check over the queries that follow, client
  or server, so a run of server-side queries answers in one server tick. It
  stops at the first statement that isn't a query (an action, `assert`, `if`,
  `wait`, `sample`), so every server action, and the step after it, waits for
  a client tick to be gated.
- A `side=server` read is placed on the client (`affinity` reads the spec's
  `runsOnServer()`, not the option) and reaches the server through a call
  from the client thread.
- An `if` that holds puts its statement in its place and sets
  `gatedThrough = index - 1`, so that statement is gated on a client tick.
- A server action is ticked in the step that starts it. `server` answers
  when its `settle` count runs out, so `settle=0t` answers in the server tick
  the command ran in, not on the next tick as the spec says, and
  `settle=1t` on the following one.
- The only built-in server actions are `server` and `tick`; extension
  commands can register more (`onServer()`).
- Turning agent control off stops every script (`engine.stopAll`), and a
  paused game fails server work from the client step, which still reaches
  the affinity check when a statement is already gated.

## Goals / Non-Goals

**Goals:**
- Steps on the server thread run on consecutive server ticks, or in one,
  as `settle=` says, whatever the client's ticks do.

**Non-Goals:**
- A new construct for timed server commands (`server at=+Nt`, a block of
  commands with ticks). `settle=` already says when the next step runs once
  the hand-off is gone.
- A tick wait on the server clock (`wait <N>t clock=server`). See Open
  Questions.
- Changing `settle`'s default, or how client steps are gated.

## Decisions

### D1: Gate the whole run of server steps on the client

The gating after a statement goes on through every following statement
placed on the server thread whose gate passes now: server-side queries as
today, `if` and `assert` on them, and server actions. It stops at the first
statement placed on the client, a `side=server` read among them, or whose
gate fails now, which is then checked again at its turn and fails there as
today. The run then advances on the server thread without a client step
between its statements.

An `if` that holds ends the run: its statement, which may be any step, is
gated on the next client tick as today, since its gate reads client state the
server thread can't. An `if` that doesn't hold answers and the run goes on.

Alternatives:
- Check the gates on the server thread: the gate reads client state that
  the server thread mustn't touch.
- Only add `wait <N>t clock=server`: a server step after it would still wait
  for a client tick to be gated, so the hand-off and its drift stay.
- A batch construct: new grammar for what `settle=` expresses once steps run
  back to back.

### D2: `settle` stays the spacing

No new option. `server settle=<N>t` already answers N server ticks after the
command ran; with the run gated ahead, the next server step starts in that
tick. The help and docs say so, with `settle=0t` for several commands in one
tick and `settle=1t` for one per tick.

## Risks / Trade-offs

- [A command in the run takes away what a later step's gate needs, such as
  `deop` of the player] → The later server steps of that run still run, as
  the agent sent them; the next client step is gated as usual. Turning agent
  control off still stops the script at once, and leaving the world stops
  the server, so nothing more runs there.
- [Many `settle=0t` steps make one server tick long] → As a run of 1,000
  server-side queries does today; the agent chose it, and `settle=1t` spreads
  them.
- [The client sees a run's changes later than its replies] → A step that
  reads the world from the client after a run waits for the last step's
  `settle`, as today; the default stays `2t`.

## Open Questions

- Should a later change add `wait <N>t clock=server`, to space server steps
  without a command, mirroring `wait … until … clock=server`? It builds on
  this change without altering it.
