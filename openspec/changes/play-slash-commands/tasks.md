# Tasks

## 1. Slash commands by root (agent-safety)

- [ ] 1.1 Add `playSlashCommands` to `Config` (cleaned like the other lists), `SafetyPolicy.rootOf` and `slashCommandAllowed` to both policies, the per-root `cmd` check in `CommandGate` and the per-root send hooks in `PlayGuard` (D1); verify with unit tests for the root parsing (`/fill`, `minecraft:fill`, `FILL`, empty), each policy and the gate in both profiles
- [ ] 1.2 Document `playSlashCommands` in docs/safety.md and docs/commands.md, warning in bold never to list `execute` or `function` and suggesting a backup before building with `fill`; verify the names used exist

## 2. Other mods only under play (extension-api)

- [ ] 2.1 Make `extensionAllowed` answer true under `all`, `eventAllowed` true in both policies, and drop the engine's own-namespace list (D2); verify with unit tests that events always pass and calls are refused only under `play`
- [ ] 2.2 Say so in docs/extending.md (and that extensions filter words through `isTrusted`), docs/safety.md, the README's Security section and the CHANGELOG's 1.1.1 section; verify the docs agree with the spec

## 3. Integration checks

- [ ] 3.1 Update and add security smoke checks, restoring the config afterwards: under `play` with `playSlashCommands: ["fill"]`, `cmd fill ~ ~ ~ ~ ~ ~ stone` runs and `cmd op X` is refused naming `op`; an agent-typed `/fill` goes out and `/op` doesn't; `devcheck:ping` runs under `all` without `allowedExtensions` and is refused under `play`; verify on 1.21.1 NeoForge
- [ ] 3.2 Run `tools/test_targets.py --suite security` on all 11 targets; verify every target passes

## Workflow follow-up

- Archive this change and delete the archived tasks.md.
