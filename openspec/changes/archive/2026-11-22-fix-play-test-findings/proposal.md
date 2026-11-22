# Proposal

## Why

Two days of play tests under heavy load hit four problems that cost failed
runs or hand work:
- A client killed while `create world` ran left a save folder holding only
  data packs and `session.lock`. `delete world` refused it with
  `err not_test_world`, since the test-world marker is written only after
  the game has made the world, and `create world` refused the name with
  `err exists`. Each later run failed until someone removed the folder by
  hand.
- `quit` sent while another session's `create world` ran got no answer, and
  the game had to be killed. `quit` waits for every action queued before it,
  and creating a world holds the game's thread for as long as it takes.
- `perf start own+bits` failed with `err syntax capture names use letters,
  digits, _ . - (max 64)`, which doesn't name the rejected name.
- Replies to client-side commands, and lines mods show in the chat
  themselves, weren't in `messages` or the `! chat` events, so scripts read
  them from `latest.log`. Blockpilot logs chat from the loaders' chat
  events, which only lines from the server raise. On Forge and NeoForge the
  same events also carry action-bar text, which was logged as `chat` too.

## What Changes

- `create world` writes the test-world marker before the game starts making
  the world, so a creation cut short leaves a marked folder that
  `delete world` removes.
- `create world <name>` replaces a marked folder without `level.dat`, which
  only a creation cut short leaves. A folder with `level.dat`, or without
  the marker, still fails with `err exists`.
- `quit` cancels, as `stop` does, the actions other sessions queued before
  it, including a `create world` holding the game, and goes next. Actions
  its own session queued before it still run first.
- Capture, recording and snapshot names may hold `+`. A bad capture or
  snapshot name fails with an error that names it, as a recording's does.
- `messages` and `! chat` events carry every line the chat window shows,
  including replies to client-side commands and lines mods add on the
  client. Action-bar text is no longer logged as `chat` on Forge and
  NeoForge.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `test-worlds`: the marker comes first; `create world` replaces a creation
  cut short; snapshot names may hold `+`.
- `game-session`: `quit` doesn't wait for other sessions' actions.
- `client-observation`: the message log holds every chat window line, and
  only those, as `chat`.
- `performance-capture`: the capture name rule, with `+`.
- `video-recording`: recording names may hold `+`.

## Impact

- `game/TestWorlds.java`: marker before creation, replacing an unfinished
  folder.
- `protocol/Engine.java`, `protocol/ScriptJob.java`: a script that starts
  with `quit` cancels other sessions' actions queued before it.
- `game/perf/PerfCommands.java`, `game/record/RecordCommands.java`,
  `game/Snapshots.java`: the name rule and errors.
- A new common mixin on the chat window; the chat listeners of
  `platform/neoforge`, `platform/forge`, `platform/fabric` and the vanilla
  agent's mixins stop logging, and keep withholding untrusted chat.
  `ClientPlatform.showLocal` lines stay out of the log.
- `EngineTest`, the smoke test, `docs/commands.md` and the help texts.
