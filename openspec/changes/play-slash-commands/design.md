# Design

## Context

- Every safety decision is a `SafetyPolicy` method, answered by
  `ConfiguredPolicy` and `AllowAllPolicy`, and swapped whole on reload
  (add-agent-safety D11).

## Decisions

### D1: Slash commands by root, as a policy method

`SafetyPolicy.slashCommandAllowed(root)`: `ConfiguredPolicy` answers
`!playOnly || playSlashCommands` holds the root ignoring case, and
`AllowAllPolicy` answers true. `SafetyPolicy.rootOf(command)` takes a
command's first word, drops a leading `/` and a `minecraft:` prefix, and
lowercases it; an empty command has the root "". The command gate no longer
refuses `cmd` as a whole: it reads `cmd`'s argument and refuses only a root
the policy doesn't allow, naming it (`cmd op`). `PlayGuard`'s send hooks ask
the same method for a command an agent typed or clicked, so one list
decides both ways.

`execute` and `function` run any other command, so listing either undoes
the list; the docs say so in bold, as the code can't tell intent.

### D2: Other mods only under play, events always

`ConfiguredPolicy.extensionAllowed` becomes `!playOnly || builtIn ||
allowedExtensions` holds the namespace: under `all`, as before 1.1.0, every
mod's commands run. Server-side extension commands stay refused under
`play` by the command gate. `eventAllowed` answers true in both policies,
so the engine's own-namespace list goes: an extension that turns players'
words into events filters them through `isTrusted`, as docs/extending.md
asks.

### D3: Trust with everything allowed

With `dangerouslyAllowAll` on, `trust` reports `dangerously_allow_all` and
lists no names, as `AllowAllPolicy` has none to give and everyone counts
as trusted; the spec now says so.

## Risks / Trade-offs

- [A listed root that runs others, such as `execute`] → Bold warnings in
  docs/safety.md and docs/commands.md.
- [`fill` can wreck a build] → The docs suggest a world backup first.
