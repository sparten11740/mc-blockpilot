# Spec Delta

## ADDED Requirements

### Requirement: Server-thread extension handlers
An extension command SHALL be able to declare that it runs on the integrated server thread. Its query runs between server ticks; its action's start, tick and cancel run on server ticks. Such commands follow the server-bridge gate and can be used with `clock=server` in `sample` and `wait`.

#### Scenario: Server-side build command
- **WHEN** an extension registers `aerolarge:build` as a server-thread action and an agent runs it in a cheats-enabled singleplayer world
- **THEN** the handler runs on the server thread, and the result returns after the build has finished

#### Scenario: Server-thread command without integrated server
- **WHEN** an agent runs a server-thread extension command while connected to a remote server
- **THEN** the result is `err no_server`, and the handler is not called

#### Scenario: Server-clock sampling of an extension query
- **WHEN** an agent runs `sample sable:info <uuid> every 40t x5 clock=server` and `sable:info` is a server-thread query
- **THEN** five rows are returned, taken on server ticks exactly 40 apart
