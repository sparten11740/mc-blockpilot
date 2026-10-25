# extension-api Specification

## Purpose

Lets other mods, and Blockpilot's own compatibility modules, add commands and
events to the command language. Modded content then gets structured, first-class
access without Blockpilot knowing about each mod.

## Requirements

### Requirement: Registering extension commands
A mod SHALL be able to register commands during startup through a public API.
Each registration declares the command name, its forms (keywords, slots of the
grammar's slot types, optional groups and a final text slot), its options with
the slot types of their values, whether it is a query or an action, its result
fields, help text, and one example, which must parse. A registered declaration
can't be changed afterwards.

#### Scenario: Registered command is callable
- **WHEN** a mod with id `aerolarge` registers an action `build` taking a
  position and a direction
- **THEN** `aerolarge:build 0,-60,0 south` runs that mod's handler, and the
  result comes back in the session's format

### Requirement: Namespaced command names
Extension commands SHALL be invoked as `<namespace>:<name>`. The namespace is
given at registration: normally the registering mod's id, or for a compat module
the id of the mod it integrates. An extension SHALL NOT be able to shadow a
built-in command. A second registration of the same name SHALL fail at startup
with a logged error naming both registering mods.

#### Scenario: Duplicate registration
- **WHEN** Sable itself and Blockpilot's Sable compat module both register
  `sable:info`
- **THEN** the first registration stays active, and the log names both
  registering mods

### Requirement: Extensions follow the language rules
Extension commands SHALL get parsed arguments (positions, entity refs, counts,
durations, options, and which slot type each matched), and SHALL take part in
scripts, fail-fast, the action queue, `stop`, timeouts, diffs for keyed rows,
`fmt json`, `wait … until`, `sample`, `assert`, `if` and `help grammar` exactly
as built-in commands do.

#### Scenario: Wait on an extension query
- **WHEN** an agent runs `wait sable:info <uuid> until speed<0.01 stable=20t`
- **THEN** the extension query is re-run every tick until the condition has held
  for 20 ticks

#### Scenario: Stop cancels an extension action
- **WHEN** an extension action is running and another session runs `stop`
- **THEN** the extension action is cancelled through its cancel hook and its
  script result is `err cancelled`

### Requirement: Discovery through help
`help` SHALL list registered namespaces. `help <namespace>` (or `help
<namespace>:`, needed when a built-in command has the same name) SHALL list that
namespace's commands, and `help <namespace>:<name>` SHALL return its syntax,
result fields and example. Registering extensions SHALL NOT change the MCP tool
list or tool descriptions.

#### Scenario: MCP tools unchanged
- **WHEN** an extension registers ten commands
- **THEN** `tools/list` still returns only `run`, `screenshot` and `help`, with
  the same descriptions

### Requirement: Fault isolation
An exception thrown by an extension handler SHALL produce `err ext_failed
<namespace>:<name> <message>` and be logged with its stack trace. It SHALL NOT
close the session, stop the endpoint, or crash the game.

#### Scenario: Handler throws
- **WHEN** an extension query handler throws a NullPointerException
- **THEN** the result is `err ext_failed`, the remaining script commands are
  `skip`, and the next request is served normally

### Requirement: Extension events
An extension SHALL be able to emit events. They are delivered like built-in
events, as `! <namespace>:<event> <fields>` in text mode and as event objects in
JSON mode.

#### Scenario: Extension reports an event
- **WHEN** an extension emits `assembled` with field `uuid`
- **THEN** the next reply of each session carries `! sable:assembled
  uuid=<uuid>`

### Requirement: Optional, version-independent API
The extension API SHALL contain no Minecraft or loader types. It SHALL be
published as a compile-only artifact that is the same for every Minecraft
version and loader. A mod that integrates only when Blockpilot is installed
SHALL keep working without Blockpilot.

#### Scenario: Integrating mod without Blockpilot
- **WHEN** a mod that registers Blockpilot commands only when the `blockpilot`
  mod is loaded runs without Blockpilot installed
- **THEN** it loads and works normally, and no Blockpilot class is loaded

#### Scenario: One API jar for all targets
- **WHEN** a mod compiles against the published `blockpilot-api` artifact
- **THEN** the same compiled integration works with the 1.21.1 and 26.1
  Blockpilot jars, on NeoForge and on Fabric
