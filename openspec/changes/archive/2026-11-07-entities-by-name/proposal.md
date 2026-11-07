# Proposal

## Why

An agent that summons actors for a scene, or a test that spawns mobs, can't
point a command at one of them without first finding its `e<id>`, and `find`
and `look` search around the player, which for a camera placed away from the
player finds nothing. Filming an episode, the camera couldn't follow a
summoned villager for that reason. Every command that takes an entity would
be easier to use with a name the agent chose itself: the scoreboard tag it
summoned the entity with, or a name tag it put on it.

## What Changes

- Wherever a command takes `e<id>`, it also takes `@<name>`: the entity whose
  custom name is `<name>`, ignoring case, or, in singleplayer with commands
  allowed, whose scoreboard tags hold `<name>` exactly. Of several, the one
  nearest the player, or the camera while one is placed with `away`, is
  taken.
- The engine resolves `@<name>` to the `e<id>` it stands for on the thread
  the command runs on, just before the command runs, so every command,
  built-in or extension, takes names without changing.
- No entity by that name fails with `err no_entity @<name>`; `wait` and
  `sample` look the name up on every check, so a wait can await an actor
  that doesn't exist yet.
- Free text keeps its `@`: `chat hello @bob` says what it says.
- A name is at least two characters, starting with a letter, so it can't be
  taken for a position after `@` in `pixels` or for a selector such as `@p`.
- The grammar's `entity` production, `help`, and `docs/command-language.md`
  show the new form.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: entity references by name or tag, and the grammar.

## Impact

- `api/Lexicon.java` and `api/ArgType.java`: `entity ::= "e" digits | "@"
  entity-name`, through a new `isEntityName`; `EntityRef` stays an id, and
  `Args.entity` refuses an unresolved name clearly.
- `protocol`: one place that turns `@<name>` arguments into `e<id>` before a
  handler runs, used by scripts, `wait` and `sample`; `GameBridge` gains the
  lookup.
- `game`: the lookup, on the client by custom name and through the
  integrated server by tag, on the server by both.
- `protocol/Grammar.java` and the command-language spec's grammar,
  `docs/grammar.ebnf`, `docs/grammar.xhtml`, `docs/command-language.md`.
- `help`, the MCP `run` tool's description, `docs/extending.md`, the
  README and the 1.1.0 section of `CHANGELOG.md`.
- Tests: lexicon and parser tests for the token, engine tests that a query,
  an action, `wait` and `sample` see the resolved id and that an unknown
  name fails; a smoke check with a tagged cow.
