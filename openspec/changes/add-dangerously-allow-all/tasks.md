# Tasks

## 1. The switch (agent-safety)

- [x] 1.1 Add `dangerouslyAllowAll` to `Config` (false by default and in a broken file's fallback), make `playOnly()` false while it is on, and lift trust (`Trust`) and allowed extensions (`Engine`, events included) through flags set at load and on reload (D1); verify with unit tests for each lifted check and the fail-closed default
- [x] 1.2 Show it (D2): a warning at start and on each change while on, `dangerously_allow_all` in `trust`, "safety off" in the agent indicator; verify with a unit test for `trust` and in a 1.21.1 NeoForge dev client
- [x] 1.3 Document it as dangerous in docs/safety.md (what it turns off, when it is fine), docs/commands.md (`trust`), the README's Security section and a new "## Unreleased" CHANGELOG section; verify the settings and commands named exist

## 2. Integration checks

- [x] 2.1 Add a security smoke check: with `dangerouslyAllowAll` on, `devcheck:ping` runs without `allowedExtensions`, a command `play` refuses runs, and `trust` reports the flag, the config restored afterwards; verify it passes on 1.21.1 NeoForge
- [ ] 2.2 Run `tools/test_targets.py --suite security` on all 11 targets; verify every target passes

## Workflow follow-up

- Archive this change and delete the archived tasks.md.
