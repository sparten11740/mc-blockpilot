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
- **WHEN** a mod with id `mymod` registers an action `build` taking a
  position and a direction
- **THEN** `mymod:build 0,-60,0 south` runs that mod's handler, and the
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
- **THEN** the same compiled integration works with the 1.20.1, 1.21.1, 26.1,
  26.2 and 26.3 Blockpilot jars, on NeoForge, Forge and Fabric

### Requirement: Server-thread extension handlers
An extension command SHALL be able to declare that it runs on the integrated
server thread. Its query runs between server ticks; its action's start, tick and
cancel run on server ticks. Such commands follow the server-bridge gate and can
be used with `clock=server` in `sample` and `wait`.

#### Scenario: Server-side build command
- **WHEN** an extension registers `mymod:build` as a server-thread action
  and an agent runs it in a cheats-enabled singleplayer world
- **THEN** the handler runs on the server thread, and the result returns after
  the build has finished

#### Scenario: Server-thread command without integrated server
- **WHEN** an agent runs a server-thread extension command while connected to a
  remote server
- **THEN** the result is `err no_server`, and the handler is not called

#### Scenario: Server-clock sampling of an extension query
- **WHEN** an agent runs `sample sable:info <uuid> every 40t x5 clock=server`
  and `sable:info` is a server-thread query
- **THEN** five rows are returned, taken on server ticks exactly 40 apart

### Requirement: Repeated groups in extension commands
A registration SHALL be able to declare an optional group that repeats up to
a limit, as command-language describes, and its handler SHALL read the values
each slot of the group took, in the order written. This SHALL be an addition
that leaves integrations compiled against the earlier 2.x API working.

#### Scenario: A repeated group in an extension command
- **WHEN** a mod registers `test:path <pos> [via <pos>]…` with a limit of 4
  and an agent runs `test:path 0,0,0 via 1,0,0 via 2,0,0`
- **THEN** the handler reads the via positions `1,0,0` and `2,0,0` in that
  order

#### Scenario: An integration built for 2.0
- **WHEN** a mod compiled against `blockpilot-api` 2.0.0 runs with a
  Blockpilot that has the 2.1.0 API
- **THEN** its commands register and run as before

### Requirement: An action when the script ends
`CommandContext.atEnd(Action)` SHALL run the action when the script the
command runs in ends, however it ends, on the client thread and before the
script's reply, after the endings registered before it. Called on a
context that isn't a script's, it SHALL throw
`UnsupportedOperationException`.

#### Scenario: Cleaning up after a failure
- **WHEN** an extension's command registers an ending and a later step of
  its script fails
- **THEN** the ending runs after the failing step's error and the skipped
  steps, before the reply is sent

#### Scenario: Outside a script
- **WHEN** code calls `atEnd` on a context that isn't a script's
- **THEN** it throws `UnsupportedOperationException`

### Requirement: What an ending answers
An ending's result SHALL arrive among the reply's events, or be pushed to a
session that pushes events, as an event named after the action's label,
with the result's fields, or `err` and the code; one that answers `skip`
SHALL leave no event. An extension's ending that hasn't answered after
60 s SHALL be cancelled and answer `err timeout`.

#### Scenario: The ending's event
- **WHEN** a script whose command registered an ending labelled `tidy`
  ends
- **THEN** the reply's events include `! tidy` with the ending's fields

### Requirement: Trusted players for extensions
`BlockpilotApi.isTrusted(UUID)` SHALL tell whether a player counts as
trusted, as agent-safety defines, so an extension that turns players'
words into agent input applies the same list. Extensions SHALL NOT be able
to change the list. It SHALL be an addition to the 2.1.0 API that leaves
2.0 integrations working.

#### Scenario: An add-on hears only trusted players
- **WHEN** the list holds only `Steve` and an extension asks `isTrusted`
  for `Mallory`'s UUID
- **THEN** it answers false, and true for `Steve`'s UUID

#### Scenario: Nobody trusted yet
- **WHEN** the list is empty
- **THEN** `isTrusted` answers false for every player but the agent's own

### Requirement: Extensions are used only when allowed
With `agentCommands` set to `play`, an extension's commands SHALL be used only
while its namespace is in `allowedExtensions` in the config file, empty by
default and reloaded like the other settings; namespaces Blockpilot registers
itself need none. A call SHALL fail before any step with `err not_allowed
<namespace>:<name>`, and `help` SHALL list the namespace marked as not allowed,
naming the setting. Under `all` every extension's commands SHALL be used, and
events SHALL always be delivered.

#### Scenario: Calling a command that isn't allowed
- **WHEN** `agentCommands` is `play`, `mymod` registered `mymod:gear`,
  `allowedExtensions` lacks `mymod`, and an agent runs `mymod:gear`
- **THEN** the result is `err not_allowed mymod:gear`, and within a second
  of `mymod` being added to the file the command runs

#### Scenario: Under all
- **WHEN** `agentCommands` is `all` and `allowedExtensions` lacks `mymod`
- **THEN** `mymod:gear` runs

#### Scenario: An event that isn't allowed
- **WHEN** `mymod` isn't allowed and emits `assembled`
- **THEN** every session gets `! mymod:assembled`

#### Scenario: Help names the setting
- **WHEN** `agentCommands` is `play`, `mymod` isn't allowed and an agent
  runs `help`
- **THEN** the `mymod` row says it isn't allowed and names
  `allowedExtensions` in `config/blockpilot/config.json`
