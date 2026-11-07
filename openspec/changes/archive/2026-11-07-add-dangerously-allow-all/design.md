# Design

## Context

- add-agent-safety put the safety settings in `protocol/Config`, reloaded
  by `ConfigReload` into `Trust`, the `Engine` (play profile, allowed
  extensions) and `PlayGuard` (screens, links, slash commands), which
  reads `Config.playOnly()`.

## Decisions

### D1: One flag, read at each check

`Config.dangerouslyAllowAll` defaults to false, and so does the config a
broken file yields. `Config.playOnly()` answers false while it is on, so
the engine's command refusals and `PlayGuard`'s screen, link and slash
command refusals all lift with no change of their own. `Trust` and the
`Engine` each get a volatile flag, set at load and on every reload:
`Trust.trusts` answers true for everyone (so chat, the server's log line
and `isTrusted` follow), and `Engine` uses every extension's commands and
events. Each check reads its flag where it decides, so turning it off in
the file takes effect within the same second.

### D2: Hard to miss

A warning in the log at start and on each change of the file while it is
on, `dangerously_allow_all` in `trust`, and "safety off" in the agent
indicator, which recording already hides.

## Risks / Trade-offs

- [A person leaves it on while playing with others] → The indicator, the
  log and `trust` show it; the docs call it dangerous and say when it is
  fine.
