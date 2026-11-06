# Design

## Context

- `ArgType.ENTITY` matches `e<id>`; `Args.entity` parses it into an
  `EntityRef`, and handlers look the id up in the client or server level.
- Handlers run from four places: a script's call and query, a `wait`'s
  query and a `sample`'s query. Some run on the client thread, some on the
  server thread.
- Scoreboard tags live only on the server; custom names reach the client.
- `pixels` already reads `@` followed by a position as a region.

## Goals / Non-Goals

**Goals:**
- Every command that takes an entity takes a name, with no change to the
  command.
- A name means the same entity whichever thread the command runs on.

**Non-Goals:**
- Minecraft's selectors (`@e[type=cow,limit=1]`): `server` already runs
  them, and a name covers what scripts need.
- Several entities at once.
- Tags or names the token can't spell, with `+` or spaces: scripts choose
  the names they summon with.
- Widening `EntityRef`: it stays an id, so `blockpilot-api` only grows.

## Decisions

### D1: Resolve before the handler runs

The protocol turns each argument taken as an entity whose text starts with
`@` into `e<id>` just before the handler runs, through one helper that the
four places call, so handlers see only ids and no command changes. The
helper builds a new `Args` with those values swapped, the single value and
every value of a repeated slot, and leaves arguments of other types alone:
free text such as `chat hello @bob` keeps its `@`. An unknown name fails the
step with `err no_entity @<name>`, as an unknown id fails in the command.
`wait` and `sample` resolve on every check, so they follow an entity that
appears or is replaced, and `wait … until` retries `err no_entity` as it
retries every error but `err syntax`.

`Lexicon.isEntity` and `EntityRef` stay as they are, ids only; a new
`Lexicon.isEntityName` and `ArgType.ENTITY` matching either form carry the
name, and `Args.entity` given a name the engine didn't resolve fails with an
`IllegalStateException` that says so, rather than a parse error.

Alternatives:
- Teach `EntityRef` names and have every handler resolve them: each
  command would change, and an extension built against 2.0.0 wouldn't
  understand names.

### D2: The game looks the name up

`GameBridge.resolveEntity(name)` answers with an entity id. On the server
thread it searches the server's level in the player's dimension, by tag and
custom name. On the client it searches the client's entities by custom name
and, in singleplayer with commands allowed, asks the integrated server for
entities tagged with the name, keeping those the client has. Of several, the
one nearest where the server takes the player to be wins: the away camera's
place while one is placed (`AwayCamera`), else the player. Both threads can
read that point, the server through `AwayCamera.presence`, the client
through the camera's place it writes for the server, so a name means the
same entity wherever the command runs.

### D3: A name starts with a letter and has two characters or more

`entity-name ::= [a-zA-Z] [a-zA-Z0-9_.-]+` keeps names apart from `pixels`
positions after `@` and from Minecraft's one-letter selectors, which would
otherwise read as names and surprise an agent.

## Risks / Trade-offs

- [A name matches an entity the agent didn't mean] → The nearest wins, and
  replies carry the `e<id>`, so the agent sees which it got.
- [On a remote server there are no tags] → Custom names still work; the
  help says tags need the integrated server.
