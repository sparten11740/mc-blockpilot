## ADDED Requirements

### Requirement: Running GameTests
`gametest run <selector>` SHALL run the registered GameTests whose ids match the
selector, where `*` matches any run of characters, on the integrated server, and
answer once they have finished with a row per test, `passed` or `failed` with
its message.

#### Scenario: A namespace's tests
- **WHEN** an agent runs `gametest run blockpilot_smoke:*` in a 26.x world
  with the smoke check's data pack, one test passing and one failing
- **THEN** the reply lists the passing test as `passed` and the failing one
  as `failed` with its message

#### Scenario: Tests in code on 1.21.1
- **WHEN** an agent runs `gametest run blockpilotsmoketests.*` in a 1.21.1
  dev client, with Blockpilot's own dev tests registered
- **THEN** the reply lists `blockpilotsmoketests.passes` as `passed` and
  `blockpilotsmoketests.fails` as `failed` with its message

### Requirement: When GameTests can't run
Without an integrated server `gametest run` SHALL fail with `err no_server`, and
on NeoForge without GameTests enabled with `err unsupported` naming what enables
them, whatever the selector; otherwise a selector that matches no test SHALL
fail with `err no_test`.

#### Scenario: No such test
- **WHEN** an agent runs `gametest run nothing_here:*`
- **THEN** the result is `err no_test`

#### Scenario: Not enabled
- **WHEN** an agent runs `gametest run *` on NeoForge without
  `-Dneoforge.enableGameTest=true`
- **THEN** the result is `err unsupported` naming that property
