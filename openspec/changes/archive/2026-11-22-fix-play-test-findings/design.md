# Design

## Context

- `create world` calls the game's `createFreshLevel`, which holds the client
  thread until the world is ready, in every version from 1.20.1 to 26.3:
  first while the data packs load, running only queued tasks, then while the
  integrated server prepares the spawn area, running client ticks. The game
  writes `level.dat` between the two. Blockpilot writes its marker after the
  call returns.
- Actions run one at a time from one queue, in order. A script with only
  queries runs at once. `stop` is a query; it cancels on the client thread,
  where it reaches a job that holds the thread by setting a pending cancel.
- The loaders' chat events (NeoForge and Forge `ClientChatReceivedEvent`,
  Fabric `ClientReceiveMessageEvents`) and the vanilla agent's
  `ChatListener` hook fire only for lines from the server. Client-side
  command replies and `LocalPlayer.displayClientMessage` go straight to the
  chat window. Every chat window line goes through one method: the
  three-argument `ChatComponent.addMessage` up to 1.21.11, the private
  four-argument one from 26.1.

## Goals / Non-Goals

**Goals:**
- A creation cut short never blocks the next run.
- `quit` answers and closes the game even while another session's action
  holds the game.
- One rule for capture, recording and snapshot names.
- The message log is exactly what the chat window shows.

**Non-Goals:**
- Aborting a world creation part way. The game offers no way out of
  `createFreshLevel`.
- Deleting folders that lack the marker, such as one an older Blockpilot
  left. Those may be a person's, so they stay for a person to remove.
- `quit` other than first in its script. By the time a later `quit` runs,
  everything queued before its script has already finished.

## Decisions

### D1: The marker first, and "unfinished" means marked without level.dat
`create world` creates the folder and writes the marker, then calls the
game. `createFreshLevel` opens an existing folder without complaint. A folder
that is marked but has no `level.dat` can't be loaded and holds nothing of a
person's, so `create world` deletes it and goes on. One with `level.dat`
still fails with `err exists`: it is a world someone may want, and
`delete world` removes it. A `replace` option was the alternative, but
replacing only what can't be loaded needs no option and keeps a typo from
wiping a real test world.

### D2: quit cancels other sessions' earlier actions, not its own
When a script whose first statement is `quit` is queued, the engine cancels,
on the client thread, every queued job of another session ahead of it, as
`stop` does. Running there reaches a job holding the thread through its
pending cancel, and the queue then has the `quit` job at its head. The
spawn-area ticks step it, it answers, and the game stops once
`createFreshLevel` returns. The session's own earlier jobs stay, so a client
that sends requests without waiting for answers keeps its order, as the spec
promised before. Cancelling everything, as `stop` does, was the alternative.
It would end a pipelined walk of the same agent that sent `quit`. Each
launcher call is its own session, so a launcher `quit` still cancels
everything else. The check is by command name in the engine, as
`CommandGate` checks `quit` and other built-ins by name, rather than a new
flag on the public `CommandSpec`.

### D3: Log chat where the window takes it
A common mixin at the head of the chat window's one entry method logs each
line, and the loader listeners and the vanilla hooks stop logging. Untrusted
chat is still judged where it is today, before the window, so the window,
and with it the log, get the stand-in. `ClientPlatform.showLocal` sets a
flag while it adds a line, and the mixin skips lines added under it. That
keeps Blockpilot's own notes to a person out, and lets Fabric's withheld
path keep logging its stand-in explicitly. Comparing a line with the last
one an event delivered was the alternative. It breaks when a mod's listener
replaces the component, or a Fabric listener cancels the line before the
window.

### D4: One name rule with a plus
Captures, recordings and snapshots take `[A-Za-z0-9_.+-]{1,64}`, not
starting with `.`. `+` is safe in file names on every platform. Every bad
name error names the rejected name, as the recording's did.

## Risks / Trade-offs

- Logging from the chat window moves the log a moment later than the
  events: after every other listener, rather than in the middle of them.
  Nothing reads it in between.
- On Forge and NeoForge, scripts that matched action-bar text with
  `messages kind=chat` stop matching it. That was a double entry, and
  `kind=actionbar` still lists the text.
- A `quit` behind a long data-pack load still answers only once the spawn
  area starts loading, since no client tick runs before then.
