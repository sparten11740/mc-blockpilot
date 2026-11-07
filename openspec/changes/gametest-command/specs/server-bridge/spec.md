## ADDED Requirements

### Requirement: Running GameTests
Where Blockpilot supports it, `gametest run <test|namespace:*>` SHALL run
the selected GameTests on the integrated server and answer once they have
finished with a row per test, `passed` or `failed` with its message. It
SHALL fail with `err unsupported` where the game has no GameTests enabled,
naming what enables them, and on a version or loader without a result hook
or a client without an integrated server, naming which.

#### Scenario: A namespace's tests
- **WHEN** an agent runs `gametest run blockpilot_fixture:*`, the dev
  fixture's namespace, in a dev client with its GameTests enabled
- **THEN** the reply lists the passing test as `passed` and the failing one
  as `failed` with its message

#### Scenario: Not enabled
- **WHEN** an agent runs `gametest run blockpilot_fixture:*` in a game
  without GameTests enabled
- **THEN** the result is `err unsupported` naming what enables them
